"""Pretend hardware for the tests that run the Pico program on a PC.

Importing this module puts fake `machine`, `dht`, `ssd1306`, `bmp280` and `network` modules in place,
and adds the MicroPython time functions to `time`. `World` holds what the pretend hardware does; tests
change it. The tests that use it must import it before importing `main`.
"""
import sys
import time
import types

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
    heartbeats = []            # the heartbeat characters drawn, in order

    # The pretend Wi-Fi chip.
    sta_ip = "192.168.43.17"   # the address the chip reports for the network it joined
    net_accept = set()         # names of networks that let the Pico in
    net_refuse = {}            # name -> status code to report when trying it (-3 wrong password, -2 not found)
    net_attempts = []          # names the Pico tried, in order
    ap_stations = []           # devices connected to the Pico's own network
    ap_config = None           # what the Pico set up as its own network
    radio_active = {"sta": False, "ap": False}

    @classmethod
    def reset(cls):
        cls.bmp_fails = cls.dht_fails = cls.light_fails = cls.oled_fails = False
        cls.mic_swing = 800
        cls.events = []
        cls.wdt_feeds = 0
        cls.resets = 0
        cls.last_screen = {}
        cls.heartbeats = []
        cls.sta_ip = "192.168.43.17"
        cls.net_accept = set()
        cls.net_refuse = {}
        cls.net_attempts = []
        cls.ap_stations = []
        cls.ap_config = None
        cls.radio_active = {"sta": False, "ap": False}


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
        if x >= 112:
            World.heartbeats.append(text)   # the heartbeat in the corner, not a line of text
        else:
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

    def measure(self):
        if World.bmp_fails:
            raise OSError(5, "EIO")
        return 22.46, 101324.9

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


network = types.ModuleType("network")
network.STA_IF = 0
network.AP_IF = 1
network.hostname = lambda name=None: None


class WLAN:
    PM_PERFORMANCE = 0xA11140

    def __init__(self, interface):
        self.kind = "sta" if interface == network.STA_IF else "ap"
        self._ssid = None
        self._ifconfig = None

    def active(self, flag=None):
        if flag is None:
            return World.radio_active[self.kind]
        World.radio_active[self.kind] = bool(flag)

    def config(self, *args, **kwargs):
        if self.kind == "ap" and kwargs:
            World.ap_config = dict(kwargs)

    def connect(self, ssid, password):
        self._ssid = ssid
        World.net_attempts.append(ssid)

    def disconnect(self):
        self._ssid = None

    def isconnected(self):
        return self.kind == "sta" and World.radio_active["sta"] and self._ssid in World.net_accept

    def status(self, what=None):
        if what == "stations":
            return list(World.ap_stations)
        if self._ssid in World.net_accept:
            return 3
        return World.net_refuse.get(self._ssid, 1)  # 1 = still trying

    def ifconfig(self, settings=None):
        if settings is not None:
            self._ifconfig = settings
        if self._ifconfig is None:   # the station: whatever address the test says the network handed out
            return (World.sta_ip, "255.255.255.0", "192.168.43.1", "8.8.8.8")
        return self._ifconfig


network.WLAN = WLAN
sys.modules["network"] = network
