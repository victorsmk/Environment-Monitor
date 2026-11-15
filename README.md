# Environment Monitor

An Android app that rates how good a room is for what you plan to do in it (sleep, office work,
sport, ...). A Raspberry Pi Pico W with temperature, humidity, light and sound sensors will feed it
live readings.

**Status:** phase 1 (the app, running on mock sensor values) is complete. Open ideas and questions
are in [BACKLOG.md](BACKLOG.md).

| Step | What | State |
|---|---|---|
| 1 | Project skeleton | done |
| 2 | Profiles CRUD | done |
| 3 | Sessions CRUD | done (tag `lab-crud`) |
| 4 | Scoring engine | done |
| 5 | Mock sensors and live Dashboard | done |
| 6 | Recording readings during a session | done |
| 7 | Session results and charts | done |
| 8 | Settings (how often to record, demo data) and polish | done |

Later phases: **2** connect the real Pico W over Wi-Fi, **3** record in the background with a
foreground service.

## What the app does

- **Dashboard:** live score (0–100, Good / Fair / Poor), four sensor tiles, advice naming the sensor
  that costs the most points, a profile picker, and Start / Stop session.
- **Icons:** all drawn in code in one outline style, with no emoji. Each sensor has one (thermometer,
  droplet, bulb, loudspeaker), and each profile picks one of ten (moon, laptop, dumbbell, book, game
  controller, lotus, pot, leaf, palette, headphones).
- **Profiles:** presets (Sleep, Office, Sport) plus your own. Each profile has an ideal range and an
  importance (Off / Low / Medium / High) per sensor, set with a slider and filled buttons. For
  light there are also five chips (Dark, Dim, Soft, Bright, Very bright): tap two and the slider
  selects the range between them. Presets are read-only but can be duplicated.
- **History:** every session with its profile, start time and duration. Open one to see its results,
  rename it, add notes, stop it or delete it.
- **Session results:** the average score with a one-line verdict (for example "Light cost the most
  points, mostly too high"), the share of time each sensor spent in range, a chart of the score over
  time with the Good / Fair / Poor zones shaded, and one chart per sensor with the ideal range
  shaded. Tap or drag on a chart to read the exact value and time at that moment; pinch to zoom in
  on a stretch of time (arrows and "Reset zoom" appear while zoomed). Both axes show several round
  values and clock times: temperature and humidity are
  striped like the light chart (humidity in multiples of 5), the score chart has lines at 0, 10, 30,
  50, 65, 80 and 100, and the light chart is split into the five light levels. Each sensor has its
  own colour (temperature orange, humidity blue, light gold, sound
  purple), also used for the dot next to its name on the Dashboard. Charts are drawn by hand on a
  Compose Canvas, with no chart library. They update live while a session runs and need at least
  two readings (about 20 seconds).
- **Recording:** while a session runs, a reading (temperature, humidity, light, sound) is stored at the
  chosen interval, starting the moment the session starts. Recording belongs to the app, not to a
  screen, so it carries on when you switch tabs. Deleting a session deletes its readings.
- **Settings:**
  - *Appearance:* Use system setting (the default), Light or Dark. The choice is applied inside the
    app, so it works whatever the phone is set to, and the status bar icons follow it.
  - *Recording interval:* every second, 5 seconds, 10 seconds (default), 30 seconds, minute or
    5 minutes. Each option shows roughly how many readings an 8 hour night would store. A change
    applies to the next session. A session keeps the interval it started with, so its statistics
    stay consistent.
  - *Demo data:* which pretend room the mock sensors are in (Comfortable bedroom, Quiet office, Noisy
    room, Hot and bright, or Changing). A short interval plus "Changing" is a quick way to fill the
    charts for a demo.
- **Editing safety:** leaving a profile or session screen with unsaved changes asks before discarding
  them, and open dialogs survive rotating the phone.

### How the score works

- Each sensor scores 100 inside its ideal range and falls in a straight line to 0 at a fixed distance
  outside it: 5 °C, 20 % humidity, 150 lux, 25 sound-level points.
- The overall score is the weighted average of the sensors that count. Importance sets the weight:
  Off is ignored, Low 1, Medium 2, High 3.
- Good is 80 or more, Fair 50–79, Poor under 50.

Units in the app: °C, %, lux, and a sound level from 0 to 100. Because lux means little to most
people, light also gets a plain word wherever it is shown: 0–10 Dark, 10–50 Dim, 50–200 Soft,
200–500 Bright, above 500 Very bright. The words are for display only; scoring, profiles and stored
readings all use lux.

## Requirements

| Tool | Version | Notes |
|---|---|---|
| Android Studio | current stable (a version that supports Android Gradle Plugin 9.4) | Easiest way to get everything below |
| JDK | **25** | The JDK bundled with Android Studio (the "JBR") is 25. `gradle/gradle-daemon-jvm.properties` asks for it |
| Android SDK platform | **android-37.0** (API 37) | `compileSdk 37` |
| Android build-tools | 36.0.0 | |
| Gradle | 9.6.0 | Downloaded by the wrapper (`gradlew`), nothing to install |

The app itself runs on **Android 8.0 (API 26) and newer**. It targets API 36 (Android 16).

All libraries are downloaded by Gradle on the first build, so the first sync needs internet access.
Versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml):

- Kotlin 2.2.10 (built into the Android Gradle Plugin 9.4.1) and KSP 2.3.14
- Jetpack Compose (BOM 2026.02.01) with Material 3
- Navigation Compose 2.10.2, Lifecycle 2.11.0, Activity Compose 1.13.0
- Room 2.8.5 for the local SQLite database
- JUnit 4.13.2, kotlinx-coroutines-test 1.10.2, AndroidX Test 1.7.0, ext-junit 1.3.0 and the Compose
  UI test library (version from the Compose BOM) for tests

There is no cloud service, account or API key. All data stays on the phone.

## Open the project

1. Start Android Studio and choose **Open**, then select this folder.
2. Wait for the Gradle sync to finish. If Android Studio offers to install a missing SDK platform or
   build-tools version, accept.
3. Check **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**. It should be
   the bundled JetBrains Runtime 25.

## Run on the emulator

1. In Android Studio, open **Device Manager** and create a device if you have none (for example
   *Medium Phone*, API 36 or 37 system image).
2. Start it with ▶, wait for the home screen, then press **Run ▶** (Shift+F10) with the `app`
   configuration selected.

Or from a terminal (Windows PowerShell). `emulator.exe -list-avds` lists your virtual devices:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Start-Process "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd","Medium_Phone_API_37.0"
# wait until the emulator shows its home screen, then:
.\gradlew.bat installDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.environmentmonitor.app/.MainActivity
```

The emulator is heavy. If your PC struggles, give the virtual device less RAM (about 2 GB) in Device
Manager, or test on a real phone instead.

## Run on your phone

One-time setup on the phone:

1. **Settings → About phone**, tap **Build number** seven times (on some phones it is under
   *Software information*).
2. **Settings → System → Developer options**, turn on **USB debugging**.
3. Connect the phone to the PC with a USB cable that carries data (not a charge-only cable). Choose
   **File transfer** as the USB mode if the phone asks.
4. Accept the **Allow USB debugging?** prompt on the phone and tick *Always allow from this computer*.

Check that the PC sees the phone. It should print a line ending in `device`:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

(In Command Prompt instead of PowerShell, write `"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" devices`.)

Then install. In Android Studio pick the phone in the device dropdown and press **Run ▶**, or:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
```

If it says `unauthorized`, unlock the phone and accept the prompt. If nothing is listed, try another
cable or USB port. If both an emulator and a phone are connected, `installDebug` installs on both.

Wireless debugging (**Developer options → Wireless debugging**) also works if the PC and phone are on
the same Wi-Fi.

## Run the tests

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest          # unit tests, no device needed
.\gradlew.bat connectedDebugAndroidTest  # on-device tests, needs an emulator or phone
```

Unit tests cover the scoring engine, the session analysis behind the results, the chart maths
(including tap lookup, zoom and pan), the mock sensor model, the recorder (on virtual time, so a
"10 second" interval takes no real time), validation and formatting. The on-device tests run
create / read / update / delete against a real in-memory Room database, including readings and the
cascade delete from sessions, and a Compose UI test really pinches, taps and drags a chart.
HTML reports are written to `app/build/reports/`.

Note: `connectedDebugAndroidTest` uninstalls the app when it finishes, which also deletes the
app's data on that device (your profiles and sessions). To run the same tests and keep the data,
install the test APK next to the app and run it directly:

```powershell
.\gradlew.bat installDebug assembleDebugAndroidTest
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am instrument -w com.environmentmonitor.app.test/androidx.test.runner.AndroidJUnitRunner
```

## Using the mock sensors

Until the real device is connected, readings come from a simulator. In **Settings → Demo data**,
you can choose the pretend room: *Comfortable bedroom*, *Quiet office*, *Noisy room*,
*Hot and bright*, or *Changing* (cycles through all four). Values glide towards the room's target, so
a change takes about 20 seconds to settle.

## Project layout

```
app/src/main/java/com/environmentmonitor/app/
  data/        Room entities, DAOs, repositories, scoring engine, validation
  sensors/     SensorSource interface, the mock implementation, and the SessionRecorder
  ui/          Compose screens: dashboard/, profiles/, history/, settings/, charts/, theme/, navigation
  AppContainer.kt   hand-wired dependencies (no DI framework)
  AppSettings.kt    small preferences (selected profile, recording interval, demo scenario)
app/src/test/          unit tests
app/src/androidTest/   database tests
```

Architecture in one line: Compose screens → ViewModels → repositories → Room database and the
`SensorSource` interface. The scoring engine is plain Kotlin with no Android dependencies.

## Git tags

Each step is committed and tagged, so you can look at or return to any milestone:

```powershell
git tag                      # list milestones
git checkout lab-crud        # the CRUD milestone (profiles + sessions)
git checkout main            # back to the latest
```

## Known limitations

- Moving from database version 4 to 5 (the drawn icons) keeps all data through a real migration, but
  older versions have none: the database is wiped when it is that old
  (`fallbackToDestructiveMigration` in `AppContainer.kt`). Remove that before a real release, and
  write a migration for every schema change from now on.
- Recording only works while the app process is alive. If Android closes the app, recording pauses
  until it is opened again (background recording is phase 3).
- The mock sensors produce one new value per second, so recording every second can occasionally store
  the same value twice. A real device will not have this limit.
- Tested on an Android 17 emulator and on a Samsung Galaxy A54 (Android 16). Other devices have not
  been tried.

## Troubleshooting

| Problem | Fix |
|---|---|
| `& was unexpected at this time` | You are in Command Prompt; the commands above are for PowerShell. Open PowerShell, or use the `cmd` form |
| `No connected devices!` from Gradle | Start the emulator or connect the phone, then check `adb devices` |
| Gradle complains about the Java version | Set `JAVA_HOME` to Android Studio's `jbr` folder (JDK 25), as in the commands above |
| Sync or build fails on a missing SDK platform | Install **Android 37** (API 37) and build-tools 36.0.0 in **SDK Manager** |
| Emulator shows `offline` in `adb devices` | `adb kill-server`, then restart the emulator |
| App opens to a long splash screen on a fresh emulator | Normal on the first launch; wait a few seconds |
