# Wi-Fi for the station: join one of the known networks (home Wi-Fi, the phone's hotspot) and, if none
# works, open its own network so the phone can still reach it. See docs/pico-protocol.md, "Networks".
#
# Everything here is a small state machine that is called from the main loop with the current time.
# It never waits, so the microphone keeps being sampled while the Pico is still connecting.

import network
import socket

import config
from logic import age_ms

MODE_OFF = "off"                # the radio has not been started
MODE_CONNECTING = "connecting"  # trying the known networks one after the other
MODE_STATION = "station"        # joined a network; ip is the address on it
MODE_AP = "ap"                  # running its own network; ip is config.AP_ADDRESS


def load_secrets(module):
    """The Wi-Fi settings from secrets.py: ([(ssid, password), ...], access point password or None).

    These layouts are understood (the first one that is present is used):
      NETWORKS = [("home", "pass"), ("my-phone", "pass")]     tried in this order
      secrets = {"ssid": "...", "password": "..."}            an older layout, one network
      SSID = "..."  and  PASSWORD = "..."                      another older layout, one network
    plus an optional AP_PASSWORD (8 characters or more) for the Pico's own network.
    """
    if module is None:
        return [], None
    networks = []
    listed = getattr(module, "NETWORKS", None)
    if listed:
        for item in listed:
            networks.append((item[0], item[1]))
    else:
        legacy = getattr(module, "secrets", None)
        if isinstance(legacy, dict) and legacy.get("ssid"):
            networks.append((legacy["ssid"], legacy.get("password", "")))
        elif getattr(module, "SSID", None):
            networks.append((module.SSID, getattr(module, "PASSWORD", "")))
    return networks, getattr(module, "AP_PASSWORD", None)


KEEPALIVE_PORT = 53        # the DNS port: routers and phones either accept the connection or refuse it at once
KEEPALIVE_TIMEOUT = 1.0    # seconds to wait when nobody answers; only ever spent when the network is dead


def _send_keepalive(gateway):
    """Try to open a TCP connection to the gateway, and close it again.

    What matters is that packets go out to the gateway and have to be acknowledged. If the gateway is there,
    it answers at once (accepts or refuses); if it is gone, the chip sees no acknowledgement and reports the
    link as lost within seconds. Measured on the real Pico: this is what made the loss visible in 4 seconds.
    The result itself is ignored.
    """
    sock = socket.socket()
    sock.settimeout(KEEPALIVE_TIMEOUT)
    try:
        sock.connect((gateway, KEEPALIVE_PORT))
    except OSError:
        pass  # refused or timed out: the packets have gone out, which is all that was wanted
    finally:
        sock.close()


class WifiManager:
    def __init__(self, networks, ap_password=None, keepalive=None):
        # keepalive(gateway): sends the keep-alive packet; replaceable so a test can watch it
        self._keepalive = keepalive or _send_keepalive
        self.networks = list(networks)
        self.ap_password = ap_password
        self.mode = MODE_OFF
        self.ip = None            # the Pico's address, None while there is none
        self.ssid_index = None    # which known network is in use
        self.changed = False      # set whenever the mode or address changes; the caller clears it
        self.last_error = None
        self._sta = None
        self._ap = None
        self._began = 0           # when the current round of trying began
        self._index = 0           # the known network being tried
        self._try_started = 0
        self._lost_since = None
        self._last_keepalive = 0
        self._ap_since = 0

    # ----- starting ---------------------------------------------------------

    def start(self, now):
        """Switch the radio on and start connecting. This one call takes about a second."""
        try:
            network.hostname(config.HOSTNAME)
        except Exception:
            pass
        self._sta = network.WLAN(network.STA_IF)
        self._sta.active(True)
        try:
            self._sta.config(pm=network.WLAN.PM_PERFORMANCE)  # no power saving: answers arrive quickly
        except Exception:
            pass
        self._begin_round(now, 0)

    def _begin_round(self, now, first):
        self._began = now
        self._set(MODE_CONNECTING, None)
        if not self.networks:
            self._start_ap(now)  # nothing to try: only the Pico's own network is left
            return
        self._try(first, now)

    def _try(self, index, now):
        self._index = index % len(self.networks)
        ssid, password = self.networks[self._index]
        self._try_started = now
        try:
            self._sta.disconnect()
        except Exception:
            pass
        self._sta.connect(ssid, password)

    def _set(self, mode, ip):
        if mode != self.mode or ip != self.ip:
            self.changed = True
        self.mode = mode
        self.ip = ip

    # ----- the main loop calls this -----------------------------------------

    def step(self, now):
        if self.mode == MODE_CONNECTING:
            self._step_connecting(now)
        elif self.mode == MODE_STATION:
            self._step_station(now)
        elif self.mode == MODE_AP:
            self._step_ap(now)

    def _step_connecting(self, now):
        if not self.networks:
            return  # nothing to try and no network of its own possible: there is nothing to do but wait
        if self._sta.isconnected():
            ip = self._sta.ifconfig()[0]
            if ip != "0.0.0.0":  # joined, but the address from the new network has not arrived yet
                self.ssid_index = self._index
                self._lost_since = None
                self._set(MODE_STATION, ip)
                return
        waited = age_ms(now, self._try_started)
        # A refusal (wrong password, network not found) is final for this try, but wait a second
        # before the next one so a failing network is not hammered.
        refused = waited >= 1000 and self._sta.status() < 0
        if refused or waited >= config.WIFI_TRY_MS:
            following = self._index + 1
            if following >= len(self.networks):
                # Every known network has had its turn. After AP_AFTER_MS of this, open our own network.
                if age_ms(now, self._began) >= config.AP_AFTER_MS and self._start_ap(now):
                    return
                following = 0
            self._try(following, now)

    def _step_station(self, now):
        if self._sta.isconnected():
            self._lost_since = None
            # The address can change after joining: moving to another network leaves the old address in
            # place until the new one arrives (seen on the real board: the display showed the hotspot's address
            # while the Pico was on the home Wi-Fi). Reading it again keeps the display and the app right.
            ip = self._sta.ifconfig()[0]
            if ip != "0.0.0.0" and ip != self.ip:
                self._set(MODE_STATION, ip)
            self._send_keepalive_if_due(now)
            return
        if self._lost_since is None:
            self._lost_since = now
        elif age_ms(now, self._lost_since) >= config.WIFI_LOST_MS:
            self._lost_since = None
            self._begin_round(now, self.ssid_index or 0)  # the network that worked is tried first

    def _send_keepalive_if_due(self, now):
        """Give the Wi-Fi chip something to be acknowledged, so that it notices when the network vanishes.

        The chip only finds out that its access point is gone when its own packets stop being acknowledged.
        A Pico that sits idle and merely answers requests sends nothing, so after the phone's hotspot was
        switched off it went on reporting "connected" for minutes, with a signal strength "read" from
        neighbouring routers (seen on the real board). A connection attempt to the gateway every few seconds
        makes the loss show up within seconds. The answer is not looked at.
        """
        if age_ms(now, self._last_keepalive) < config.WIFI_KEEPALIVE_MS:
            return
        self._last_keepalive = now
        try:
            self._keepalive(self._sta.ifconfig()[2])
        except Exception:
            pass  # a failed send is the very thing the chip is meant to notice

    def rssi(self):
        """Signal strength in dBm while joined to a network, else None. For the console, to see how good the link is."""
        if self.mode != MODE_STATION:
            return None
        try:
            return self._sta.status("rssi")
        except Exception:
            return None

    def _step_ap(self, now):
        if age_ms(now, self._ap_since) < config.AP_RETRY_MS:
            return
        if self._has_clients():
            self._ap_since = now  # somebody is using our network: do not drop it
            return
        # Nobody connected for a while: see whether a known network (say, the hotspot) is on now.
        self._stop_ap()
        self._sta.active(True)
        self._begin_round(now, 0)

    # ----- the Pico's own network -------------------------------------------

    def _start_ap(self, now):
        password = self.ap_password
        if not password or len(password) < 8:
            self.last_error = "No AP_PASSWORD (8 characters or more) in secrets.py: no network of its own."
            return False
        try:
            self._sta.disconnect()
            self._sta.active(False)
            ap = network.WLAN(network.AP_IF)
            ap.config(essid=config.AP_SSID, password=password)
            ap.active(True)
            ap.ifconfig((config.AP_ADDRESS, "255.255.255.0", config.AP_ADDRESS, "8.8.8.8"))
            self._ap = ap
        except Exception as e:
            self.last_error = "Could not start the network of its own: " + str(e)
            try:
                self._sta.active(True)
            except Exception:
                pass
            return False
        self._ap_since = now
        self._set(MODE_AP, config.AP_ADDRESS)
        return True

    def _stop_ap(self):
        try:
            self._ap.active(False)
        except Exception:
            pass

    def _has_clients(self):
        try:
            return len(self._ap.status("stations")) > 0
        except Exception:
            return True  # cannot tell: assume somebody is connected

    # ----- for the display and the console ----------------------------------

    def describe(self):
        """A short text that fits the 16 characters of the display."""
        if self.mode == MODE_STATION:
            return self.ip
        if self.mode == MODE_AP:
            return "AP " + self.ip
        if self.mode == MODE_CONNECTING:
            return "WiFi: conectez.."
        return "WiFi: oprit"
