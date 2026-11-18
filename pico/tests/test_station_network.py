"""The whole station on a PC, with the network switched on: a client asks it over a real socket.

    python -m unittest discover -s pico/tests -v
"""
import json
import socket
import threading
import time
import types
import unittest

from fakes import World  # noqa: F401
from test_station import StationTestCase  # also sets up the path and the pretend hardware

import config
import main


def free_port(kind):
    with socket.socket(socket.AF_INET, kind) as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


class NetworkOnTheStation(StationTestCase):
    def setUp(self):
        super().setUp()
        self._net_saved = {
            name: getattr(config, name)
            for name in ("USE_NETWORK", "HTTP_PORT", "DISCOVERY_PORT", "WIFI_TRY_MS", "AP_AFTER_MS")
        }
        config.USE_NETWORK = True
        config.HTTP_PORT = free_port(socket.SOCK_STREAM)
        config.DISCOVERY_PORT = free_port(socket.SOCK_DGRAM)
        self._secrets = main.secrets_module
        main.secrets_module = types.SimpleNamespace(NETWORKS=[("hotspot", "pw")], AP_PASSWORD="pico-secret")
        World.net_accept = {"hotspot"}
        # The keep-alive to the gateway has its own tests; here it must not try the PC's real network.
        self._keepalive = main.wifi._send_keepalive
        main.wifi._send_keepalive = lambda gateway: None

    def tearDown(self):
        main.wifi._send_keepalive = self._keepalive
        main.secrets_module = self._secrets
        for name, value in self._net_saved.items():
            setattr(config, name, value)
        super().tearDown()

    def start_with_network(self):
        station = self.start()
        station.start_network()
        return station

    def ask_in_background(self, request, after=0.3):
        """Send `request` to the station from another thread after `after` seconds; the answer lands in the result."""
        result = {}

        def client():
            time.sleep(after)
            try:
                with socket.create_connection(("127.0.0.1", config.HTTP_PORT), timeout=2) as c:
                    c.sendall(request)
                    data = b""
                    while True:
                        chunk = c.recv(4096)
                        if not chunk:
                            break
                        data += chunk
                result["data"] = data
            except Exception as e:  # reported by the test that looks at the result
                result["error"] = repr(e)

        thread = threading.Thread(target=client)
        thread.start()
        return thread, result

    def test_a_client_gets_the_live_readings(self):
        station = self.start_with_network()
        thread, result = self.ask_in_background(b"GET /data HTTP/1.1\r\nHost: pico\r\n\r\n", after=0.4)
        self.run_for(station, 1.0)
        thread.join()
        self.assertNotIn("error", result)
        head, _, body = result["data"].partition(b"\r\n\r\n")
        self.assertTrue(head.startswith(b"HTTP/1.0 200 OK"))
        data = json.loads(body)
        self.assertEqual(data["v"], 1)
        self.assertEqual(data["temperature"], 22.5)          # BMP280
        self.assertEqual(data["humidity"], 48.2)             # DHT22
        self.assertAlmostEqual(data["light"], 833.3, places=1)
        self.assertIsNotNone(data["sound"])
        self.assertEqual(data["pressure_hpa"], 1013.2)
        self.assertEqual(data["temperature_dht"], 21.4)

    def test_a_dead_sensor_is_null_in_the_answer(self):
        World.dht_fails = True
        station = self.start_with_network()
        thread, result = self.ask_in_background(b"GET /data HTTP/1.1\r\n\r\n", after=0.7)  # past MAX_AGE_DHT (0.3 s)
        self.run_for(station, 1.2)
        thread.join()
        data = json.loads(result["data"].partition(b"\r\n\r\n")[2])
        self.assertIsNone(data["humidity"])
        self.assertEqual(data["temperature"], 22.5)          # the others are unaffected

    def test_the_phone_can_find_the_station_by_asking_who_is_there(self):
        station = self.start_with_network()
        client = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        client.settimeout(0.05)
        reply = None
        try:
            end = time.monotonic() + 1.0
            while time.monotonic() < end and reply is None:
                client.sendto(b"PICO?", ("127.0.0.1", config.DISCOVERY_PORT))
                station.step()
                try:
                    reply = json.loads(client.recvfrom(512)[0])
                except (socket.timeout, ConnectionResetError):
                    pass  # not listening yet (Windows reports that as a reset); ask again
        finally:
            client.close()
        self.assertIsNotNone(reply)
        self.assertEqual(reply["ip"], "192.168.43.17")       # the address the pretend Wi-Fi chip handed out
        self.assertEqual(reply["port"], config.HTTP_PORT)

    def test_the_display_shows_the_address(self):
        station = self.start_with_network()
        self.run_for(station, 0.4)
        self.assertEqual(World.last_screen[45], "192.168.43.17")

    def test_the_display_shows_that_it_is_connecting(self):
        World.net_accept = set()
        World.net_refuse = {"hotspot": -2}
        station = self.start_with_network()
        self.run_for(station, 0.4)
        self.assertEqual(World.last_screen[45], "WiFi: conectez..")

    def test_in_its_own_network_it_shows_that_and_still_answers(self):
        World.net_accept = set()
        World.net_refuse = {"hotspot": -2}
        config.WIFI_TRY_MS = 50
        config.AP_AFTER_MS = 150
        station = self.start_with_network()
        thread, result = self.ask_in_background(b"GET /data HTTP/1.1\r\n\r\n", after=0.8)
        self.run_for(station, 1.3)
        thread.join()
        self.assertEqual(World.last_screen[45], "AP 192.168.4.1")
        self.assertEqual(World.ap_config["essid"], "PicoMeteo")
        self.assertTrue(result["data"].startswith(b"HTTP/1.0 200 OK"))

    def test_the_servers_follow_the_network_when_it_changes(self):
        station = self.start_with_network()
        self.run_for(station, 0.3)
        first_listener = station.http._listener
        self.assertIsNotNone(first_listener)
        World.net_accept = set()                              # the hotspot disappears
        self.run_for(station, 0.3)
        station.wifi._lost_since = 0                          # (pretend 5 s have passed)
        station.wifi.step(station.wifi._lost_since + config.WIFI_LOST_MS + 1)
        self.run_for(station, 0.2)
        self.assertIsNone(station.http._listener)             # no network, no open port
        World.net_accept = {"hotspot"}
        self.run_for(station, 0.5)
        self.assertIsNotNone(station.http._listener)
        self.assertIsNot(station.http._listener, first_listener)

    def test_a_network_that_throws_does_not_stop_the_measuring(self):
        station = self.start_with_network()

        def boom(now):
            raise OSError("radio on fire")

        station.wifi.step = boom
        self.run_for(station, 0.6)
        p = self.payload(station)
        self.assertEqual(p["temperature"], 22.5)
        self.assertEqual(p["humidity"], 48.2)

    def test_without_wifi_hardware_everything_else_still_works(self):
        original = main.wifi
        main.wifi = None
        try:
            station = self.start()
            station.start_network()                           # does nothing
            self.run_for(station, 0.5)
        finally:
            main.wifi = original
        self.assertIsNone(station.wifi)
        self.assertEqual(self.payload(station)["temperature"], 22.5)

    def test_the_console_line_says_where_the_network_stands(self):
        import io
        import contextlib

        station = self.start_with_network()
        self.run_for(station, 0.3)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            station.report(time.ticks_ms())
        self.assertIn("net=192.168.43.17", out.getvalue())


if __name__ == "__main__":
    unittest.main()
