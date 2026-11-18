"""Tests for pico/wifi.py with a pretend Wi-Fi chip and time that the test moves by hand.

    python -m unittest discover -s pico/tests -v
"""
import os
import sys
import types
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.dirname(__file__))

from fakes import World  # noqa: E402  (puts the pretend hardware in place)

import config  # noqa: E402
import wifi  # noqa: E402
from wifi import MODE_AP, MODE_CONNECTING, MODE_OFF, MODE_STATION, WifiManager, load_secrets  # noqa: E402


def secrets_with(**names):
    return types.SimpleNamespace(**names)


class LoadSecretsTests(unittest.TestCase):
    def test_a_list_of_networks(self):
        module = secrets_with(NETWORKS=[("home", "pw1"), ("phone", "pw2")], AP_PASSWORD="pico-secret")
        self.assertEqual(load_secrets(module), ([("home", "pw1"), ("phone", "pw2")], "pico-secret"))

    def test_the_older_one_network_layout_is_still_understood(self):
        module = secrets_with(secrets={"ssid": "phone", "password": "pw"})
        self.assertEqual(load_secrets(module), ([("phone", "pw")], None))

    def test_plain_ssid_and_password_variables_are_understood_too(self):
        module = secrets_with(SSID="phone", PASSWORD="pw")
        self.assertEqual(load_secrets(module), ([("phone", "pw")], None))
        self.assertEqual(load_secrets(secrets_with(SSID="cafe"))[0], [("cafe", "")])

    def test_the_list_wins_when_both_are_there(self):
        module = secrets_with(NETWORKS=[("home", "pw")], secrets={"ssid": "old", "password": "x"})
        self.assertEqual(load_secrets(module)[0], [("home", "pw")])

    def test_an_open_network_has_an_empty_password(self):
        module = secrets_with(secrets={"ssid": "cafe"})
        self.assertEqual(load_secrets(module)[0], [("cafe", "")])

    def test_no_secrets_file_or_nothing_in_it(self):
        self.assertEqual(load_secrets(None), ([], None))
        self.assertEqual(load_secrets(secrets_with()), ([], None))
        self.assertEqual(load_secrets(secrets_with(secrets={})), ([], None))

    def test_a_module_with_other_names_only_is_harmless(self):
        # For example the standard "secrets" module of a PC, which has nothing to do with Wi-Fi.
        other = secrets_with(token_hex=lambda n: "00", choice=lambda x: x)
        self.assertEqual(load_secrets(other), ([], None))


class WifiTestCase(unittest.TestCase):
    def setUp(self):
        World.reset()

    def manager(self, networks, ap_password=None):
        m = WifiManager(networks, ap_password)
        m.start(0)
        return m

    def run_until(self, m, seconds, step_ms=100, start=0):
        """Call step() every `step_ms` of pretend time, up to `seconds`. Returns the last time used."""
        now = start
        while now <= start + seconds * 1000:
            m.step(now)
            now += step_ms
        return now


class JoiningANetwork(WifiTestCase):
    def test_it_joins_the_first_network_that_works(self):
        World.net_accept = {"home"}
        m = self.manager([("home", "pw"), ("phone", "pw")])
        self.assertEqual(m.mode, MODE_CONNECTING)
        m.step(100)
        self.assertEqual(m.mode, MODE_STATION)
        self.assertEqual(m.ip, "192.168.43.17")
        self.assertEqual(m.ssid_index, 0)
        self.assertEqual(World.net_attempts, ["home"])

    def test_it_asks_to_be_told_about_the_change(self):
        World.net_accept = {"home"}
        m = self.manager([("home", "pw")])
        m.changed = False
        m.step(100)
        self.assertTrue(m.changed)
        m.changed = False
        m.step(200)
        self.assertFalse(m.changed)  # nothing new

    def test_a_refused_network_is_skipped_after_a_second(self):
        World.net_refuse = {"home": -3}      # wrong password
        World.net_accept = {"phone"}
        m = self.manager([("home", "pw"), ("phone", "pw")])
        m.step(500)
        self.assertEqual(World.net_attempts, ["home"])   # not yet: a refused network is not hammered
        m.step(1000)
        self.assertEqual(World.net_attempts, ["home", "phone"])
        m.step(1100)
        self.assertEqual(m.mode, MODE_STATION)
        self.assertEqual(m.ssid_index, 1)

    def test_a_network_that_never_answers_gets_ten_seconds(self):
        World.net_accept = {"phone"}
        m = self.manager([("home", "pw"), ("phone", "pw")])  # "home" stays at status 1 (still trying)
        m.step(9_900)
        self.assertEqual(World.net_attempts, ["home"])
        m.step(10_000)
        self.assertEqual(World.net_attempts, ["home", "phone"])

    def test_it_goes_round_the_list_again_and_again(self):
        World.net_refuse = {"home": -2, "phone": -2}
        m = self.manager([("home", "pw"), ("phone", "pw")])
        self.run_until(m, 5)
        self.assertGreaterEqual(len(World.net_attempts), 5)
        self.assertEqual(World.net_attempts[:4], ["home", "phone", "home", "phone"])

    def test_a_network_that_appears_later_is_found(self):
        World.net_refuse = {"phone": -2}
        m = self.manager([("phone", "pw")])
        now = self.run_until(m, 8)
        self.assertEqual(m.mode, MODE_CONNECTING)
        World.net_accept = {"phone"}                      # the hotspot is switched on
        self.run_until(m, 3, start=now)
        self.assertEqual(m.mode, MODE_STATION)

    def test_it_tries_the_network_that_worked_first_after_losing_it(self):
        World.net_accept = {"home", "phone"}
        m = self.manager([("home", "pw"), ("phone", "pw")])
        m.step(100)
        World.net_accept = {"phone"}                      # the home router is switched off
        m.step(200)
        self.assertEqual(m.mode, MODE_STATION)           # a lost connection is not given up on at once
        m.step(5_300)
        self.assertEqual(m.mode, MODE_CONNECTING)
        self.assertIsNone(m.ip)
        # It starts with the network that worked, then moves on.
        self.assertEqual(World.net_attempts[-1], "home")
        World.net_refuse = {"home": -2}
        self.run_until(m, 3, start=5_400)
        self.assertEqual(m.mode, MODE_STATION)
        self.assertEqual(m.ssid_index, 1)

    def test_a_short_loss_is_ridden_out(self):
        World.net_accept = {"home"}
        m = self.manager([("home", "pw")])
        m.step(100)
        World.net_accept = set()
        m.step(1_000)
        m.step(4_000)
        World.net_accept = {"home"}                      # it came back within 5 seconds
        m.step(4_500)
        m.step(20_000)
        self.assertEqual(m.mode, MODE_STATION)
        self.assertEqual(World.net_attempts, ["home"])   # never had to reconnect


class KeepingTheLinkHonest(WifiTestCase):
    """While joined, a tiny packet goes to the gateway now and then, so the chip notices a vanished network."""

    def manager_with_log(self, networks):
        sent = []
        m = WifiManager(networks, "pico-secret", keepalive=sent.append)
        m.start(0)
        return m, sent

    def test_a_packet_goes_to_the_gateway_every_ten_seconds_while_joined(self):
        World.net_accept = {"home"}
        m, sent = self.manager_with_log([("home", "pw")])
        m.step(100)                           # joins
        for now in range(100, 35_000, 100):
            m.step(now)
        # at 10 s, 20 s and 30 s of the pretend clock (the first is due 10 s after the clock's zero)
        self.assertEqual(len(sent), 3)
        self.assertEqual(set(sent), {"192.168.43.1"})   # the gateway the pretend chip handed out

    def test_nothing_is_sent_while_not_joined(self):
        World.net_refuse = {"home": -2}
        m, sent = self.manager_with_log([("home", "pw")])
        self.run_until(m, 25)
        self.assertEqual(sent, [])

    def test_nothing_is_sent_in_its_own_network(self):
        m, sent = self.manager_with_log([])   # no known network: it opens its own straight away
        self.assertEqual(m.mode, MODE_AP)
        self.run_until(m, 40)
        self.assertEqual(sent, [])

    def test_a_failing_send_does_not_break_anything(self):
        World.net_accept = {"home"}

        def failing(gateway):
            raise OSError(113, "EHOSTUNREACH")

        m = WifiManager([("home", "pw")], "pico-secret", keepalive=failing)
        m.start(0)
        self.run_until(m, 30)
        self.assertEqual(m.mode, MODE_STATION)

    def test_the_default_keepalive_really_opens_a_tcp_connection(self):
        import socket
        import wifi as wifi_module
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        listener.settimeout(2.0)
        real_port = wifi_module.KEEPALIVE_PORT
        wifi_module.KEEPALIVE_PORT = listener.getsockname()[1]
        try:
            wifi_module._send_keepalive("127.0.0.1")
            connection, _ = listener.accept()     # the connection arrived
            connection.close()
        finally:
            wifi_module.KEEPALIVE_PORT = real_port
            listener.close()

    def test_a_refused_connection_is_fine(self):
        import socket
        import wifi as wifi_module
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        closed_port = probe.getsockname()[1]
        probe.close()                              # nothing listens there now
        real_port = wifi_module.KEEPALIVE_PORT
        wifi_module.KEEPALIVE_PORT = closed_port
        try:
            wifi_module._send_keepalive("127.0.0.1")   # must simply return
        finally:
            wifi_module.KEEPALIVE_PORT = real_port

    def test_a_silent_gateway_costs_at_most_the_timeout(self):
        import time
        import wifi as wifi_module
        real_timeout = wifi_module.KEEPALIVE_TIMEOUT
        wifi_module.KEEPALIVE_TIMEOUT = 0.3
        started = time.monotonic()
        try:
            wifi_module._send_keepalive("10.255.255.1")  # nobody there (or no route at all: both are fine)
        finally:
            wifi_module.KEEPALIVE_TIMEOUT = real_timeout
        self.assertLess(time.monotonic() - started, 2.0)

    def test_the_address_is_read_again_after_joining(self):
        # Seen on the real board: after moving from the hotspot to the home Wi-Fi the Pico went on showing the
        # hotspot's address until the new one arrived.
        World.net_accept = {"home"}
        World.sta_ip = "10.162.98.24"             # the stale address of the previous network
        m, _ = self.manager_with_log([("home", "pw")])
        m.step(100)
        self.assertEqual(m.ip, "10.162.98.24")
        m.changed = False
        World.sta_ip = "192.168.1.25"             # the home router's lease arrives
        m.step(200)
        self.assertEqual(m.ip, "192.168.1.25")
        self.assertTrue(m.changed)                # so the servers are told

    def test_an_address_of_zeros_is_not_accepted_as_joined(self):
        World.net_accept = {"home"}
        World.sta_ip = "0.0.0.0"
        m, _ = self.manager_with_log([("home", "pw")])
        m.step(100)
        self.assertEqual(m.mode, MODE_CONNECTING)   # still waiting for an address
        self.assertIsNone(m.ip)
        World.sta_ip = "192.168.1.25"
        m.step(200)
        self.assertEqual(m.mode, MODE_STATION)
        self.assertEqual(m.ip, "192.168.1.25")

    def test_an_unchanged_address_does_not_restart_the_servers(self):
        World.net_accept = {"home"}
        m, _ = self.manager_with_log([("home", "pw")])
        m.step(100)
        m.changed = False
        for now in range(200, 5_000, 100):
            m.step(now)
        self.assertFalse(m.changed)

    def test_the_signal_strength_is_only_given_while_joined(self):
        World.net_accept = {"home"}
        m = WifiManager([("home", "pw")], "pico-secret")
        self.assertIsNone(m.rssi())          # not started
        m.start(0)
        self.assertIsNone(m.rssi())          # still connecting
        m.step(100)
        self.assertIsNotNone(m.rssi())       # joined: the pretend chip answers


class OpeningItsOwnNetwork(WifiTestCase):
    def test_after_thirty_seconds_without_a_known_network_it_opens_its_own(self):
        World.net_refuse = {"home": -2, "phone": -2}
        m = self.manager([("home", "pw"), ("phone", "pw")], ap_password="pico-secret")
        self.run_until(m, 40)
        self.assertEqual(m.mode, MODE_AP)
        self.assertEqual(m.ip, "192.168.4.1")
        self.assertEqual(World.ap_config, {"essid": "PicoMeteo", "password": "pico-secret"})
        self.assertTrue(World.radio_active["ap"])
        self.assertFalse(World.radio_active["sta"])

    def test_not_before_thirty_seconds(self):
        World.net_refuse = {"home": -2}
        m = self.manager([("home", "pw")], ap_password="pico-secret")
        self.run_until(m, 25)
        self.assertEqual(m.mode, MODE_CONNECTING)

    def test_without_a_password_it_does_not_open_an_open_network(self):
        World.net_refuse = {"home": -2}
        m = self.manager([("home", "pw")], ap_password=None)
        self.run_until(m, 60)
        self.assertEqual(m.mode, MODE_CONNECTING)        # still trying the known network
        self.assertFalse(World.radio_active["ap"])
        self.assertIn("AP_PASSWORD", m.last_error)

    def test_a_too_short_password_is_refused_too(self):
        World.net_refuse = {"home": -2}
        m = self.manager([("home", "pw")], ap_password="short")
        self.run_until(m, 40)
        self.assertFalse(World.radio_active["ap"])

    def test_with_no_known_network_it_opens_its_own_straight_away(self):
        m = self.manager([], ap_password="pico-secret")
        self.assertEqual(m.mode, MODE_AP)

    def test_with_nothing_at_all_it_just_waits(self):
        m = self.manager([], ap_password=None)
        self.assertEqual(m.mode, MODE_CONNECTING)
        self.run_until(m, 10)                            # must not crash
        self.assertEqual(m.mode, MODE_CONNECTING)

    def test_with_nobody_connected_it_looks_for_a_known_network_again(self):
        World.net_refuse = {"phone": -2}
        m = self.manager([("phone", "pw")], ap_password="pico-secret")
        now = self.run_until(m, 40)
        self.assertEqual(m.mode, MODE_AP)
        World.net_accept = {"phone"}                      # the hotspot is switched on while we are in AP mode
        World.net_refuse = {}
        self.run_until(m, 70, start=now)
        self.assertEqual(m.mode, MODE_STATION)
        self.assertFalse(World.radio_active["ap"])
        self.assertTrue(World.radio_active["sta"])

    def test_it_keeps_its_own_network_while_somebody_is_connected(self):
        World.net_refuse = {"phone": -2}
        m = self.manager([("phone", "pw")], ap_password="pico-secret")
        now = self.run_until(m, 40)
        self.assertEqual(m.mode, MODE_AP)
        World.ap_stations = ["aa:bb:cc:dd:ee:ff"]
        World.net_accept = {"phone"}
        World.net_refuse = {}
        self.run_until(m, 130, start=now)                 # two minutes with the phone on our network
        self.assertEqual(m.mode, MODE_AP)
        self.assertTrue(World.radio_active["ap"])

    def test_after_losing_a_known_network_it_goes_round_again_and_may_open_its_own(self):
        World.net_accept = {"home"}
        m = self.manager([("home", "pw")], ap_password="pico-secret")
        m.step(100)
        World.net_accept = set()
        World.net_refuse = {"home": -2}
        self.run_until(m, 50, start=200)
        self.assertEqual(m.mode, MODE_AP)


class Describing(WifiTestCase):
    def test_texts_for_the_display(self):
        World.net_accept = {"home"}
        m = WifiManager([("home", "pw")], "pico-secret")
        self.assertEqual(m.describe(), "WiFi: oprit")
        m.start(0)
        self.assertEqual(m.describe(), "WiFi: conectez..")
        m.step(100)
        self.assertEqual(m.describe(), "192.168.43.17")
        World.net_accept = set()
        World.net_refuse = {"home": -2}
        self.run_until(m, 50, start=200)
        self.assertEqual(m.describe(), "AP 192.168.4.1")

    def test_every_text_fits_the_sixteen_characters_of_the_display(self):
        for text in ("WiFi: oprit", "WiFi: conectez..", "AP 192.168.4.1", "255.255.255.255"):
            self.assertLessEqual(len(text), 16)


if __name__ == "__main__":
    unittest.main()
