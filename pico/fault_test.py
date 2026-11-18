"""Fault test for the real Pico: makes each sensor fail on purpose, one after the other, and checks that the
station copes. Nothing is unplugged: the failures are injected into the program's own sensor code, but the
program, the I2C chips, the timings and the display are all the real ones.

Run it from the PC with the Pico connected (about 3 minutes):

    python -m mpremote connect COM13 run pico/fault_test.py

What happens, in seconds from the start (watch the OLED too: it shows "--" and "Fara: ..."):

    10-35   the BMP280 fails      temperature and pressure must become "--", then come back by themselves
    50-70   the BH1750 fails      light must become "--", then come back
    85-115  the DHT22 fails       humidity must become "--" (after its 10 s limit), then come back
    130-145 the display fails     the measuring must go on; the heartbeat on the OLED stops, then returns
    165     done

The script prints CHECK lines and a verdict. When it ends the Pico restarts by itself (the watchdog, which
the script keeps fed while it runs), and the normal program takes over again. The Wi-Fi is off during the test.
"""
import time

import machine

import config
import main

config.USE_NETWORK = False  # a local test: no Wi-Fi, so the phone only sees the Pico leave and come back

STEP_SECONDS = 1.0  # lowered by tests on a PC to run the timeline faster


def failing(*args, **kwargs):
    raise OSError(5, "EIO")  # what a sensor that stops answering looks like to the program


class Fault:
    """Replaces a method of a library class with one that fails, and puts the real one back."""

    def __init__(self, owner, name):
        self.owner = owner
        self.name = name
        self.real = getattr(owner, name)

    def start(self):
        setattr(self.owner, self.name, failing)

    def stop(self):
        setattr(self.owner, self.name, self.real)


def run(scale=1.0):
    """Run the whole timeline. `scale` shortens the time between events (the sensor limits stay the same)."""
    wdt = machine.WDT(timeout=8000)  # the real program was interrupted: this keeps the board alive meanwhile

    bmp = Fault(main.BMP280, "measure")
    light = Fault(main.BH1750, "trigger")
    dht = Fault(main.dht.DHT22, "measure")
    display = Fault(main.ssd1306.SSD1306_I2C, "show")

    station = main.Station()
    station.start_background_jobs()

    results = []

    def check(description, ok):
        results.append(ok)
        print("CHECK", "ok  " if ok else "FAIL", description)

    def readings():
        return station.payload(time.ticks_ms())

    def has(p, *names):
        return all(p[n] is not None for n in names)

    def lacks(p, *names):
        return all(p[n] is None for n in names)

    events = [
        (10, "=== The BMP280 fails now ===", bmp.start),
        (35, "=== The BMP280 works again ===", bmp.stop),
        (50, "=== The BH1750 fails now ===", light.start),
        (70, "=== The BH1750 works again ===", light.stop),
        (85, "=== The DHT22 fails now ===", dht.start),
        (115, "=== The DHT22 works again ===", dht.stop),
        (130, "=== The display fails now ===", display.start),
        (145, "=== The display works again ===", display.stop),
    ]
    checks = [
        (9, "at the start all four sensors have values",
         lambda: has(readings(), "temperature", "humidity", "light", "sound", "pressure_hpa")),
        (33, "BMP280 failing: temperature and pressure show no value, the others carry on",
         lambda: lacks(readings(), "temperature", "pressure_hpa") and has(readings(), "humidity", "light", "sound")),
        (48, "BMP280 back: temperature and pressure return by themselves",
         lambda: has(readings(), "temperature", "pressure_hpa")),
        (68, "BH1750 failing: light shows no value, the others carry on",
         lambda: lacks(readings(), "light") and has(readings(), "temperature", "humidity", "sound")),
        (83, "BH1750 back: light returns by itself",
         lambda: has(readings(), "light")),
        (112, "DHT22 failing: humidity shows no value, the others carry on",
         lambda: lacks(readings(), "humidity", "temperature_dht") and has(readings(), "temperature", "light", "sound")),
        (128, "DHT22 back: humidity returns by itself",
         lambda: has(readings(), "humidity")),
        (142, "display failing: the measuring goes on",
         lambda: has(readings(), "temperature", "humidity", "light", "sound")),
        (163, "display back: it updates again without errors, and all four sensors have values",
         lambda: station.oled is not None and station._failures["OLED"] == 0
         and has(readings(), "temperature", "humidity", "light", "sound")),
    ]
    end = 165

    start = time.ticks_ms()
    next_event = 0
    next_check = 0
    print("Fault test: about %d seconds." % int(end * scale))
    while True:
        elapsed = time.ticks_diff(time.ticks_ms(), start) / 1000 / (STEP_SECONDS * scale)
        station.step()
        wdt.feed()
        if next_event < len(events) and elapsed >= events[next_event][0]:
            print(events[next_event][1])
            events[next_event][2]()
            next_event += 1
        if next_check < len(checks) and elapsed >= checks[next_check][0]:
            check(checks[next_check][1], bool(checks[next_check][2]()))
            next_check += 1
        if elapsed >= end:
            break
    for fault in (bmp, light, dht, display):
        fault.stop()
    station.close()
    print("RESULT: %d of %d checks passed. %s" % (sum(results), len(results), "ALL OK" if all(results) else "SOMETHING FAILED"))
    print("The Pico restarts by itself in a few seconds (the watchdog).")
    return all(results)


if __name__ == "__main__":
    run()
