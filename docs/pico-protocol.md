# Pico W protocol (version 1)

How the Android app gets readings from the Raspberry Pi Pico W. Status: **agreed in step 9** of
[PHASE2.md](../PHASE2.md) (decisions A to D below).

## Overview

- The Pico joins a Wi-Fi network (2.4 GHz) and runs a tiny HTTP server on port 80. If it cannot join
  any known network it starts its own (see "Networks").
- The phone polls `GET /data` about once a second. The Pico never pushes.
- No authentication and no encryption: it is meant for a trusted local network only.
- The phone adds the timestamp when the response arrives (the Pico has no real clock).
- The Pico answers from its latest cached values. It never reads a sensor while answering.

## Request

```
GET http://<pico-address>/data
```

Any other path returns `404`. Only `GET` is supported.

## Response

`200 OK`, `Content-Type: application/json`, for example:

```json
{
  "v": 1,
  "uptime_ms": 5321000,
  "temperature": 22.1,
  "humidity": 48.2,
  "light": 312.5,
  "sound": 17,
  "pressure_hpa": 1013.2,
  "temperature_dht": 21.4,
  "noise_rms": 143.7
}
```

| Field | Type | Unit | Meaning |
|---|---|---|---|
| `v` | integer | | Protocol version. The app rejects versions it does not know |
| `uptime_ms` | integer | ms | Time since the Pico booted, when this response was built |
| `temperature` | number or `null` | °C | **BMP280** only. This is the value the app scores |
| `humidity` | number or `null` | % | **DHT22** only |
| `light` | number or `null` | lux | BH1750 |
| `sound` | number or `null` | 0-100 | Sound level, relative scale from the microphone (not dB) |
| `pressure_hpa` | number or `null` | hPa | BMP280. Not used by the app yet |
| `temperature_dht` | number or `null` | °C | Diagnostic: the DHT22's own temperature, to compare with the BMP280 in step 16. Never scored |
| `noise_rms` | number or `null` | ADC counts | Diagnostic, used to calibrate `sound` |

Rules:

- `v`, `uptime_ms`, `temperature`, `humidity`, `light` and `sound` must always be present. The
  diagnostic fields may be left out. The app ignores fields it does not know, so new ones can be
  added without changing the version.
- A value is **`null`** when its sensor failed on the last read, or when the last good value is
  older than the limit below. The Pico must never send an old value as if it were fresh.
- Rounding: temperature, humidity and light to 1 decimal, sound to an integer.

Staleness limits on the Pico (a value older than this becomes `null`):

| Sensor | Read every | Becomes `null` after |
|---|---|---|
| BMP280 (`temperature`, `pressure_hpa`) | 1 s | 5 s |
| DHT22 (`humidity`, `temperature_dht`) | 2.5 s | 10 s |
| BH1750 (`light`) | 1 s | 5 s |
| Microphone (`sound`) | 1 s | 5 s |

## Networks (decision B)

The project is used in two places: at home on the home Wi-Fi, and at school with the phone on mobile
data and no school Wi-Fi. The Pico and the app therefore have to cope with a changing network.

**On the Pico**, `secrets.py` holds an ordered list of networks (for example "home" and "my phone's
hotspot"). At start-up, and whenever the connection is lost, the Pico tries them in order, about
10 seconds each. Whichever connects first is used.

1. **Home Wi-Fi.** Phone and Pico are on the same router.
2. **Phone hotspot (the school setup).** The phone shares its mobile data as a Wi-Fi hotspot and the
   Pico joins it. The phone keeps its mobile data, and the app reaches the Pico over the hotspot. The
   hotspot must be on the **2.4 GHz band** (Samsung: Mobile Hotspot, Configure, Band).
3. **Last resort: the Pico's own network.** If none of the known networks connects within about
   30 seconds, the Pico starts an access point called `PicoMeteo` (WPA2, password in `secrets.py`) at
   the fixed address `192.168.4.1`. The phone joins it. Android may route traffic to a Wi-Fi network
   without internet over mobile data instead, so in this mode the app must bind its requests to the
   Wi-Fi network (`ConnectivityManager`). This is built in step 12 and only matters for this mode.

**Finding the Pico.** The address differs from place to place (a hotspot hands out different
addresses), so the app does not rely on a typed IP:

- **Discovery:** the app broadcasts a UDP packet containing `PICO?` to port `4210` on the current
  Wi-Fi/hotspot network. The Pico replies to the sender with
  `{"v":1,"name":"pico-meteo","ip":"192.168.43.17","port":80}`. The app saves the address and uses
  it.
- **Manual address:** Settings also has an address field (default `192.168.4.1`) as a fallback.
- **On the Pico's screen:** the OLED shows which network it is on and its IP, so the address can
  always be read and typed in.
- **Remembered address:** the app tries the last known address first, and runs discovery when it
  fails.

## What the app does with it

- **Connection state:** *connecting* until the first valid response; *connected* while responses
  keep arriving; *no signal* after 3 failed polls in a row (timeout 2 s per request, so about 6 s).
  It keeps retrying every second, runs discovery again if the saved address stops answering, and
  recovers by itself.
- **Failed request:** a timeout, a non-200 status, invalid JSON or an unknown `v`.
- **Pico reboot:** `uptime_ms` going backwards means the Pico restarted. The app carries on and only
  logs it.
- **A `null` sensor:** see decision C.

## Decisions

**A. Temperature.** `temperature` is the **BMP280** reading. The DHT22 provides humidity only.
`temperature_dht` is sent as a diagnostic and never scored.

**B. Finding the Pico.** Flexible, as described under "Networks": a list of known networks on the
Pico, the phone hotspot for school, the Pico's own network as a last resort, and UDP discovery plus
a manual address in the app.

**C. A sensor that is `null`.** A failed sensor must not throw away the rest of the data, and it
must not make the app score the room as if the sensor were fine.

- **Dashboard:** that tile shows `--` and a short "no data" note. The other tiles stay live.
- **Score:** worked out from the sensors that do have a value. The weights of the missing ones are
  left out, in the same way "Ignore" is left out today, so the remaining shares add up to 100 % again.
  The score card says which sensor is missing ("Score without light"). If every sensor that counts
  in the profile is missing, there is no score.
- **Recording:** the reading is stored with the missing values as empty (the sensor columns become
  nullable: database migration 6 to 7, existing readings are kept as they are). A sample where all
  four are `null` is not stored at all.
- **Session results:** each sensor's statistics and chart use only the readings where it had a
  value, and the chart line breaks over missing stretches. The results say how much of the session a
  sensor was missing.
- **Why not skip the whole sample:** hobby sensors fail now and then (the DHT22 especially). If one
  sensor were dead for the whole night, skipping would leave an empty session with no explanation.

**D. Wi-Fi credentials.** A `secrets.py` file on the Pico with the list of networks and the access
point password. It is listed in `.gitignore`. A template is in `pico/secrets_example.py`.

## Not in version 1

- Authentication or encryption.
- Settings pushed from the phone to the Pico.
- Stored readings on the Pico while the phone is away.
