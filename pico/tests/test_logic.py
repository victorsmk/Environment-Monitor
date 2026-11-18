"""Tests for pico/logic.py. Run from the repository root with:

    python -m unittest discover -s pico/tests -v
"""
import math
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import logic  # noqa: E402
from logic import NoiseMeter, Readings, age_ms, build_payload, fmt, missing_letters, noise_level  # noqa: E402


class AgeTests(unittest.TestCase):
    def test_plain_difference(self):
        self.assertEqual(age_ms(5000, 3000), 2000)

    def test_counter_wrapping_around(self):
        # The tick counter counts to 2**30 and starts again: 100 ms before the end to 200 ms after.
        self.assertEqual(age_ms(200, 2**30 - 100), 300)

    def test_a_time_a_little_later_than_now_is_no_time_at_all(self):
        # The DHT22 thread can store a value a few ms after the main loop took its "now".
        self.assertEqual(age_ms(1000, 1005), 0)
        self.assertEqual(age_ms(1000, 1000), 0)

    def test_a_time_a_little_later_across_the_counter_wrapping(self):
        self.assertEqual(age_ms(2**30 - 3, 4), 0)


class FormatTests(unittest.TestCase):
    def test_numbers(self):
        self.assertEqual(fmt(21.46, 1), "21.5")
        self.assertEqual(fmt(21.44, 1), "21.4")
        self.assertEqual(fmt(48.6, 0), "49")
        self.assertEqual(fmt(0, 0), "0")

    def test_no_value_is_two_dashes(self):
        self.assertEqual(fmt(None, 1), "--")
        self.assertEqual(fmt(None, 0), "--")


class ReadingsTests(unittest.TestCase):
    def setUp(self):
        self.readings = Readings({"temperature": 5000, "humidity": 10000})

    def test_a_fresh_value_is_returned(self):
        self.readings.set("temperature", 21.5, 1000)
        self.assertEqual(self.readings.get("temperature", 1000), 21.5)
        self.assertEqual(self.readings.get("temperature", 5000), 21.5)  # 4 s old, limit 5 s

    def test_a_value_exactly_at_the_limit_is_still_good(self):
        self.readings.set("temperature", 21.5, 0)
        self.assertEqual(self.readings.get("temperature", 5000), 21.5)

    def test_an_old_value_becomes_none(self):
        self.readings.set("temperature", 21.5, 0)
        self.assertIsNone(self.readings.get("temperature", 5001))

    def test_never_read_is_none(self):
        self.assertIsNone(self.readings.get("humidity", 1000))

    def test_each_sensor_has_its_own_limit(self):
        self.readings.set("temperature", 21.5, 0)
        self.readings.set("humidity", 50.0, 0)
        self.assertIsNone(self.readings.get("temperature", 7000))
        self.assertEqual(self.readings.get("humidity", 7000), 50.0)

    def test_a_new_value_replaces_and_refreshes(self):
        self.readings.set("temperature", 21.5, 0)
        self.readings.set("temperature", 22.0, 9000)
        self.assertEqual(self.readings.get("temperature", 12000), 22.0)

    def test_unknown_name_is_rejected(self):
        with self.assertRaises(KeyError):
            self.readings.set("wind", 1, 0)

    def test_works_across_the_counter_wrapping(self):
        self.readings.set("temperature", 21.5, 2**30 - 1000)
        self.assertEqual(self.readings.get("temperature", 500), 21.5)  # 1.5 s old

    def test_a_value_stored_just_after_now_is_not_taken_for_ancient(self):
        # Seen on the real Pico: humidity showed "--" now and then, because the thread that reads the
        # DHT22 stored it a few ms after the main loop took its "now".
        self.readings.set("humidity", 57.0, 10_005)
        self.assertEqual(self.readings.get("humidity", 10_000), 57.0)


class NoiseLevelTests(unittest.TestCase):
    def test_ends_of_the_scale(self):
        self.assertEqual(noise_level(0, 80, 2500), 0)
        self.assertEqual(noise_level(80, 80, 2500), 0)
        self.assertEqual(noise_level(2500, 80, 2500), 100)
        self.assertEqual(noise_level(99999, 80, 2500), 100)

    def test_the_scale_is_logarithmic_so_the_geometric_middle_reads_50(self):
        # sqrt(80 * 2500) = 447.2: halfway in ratio between quiet and loud
        self.assertEqual(noise_level(447, 80, 2500), 50)

    def test_every_doubling_adds_the_same_number_of_points(self):
        # quiet 50, loud 1600 is five doublings: each adds 20 points
        self.assertEqual(
            [noise_level(v, 50, 1600) for v in (100, 200, 400, 800)],
            [20, 40, 60, 80],
        )

    def test_a_quiet_voice_is_not_squashed_next_to_a_shout(self):
        # Measured values: quiet room 90, a normal voice 127-164, a shout 1200+; scale 90..2000.
        voice = noise_level(145, 90, 2000)
        shout = noise_level(1200, 90, 2000)
        self.assertGreaterEqual(voice, 10)
        self.assertLessEqual(voice, 25)
        self.assertGreater(shout, 75)

    def test_levels_never_go_backwards_as_the_sound_grows(self):
        levels = [noise_level(v, 90, 2000) for v in range(0, 2500, 10)]
        self.assertEqual(levels, sorted(levels))
        self.assertEqual(levels[0], 0)
        self.assertEqual(levels[-1], 100)

    def test_no_value_is_the_quietest(self):
        self.assertEqual(noise_level(None, 80, 2500), 0)


def meter(**overrides):
    values = dict(window_ms=100, report_ms=1000, min_window_samples=100, quiet=80, loud=2500, max_age=5000)
    values.update(overrides)
    return NoiseMeter(**values)


def feed(m, start_ms, seconds, deviation, per_burst=40, burst_ms=10):
    """Feed bursts of samples that all sit `deviation` (12-bit units) away from the resting level."""
    now = start_ms
    end = start_ms + int(seconds * 1000)
    while now < end:
        now += burst_ms
        m.add(per_burst, per_burst * deviation * deviation, now)
    return now


class NoiseMeterTests(unittest.TestCase):
    def test_nothing_before_the_first_second_is_over(self):
        m = meter()
        feed(m, 0, 0.5, 10)
        self.assertIsNone(m.current(500))

    def test_a_steady_noise_gives_its_rms_in_16_bit_units(self):
        m = meter()
        now = feed(m, 0, 1.2, 50)
        level, rms, peak = m.current(now)
        self.assertAlmostEqual(rms, 50 * 16, delta=1)    # 12-bit rms 50 = 800 in 16-bit units
        self.assertAlmostEqual(peak, 50 * 16, delta=1)
        self.assertEqual(level, noise_level(800, 80, 2500))

    def test_a_quiet_room_reads_zero(self):
        m = meter()
        now = feed(m, 0, 1.2, 3)   # 3 * 16 = 48, below "quiet"
        self.assertEqual(m.current(now)[0], 0)

    def test_a_loud_room_reads_100(self):
        m = meter()
        now = feed(m, 0, 1.2, 200)  # 3200
        self.assertEqual(m.current(now)[0], 100)

    def test_the_level_follows_the_loudest_window_not_the_average(self):
        m = meter()
        now = feed(m, 0, 0.45, 5)           # quiet
        now = feed(m, now, 0.1, 150)        # a loud 100 ms (a clap)
        now = feed(m, now, 0.5, 5)          # quiet again
        level, rms, peak = m.current(now)
        self.assertGreater(peak, 1500)  # a clap rarely lines up with a window, so it is partly averaged
        self.assertGreater(level, 70)
        self.assertLess(rms, peak / 2)      # the one-second average hardly moves

    def test_the_peak_starts_over_every_second(self):
        m = meter()
        now = feed(m, 0, 0.45, 5)
        now = feed(m, now, 0.1, 150)
        now = feed(m, now, 0.5, 5)
        loud_second_level = m.current(now)[0]
        now = feed(m, now, 1.1, 5)
        self.assertLess(m.current(now)[0], loud_second_level)

    def test_windows_with_too_few_samples_are_ignored(self):
        m = meter(min_window_samples=100)
        # Only 40 samples per 100 ms window (the loop was busy): not enough to trust.
        now = 0
        for _ in range(130):
            now += 10
            if now % 100 == 0:
                m.add(40, 40 * 500 * 500, now)  # looks very loud, but is too thin to count
            else:
                m.add(0, 0, now)
        self.assertIsNone(m.current(now))

    def test_a_missing_second_goes_stale_instead_of_freezing(self):
        m = meter(max_age=5000)
        now = feed(m, 0, 1.2, 50)
        self.assertIsNotNone(m.current(now))
        self.assertIsNone(m.current(now + 6000))

    def test_no_samples_at_all_gives_no_value(self):
        m = meter()
        now = 0
        for _ in range(250):
            now += 10
            m.add(0, 0, now)
        self.assertIsNone(m.current(now))

    def test_works_across_the_counter_wrapping(self):
        m = meter()
        now = feed(m, 2**30 - 600, 0.5, 50)       # runs up to the wrap
        now = feed(m, now & logic.TICKS_MASK, 1.0, 50)
        self.assertIsNotNone(m.current(now & logic.TICKS_MASK))


class PayloadTests(unittest.TestCase):
    def make(self):
        readings = Readings(
            {"temperature": 5000, "humidity": 10000, "light": 5000, "pressure": 5000, "temperature_dht": 10000}
        )
        return readings, meter()

    def test_everything_present(self):
        readings, m = self.make()
        now = feed(m, 0, 1.2, 50)
        for name, value in (("temperature", 22.46), ("humidity", 48.24), ("light", 312.55),
                            ("pressure", 1013.249), ("temperature_dht", 21.44)):
            readings.set(name, value, now)
        p = build_payload(readings, m, now, uptime_ms=5321000)
        self.assertEqual(p["v"], 1)
        self.assertEqual(p["uptime_ms"], 5321000)
        self.assertEqual(p["temperature"], 22.5)
        self.assertEqual(p["humidity"], 48.2)
        self.assertEqual(p["light"], 312.6)
        self.assertEqual(p["pressure_hpa"], 1013.2)
        self.assertEqual(p["temperature_dht"], 21.4)
        self.assertIsInstance(p["sound"], int)
        self.assertEqual(p["noise_rms"], round(m.current(now)[1], 1))
        self.assertEqual(missing_letters(p), "")

    def test_stale_values_are_null(self):
        readings, m = self.make()
        now = feed(m, 0, 1.2, 50)
        readings.set("temperature", 22.0, now)
        readings.set("humidity", 48.0, now)
        later = now + 7000  # temperature (5 s) and sound (5 s) are stale, humidity (10 s) is not
        p = build_payload(readings, m, later, uptime_ms=1)
        self.assertIsNone(p["temperature"])
        self.assertIsNone(p["sound"])
        self.assertIsNone(p["noise_rms"])
        self.assertEqual(p["humidity"], 48.0)
        self.assertEqual(missing_letters(p), "TLS")

    def test_every_field_the_app_requires_is_always_present(self):
        readings, m = self.make()
        p = build_payload(readings, m, 0, uptime_ms=0)
        for key in ("v", "uptime_ms", "temperature", "humidity", "light", "sound"):
            self.assertIn(key, p)
        self.assertIsNone(p["temperature"])

    def test_it_is_valid_json_for_the_app(self):
        import json
        readings, m = self.make()
        now = feed(m, 0, 1.2, 50)
        readings.set("humidity", 50.0, now)
        text = json.dumps(build_payload(readings, m, now, 7))
        data = json.loads(text)
        self.assertIsNone(data["light"])
        self.assertEqual(data["humidity"], 50.0)


if __name__ == "__main__":
    unittest.main()
