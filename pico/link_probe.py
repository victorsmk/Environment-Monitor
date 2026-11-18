"""Diagnostic: what does the Wi-Fi chip report when the access point vanishes?

Joins the first network in secrets.py and prints one line per second with every signal that might reveal
that the network is gone: the "connected" flag, the status code, the signal strength (RSSI), and a real
test, a TCP connection to the gateway, which only works when the network is really there.

Run it from the PC with the Pico connected (about 3 minutes), switch the hotspot off once it says
"connected", and see which of the signals notices:

    python -m mpremote connect COM13 run pico/link_probe.py

The Pico restarts by itself afterwards (the watchdog, which the script keeps fed while it runs).
"""
import socket
import time

import machine
import network

import config
import wifi

try:
    import secrets as secrets_module
except ImportError:
    secrets_module = None

DURATION_SECONDS = 170


def probe(gateway):
    """Tries a TCP connection to the gateway. "open" or "refused" mean the network is really there."""
    s = socket.socket()
    s.settimeout(1.0)
    try:
        s.connect((gateway, 53))
        return "open"
    except OSError as e:
        code = e.args[0] if e.args else 0
        if code == 111:  # ECONNREFUSED: somebody answered with a reset, so the network is there
            return "refused"
        if code in (110, 116):  # ETIMEDOUT
            return "timeout"
        return "error%s" % code
    finally:
        s.close()


def main():
    wdt = machine.WDT(timeout=8000)
    networks, _ = wifi.load_secrets(secrets_module)
    ssid, password = networks[0]
    sta = network.WLAN(network.STA_IF)
    sta.active(True)
    sta.connect(ssid, password)
    started = time.ticks_ms()
    while not sta.isconnected():
        wdt.feed()
        if time.ticks_diff(time.ticks_ms(), started) > 20000:
            print("never connected; is the hotspot on?")
            return
        time.sleep_ms(200)
    gateway = sta.ifconfig()[2]
    print("connected, gateway", gateway, "- switch the hotspot off now")
    t0 = time.ticks_ms()
    while time.ticks_diff(time.ticks_ms(), t0) < DURATION_SECONDS * 1000:
        wdt.feed()
        try:
            rssi = sta.status("rssi")
        except Exception as e:
            rssi = "n/a(%s)" % e
        t = time.ticks_diff(time.ticks_ms(), t0) / 1000
        print("t=%5.1f isconnected=%s status=%s rssi=%s probe=%s" % (t, sta.isconnected(), sta.status(), rssi, probe(gateway)))
        time.sleep_ms(500)
    print("done")


main()
