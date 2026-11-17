# Phase 2: connect the real Pico W

**Goal:** the app shows and records real readings from the Pico W over Wi-Fi using HTTP, both at
home (home Wi-Fi) and at school (phone hotspot, phone on mobile data). Phase 1 behaviour is
unchanged when the Pico is switched off, and the demo data stays available.

**Done when:** a full night session on real hardware records readings, the Dashboard shows a clear
"offline" state if the Pico drops out, a failed sensor does not ruin a session, and the whole thing
works at home and on a phone hotspot.

Steps continue the numbering of the README table. Each step ends in a commit and a tag, as in
phase 1. The data format and the decisions behind it are in
[docs/pico-protocol.md](docs/pico-protocol.md).

**Transport decision:** HTTP, the phone polls the Pico. MQTT was considered and not taken: it needs
a broker that must always be running, which breaks "no cloud, no account, nothing to install". The
app only knows the `SensorSource` interface, so an MQTT source could be added later without touching
any screen.

**Decisions made in step 9**
- Temperature comes from the BMP280 only; the DHT22 is used for humidity only.
- Flexible network: the Pico tries a list of known Wi-Fi networks (home, phone hotspot), falls back to
  its own network, and the app finds it by UDP discovery (with a manual address as backup).
- A sensor that is `null` does not discard the sample: it is stored as empty and the score is worked
  out from the other sensors (nullable columns, database migration 6 to 7).

| Step | What | State |
|---|---|---|
| 9 | Contract and decisions | done |
| 10 | Connection state in the app (still on mock data) | done |
| 11 | Missing sensor values (nullable readings, score without the sensor) | done |
| 12 | `PicoSensorSource` (HTTP client) | done |
| 13 | Settings for the source, discovery | done |
| 14 | Pico firmware, part 1: robustness, no Wi-Fi | done (running on the Pico) |
| 15 | Pico firmware, part 2: networks and `GET /data` | |
| 16 | Integration on real hardware (home, hotspot, own network) | |
| 17 | Gaps in session results | |
| 18 | Wrap-up | |

## Step 9: Contract and decisions (no app code)
- `docs/pico-protocol.md`, the Pico code moved into `pico/`, a Wi-Fi settings template.
- **Check:** the contract is read and agreed before any code is written.

## Step 10: Connection state in the app (still on mock data)
- Extend `SensorSource` with a status: connecting, connected, no signal.
- Dashboard: an offline banner and greyed tiles when there is no signal.
- `SessionRecorder` does not re-save the last value when the source is offline.
- The mock source reports "connected"; a debug switch simulates drop-outs.
- **Check:** unit tests for the recorder across a drop-out; a visual check on the emulator.

## Step 11: Missing sensor values
- `Measurement` fields become nullable. `Reading` columns become nullable: database migration 6 to 7
  (existing readings keep their values), with a migration test.
- `ScoringEngine`: a missing sensor is left out and the other weights are renormalised; no score if
  nothing that counts has a value. The score card says "Score without light".
- Dashboard tiles show `--` for a missing sensor. The mock source can drop a sensor on demand.
- The recorder stores a sample with some empty values, and skips one with all four empty.
- **Check:** unit tests for the scoring with missing sensors (one, several, all, the ignored one),
  the migration test, and the recorder.

## Step 12: `PicoSensorSource` (HTTP client)
- Poll once a second, parse the JSON, map `null` to a missing value, report connection status.
- Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, and a network security config that allows plain HTTP
  to the local network. (`ACCESS_WIFI_STATE` is added in step 13, where discovery needs it.)
- Bind requests to the Wi-Fi network when needed, so the Pico's own network works while mobile data
  is on (`ConnectivityManager`).
- **Check:** unit tests against a fake HTTP server: good data, `null` values, timeout, bad JSON,
  unknown version, reconnect.

## Step 13: Settings for the source, discovery
- (Built: Settings > Sensors. The source is locked while a session records. No `ACCESS_WIFI_STATE`
  permission was needed. The address is followed automatically when the signal is lost and the Pico
  has moved.)
- Choose Pico or Demo data; a "Find Pico" button (UDP broadcast on port 4210); a manual address
  field (default `192.168.4.1`); "Test connection"; the last working address is remembered and tried
  first.
- A wrapper that can switch the active source while the app runs (`AppContainer` builds a fixed
  `MockSensorSource` today). A rule for switching during a running session, for example "applies to
  the next session".
- **Check:** discovery and switching on the emulator against a fake Pico running on the PC.

## Step 14: Pico firmware, part 1 (robustness, no Wi-Fi yet)
- Remove the LED code (the onboard LED is hidden and unused).
- Temperature from the BMP280 only; DHT22 for humidity only (its temperature kept as a diagnostic).
- Take microphone sampling off the main loop: hardware timer with a buffer.
- Start the light measurement early and read it later, so the loop does not block about 180 ms.
- No big-integer maths in the sampling.
- A validity/age per sensor, `--` on the OLED when a value is stale.
- try/except around the loop and a watchdog.
- **Check:** the printed sample count is close to 4000 per second; unplugging a sensor shows `--`
  instead of a frozen value.

## Step 15: Pico firmware, part 2 (networks and `GET /data`)
- Try the networks from `secrets.py` in order, reconnect on loss, and fall back to its own network
  `PicoMeteo` (192.168.4.1) after about 30 seconds.
- The UDP discovery answer on port 4210.
- The HTTP server returns only the latest cached values and never reads sensors itself.
- The OLED shows the network in use and the IP.
- **Check:** open `http://<ip>/data` in a browser. Leave it for an hour: no drop-outs or memory
  growth.

## Step 16: Integration on real hardware
- App and Pico together in all three setups: home Wi-Fi, phone hotspot with the phone on mobile data
  (the school case), and the Pico's own network.
- Calibrate the sound scale (`NOISE_RMS_QUIET`, `NOISE_RMS_LOUD`) against a quiet and a loud room;
  confirm the light values; compare `temperature` with `temperature_dht` in the real room.
- **Check:** a 30-minute session with the Pico turned off and on in the middle, and with one sensor
  unplugged; a run on the Samsung A54 as well as the emulator.

## Step 17: Gaps in session results
- Check how `SessionAnalysis` and the charts treat missing stretches of time and missing sensors;
  fix any that assume evenly spaced, complete readings.
- Per-sensor statistics use only the readings where the sensor had a value; the results show how much
  of the session a sensor was missing.
- **Check:** unit tests with a session that has a gap and one with a sensor missing; charts break the
  line instead of drawing across it.

## Step 18: Wrap-up
- README: status table, how to flash and configure the Pico, the wiring, the three network setups,
  known limitations.
- Tag phase 2; list what carries into phase 3 (foreground service, resume after the process is
  killed).

## Order and dependencies
- Step 9 comes first; everything depends on the contract.
- Steps 10-13 (app) and 14-15 (Pico) are independent and can run in either order or in parallel. The
  app side uses a fake server, so it does not need the hardware.
- Steps 16-18 need both sides finished.

## Risks to watch
- **Router "client isolation" or a guest network** can stop the phone reaching the Pico. Test early,
  in step 15.
- **The Pico only supports 2.4 GHz Wi-Fi.** A phone hotspot set to 5 GHz will not be found.
- **Hotspot addresses change** between sessions, which is why discovery exists.
- **Broadcasts can be blocked** on some networks, so the manual address stays as a backup.
- **Android may send traffic over mobile data** instead of a Wi-Fi network without internet, which
  is the reason for the network binding in step 12.
- **Memory on the Pico** is tight with Wi-Fi plus the display driver; watch it in the one-hour test.
