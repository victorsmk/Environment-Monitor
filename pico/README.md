# Pico W firmware (PICO METEO)

MicroPython program for the Raspberry Pi Pico W weather station. It measures temperature and pressure
(BMP280), humidity (DHT22), light (BH1750) and sound level (MAX4466), shows them on the OLED and
prints them on the serial console. Phase 2 step 15 adds Wi-Fi and the `GET /data` endpoint described in
[docs/pico-protocol.md](../docs/pico-protocol.md).

## Files

| File | What it is | Goes on the Pico |
|---|---|---|
| `main.py` | The program: starts the hardware, the main loop, the display | yes |
| `config.py` | Pins, intervals, how old a value may get, sound calibration, watchdog | yes |
| `logic.py` | Freshness of values, the sound level, the JSON for the app (no hardware) | yes |
| `lightsensor.py` | BH1750 driver that never makes the program wait | yes |
| `secrets_example.py` | Template for the Wi-Fi settings (used from step 15) | later, as `secrets.py` |
| `tests/` | Tests that run on a PC | no |

Also needed on the Pico, and already there: `bmp280.py` and `ssd1306.py`. The old `bh1750.py` is no
longer used (`lightsensor.py` replaces it) and can stay or be deleted.

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

## Checking it on the real Pico (step 14)

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

## The DHT22 runs on the second core

The DHT22 library waits about 270 ms for every reading. In the main loop that left the microphone unheard
for that long every few seconds, so the DHT22 is read on the Pico's second core instead (`_thread`). If
that thread cannot start (an older run is still alive after pressing Stop in Thonny) the program says so
and reads the DHT22 in the main loop, which still works but with the gaps. After Stop in Thonny use
**Stop/Restart backend** (Ctrl+F2) before running again.

## Sound calibration

The sound level is 0 at `NOISE_RMS_QUIET` and 100 at `NOISE_RMS_LOUD` (in `config.py`, 16-bit ADC
units, the same ones the first version used, 80 and 2500). Read `rms` and `peak` on the console in the
quietest room you will use and in a really loud one, and put those two numbers there. (On the first
run here the room read `rms` about 630 with the old numbers, level 24, so `NOISE_RMS_QUIET` needs to go up
to about that value; calibrating it is part of step 16.)

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
