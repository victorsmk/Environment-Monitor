# Backlog

Ideas, requests and open questions to come back to. Nothing here is decided or scheduled yet.
Move an item to the plan (see the status table in the README) when we pick it up.

## Requests

### Crowded time labels on the session charts
- Seen on a Samsung Galaxy A54 (Android 16): the labels along the bottom of the charts run together
  ("9:56:00 PM9:56:30 PM9:57:00 PM") when a session is short, because the labels are in seconds and
  are wider than the space between them. Not caused by phase 2. Idea: drop every other label, or use
  the 24-hour format, when they would touch.

## Done

### Look refresh, second half
- **Dashboard:** the score card takes a soft tint of Good / Fair / Poor, the ring has rounded ends and
  sweeps up with a counting number, and each sensor tile has a small gauge: the ideal range shaded,
  a dot for where the room is now. The ends of the gauge are where that sensor's score hits zero.
- **Profiles:** one line per sensor, its range on the left and its importance on the right.
- **History:** rounder cards and a round badge with the session's average score, coloured by
  Good / Fair / Poor. The average is stored on the session (database version 6, with a migration);
  sessions from before it get theirs filled in the first time History opens.
- **Light slider** now uses the same five equal-width levels as the chips and the chart, so Dark and
  Dim are no longer slivers at the left end.
- **Light chart:** the level names are drawn on top of the line, each on a small backing, so the line
  can no longer hide them.
- **App icon:** a custom adaptive launcher icon (a dial on teal), with a themed variant for Android 13+.
### Make "importance" clear
- **Clearer names:** Ignore / Minor / Important / Critical replace Off / Low / Medium / High. Only the
  shown names changed: the weights are still 0 / 1 / 2 / 3 and what is stored in the database (the
  enum names) is unchanged, so existing profiles and sessions were not touched. A test pins this.
- **Heading and sentence:** each sensor card has "How much does this matter?" above the buttons and
  a sentence under them for the chosen level ("Matters most. A miss here costs the most points.").
- **Share of the score:** each card shows its share, for example "27 % of the score", in the
  sensor's colour. It updates on every card whenever any button changes. Shares are rounded one by
  one, so equally important sensors always show equal shares (Sleep: 27, 18, 27, 27, which adds up
  to 99, not 100).
- **A split bar at the top of the editor:** one block per sensor that counts, as wide as its share,
  in the sensor's colour, with the percentage inside and the sensor and level listed underneath. A
  block grows or shrinks smoothly when its importance changes and disappears when set to Ignore. If
  nothing counts, the bar says so.
- **A "?" beside each heading** opens a short explanation of the four levels with the sleep example.
- **A badge on each Dashboard tile**, for example "Critical · 27 %", for the profile (or running
  session) in use. Ignored sensors have none, since the tile already says they are ignored.
- Checked on a real phone in dark mode (the phone had switched to dark for the evening). The
  "Important" button was first clipped to "Importa" and now fits.
### Interactive charts
- **Read an exact value:** tap a chart, or drag one finger along it, and a marker (dashed line and
  dot) with a bubble shows the time and the exact value of the closest real reading. The bubble
  also says how it compares with the ideal range ("In range", "Too low", "Too high"); the light
  chart adds the plain word ("Bright · Too high") and the score chart the label ("Score 35, Poor").
  Tapping the same reading again hides it.
- **Zoom:** pinch with two fingers to zoom in on a stretch of time. Only the readings in view are
  redrawn, so zooming in shows finer detail instead of a magnified average, and the time labels
  switch to seconds. Two-finger drags pan; arrow buttons step earlier or later; "Reset zoom"
  returns to the whole session. Charts with fewer than four readings do not zoom.
- **Scrolling still works:** a mostly vertical one-finger swipe that starts on a chart scrolls the
  page as usual.
- **Tested:** the maths (nearest reading, zoom, pan, which readings are in view) is unit tested.
  The gestures are checked by on-device UI tests that really pinch, tap and drag a chart (they
  cannot be done through `adb`, so they run as a Compose UI test).
- Not remembered: the zoom and the touched reading reset when you leave the screen or rotate the
  phone.

### Is lux the right unit for light?
- Decided: keep lux for storage, scoring and profiles, and add a plain word next to it. The scale is
  0–10 Dark, 10–50 Dim, 50–200 Soft, 200–500 Bright, above 500 Very bright.
- The words appear on the Dashboard light tile ("Bright" beside the sensor name), under the light
  slider in the profile editor ("Bright", or "Dim to Bright" for a wide range), and next to the
  light range on the session screen. A reading exactly on a boundary takes the higher word (10 lux
  is Dim), and the top end of a range takes the lower one, so the office preset of 300–500 lux reads
  "Bright".
- Other options that were considered and not taken: a 0–100 light level like sound, or a setting
  to choose between the two.

### A different colour for each chart
- Each sensor has its own colour: temperature orange, humidity blue, light gold, sound purple. The
  score chart stays neutral. The colour appears on the chart line and its shaded ideal range, and
  as a dot next to the sensor's name on the Dashboard tiles, the session summary and chart titles.
- Each colour has a darker variant for light mode and a lighter one for dark mode, and a unit test
  checks that they keep at least a 3:1 contrast against the card colours. The names stay next to
  the dots, so colour is never the only clue.
- Dark mode has now been looked at on a real screen (see the next entry).

### Drawn icons instead of emoji
- Every icon in the app is now drawn in code in one consistent outline style (24 by 24 grid, one
  stroke weight, round ends), with no emoji and no icon library.
  - **Sensors:** thermometer, droplet, light bulb and loudspeaker, drawn in each sensor's colour. They
    replace the coloured dots on the Dashboard tiles, the session summary, the chart titles and the
    editor cards.
  - **Profiles:** ten icons to choose from (moon, laptop, dumbbell, book, game controller, lotus, pot,
    leaf, palette, headphones), shown in the profile list, History, the Dashboard picker, the session
    screen and the editor's icon picker. Sleep, Office and Sport use the moon, laptop and dumbbell.
- **Nothing was lost:** a real database migration (version 4 to 5) turns each old emoji into its
  drawn icon, in both profiles and sessions, and keeps everything else. It was checked on a real
  phone with real data, and the migration SQL is tested on the device.
- Every icon is rendered to an image by an on-device test, which fails if one comes out blank or
  clipped, and saves a contact sheet of the whole set for looking at.
- The four tab icons are drawn in the same style too (a dial, three sliders, a clock, a cog).

### Dark mode setting
- Settings has an **Appearance** section with Use system setting (default), Light and Dark. The
  choice applies immediately inside the app and does not touch the phone's own setting. The status
  and navigation bar icons follow the app's choice, and the sensor colours switch to their lighter
  dark-mode shades with the theme (not with the phone's setting).
- Checked on a real phone: Settings, Dashboard, the profile editor, and the session charts including
  the tooltip bubble all read well in dark.

### Importance buttons give no visual feedback
- Off / Low / Medium / High in the profile editor now fill with the brand colour when chosen, and
  the others are plain outlines. The chosen one is also visible (a bit softer) on a read-only
  preset.

### Pick the light range by level
- Under the light slider there are five chips: Dark, Dim, Soft, Bright and Very bright. Taps come
  in pairs: the first picks one level, the second selects everything between the two and the slider
  follows (Dark then Dim gives 0–50 lux). The next tap starts a new pair. Moving the slider by hand
  cancels a half-finished pair.

### More readable charts
- Both axes now carry several labelled lines instead of only the lowest and highest value, and the
  round clock times on the horizontal axis are whole seconds, minutes or hours of the phone's local
  clock, depending on how long the session is.
- On the vertical axis:
  - **Temperature:** labels every half degree for a narrow range, widening to every 1, 2, 5 or 10
    degrees as the readings vary more (at most 8 labels), with alternating stripes between them
    like the light chart.
  - **Humidity:** always multiples of 5 (widening to 10, 20 or 25 when the range is big), also
    striped.
  - **Score:** lines at 0, 10, 30, 50, 65, 80 and 100, with the Good, Fair and Poor zones shaded and
    named at the top of each zone.
  - **Sound:** a lighter set of round labels, unchanged.
  - **Light:** the five named levels, unchanged.
- The light chart splits its height into the five light levels, each named, so it reads like the
  level picker. Each level has the same height, so Dark and Dim are not squeezed into slivers.
