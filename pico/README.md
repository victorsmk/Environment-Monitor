# Pico W firmware (PICO METEO)

MicroPython program for the Raspberry Pi Pico W weather station. It measures temperature and pressure
(BMP280), humidity (DHT22), light (BH1750) and sound level (MAX4466), shows them on the OLED and
prints them on the serial console. It also joins Wi-Fi and serves the readings at `GET /data`, described in
[docs/pico-protocol.md](../docs/pico-protocol.md).

## Files

| File | What it is | Goes on the Pico |
|---|---|---|
| `main.py` | The program: starts the hardware, the main loop, the display | yes |
| `config.py` | Pins, intervals, how old a value may get, sound calibration, watchdog | yes |
| `logic.py` | Freshness of values, the sound level, the JSON for the app (no hardware) | yes |
| `lightsensor.py` | BH1750 driver that never makes the program wait | yes |
| `bmp280.py` | BMP280 driver written for this project (one reading on request, exact integer maths) | yes |
| `wifi.py` | Joins the known networks, opens the Pico's own network if none works | yes |
| `server.py` | `GET /data` over HTTP and the answer to "where is the Pico?" | yes |
| `secrets.py` | Your Wi-Fi settings, see `secrets_example.py` (never committed) | yes, you create it |
| `deploy.py` | Copies all of the above to a Pico from the PC (see below) | no |
| `tests/` | Tests that run on a PC | no |

The display library `ssd1306` is not part of this folder: `deploy.py` installs it from the MicroPython
package index.

### Wi-Fi settings (`secrets.py`)

Either layout works. The first is the one to use from now on; the others are understood so that an
older file keeps working:

```python
NETWORKS = [("home-wifi", "password"), ("my-phone", "password")]   # tried in this order
AP_PASSWORD = "at-least-8-characters"                               # for the Pico's own network
```

or `secrets = {"ssid": "...", "password": "..."}`, or plain `SSID = "..."` and `PASSWORD = "..."`. Without
`AP_PASSWORD` the Pico never opens a network of its own (it will not open an unprotected one).

## A new or replacement board

1. Hold **BOOTSEL** while plugging the Pico into the PC. A drive called `RPI-RP2` appears.
2. Copy the MicroPython file for the **Pico W** onto it (from <https://micropython.org/download/RPI_PICO_W/>;
   this project was tested on **v1.28.0**). The drive disappears by itself and the Pico restarts.
3. From the repository root, with `pip install mpremote` done once:

   ```
   python pico/deploy.py --port COM13
   ```

   (use your port; leave `--port` out to let it search). This installs the display library and copies the
   program, with the watchdog **off**, which is the safe setting while you are still working on it.
4. Check the console (`python -m mpremote connect COM13 repl`, leave with Ctrl-]) and the checks below.
5. When all is well, run `python pico/deploy.py --watchdog` once to switch the watchdog on.

Flashing MicroPython does **not** erase the Pico's file storage. If the board has files from earlier
projects they stay, and a full storage stops `deploy.py` from copying; back them up first (`mpremote cp -r : backup/`).

Wiring (unchanged): BMP280 and BH1750 on I2C0 (GP0 SDA, GP1 SCL), OLED on I2C1 (GP2 SDA, GP3 SCL),
DHT22 on GP15, microphone on GP26. Change them in `config.py` if needed.

## Putting it on the Pico (Thonny)

1. Open Thonny with the Pico connected (interpreter: MicroPython, Raspberry Pi Pico).
2. **View, Files**. In the left pane find this `pico` folder, select `main.py`, `config.py`,
   `logic.py` and `lightsensor.py`, right-click, **Upload to /**. This replaces the old `main.py`.
3. **While you are working in Thonny, set `USE_WATCHDOG = False` in `config.py`** (on the Pico copy).
   The watchdog cannot be stopped once started, so with it on the Pico restarts 8 seconds after you press
   Stop. Set it back to `True` for normal use.
4. Open `main.py` on the Pico and press Run, or just press the Pico's reset (it starts `main.py` by
   itself).

## What the serial console shows

One line per second:

```
T=22.5 H=48 L=312 S=17 | P=1013.2 Tdht=21.4 | rms=143.7 peak=160.2 samples/s=3980 | missing:-
```

`T` temperature (BMP280), `H` humidity (DHT22), `L` light in lux, `S` sound level 0-100, `P` pressure,
`Tdht` the DHT22's own temperature (a diagnostic, never used as the temperature), `rms` and `peak` the
sound in 16-bit ADC units, `samples/s` how many microphone samples were counted in the last second, and
`missing` the sensors with no value (`T`, `H`, `L`, `S`, or `-` for none). The OLED shows `--` for a
missing value and `Fara: ...` on its last line.

## Checking it on the real Pico

Measured on the real Pico W (MicroPython v1.28.0): 3 and 4 minutes of running with the watchdog on, no
restarts, no errors, no value missing. What to expect:

1. `samples/s` shows about 3300 to 3600. The real rate is about 3650 per second: the program counts only
   finished 100 ms windows, so the count is a little low, and the second core costs about 9 %. Under
   about 3000 would mean something is blocking.
2. Pull the DHT22's data wire: after about 10 seconds `H=--` and the OLED says `Umid: -- %` and
   `Fara: H`, while the other values keep updating. Plug it back: the value returns within seconds.
3. Same with the BH1750 or BMP280 (power down first): `--` after 5 seconds, and when it is plugged back it
   is started again within about 10 seconds (`BH1750 started.` appears).
4. Clap next to the microphone: `S` jumps for one second, then falls back.
5. Leave it for an hour with the watchdog on: it must not restart by itself (no `PICO METEO is
   running.` line appearing again). Done for 4 minutes so far.

## Fault test on the real board

`fault_test.py` makes each sensor fail on purpose, one after the other (the BMP280, the BH1750, the DHT22,
then the display), for 15 to 30 seconds each, and checks that the station copes: the failed value must turn
to `--` while the others carry on, and come back by itself afterwards. Nothing is unplugged: the failures
are injected into the program's own sensor code, but everything else is the real thing. It takes about
3 minutes:

```
python -m mpremote connect COM13 run pico/fault_test.py
```

It prints `CHECK ok` or `CHECK FAIL` lines and a verdict, and the Pico restarts by itself afterwards (the
watchdog). Last run on the real Pico W: 9 of 9 checks passed.

## When the network disappears (measured on the real board)

The Wi-Fi chip does **not** notice by itself that its access point has gone: it only finds out when its own
packets stop being acknowledged, and an idle Pico sends nothing. After the phone's hotspot was switched off
the Pico went on reporting "connected" for minutes (with a signal strength "read" from neighbouring routers).
So while it is joined, `wifi.py` tries to open a TCP connection to the gateway every 10 seconds
(`WIFI_KEEPALIVE_MS`) and ignores the result; the unacknowledged packets make the chip report the loss.
Now the Pico notices a vanished hotspot within about 10 seconds, tries it for 10 seconds, and moves on to the
next network in `secrets.py` (the home Wi-Fi) or opens its own network.

The address is read again while joined, because moving from one network to another leaves the old address in
place until the new one arrives. `link_probe.py` is the diagnostic that was used to find all this: it joins the
first network and prints what the chip reports while you switch the hotspot off.

## The DHT22 runs on the second core

The DHT22 library waits about 270 ms for every reading. In the main loop that left the microphone unheard
for that long every few seconds, so the DHT22 is read on the Pico's second core instead (`_thread`). If
that thread cannot start (an older run is still alive after pressing Stop in Thonny) the program says so
and reads the DHT22 in the main loop, which still works but with the gaps. After Stop in Thonny use
**Stop/Restart backend** (Ctrl+F2) before running again.

## Sound calibration

The sound level is 0 at `NOISE_RMS_QUIET` and 100 at `NOISE_RMS_LOUD` (in `config.py`, 16-bit ADC
units, the same ones the first version used). Between them the scale is logarithmic, like decibels: every
doubling of the sound adds the same number of points. Read `peak` on the console in the quietest room
you will use and for a loud sound, and put those two numbers there.

Measured on the real Pico in its final place (the peak of a 100 ms window, median and highest): silence 79
(93), normal talking 340 (460), a loud voice or music 1300 (3200), shouting or hard claps 2600 (10300). The
values in `config.py` (quiet 95, loud 8000) come from these. The same recording made the day before, with the
board on a desk, gave about 2.7 times lower numbers, so **repeat this recording whenever the board or the
microphone is moved**: the recording is four guided levels of 15 seconds (silence, normal talking, loud,
very loud) read from the `peak` field of the console.

## Tests on a PC

From the repository root (needs only Python 3):

```
python -m unittest discover -s pico/tests -v
```

`test_logic.py` tests the freshness rules, the sound level and the JSON. `test_station.py` runs
`main.py` against a pretend microphone, sensors and display: values reach the display, a failing sensor
shows `--` without stopping the others, a broken sensor is started again, the light sensor is never
waited for, and an unexpected error restarts the Pico. It cannot say how fast the real Pico is, which is
why the checks above exist.
