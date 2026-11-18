"""Runs pico/main.py on a PC against pretend hardware: pretend microphone, sensors and display.

It cannot say how fast the real Pico is. It does check that the program's logic holds together: values
get through, a failing sensor shows as "--" and does not stop the others, a broken sensor is started
again when it comes back, and the light sensor is never waited for. Run from the repository root:

    python -m unittest discover -s pico/tests -v
"""
import os
import sys
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.dirname(__file__))

from fakes import World  # noqa: E402,F401  (puts the pretend hardware in place; must come before main)

import config  # noqa: E402
import main  # noqa: E402
from logic import noise_level  # noqa: E402

FAST = dict(
    INTERVAL_BMP=30, INTERVAL_DHT=30, INTERVAL_LIGHT=60, LIGHT_MEASURE_MS=40, INTERVAL_DISPLAY=30,
    INTERVAL_REPORT=1000, INTERVAL_REINIT=100,
    MAX_AGE_BMP=300, MAX_AGE_DHT=300, MAX_AGE_LIGHT=300, MAX_AGE_SOUND=600,
    MIC_BURST_US=5000, MIC_WINDOW_MS=40, MIC_REPORT_MS=200, MIC_MIN_WINDOW_SAMPLES=20,
    USE_WATCHDOG=True, USE_NETWORK=False,
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
        # The pretend microphone swings 800 (16-bit units) either side: rms 800, whatever the scale is.
        self.assertAlmostEqual(p["noise_rms"], 800, delta=40)
        expected = noise_level(800, config.NOISE_RMS_QUIET, config.NOISE_RMS_LOUD)
        self.assertIn(p["sound"], range(expected - 3, expected + 4))

    def test_silence_reads_zero(self):
        World.mic_swing = 10
        station = self.start()
        self.run_for(station, 0.8)
        self.assertEqual(self.payload(station)["sound"], 0)

    def test_the_display_shows_the_values(self):
        station = self.start()
        self.run_for(station, 0.8)
        screen = World.last_screen
        self.assertEqual(screen[9], "Temp: 22.5 C")
        self.assertEqual(screen[18], "Umid: 48 %")
        self.assertEqual(screen[27], "Lux: 833")
        self.assertTrue(screen[36].startswith("Zgomot: "))
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


class TheHeartbeat(StationTestCase):
    def test_a_character_in_the_corner_turns_with_every_update(self):
        station = self.start()
        self.run_for(station, 0.6)
        beats = World.heartbeats
        self.assertGreaterEqual(len(beats), 8)
        self.assertEqual(set(beats), set("|/-\\"))
        # The characters come in order, one step each time: | / - \ | / ...
        order = "|/-\\"
        for before, after in zip(beats, beats[1:]):
            self.assertEqual(after, order[(order.index(before) + 1) % 4])

    def test_the_text_lines_are_unaffected_by_the_heartbeat(self):
        station = self.start()
        self.run_for(station, 0.5)
        self.assertEqual(World.last_screen[9], "Temp: 22.5 C")
        self.assertEqual(World.last_screen[54], "Senzori: OK")

    def test_the_heartbeat_stops_when_the_display_stops(self):
        station = self.start()
        self.run_for(station, 0.3)
        World.oled_fails = True
        self.run_for(station, 0.4)
        count = len(World.heartbeats)
        self.run_for(station, 0.3)
        self.assertEqual(len(World.heartbeats), count)   # a broken display shows no heartbeat


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
        self.assertEqual(World.last_screen[18], "Umid: -- %")
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
        self.assertEqual(World.last_screen[9], "Temp: -- C")

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
