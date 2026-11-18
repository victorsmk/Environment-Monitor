# The two things the Pico answers on the network (see docs/pico-protocol.md):
#   HttpServer          GET /data      the latest readings as JSON (port 80)
#   DiscoveryResponder  "PICO?" in a UDP packet (port 4210) -> who and where the Pico is
#
# Both are polled from the main loop and never wait: a connection that has not sent its request yet
# is simply looked at again on the next pass, so the microphone keeps being sampled.

import json
import socket

import config
from logic import age_ms


class HttpServer:
    def __init__(self, payload_fn):
        # payload_fn(now) -> the dict that GET /data sends
        self._payload_fn = payload_fn
        self._listener = None
        self._waiting = []  # connections that have not finished sending their request: [conn, bytes, since]

    def start(self):
        """(Re)open the port. Called when the network changes, as sockets do not survive that."""
        self.stop()
        listener = socket.socket()
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listener.bind(("0.0.0.0", config.HTTP_PORT))
        listener.listen(3)
        listener.setblocking(False)
        self._listener = listener

    def stop(self):
        for entry in self._waiting:
            self._close(entry[0])
        self._waiting = []
        if self._listener is not None:
            self._close(self._listener)
            self._listener = None

    def poll(self, now):
        if self._listener is None:
            return
        try:
            conn, _ = self._listener.accept()
            conn.setblocking(False)
            self._waiting.append([conn, b"", now])
        except OSError:
            pass  # nobody is knocking
        still_waiting = []
        for entry in self._waiting:
            if not self._serve(entry, now):
                still_waiting.append(entry)
        self._waiting = still_waiting

    def _serve(self, entry, now):
        """Look at one connection. True when it is finished (answered or dropped)."""
        conn = entry[0]
        try:
            data = conn.recv(512)
            if not data:
                self._close(conn)  # the client went away
                return True
            entry[1] += data
        except OSError:
            # Nothing to read yet. Give a slow client a little time, then drop it.
            if age_ms(now, entry[2]) >= config.HTTP_REQUEST_TIMEOUT_MS:
                self._close(conn)
                return True
            return False
        request = entry[1]
        if b"\r\n\r\n" not in request and b"\n\n" not in request and len(request) < 1024:
            return False  # the rest of the request is still on its way
        try:
            conn.send(self._answer(request, now))
        except OSError:
            pass
        self._close(conn)
        return True

    def _answer(self, request, now):
        parts = request.split(b"\n", 1)[0].strip().split(b" ")
        if len(parts) < 2:
            return self._reply("400 Bad Request", "text/plain", "Bad request")
        method = parts[0]
        path = parts[1].split(b"?", 1)[0]
        if method != b"GET":
            return self._reply("405 Method Not Allowed", "text/plain", "Only GET is supported", "Allow: GET\r\n")
        if path != b"/data":
            return self._reply("404 Not Found", "text/plain", "Not found")
        return self._reply("200 OK", "application/json", json.dumps(self._payload_fn(now)))

    @staticmethod
    def _reply(status, content_type, body, extra=""):
        body = body.encode()
        head = (
            "HTTP/1.0 " + status + "\r\nContent-Type: " + content_type + "\r\nContent-Length: " + str(len(body))
            + "\r\nCache-Control: no-store\r\nConnection: close\r\n" + extra + "\r\n"
        )
        return head.encode() + body

    @staticmethod
    def _close(sock):
        try:
            sock.close()
        except Exception:
            pass


class DiscoveryResponder:
    REQUEST = b"PICO?"

    def __init__(self, ip_fn):
        # ip_fn() -> the Pico's own address as text, or "" when it has none
        self._ip_fn = ip_fn
        self._sock = None

    def start(self):
        self.stop()
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.bind(("0.0.0.0", config.DISCOVERY_PORT))
        sock.setblocking(False)
        self._sock = sock

    def stop(self):
        if self._sock is not None:
            try:
                self._sock.close()
            except Exception:
                pass
            self._sock = None

    def poll(self):
        if self._sock is None:
            return
        for _ in range(4):  # a few questions per pass, so a flood cannot hold up the main loop
            try:
                data, sender = self._sock.recvfrom(64)
            except OSError:
                return
            if data.strip() == self.REQUEST:
                reply = json.dumps({"v": 1, "name": config.HOSTNAME, "ip": self._ip_fn(), "port": config.HTTP_PORT})
                try:
                    self._sock.sendto(reply.encode(), sender)
                except OSError:
                    pass
