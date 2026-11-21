# Environment Monitor

An Android app that rates how good a room is for what you plan to do in it: sleep, office work, sport and
so on. A Raspberry Pi Pico W with temperature, humidity, light and sound sensors sends it live readings
over Wi-Fi. Everything is stored on the phone: no cloud, no account.

```
Sensors → Pico W  --HTTP (Wi-Fi)-->  Android app (Compose, Room)
              demo data can stand in for the Pico, so the app also runs on its own
```

## Features

- **Dashboard:** a live score from 0 to 100 (Good, Fair or Poor), one tile per sensor with a gauge showing
  where the room sits against the ideal range, and advice naming the sensor that costs the most points.
- **Profiles:** Sleep, Office and Sport presets plus your own. A profile has an ideal range and an
  importance (Ignore, Minor, Important, Critical) for each sensor, and one of ten drawn icons.
- **Sessions:** start a session and the app stores a reading at the interval you chose (1 second to
  5 minutes, 10 seconds by default). Recording continues with the screen off or the app closed, using a
  foreground service with a notification, and resumes by itself after a restart.
- **History and results:** every session with its average score. Open one for a summary, the share of time
  each sensor spent in range, and interactive charts (tap to read a value, pinch to zoom). Periods without
  readings show as gaps.
- **Settings:** demo data or the real Pico (with *Find Pico* and *Test connection*), light, dark or system
  theme, recording interval, and background recording status.

## How the score works

- Each sensor scores 100 inside its ideal range and falls in a straight line to 0 at a fixed distance
  outside it: 5 °C, 20 % humidity, 150 lux or 25 sound-level points.
- The overall score is the weighted average of the sensors that count. Ignore is left out, Minor counts 1,
  Important 2 and Critical 3.
- 80 or more is Good, 50 to 79 is Fair, below 50 is Poor.

Units: °C, %, lux, and a sound level from 0 to 100 (not decibels). Light also gets a plain word wherever it
is shown: Dark, Dim, Soft, Bright or Very bright.

## The Pico W station

A Pico W with a BMP280 (temperature), a DHT22 (humidity), a BH1750 (light), a MAX4466 microphone (sound)
and an OLED display. The app polls it about once a second (`GET /data`, JSON).

- The Pico tries your known Wi-Fi networks in order (for example a phone hotspot, then home Wi-Fi) and opens
  a network of its own if none is found. Only 2.4 GHz works. Networks go in `pico/secrets.py`, which is
  never committed (copy `pico/secrets_example.py`).
- The app finds the Pico by itself (*Find Pico*), and again whenever the signal is lost and the address has
  changed.
- A failed sensor shows `--` and does not spoil the others. The Pico restarts itself if it hangs.

Wiring, flashing a board, calibration and checks: [pico/README.md](pico/README.md).
Data format: [docs/pico-protocol.md](docs/pico-protocol.md).

## Requirements

| Tool | Version |
|---|---|
| Android Studio | a current stable version that supports Android Gradle Plugin 9.4 |
| JDK | 25 (the one bundled with Android Studio) |
| Android SDK | platform 37 (`compileSdk 37`), build-tools 36.0.0 |
| Gradle | 9.6.0, downloaded by the wrapper |

The app runs on Android 8.0 (API 26) and newer. Libraries are downloaded by Gradle on the first build, so
the first sync needs internet. Versions are in [gradle/libs.versions.toml](gradle/libs.versions.toml). For
the Pico firmware you need Python 3 and `mpremote` (`pip install mpremote`).

## Build and run

Open this folder in Android Studio, wait for the Gradle sync, check that **Settings, Build Tools, Gradle,
Gradle JDK** is the bundled JDK 25, pick a device and press **Run**.

**On a phone** (recommended; the emulator is heavy): enable Developer options (tap *Build number* seven
times), turn on **USB debugging**, connect a USB cable that carries data and accept the prompt on the
phone. Then, in PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices   # should list the phone as "device"
.\gradlew.bat installDebug
```

**On the emulator:** create a device in Device Manager (API 36 or 37), start it and press Run, or use
`installDebug` as above.

The app starts on **demo data**. To use the real Pico, open **Settings, Sensors**, choose *Raspberry Pi
Pico W*, then *Find Pico* (or type its address) and *Test connection*. In demo mode you can pick the
pretend room (Comfortable bedroom, Quiet office, Noisy room, Hot and bright, Changing).

## Tests

```powershell
.\gradlew.bat testDebugUnitTest            # app unit tests, no device needed
.\gradlew.bat connectedDebugAndroidTest    # on-device tests, needs a phone or emulator
python -m unittest discover -s pico/tests  # Pico firmware tests, run on the PC
```

The unit tests cover scoring, session analysis, chart maths, the recorder (on virtual time), the Pico
client and the notification and resume rules. The on-device tests cover the Room database and its
migrations, the Pico client against a small server, and Compose UI tests for chart gestures and icons.

`connectedDebugAndroidTest` uninstalls the app afterwards, which deletes its data on that device. To run the
on-device tests and keep your data:

```powershell
.\gradlew.bat installDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.environmentmonitor.app.test/androidx.test.runner.AndroidJUnitRunner
```

## Project layout

```
app/src/main/java/com/environmentmonitor/app/
  data/         Room entities, DAOs, repositories, scoring engine
  sensors/      SensorSource interface, demo source, Pico client and discovery, SessionRecorder
  recording/    foreground service, notification, wake and Wi-Fi locks, resume after a restart
  ui/           Compose screens (dashboard, profiles, history, settings), charts, icons, theme
  AppContainer.kt   hand-wired dependencies (no DI framework)
app/src/test/, app/src/androidTest/   unit and on-device tests
pico/           MicroPython firmware of the weather station, its tools and tests
docs/           the data format between the Pico and the app
```

Architecture: Compose screens, ViewModels, repositories, then the Room database and the `SensorSource`
interface. The scoring engine is plain Kotlin with no Android code.

## Known limitations

- **Database:** at version 7. Migrations from 4 to 7 keep all data; older versions are wiped
  (`fallbackToDestructiveMigration` in `AppContainer.kt`). Remove that before a real release and write a
  migration for every schema change.
- **Background recording:** a force-stopped app records nothing until it is opened again (Android's rule).
  Allow notifications and set the app to "Don't optimise" in battery settings (both shown in Settings), and
  keep the phone on a charger overnight. It was tested on a Samsung Galaxy A54 with screen off, app swiped
  away, process killed, forced Doze, Wi-Fi lost and a phone restart. A full 8 hour night and the Pico on a
  phone hotspot have not been tried by hand.
- **Android 17:** the two Compose UI tests (chart gestures, icon drawing) fail with
  `NoSuchMethodException: InputManager.getInstance`, because the Compose test library in use calls a function
  Android 17 removed. Updating the Compose BOM and AndroidX test libraries should fix it. The app and the other
  tests are not affected.
- **Charts:** time labels along the bottom can run together on a short session.
- **Pico:** plain HTTP with no password, so use a trusted network (its own network is protected by
  `AP_PASSWORD`). The sound level is relative, so repeat the calibration in `pico/README.md` whenever the
  board or microphone is moved.
- Tested on a Samsung Galaxy A54 (Android 16 and 17) and a Pico W with MicroPython 1.28.0. Other devices
  have not been tried.

## Troubleshooting

| Problem | Fix |
|---|---|
| `& was unexpected at this time` | You are in Command Prompt; the commands here are for PowerShell |
| `No connected devices!` | Connect the phone or start the emulator, then check `adb devices` |
| Gradle complains about the Java version | Set `JAVA_HOME` to Android Studio's `jbr` folder (JDK 25) |
| Build fails on a missing SDK platform | Install Android 37 and build-tools 36.0.0 in SDK Manager |
| Emulator shows `offline` | `adb kill-server`, then restart the emulator |
| The app cannot find the Pico | Check the phone and the Pico are on the same 2.4 GHz network, then press *Find Pico* |
