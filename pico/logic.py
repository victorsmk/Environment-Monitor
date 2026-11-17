# The parts of the station that need no hardware: how old a value may get, the sound level, and the
# JSON for the app. Plain Python that runs on the Pico (MicroPython) and on a PC, so it can be tested
# on a PC (see pico/tests).

import math

# Samples are taken as 12-bit numbers (0-4095). The calibration in config.py is in 16-bit ADC units
# (what read_u16() returns), which is 16 times bigger.
SCALE_12_TO_16 = 16

TICKS_MASK = 0x3FFFFFFF  # time.ticks_ms() counts up to 2**30 on the Pico and then starts over


def age_ms(now, then):
    """Milliseconds from `then` to `now`, correct even when the tick counter has wrapped around.

    A `then` that is a little later than `now` counts as no time at all. That happens for real: the
    DHT22 is read on the Pico's second core and can store a value a few milliseconds after the main
    loop has taken its "now". Without this, that value would look almost 12 days old (the whole
    counter period) and show as missing.
    """
    difference = (now - then) & TICKS_MASK
    if difference > TICKS_MASK >> 1:
        return 0
    return difference


def fmt(value, decimals=0):
    """Text for the display: the number, or "--" when there is no value."""
    if value is None:
        return "--"
    if decimals == 0:
        return str(int(round(value)))
    return ("{:." + str(decimals) + "f}").format(value)


class Readings:
    """The latest value of each sensor, and when it arrived. Values that are too old read as None."""

    def __init__(self, max_ages):
        # max_ages: {"temperature": 5000, ...} in milliseconds; a name not listed cannot be stored.
        self._max_ages = max_ages
        self._values = {}

    def set(self, name, value, now):
        if name not in self._max_ages:
            raise KeyError(name)
        self._values[name] = (value, now)

    def get(self, name, now):
        """The value, or None when it was never read or its last good reading is too old."""
        entry = self._values.get(name)
        if entry is None:
            return None
        value, stamp = entry
        if age_ms(now, stamp) > self._max_ages[name]:
            return None
        return value


def noise_level(rms, quiet, loud):
    """0-100 from an RMS value: 0 at or below `quiet`, 100 at or above `loud`, a straight line between."""
    if rms is None or rms <= quiet:
        return 0
    if rms >= loud:
        return 100
    return int((rms - quiet) / (loud - quiet) * 100 + 0.5)


class NoiseMeter:
    """Turns bursts of microphone samples into a sound level.

    The caller reports each burst as (number of samples, sum of the squared distances from the
    microphone's resting level). The meter groups them into windows (100 ms by default), keeps the
    loudest window of each second, and once a second works out the sound level from it. A short loud
    noise therefore shows up even if the rest of the second was quiet.
    """

    def __init__(self, window_ms, report_ms, min_window_samples, quiet, loud, max_age):
        self._window_ms = window_ms
        self._report_ms = report_ms
        self._min_window_samples = min_window_samples
        self._quiet = quiet
        self._loud = loud
        self._max_age = max_age

        self._window_start = None
        self._report_start = None
        self._window_n = 0
        self._window_sq = 0
        self._second_n = 0
        self._second_sq = 0
        self._peak = 0.0

        # What the last full second said. None until the first second is over.
        self.level = None
        self.rms = None
        self.peak = None
        self.samples = 0
        self._reported_at = None

    def add(self, n, sum_sq, now):
        if self._window_start is None:
            self._window_start = now
            self._report_start = now
        self._window_n += n
        self._window_sq += sum_sq
        if age_ms(now, self._window_start) >= self._window_ms:
            self._close_window(now)
        if age_ms(now, self._report_start) >= self._report_ms:
            self._report(now)

    def _close_window(self, now):
        if self._window_n >= self._min_window_samples:
            rms = math.sqrt(self._window_sq / self._window_n) * SCALE_12_TO_16
            if rms > self._peak:
                self._peak = rms
            self._second_n += self._window_n
            self._second_sq += self._window_sq
        self._window_n = 0
        self._window_sq = 0
        self._window_start = now

    def _report(self, now):
        if self._second_n > 0:
            self.rms = math.sqrt(self._second_sq / self._second_n) * SCALE_12_TO_16
            self.peak = self._peak
            self.level = noise_level(self._peak, self._quiet, self._loud)
            self.samples = self._second_n
            self._reported_at = now
        self._second_n = 0
        self._second_sq = 0
        self._peak = 0.0
        self._report_start = now

    def current(self, now):
        """(level, rms, peak) from the last full second, or None if there is none or it is too old."""
        if self._reported_at is None or age_ms(now, self._reported_at) > self._max_age:
            return None
        return self.level, self.rms, self.peak


def _round(value, decimals):
    return None if value is None else round(value, decimals)


def build_payload(readings, meter, now, uptime_ms):
    """The answer of GET /data, as a dict (see docs/pico-protocol.md). None means "no value"."""
    sound = meter.current(now)
    return {
        "v": 1,
        "uptime_ms": uptime_ms,
        "temperature": _round(readings.get("temperature", now), 1),
        "humidity": _round(readings.get("humidity", now), 1),
        "light": _round(readings.get("light", now), 1),
        "sound": None if sound is None else sound[0],
        "pressure_hpa": _round(readings.get("pressure", now), 1),
        "temperature_dht": _round(readings.get("temperature_dht", now), 1),
        "noise_rms": None if sound is None else _round(sound[1], 1),
    }


def missing_letters(payload):
    """The sensors with no value, as letters for the display: "T" temperature, "H" humidity, "L" light, "S" sound."""
    letters = ""
    for letter, key in (("T", "temperature"), ("H", "humidity"), ("L", "light"), ("S", "sound")):
        if payload[key] is None:
            letters += letter
    return letters
