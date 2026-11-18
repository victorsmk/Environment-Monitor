"""Tests for pico/server.py over real sockets on this computer (the "phone" is a socket in the test).

    python -m unittest discover -s pico/tests -v
"""
import json
import os
import socket
import sys
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.dirname(__file__))

from fakes import World  # noqa: E402,F401

import config  # noqa: E402
import logic  # noqa: E402
from server import DiscoveryResponder, HttpServer  # noqa: E402


def free_port(kind):
    with socket.socket(socket.AF_INET, kind) as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def now_ms():
    return int(time.monotonic() * 1000) & logic.TICKS_MASK


PAYLOAD = {
    "v": 1, "uptime_ms": 1234, "temperature": 22.5, "humidity": None, "light": 312.5, "sound": 17,
    "pressure_hpa": 1013.2, "temperature_dht": 21.4, "noise_rms": 143.7,
}


class HttpServerTests(unittest.TestCase):
    def setUp(self):
        self._saved = (config.HTTP_PORT, config.HTTP_REQUEST_TIMEOUT_MS)
        config.HTTP_PORT = free_port(socket.SOCK_STREAM)
        self.seen_now = []
        self.server = HttpServer(self.payload)
        self.server.start()
        self.clients = []

    def tearDown(self):
        self.server.stop()
        for c in self.clients:
            c.close()
        config.HTTP_PORT, config.HTTP_REQUEST_TIMEOUT_MS = self._saved

    def payload(self, now):
        self.seen_now.append(now)
        return PAYLOAD

    def connect(self):
        client = socket.create_connection(("127.0.0.1", config.HTTP_PORT), timeout=2)
        client.setblocking(False)
        self.clients.append(client)
        return client

    def pump(self, client, seconds=1.0):
        """Poll the server while collecting what the client receives, until the server closes it."""
        received = b""
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            self.server.poll(now_ms())
            try:
                chunk = client.recv(4096)
                if chunk == b"":
                    return received, True
                received += chunk
            except BlockingIOError:
                pass
            except ConnectionResetError:
                return received, True  # the server closed with part of a long request still unread
            time.sleep(0.005)
        return received, False

    def get(self, raw):
        client = self.connect()
        client.sendall(raw)
        return self.pump(client)[0]

    @staticmethod
    def split(response):
        head, _, body = response.partition(b"\r\n\r\n")
        lines = head.decode().split("\r\n")
        headers = dict(line.split(": ", 1) for line in lines[1:])
        return lines[0], headers, body

    # ---- the one page ----

    def test_get_data_answers_with_the_readings_as_json(self):
        status, headers, body = self.split(self.get(b"GET /data HTTP/1.1\r\nHost: 192.168.4.1\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 200 OK")
        self.assertEqual(headers["Content-Type"], "application/json")
        self.assertEqual(int(headers["Content-Length"]), len(body))
        self.assertEqual(json.loads(body), PAYLOAD)

    def test_the_answer_has_every_field_the_app_requires(self):
        _, _, body = self.split(self.get(b"GET /data HTTP/1.0\r\n\r\n"))
        data = json.loads(body)
        for key in ("v", "uptime_ms", "temperature", "humidity", "light", "sound"):
            self.assertIn(key, data)
        self.assertIsNone(data["humidity"])             # null, not missing

    def test_a_typical_android_request_is_answered(self):
        request = (
            b"GET /data HTTP/1.1\r\nUser-Agent: Dalvik/2.1.0 (Linux; U; Android 16)\r\n"
            b"Host: 192.168.43.17\r\nConnection: Keep-Alive\r\nAccept-Encoding: gzip\r\n\r\n"
        )
        status, headers, _ = self.split(self.get(request))
        self.assertEqual(status, "HTTP/1.0 200 OK")
        self.assertEqual(headers["Connection"], "close")

    def test_a_query_string_is_ignored(self):
        status, _, body = self.split(self.get(b"GET /data?x=1 HTTP/1.1\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 200 OK")
        self.assertEqual(json.loads(body), PAYLOAD)

    def test_other_pages_are_not_found(self):
        for path in (b"/", b"/index.html", b"/data/", b"/DATA"):
            status, _, _ = self.split(self.get(b"GET " + path + b" HTTP/1.1\r\n\r\n"))
            self.assertEqual(status, "HTTP/1.0 404 Not Found", path)

    def test_only_get_is_supported(self):
        status, headers, _ = self.split(self.get(b"POST /data HTTP/1.1\r\nContent-Length: 0\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 405 Method Not Allowed")
        self.assertEqual(headers["Allow"], "GET")

    def test_nonsense_gets_a_bad_request(self):
        status, _, _ = self.split(self.get(b"hello\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 400 Bad Request")

    def test_the_readings_are_asked_for_at_the_time_of_the_request(self):
        before = now_ms()
        self.get(b"GET /data HTTP/1.1\r\n\r\n")
        self.assertEqual(len(self.seen_now), 1)
        self.assertGreaterEqual(self.seen_now[0], before)

    # ---- how connections behave ----

    def test_a_request_that_arrives_in_two_parts_is_answered(self):
        client = self.connect()
        client.sendall(b"GET /data HTTP/1.1\r\nHost: pico\r\n")
        for _ in range(10):
            self.server.poll(now_ms())
            time.sleep(0.01)
        client.sendall(b"Accept: */*\r\n\r\n")
        response, _ = self.pump(client)
        self.assertTrue(response.startswith(b"HTTP/1.0 200 OK"))

    def test_a_client_that_says_nothing_is_dropped_after_the_timeout(self):
        config.HTTP_REQUEST_TIMEOUT_MS = 200
        client = self.connect()
        started = time.monotonic()
        response, closed = self.pump(client, seconds=2)
        self.assertEqual(response, b"")
        self.assertTrue(closed)
        self.assertLess(time.monotonic() - started, 1.0)

    def test_a_client_that_hangs_up_is_forgotten(self):
        client = self.connect()
        client.close()
        for _ in range(20):
            self.server.poll(now_ms())
            time.sleep(0.005)
        self.assertEqual(self.server._waiting, [])

    def test_a_slow_client_does_not_stop_another_being_served(self):
        slow = self.connect()
        slow.sendall(b"GET /da")                          # never finishes
        status, _, _ = self.split(self.get(b"GET /data HTTP/1.1\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 200 OK")

    def test_the_app_polling_every_second_for_a_while_works(self):
        for _ in range(25):
            status, _, body = self.split(self.get(b"GET /data HTTP/1.1\r\n\r\n"))
            self.assertEqual(status, "HTTP/1.0 200 OK")
            self.assertEqual(json.loads(body)["v"], 1)

    def test_a_very_long_request_does_not_pile_up_in_memory(self):
        client = self.connect()
        client.sendall(b"GET /data HTTP/1.1\r\nX-Padding: " + b"a" * 3000)
        self.pump(client)  # the answer may or may not get through before the close; the server must cope
        self.assertEqual(self.server._waiting, [])
        status, _, _ = self.split(self.get(b"GET /data HTTP/1.1\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 200 OK")

    def test_polling_with_nobody_there_does_nothing(self):
        for _ in range(50):
            self.server.poll(now_ms())
        self.assertEqual(self.server._waiting, [])

    def test_the_port_can_be_closed_and_opened_again(self):
        self.server.stop()
        with self.assertRaises(OSError):
            socket.create_connection(("127.0.0.1", config.HTTP_PORT), timeout=0.5)
        self.server.start()
        status, _, _ = self.split(self.get(b"GET /data HTTP/1.1\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 200 OK")

    def test_starting_twice_does_not_fail(self):
        self.server.start()
        self.server.start()
        status, _, _ = self.split(self.get(b"GET /data HTTP/1.1\r\n\r\n"))
        self.assertEqual(status, "HTTP/1.0 200 OK")

    def test_polling_a_stopped_server_is_harmless(self):
        self.server.stop()
        self.server.poll(now_ms())


class DiscoveryTests(unittest.TestCase):
    def setUp(self):
        self._saved = (config.DISCOVERY_PORT, config.HTTP_PORT)
        config.DISCOVERY_PORT = free_port(socket.SOCK_DGRAM)
        config.HTTP_PORT = 80
        self.ip = "192.168.43.17"
        self.responder = DiscoveryResponder(lambda: self.ip)
        self.responder.start()
        self.client = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.client.settimeout(0.05)

    def tearDown(self):
        self.responder.stop()
        self.client.close()
        config.DISCOVERY_PORT, config.HTTP_PORT = self._saved

    def ask(self, message):
        self.client.sendto(message, ("127.0.0.1", config.DISCOVERY_PORT))
        deadline = time.monotonic() + 0.5
        while time.monotonic() < deadline:
            self.responder.poll()
            try:
                return self.client.recvfrom(512)[0]
            except socket.timeout:
                pass
            except ConnectionResetError:
                return None  # Windows reports nobody listening on that port this way
        return None

    def test_it_answers_the_question_with_who_and_where_it_is(self):
        reply = json.loads(self.ask(b"PICO?"))
        self.assertEqual(reply, {"v": 1, "name": "pico-meteo", "ip": "192.168.43.17", "port": 80})

    def test_a_newline_after_the_question_does_not_matter(self):
        self.assertIsNotNone(self.ask(b"PICO?\n"))

    def test_the_address_is_the_current_one(self):
        self.ip = "10.0.0.8"
        self.assertEqual(json.loads(self.ask(b"PICO?"))["ip"], "10.0.0.8")

    def test_it_says_which_http_port_to_use(self):
        config.HTTP_PORT = 8080
        self.assertEqual(json.loads(self.ask(b"PICO?"))["port"], 8080)

    def test_other_messages_get_no_answer(self):
        self.assertIsNone(self.ask(b"hello"))
        self.assertIsNone(self.ask(b"pico?"))
        self.assertIsNone(self.ask(b""))

    def test_the_answer_is_what_the_app_can_read(self):
        # The same checks the app makes of a reply (PicoDiscoveryProtocol.parseReply).
        reply = json.loads(self.ask(b"PICO?"))
        self.assertEqual(reply["v"], 1)
        self.assertIn(reply["port"], range(1, 65536))

    def test_many_questions_at_once_do_not_hold_it_up(self):
        for _ in range(50):
            self.client.sendto(b"PICO?", ("127.0.0.1", config.DISCOVERY_PORT))
        started = time.monotonic()
        self.responder.poll()
        self.assertLess(time.monotonic() - started, 0.1)  # only a few are handled per pass

    def test_polling_with_nobody_asking_does_nothing(self):
        for _ in range(20):
            self.responder.poll()

    def test_it_can_be_stopped_and_started_again(self):
        self.responder.stop()
        self.assertIsNone(self.ask(b"PICO?"))
        self.responder.start()
        self.assertIsNotNone(self.ask(b"PICO?"))


if __name__ == "__main__":
    unittest.main()
