# Phase 3: record in the background

**Goal:** a session keeps recording all night with the screen off and the app closed. Android must not stop it,
and if it does stop it, the recording must carry on by itself as soon as it can, leaving only an honest gap.

**Done when:** a full 8 hour session on the real Pico, started with the app and then left alone (screen off,
app swiped away from the recents list, phone on the charger), has readings for the whole night, with at most
short gaps that the results show.

## What is already true (so phase 3 is smaller than it sounds)

- The recorder (`SessionRecorder`), the sensor source and the Pico address finder live in `AppContainer`, which
  lives as long as the app **process**, not a screen. Switching tabs or closing the screen already changes
  nothing.
- The recorder watches the running session in the database (`SessionRepository.activeSession`). So whenever the
  process starts, it picks the running session up again by itself.
- Time with no readings already shows up in the results as a gap (phase 2, step 17).

What is missing: Android is free to kill a process with no visible screen, and it freezes work when the phone
sleeps. A **foreground service** (with a notification) tells Android "this is doing something the user wants
right now", which keeps the process alive and its priority high.

## The design

- **`RecordingService`**, a foreground service. It exists exactly while a session is running: started when a
  session starts (and when the app is opened with a session already running), stopped when the session ends.
  It does not do the recording itself (the recorder in `AppContainer` keeps doing that); it keeps the process
  alive, shows the notification, and holds the locks.
- **The notification** (ongoing, quiet, no sound): the profile and session name, how long it has run, the
  latest score, and "No signal" when the Pico is out of reach. Tapping it opens the session. A **Stop session**
  button stops the session from the notification.
- **Keeping the radio and CPU awake** while recording: a partial wake lock and a Wi-Fi lock, released the
  moment the session ends.
- **Foreground service type.** Android 14 and newer require one. Proposed: `connectedDevice` (the Pico is a
  device on the network). Its other requirement is the normal permission `CHANGE_WIFI_STATE`. It has no time
  limit, unlike `dataSync`, which Android 15 limits to 6 hours a day (less than one night). Fallback if the
  phone refuses it: `specialUse`.
- **Permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_WIFI_STATE`,
  `WAKE_LOCK`, and `POST_NOTIFICATIONS` (asked when the first session starts, on Android 13 and newer; if it is
  refused the service still runs, only its notification is hidden).
- **Battery optimisation:** Settings gets a "Background recording" section that shows whether the app is
  exempt from battery optimisation and opens the system dialog to change it. (Samsung is the strictest about
  this, so it is tested there.)

## Decisions (agreed before step 19)

- Service type **`connectedDevice`**; `specialUse` only if the phone refuses it.
- **Wake lock and Wi-Fi lock** are held during sessions only. Keep the phone on the charger overnight.
- **Resume after a reboot:** yes, tried (`BOOT_COMPLETED`). If Android forbids starting the service that way,
  the session resumes the next time the app is opened, with a gap in between.

## Steps

Steps continue the numbering. Each step ends in a commit and a tag (`phase3-step-N`).

| Step | What | State |
|---|---|---|
| 19 | The foreground service: manifest, permissions, notification channel, starts and stops with the session, a plain notification with a Stop button | done |
| 20 | The live notification: elapsed time, latest score, "No signal", updated about every 30 seconds; the text is plain tested Kotlin | done |
| 21 | Notification permission flow (Android 13+) when the first session starts | done |
| 22 | Locks: partial wake lock and Wi-Fi lock while recording, released with the session | done |
| 23 | Settings, "Background recording": notification and battery-optimisation status and shortcuts | done |
| 24 | Recovery: the service restarts itself after Android kills it (sticky), the app resumes it when opened, and an optional restart after the phone reboots | done, the real reboot is still to be tried by hand |
| 25 | Hardware and device tests: screen off, app swiped away, the process killed on purpose, Doze (charging and not), hotspot switched off and on in the background; then a real night | mostly done: a real reboot, the Pico on the hotspot and the real night are still to be tried by hand |
| 26 | Wrap-up: README, notes, tags | done |

### Step 19: the foreground service
- `recording/RecordingService.kt`, `recording/RecordingNotification.kt` (channel, builder), manifest entries.
- The Dashboard's Start session starts the service (the app is in the foreground then, which Android requires);
  `MainActivity` starts it too when it opens with a session already running.
- The service watches `activeSession` and stops itself when the session ends. The Stop button ends the session.
- **Check:** unit tests for "should the service run" and for the notification text; on the phone, start a session,
  swipe the app away, and see the notification stay and the readings keep arriving.

### Step 20: live notification
- A pure function builds the text from (session, now, latest score, connection state): for example
  "Sleep session · 2 h 13 min · Score 78, Good" or "... · No signal from the sensors".
- Updated at most every 30 seconds and when the connection state changes, so it does not wake the phone for
  nothing.
- **Check:** unit tests for the text; on the phone, watch it change when the Pico is switched off.

### Step 21: notification permission
- On Android 13 and newer the first Start session asks for `POST_NOTIFICATIONS`. A refusal is remembered and the
  session still starts; Settings (step 23) says the notification is hidden and offers the system settings.
- **Check:** unit test of the decision; on the phone, grant and refuse.

### Step 22: locks
- A partial wake lock (with a long safety timeout, renewed while recording) and a Wi-Fi lock, taken when the
  service starts and released when it stops, also when it is destroyed unexpectedly.
- **Check:** `dumpsys power` and `dumpsys wifi` show them taken and released; a screen-off run keeps polling.

### Step 23: Settings, background recording
- Shows: notifications allowed or not, battery optimisation (exempt or not), with buttons that open the right
  system screens, and a short plain explanation of why it matters on some phones.
- **Check:** on the phone.

### Step 24: recovery
- `START_STICKY` plus a restart path: when the system restarts the service after killing the process, the
  Application starts, the recorder finds the running session in the database and resumes; the service sees the
  session and goes back to the foreground.
- Opening the app with a session running and no service starts the service.
- Optional: restart after the phone reboots (`BOOT_COMPLETED`), if Android allows a service of this type to be
  started that way.
- **Check:** kill the process on purpose and watch the readings resume; the gap appears in the results.

### Step 25: tests on the phone
- Screen off for 30 minutes; the app swiped away from the recents list; the process killed with `adb`;
  Doze on the charger and on the battery; the hotspot switched off and on while the app is in the background;
  the phone restarted. Then **a real night** with the real Pico.
- Anything that stops recording is a finding: either a fix or an honest line in the README.

## Risks

- **Samsung and other makers stop background work harder than plain Android.** The battery-optimisation
  shortcut and the notes are the answer; testing on the Galaxy A54 tells how bad it is.
- **Doze.** With the phone on the charger Doze does not start. On the battery overnight the foreground service
  and the locks should hold, but this needs to be seen on the phone, not assumed.
- **Android 17** is new and may have rules that are not in the documentation I know; the service type and the
  start rules are therefore tested on the phone early (step 19), before building on them.
- **The Pico's Wi-Fi.** A hotspot on the phone may switch itself off after a while without clients, or when
  the phone sleeps; the Pico already finds its way back, but readings are lost meanwhile.

## Findings in step 24 (Galaxy A54, Android 17)

- **Killed by force:** the app process was killed with `kill -9` during a session. Android restarted the
  service by itself within 3 seconds, with a new process, the notification and both locks, and readings
  resumed. The seconds in between show as a gap in the results.
- **App updated while recording:** the receiver restarted the service with nobody opening the app (log line
  "Resumed the recording service after MY_PACKAGE_REPLACED").
- **Reboot:** not tried. adb may not send the system's boot message, and after a restart the phone needs
  the PIN before any app can run. To try it by hand: start a session, restart the phone, unlock it, and see
  whether the notification appears without opening the app (the log tag is `ResumeReceiver`). If Android
  refuses, the session resumes when the app is opened.
- A force-stopped app (Settings, Force stop) receives no broadcasts at all until the user opens it again;
  that is Android's rule and cannot be worked around.

## Findings in step 25 (so far; Galaxy A54, Android 17, real Pico over home Wi-Fi)

- **Screen off, on the charger:** 7.4 minutes with the app swiped away and the screen off gave 45 readings
  with the longest gap 10.04 s, which is the 10 s interval: nothing was lost. The process, the foreground
  service and the wake lock were all still there at the 3 and 6 minute checks.
- The planned 12 + 12 minute run (screen off, then forced Doze on a simulated battery) was stopped early
  because the user had to leave; the phone's power state was put back to normal.
- **Forced Doze on a simulated battery:** 12 minutes with the screen off, the battery faked as unplugged and
  deep idle forced (`dumpsys deviceidle force-idle`, state stayed IDLE). The process, the foreground service
  and the wake lock were there at every 3 minute check, and the session got 75 readings with the longest gap
  10.06 s (the 10 s interval): Doze did not cost a single reading.
- **Network lost and back, app in the background:** adb cannot switch the real hotspot, and the Pico was on the
  home Wi-Fi, so the phone's own Wi-Fi was switched off for 90 s (screen off) and on again instead. The
  service stayed up, there was a single gap of 95.5 s in the readings, and the first reading came 8 s after
  Wi-Fi returned. The real hotspot case (Pico joined to the hotspot) is still to be seen by hand.
- **Still to do:** a real reboot, the Pico on the hotspot, and the real night.
