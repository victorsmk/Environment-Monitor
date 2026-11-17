"""Runs pico/main.py on a PC against pretend hardware: pretend microphone, sensors and display.

It cannot say how fast the real Pico is. It does check that the program's logic holds together: values
get through, a failing sensor shows as "--" and does not stop the others, a broken sensor is started
again when it comes back, and the light sensor is never waited for. Run from the repository root:

    python -m unittest discover -s pico/tests -v
"""
import os
import sys
import time
import types
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

# ---- pretend MicroPython: the time functions the Pico has and a PC does not ----------------------
_MASK = 0x3FFFFFFF
_HALF = 0x20000000
_T0 = time.monotonic()


def _now_us():
    return int((time.monotonic() - _T0) * 1_000_000)


time.ticks_us = lambda: _now_us() & _MASK
time.ticks_ms = lambda: (_now_us() // 1000) & _MASK
time.ticks_add = lambda a, b: (a + b) & _MASK
time.ticks_diff = lambda a, b: ((a - b + _HALF) & _MASK) - _HALF
time.sleep_ms = lambda ms: time.sleep(ms / 1000)
time.sleep_us = lambda us: time.sleep(us / 1_000_000)
sys.print_exception = lambda e: print("(print_exception)", repr(e))


# ---- pretend hardware ----------------------------------------------------------------------------
class World:
    """What the pretend hardware does. Tests change these."""

    bmp_fails = False
    dht_fails = False
    light_fails = False
    oled_fails = False
    mic_swing = 800            # 16-bit units either side of the middle
    events = []                # ("trigger" | "read", time)
    wdt_feeds = 0
    resets = 0
    last_screen = {}

    @classmethod
    def reset(cls):
        cls.bmp_fails = cls.dht_fails = cls.light_fails = cls.oled_fails = False
        cls.mic_swing = 800
        cls.events = []
        cls.wdt_feeds = 0
        cls.resets = 0
        cls.last_screen = {}


machine = types.ModuleType("machine")


class Pin:
    def __init__(self, *args, **kwargs):
        pass


class I2C:
    def __init__(self, *args, **kwargs):
        pass

    def scan(self):
        return [0x23]

    def writeto(self, address, data):
        if World.light_fails:
            raise OSError(5, "EIO")
        if bytes(data) == b"\x20":
            World.events.append(("trigger", time.monotonic()))

    def readfrom(self, address, count):
        if World.light_fails:
            raise OSError(5, "EIO")
        World.events.append(("read", time.monotonic()))
        return bytes([0x03, 0xE8])  # 1000 / 1.2 = 833.3 lux


class ADC:
    def __init__(self, pin):
        self._flip = False

    def read_u16(self):
        self._flip = not self._flip
        return 32768 + (World.mic_swing if self._flip else -World.mic_swing)


class WDT:
    def __init__(self, timeout=0):
        self.timeout = timeout

    def feed(self):
        World.wdt_feeds += 1


def _reset():
    World.resets += 1


machine.Pin, machine.I2C, machine.ADC, machine.WDT, machine.reset = Pin, I2C, ADC, WDT, _reset
sys.modules["machine"] = machine

dht = types.ModuleType("dht")


class DHT22:
    def __init__(self, pin):
        pass

    def measure(self):
        if World.dht_fails:
            raise OSError(110, "ETIMEDOUT")

    def humidity(self):
        return 48.2

    def temperature(self):
        return 21.4


dht.DHT22 = DHT22
sys.modules["dht"] = dht

ssd1306 = types.ModuleType("ssd1306")


class SSD1306_I2C:
    def __init__(self, width, height, i2c):
        if World.oled_fails:
            raise OSError(5, "EIO")
        self._lines = {}

    def fill(self, colour):
        self._lines = {}

    def rect(self, *args):
        pass

    def text(self, text, x, y):
        self._lines[y] = text

    def show(self):
        if World.oled_fails:
            raise OSError(5, "EIO")
        World.last_screen = dict(self._lines)


ssd1306.SSD1306_I2C = SSD1306_I2C
sys.modules["ssd1306"] = ssd1306

bmp280 = types.ModuleType("bmp280")


class BMP280:
    def __init__(self, i2c):
        if World.bmp_fails:
            raise OSError(19, "ENODEV")

    @property
    def temperature(self):
        if World.bmp_fails:
            raise OSError(5, "EIO")
        return 22.46

    @property
    def pressure(self):
        if World.bmp_fails:
            raise OSError(5, "EIO")
        return 101324.9


bmp280.BMP280 = BMP280
sys.modules["bmp280"] = bmp280

import config  # noqa: E402
import main  # noqa: E402

FAST = dict(
    INTERVAL_BMP=30, INTERVAL_DHT=30, INTERVAL_LIGHT=60, LIGHT_MEASURE_MS=40, INTERVAL_DISPLAY=30,
    INTERVAL_REPORT=1000, INTERVAL_REINIT=100,
    MAX_AGE_BMP=300, MAX_AGE_DHT=300, MAX_AGE_LIGHT=300, MAX_AGE_SOUND=600,
    MIC_BURST_US=5000, MIC_WINDOW_MS=40, MIC_REPORT_MS=200, MIC_MIN_WINDOW_SAMPLES=20,
    USE_WATCHDOG=True,
)


class StationTestCase(unittest.TestCase):
    def setUp(self):
        World.reset()
        self._stations = []
        self._saved = {name: getattr(config, name) for name in FAST}
        for name, value in FAST.items():
            setattr(config, name, value)

    def tearDown(self):
        for station in self._stations:
            station.close()
        for name, value in self._saved.items():
            setattr(config, name, value)

    def start(self):
        station = main.Station()
        station._dc = 2048 << 10  # the microphone is already at rest, as it is a while after start-up
        self._stations.append(station)
        return station

    def run_for(self, station, seconds):
        end = time.monotonic() + seconds
        loops = 0
        while time.monotonic() < end:
            station.step()
            loops += 1
        return loops

    def payload(self, station):
        return station.payload(time.ticks_ms())


class NormalOperation(StationTestCase):
    def test_all_four_values_arrive(self):
        station = self.start()
        self.run_for(station, 0.8)
        p = self.payload(station)
        self.assertEqual(p["temperature"], 22.5)      # BMP280 only
        self.assertEqual(p["humidity"], 48.2)         # DHT22 only
        self.assertAlmostEqual(p["light"], 833.3, places=1)
        self.assertIsNotNone(p["sound"])
        self.assertEqual(p["pressure_hpa"], 1013.2)
        self.assertEqual(p["temperature_dht"], 21.4)  # a diagnostic, never the scored temperature

    def test_the_sound_level_matches_the_microphone(self):
        station = self.start()
        self.run_for(station, 1.0)
        p = self.payload(station)
        # The pretend microphone swings 800 (16-bit units) either side: rms 800 -> level 30.
        self.assertAlmostEqual(p["noise_rms"], 800, delta=40)
        self.assertIn(p["sound"], range(27, 34))

    def test_silence_reads_zero(self):
        World.mic_swing = 10
        station = self.start()
        self.run_for(station, 0.8)
        self.assertEqual(self.payload(station)["sound"], 0)

    def test_the_display_shows_the_values(self):
        station = self.start()
        self.run_for(station, 0.8)
        screen = World.last_screen
        self.assertEqual(screen[10], "Temp: 22.5 C")
        self.assertEqual(screen[20], "Umid: 48 %")
        self.assertEqual(screen[30], "Lux: 833")
        self.assertTrue(screen[40].startswith("Zgomot: "))
        self.assertEqual(screen[54], "Senzori: OK")

    def test_the_watchdog_is_fed_every_pass(self):
        World.reset()
        loops_before = World.wdt_feeds
        station = main.run(max_loops=25)
        self._stations.append(station)
        self.assertEqual(World.wdt_feeds - loops_before, 25)
        self.assertIsNotNone(station)

    def test_the_watchdog_can_be_switched_off(self):
        config.USE_WATCHDOG = False
        self._stations.append(main.run(max_loops=5))
        self.assertEqual(World.wdt_feeds, 0)


class TheDhtOnTheSecondCore(StationTestCase):
    def test_humidity_arrives_while_the_main_loop_never_reads_the_dht22_itself(self):
        reads = []
        original = main.Station.read_dht

        def counting(self):
            reads.append(__import__("threading").current_thread().name)
            original(self)

        main.Station.read_dht = counting
        try:
            station = self.start()
            station.start_background_jobs()
            self.assertTrue(station._dht_threaded)
            self.run_for(station, 0.6)
        finally:
            main.Station.read_dht = original
        self.assertEqual(self.payload(station)["humidity"], 48.2)
        self.assertGreater(len(reads), 3)
        self.assertNotIn("MainThread", reads)

    def test_a_failing_dht22_shows_dashes_and_comes_back(self):
        station = self.start()
        station.start_background_jobs()
        self.run_for(station, 0.4)
        self.assertEqual(self.payload(station)["humidity"], 48.2)
        World.dht_fails = True
        self.run_for(station, 0.8)
        self.assertIsNone(self.payload(station)["humidity"])
        World.dht_fails = False
        self.run_for(station, 0.5)
        self.assertEqual(self.payload(station)["humidity"], 48.2)

    def test_if_the_thread_cannot_start_the_main_loop_reads_the_dht22(self):
        def refuse(function, args):
            raise OSError("can't create thread")

        original = main._thread.start_new_thread
        main._thread.start_new_thread = refuse
        try:
            station = self.start()
            station.start_background_jobs()
        finally:
            main._thread.start_new_thread = original
        self.assertFalse(station._dht_threaded)
        self.run_for(station, 0.5)
        self.assertEqual(self.payload(station)["humidity"], 48.2)


class TheLightSensorNeverBlocks(StationTestCase):
    def test_it_is_started_and_collected_later_while_the_loop_keeps_running(self):
        station = self.start()
        loops = self.run_for(station, 0.5)
        triggers = [t for kind, t in World.events if kind == "trigger"]
        reads = [t for kind, t in World.events if kind == "read"]
        self.assertGreaterEqual(len(triggers), 2)
        self.assertGreaterEqual(len(reads), 2)
        # Each result is collected after the measurement time, not straight away...
        for trigger, read in zip(triggers, reads):
            self.assertGreaterEqual(read - trigger, config.LIGHT_MEASURE_MS / 1000 - 0.002)
        # ...and between the two, the program carried on (many passes, not one long wait).
        self.assertGreater(loops, 20)


class WhenASensorFails(StationTestCase):
    def test_humidity_shows_dashes_when_the_dht22_stops_but_the_rest_goes_on(self):
        station = self.start()
        self.run_for(station, 0.5)
        self.assertEqual(self.payload(station)["humidity"], 48.2)

        World.dht_fails = True
        self.run_for(station, 0.8)   # longer than MAX_AGE_DHT (0.3 s)
        p = self.payload(station)
        self.assertIsNone(p["humidity"])
        self.assertIsNone(p["temperature_dht"])
        self.assertEqual(p["temperature"], 22.5)
        self.assertIsNotNone(p["light"])
        self.assertEqual(World.last_screen[20], "Umid: -- %")
        self.assertEqual(World.last_screen[54], "Fara: H")

    def test_one_failed_read_does_not_blank_the_value(self):
        station = self.start()
        self.run_for(station, 0.4)
        World.dht_fails = True
        self.run_for(station, 0.1)   # a couple of failed reads, well inside the 0.3 s limit
        self.assertEqual(self.payload(station)["humidity"], 48.2)

    def test_a_value_comes_back_when_the_sensor_recovers(self):
        station = self.start()
        World.dht_fails = True
        self.run_for(station, 0.5)
        self.assertIsNone(self.payload(station)["humidity"])
        World.dht_fails = False
        self.run_for(station, 0.3)
        self.assertEqual(self.payload(station)["humidity"], 48.2)

    def test_a_bmp280_that_breaks_is_dropped_and_started_again_when_it_is_back(self):
        station = self.start()
        self.run_for(station, 0.3)
        self.assertIsNotNone(station.bmp)

        World.bmp_fails = True
        self.run_for(station, 0.8)
        self.assertIsNone(station.bmp)                       # dropped after 5 failed reads
        self.assertIsNone(self.payload(station)["temperature"])
        self.assertEqual(World.last_screen[10], "Temp: -- C")

        World.bmp_fails = False
        self.run_for(station, 0.5)                           # INTERVAL_REINIT is 0.1 s
        self.assertIsNotNone(station.bmp)
        self.assertEqual(self.payload(station)["temperature"], 22.5)

    def test_a_bmp280_that_is_missing_at_start_is_found_later(self):
        World.bmp_fails = True
        station = self.start()
        self.assertIsNone(station.bmp)
        self.run_for(station, 0.2)
        self.assertIsNone(self.payload(station)["temperature"])
        World.bmp_fails = False
        self.run_for(station, 0.4)
        self.assertEqual(self.payload(station)["temperature"], 22.5)

    def test_a_light_sensor_that_fails_shows_dashes_and_the_rest_goes_on(self):
        station = self.start()
        self.run_for(station, 0.5)
        self.assertIsNotNone(self.payload(station)["light"])
        World.light_fails = True
        self.run_for(station, 0.8)
        p = self.payload(station)
        self.assertIsNone(p["light"])
        self.assertEqual(p["temperature"], 22.5)
        World.light_fails = False
        self.run_for(station, 0.6)
        self.assertAlmostEqual(self.payload(station)["light"], 833.3, places=1)

    def test_a_broken_display_does_not_stop_the_measuring(self):
        station = self.start()
        self.run_for(station, 0.3)
        World.oled_fails = True
        self.run_for(station, 0.6)
        self.assertIsNone(station.oled)                      # dropped after 5 failed updates
        self.assertEqual(self.payload(station)["temperature"], 22.5)
        World.oled_fails = False
        self.run_for(station, 0.4)
        self.assertIsNotNone(station.oled)

    def test_everything_failing_at_once_does_not_crash(self):
        World.bmp_fails = World.dht_fails = World.light_fails = World.oled_fails = True
        station = self.start()
        self.run_for(station, 0.5)
        p = self.payload(station)
        self.assertIsNone(p["temperature"])
        self.assertIsNone(p["humidity"])
        self.assertIsNone(p["light"])
        self.assertIsNotNone(p["sound"])                     # the microphone has no failure mode here


class WhenSomethingUnexpectedHappens(StationTestCase):
    def test_the_station_restarts_itself(self):
        original = main.Station.step

        def broken(self):
            raise RuntimeError("something nobody planned for")

        main.Station.step = broken
        try:
            main.main()
        finally:
            main.Station.step = original
        self.assertEqual(World.resets, 1)

    def test_stop_in_thonny_is_not_turned_into_a_restart(self):
        original = main.Station.step

        def interrupted(self):
            raise KeyboardInterrupt()

        main.Station.step = interrupted
        try:
            with self.assertRaises(KeyboardInterrupt):
                main.main()
        finally:
            main.Station.step = original
        self.assertEqual(World.resets, 0)


class TheMicrophoneLoop(StationTestCase):
    def test_a_burst_returns_samples_close_to_the_target_rate(self):
        station = self.start()
        n, sum_sq = station.sample_burst(20000)
        # A PC is not a Pico, but the pacing logic should still land near 250 us per sample (80 in 20 ms).
        self.assertGreater(n, 40)
        self.assertLessEqual(n, 90)
        # Swing of 800 (16-bit) = 50 (12-bit): every squared distance is about 2500.
        self.assertAlmostEqual(sum_sq / n, 2500, delta=300)

    def test_the_sums_stay_small_whole_numbers(self):
        World.mic_swing = 32000  # a microphone driven to the rails
        station = self.start()
        n, sum_sq = station.sample_burst(10000)
        self.assertIsInstance(sum_sq, int)
        self.assertLessEqual(sum_sq / n, 1000 * 1000)        # each distance is limited to 1000


if __name__ == "__main__":
    unittest.main()
