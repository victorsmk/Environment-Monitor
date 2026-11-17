# PICO METEO: weather station on a Raspberry Pi Pico W.
#
# Measures temperature and pressure (BMP280), humidity (DHT22), light (BH1750) and sound level
# (MAX4466 microphone), shows them on the OLED and prints them on the serial console.
#
# Files needed on the Pico: main.py, config.py, logic.py, lightsensor.py (all from this folder), and
# the libraries bmp280.py and ssd1306.py that were already on the Pico. See pico/README.md.

import sys
import time
import _thread
import machine
from machine import Pin, I2C, ADC

import dht
import ssd1306
from bmp280 import BMP280

import config
from lightsensor import BH1750
from logic import Readings, NoiseMeter, age_ms, fmt, build_payload, missing_letters


class Station:
    """All the hardware and the jobs that keep it going. step() is one pass of the main loop."""

    def __init__(self):
        self.i2c_sensors = I2C(
            config.I2C_SENSORS_ID, sda=Pin(config.SDA_SENSORS), scl=Pin(config.SCL_SENSORS), freq=config.I2C_FREQ
        )
        self.i2c_oled = I2C(
            config.I2C_OLED_ID, sda=Pin(config.SDA_OLED), scl=Pin(config.SCL_OLED), freq=config.I2C_FREQ
        )
        self.dht = dht.DHT22(Pin(config.DHT_PIN))
        self.mic = ADC(Pin(config.MIC_PIN))

        # Sensors that can fail to start (or break later) are None until they work.
        self.oled = None
        self.bmp = None
        self.light = None

        self.readings = Readings({
            "temperature": config.MAX_AGE_BMP,
            "pressure": config.MAX_AGE_BMP,
            "humidity": config.MAX_AGE_DHT,
            "temperature_dht": config.MAX_AGE_DHT,
            "light": config.MAX_AGE_LIGHT,
        })
        self.meter = NoiseMeter(
            window_ms=config.MIC_WINDOW_MS,
            report_ms=config.MIC_REPORT_MS,
            min_window_samples=config.MIC_MIN_WINDOW_SAMPLES,
            quiet=config.NOISE_RMS_QUIET,
            loud=config.NOISE_RMS_LOUD,
            max_age=config.MAX_AGE_SOUND,
        )
        self._dc = None  # the microphone's resting level, times 1024

        self._last_error_print = {}
        self._failures = {"BMP280": 0, "BH1750": 0, "OLED": 0}

        self.started = time.ticks_ms()
        now = self.started
        # Every job is due at once, the first time round.
        self._due = {
            "bmp": now - config.INTERVAL_BMP,
            "dht": now - config.INTERVAL_DHT,
            "light": now - config.INTERVAL_LIGHT,
            "display": now - config.INTERVAL_DISPLAY,
            "report": now - config.INTERVAL_REPORT,
            "reinit": now,
        }
        self._light_started = None  # when the running light measurement was started, else None
        self._dht_threaded = False  # True when the DHT22 is read on the second core
        self._stop = False          # only used by tests, to stop the DHT22 thread

        self.init_missing()

    # ----- starting sensors ------------------------------------------------

    def init_missing(self):
        """Try to start every sensor that is not running. Called at start-up and every few seconds."""
        if self.oled is None:
            try:
                self.oled = ssd1306.SSD1306_I2C(128, 64, self.i2c_oled)
                self._failures["OLED"] = 0
                print("OLED started.")
            except Exception as e:
                self.oled = None
                self._error("OLED", e)
        if self.bmp is None:
            try:
                self.bmp = BMP280(self.i2c_sensors)
                self._failures["BMP280"] = 0
                print("BMP280 started.")
            except Exception as e:
                self.bmp = None
                self._error("BMP280", e)
        if self.light is None:
            try:
                self.light = BH1750(self.i2c_sensors)
                self._failures["BH1750"] = 0
                print("BH1750 started.")
            except Exception as e:
                self.light = None
                self._error("BH1750", e)

    def _error(self, name, error):
        """Print an error, but not the same one more than once every 5 seconds."""
        now = time.ticks_ms()
        last = self._last_error_print.get(name)
        if last is None or age_ms(now, last) >= 5000:
            print("[ERROR]", name, ":", error)
            self._last_error_print[name] = now

    def _failed(self, name, error):
        """Count a failed read. After 5 in a row the sensor is dropped so init_missing() starts it afresh."""
        self._error(name, error)
        self._failures[name] += 1
        return self._failures[name] >= 5

    # ----- the microphone --------------------------------------------------

    def sample_burst(self, duration_us):
        """Sample the microphone for `duration_us`, as close to MIC_SAMPLE_INTERVAL_US apart as possible.

        Returns (number of samples, sum of the squared distances from the resting level). Everything
        here is small whole numbers on purpose: no floats and no big integers, so there is no memory
        allocation, and the loop stays fast and steady.
        """
        read = self.mic.read_u16
        ticks_us = time.ticks_us
        diff = time.ticks_diff
        add = time.ticks_add
        step = config.MIC_SAMPLE_INTERVAL_US
        dc = self._dc
        n = 0
        sum_sq = 0
        t = ticks_us()
        end = add(t, duration_us)
        nxt = t
        while True:
            t = ticks_us()
            if diff(end, t) <= 0:
                break
            if diff(t, nxt) >= 0:
                v = read() >> 4  # 12 bits: 0-4095
                if dc is None:
                    dc = v << 10
                rest = dc >> 10
                dc += v - rest  # the resting level follows the signal slowly (about a quarter of a second)
                d = v - rest
                if d > 1000:
                    d = 1000
                elif d < -1000:
                    d = -1000
                sum_sq += d * d
                n += 1
                nxt = add(nxt, step)
                if diff(t, nxt) > 0:
                    # Fell behind (another job took long): carry on from now, do not catch up.
                    nxt = add(t, step)
        self._dc = dc
        return n, sum_sq

    # ----- the other sensors -----------------------------------------------

    def read_bmp(self, now):
        if self.bmp is None:
            return
        try:
            temperature = float(self.bmp.temperature)
            pressure = float(self.bmp.pressure) / 100
            self.readings.set("temperature", temperature, now)
            self.readings.set("pressure", pressure, now)
            self._failures["BMP280"] = 0
        except Exception as e:
            if self._failed("BMP280", e):
                self.bmp = None

    def read_dht(self):
        """Read the DHT22. This takes about 270 ms (the sensor library waits), see start_background_jobs()."""
        try:
            self.dht.measure()
            humidity = float(self.dht.humidity())
            temperature = float(self.dht.temperature())
            if 0 <= humidity <= 100:
                now = time.ticks_ms()
                self.readings.set("humidity", humidity, now)
                self.readings.set("temperature_dht", temperature, now)
        except Exception as e:
            self._error("DHT22", e)  # it fails now and then; the next read is soon, and a value lives 10 s

    def start_background_jobs(self):
        """Read the DHT22 on the Pico's second core.

        Its library waits about 270 ms for every reading. Done in the main loop that would leave the
        microphone unheard for that long every few seconds (measured on the Pico: the sample rate swung
        between 2200 and 4000 per second). On the second core the microphone keeps its steady rate.
        If the thread cannot start (for example an older run is still alive after Stop in Thonny), the
        main loop reads the DHT22 itself instead.
        """
        try:
            _thread.start_new_thread(self._dht_loop, ())
            self._dht_threaded = True
        except Exception as e:
            self._dht_threaded = False
            print("The DHT22 thread could not start (" + str(e) + "), reading it in the main loop instead.")
            print("After Stop in Thonny use Stop/Restart backend (Ctrl+F2) before running again.")

    def _dht_loop(self):
        while not self._stop:
            self.read_dht()
            time.sleep_ms(config.INTERVAL_DHT)

    def close(self):
        """Stop the DHT22 thread. Only tests need this; on the Pico it runs until power off."""
        self._stop = True

    def light_step(self, now):
        """Start a light measurement when one is due, and collect it a moment later. Never waits."""
        if self.light is None:
            return
        try:
            if self._light_started is None:
                if age_ms(now, self._due["light"]) >= config.INTERVAL_LIGHT:
                    self.light.trigger()
                    self._light_started = now
                    self._due["light"] = now
            elif age_ms(now, self._light_started) >= config.LIGHT_MEASURE_MS:
                self._light_started = None
                self.readings.set("light", float(self.light.read()), now)
                self._failures["BH1750"] = 0
        except Exception as e:
            self._light_started = None
            if self._failed("BH1750", e):
                self.light = None

    # ----- showing the results ---------------------------------------------

    def payload(self, now):
        """Everything measured, ready for the app (see docs/pico-protocol.md)."""
        return build_payload(self.readings, self.meter, now, age_ms(now, self.started))

    def show(self, now):
        if self.oled is None:
            return
        p = self.payload(now)
        oled = self.oled
        try:
            oled.fill(0)
            oled.text("PICO METEO", 25, 0)
            oled.text("Temp: " + fmt(p["temperature"], 1) + " C", 0, 10)
            oled.text("Umid: " + fmt(p["humidity"], 0) + " %", 0, 20)
            oled.text("Lux: " + fmt(p["light"], 0), 0, 30)
            oled.text("Zgomot: " + fmt(p["sound"], 0) + "/100", 0, 40)
            missing = missing_letters(p)
            oled.text("Senzori: OK" if missing == "" else "Fara: " + missing, 0, 54)
            oled.show()
            self._failures["OLED"] = 0
        except Exception as e:
            if self._failed("OLED", e):
                self.oled = None

    def report(self, now):
        """One line on the serial console."""
        p = self.payload(now)
        sound = self.meter.current(now)
        print(
            "T=" + fmt(p["temperature"], 1),
            "H=" + fmt(p["humidity"], 0),
            "L=" + fmt(p["light"], 0),
            "S=" + fmt(p["sound"], 0),
            "| P=" + fmt(p["pressure_hpa"], 1),
            "Tdht=" + fmt(p["temperature_dht"], 1),
            "| rms=" + fmt(None if sound is None else sound[1], 1),
            "peak=" + fmt(None if sound is None else sound[2], 1),
            "samples/s=" + str(self.meter.samples),
            "| missing:" + (missing_letters(p) or "-"),
        )

    # ----- the main loop ---------------------------------------------------

    def step(self):
        """One pass: listen to the microphone for a moment, then do whatever job is due."""
        n, sum_sq = self.sample_burst(config.MIC_BURST_US)
        now = time.ticks_ms()
        self.meter.add(n, sum_sq, now)

        self.light_step(now)
        if age_ms(now, self._due["bmp"]) >= config.INTERVAL_BMP:
            self._due["bmp"] = now
            self.read_bmp(now)
        if not self._dht_threaded and age_ms(now, self._due["dht"]) >= config.INTERVAL_DHT:
            self._due["dht"] = now
            self.read_dht()
        if age_ms(now, self._due["display"]) >= config.INTERVAL_DISPLAY:
            self._due["display"] = now
            self.show(now)
        if age_ms(now, self._due["report"]) >= config.INTERVAL_REPORT:
            self._due["report"] = now
            self.report(now)
        if age_ms(now, self._due["reinit"]) >= config.INTERVAL_REINIT:
            self._due["reinit"] = now
            if self.oled is None or self.bmp is None or self.light is None:
                self.init_missing()

    def boot_screen(self):
        if self.oled is None:
            return
        try:
            self.oled.fill(0)
            self.oled.rect(0, 0, 128, 64, 1)
            self.oled.text("PICO METEO", 25, 20)
            self.oled.text("Pornire...", 32, 35)
            self.oled.show()
            time.sleep_ms(500)
        except Exception as e:
            self._error("OLED", e)


def run(max_loops=None):
    """Run the station. `max_loops` is only for tests on a PC; on the Pico it runs for ever."""
    station = Station()
    station.boot_screen()
    station.start_background_jobs()

    watchdog = None
    if config.USE_WATCHDOG:
        # From here on, a program that stops answering for WATCHDOG_MS makes the Pico restart.
        watchdog = machine.WDT(timeout=config.WATCHDOG_MS)

    print("PICO METEO is running.")
    loops = 0
    while max_loops is None or loops < max_loops:
        station.step()
        if watchdog is not None:
            watchdog.feed()
        loops += 1
    return station


def main():
    try:
        run()
    except KeyboardInterrupt:
        raise  # Stop pressed in Thonny: leave quietly
    except Exception as e:
        # Something unexpected. Show it, then restart so the station comes back by itself.
        sys.print_exception(e)
        time.sleep_ms(1000)
        machine.reset()


if __name__ == "__main__":
    main()
