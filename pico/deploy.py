"""Put the PICO METEO firmware on a Pico W that already runs MicroPython.

Needs Python 3 and mpremote on the PC (pip install mpremote), and the Pico plugged in by USB.
Run from the repository root:

    python pico/deploy.py                 # copy the files, watchdog OFF (safe while you are working)
    python pico/deploy.py --watchdog      # the final copy: watchdog ON, for normal use
    python pico/deploy.py --port COM7     # when the Pico is not found by itself
    python pico/deploy.py --watchdog --no-reset   # copy only; unplug and replug the Pico to start it

What it does: checks that the board answers, installs the display library (ssd1306) from the MicroPython
package index, copies the program files, copies pico/secrets.py if there is one (your Wi-Fi settings; that
file is never committed), and restarts the board.

Why the watchdog is off by default: it restarts the Pico 8 seconds after the program stops being fed,
which is also what happens when this script interrupts the program to copy files. Copy first with the
watchdog off, check the board, then run it once more with --watchdog.

If the program on the board already runs with the watchdog on, the script notices and re-arms the watchdog
before every file, so the board does not restart in the middle of a copy. Eight seconds after the last file
the watchdog restarts the board by itself, which starts the new program (with --no-reset too).
"""
import argparse
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))

# main.py comes last: it is the file that starts everything.
FILES = ["bmp280.py", "config.py", "lightsensor.py", "logic.py", "server.py", "wifi.py", "main.py"]


def mpremote(port, *args, check=True):
    command = [sys.executable, "-m", "mpremote", "connect", port, *args]
    print("  $ mpremote", *args)
    return subprocess.run(command, check=check)


# Starting a new watchdog re-arms it with a fresh 8 seconds. Used before each file when one is already running.
FEED = "import machine; machine.WDT(timeout=8000)"


def watchdog_is_running(port):
    """True if the program on the board was started with the watchdog on (its config.py says so)."""
    probe = (
        "try:\n import config; print('WATCHDOG=' + str(bool(config.USE_WATCHDOG)))\n"
        "except Exception:\n print('WATCHDOG=False')"
    )
    result = subprocess.run(
        [sys.executable, "-m", "mpremote", "connect", port, "exec", probe], capture_output=True, text=True
    )
    return "WATCHDOG=True" in result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", default="auto", help='serial port, for example COM7 (default: find it)')
    parser.add_argument("--watchdog", action="store_true", help="switch the watchdog on (final copy)")
    parser.add_argument("--no-libs", action="store_true", help="do not install the display library")
    parser.add_argument("--no-reset", action="store_true", help="do not restart the board afterwards (power-cycle it yourself)")
    args = parser.parse_args()

    print("1. Checking the board")
    guard = watchdog_is_running(args.port)  # first, quickly: the board restarts 8 s after it is interrupted
    if guard:
        print("  The program on the board runs with the watchdog on: it will be kept fed while files are copied.")
        mpremote(args.port, "exec", FEED)
    mpremote(args.port, "exec", "import sys; print(sys.implementation); print(sys.version)")

    if not args.no_libs:
        print("2. Installing the display library (ssd1306)")
        mpremote(args.port, "mip", "install", "ssd1306")

    print("3. Copying the program files (watchdog %s)" % ("ON" if args.watchdog else "off"))
    with tempfile.TemporaryDirectory() as folder:
        config_text = open(os.path.join(HERE, "config.py"), encoding="utf-8").read()
        wanted = "USE_WATCHDOG = %s" % ("True" if args.watchdog else "False")
        if "USE_WATCHDOG = True" not in config_text and "USE_WATCHDOG = False" not in config_text:
            sys.exit("USE_WATCHDOG not found in config.py")
        config_text = config_text.replace("USE_WATCHDOG = True", wanted).replace("USE_WATCHDOG = False", wanted)
        config_path = os.path.join(folder, "config.py")
        with open(config_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(config_text)
        for name in FILES:
            source = config_path if name == "config.py" else os.path.join(HERE, name)
            if guard:
                mpremote(args.port, "exec", FEED, "+", "cp", source, ":" + name)
            else:
                mpremote(args.port, "cp", source, ":" + name)

    secrets = os.path.join(HERE, "secrets.py")
    if os.path.exists(secrets):
        print("4. Copying your Wi-Fi settings (secrets.py)")
        if guard:
            mpremote(args.port, "exec", FEED, "+", "cp", secrets, ":secrets.py")
        else:
            mpremote(args.port, "cp", secrets, ":secrets.py")
    else:
        print("4. No pico/secrets.py here: whatever secrets.py the Pico already has is left alone; with none it has no Wi-Fi")

    if args.no_reset:
        print("5. Not restarting the board: unplug and replug it to start the new program")
    else:
        print("5. Restarting the board")
        mpremote(args.port, "reset", check=False)
    print("Done. Watch the console with:  python -m mpremote connect %s repl   (Ctrl-] to leave)" % args.port)


if __name__ == "__main__":
    main()
