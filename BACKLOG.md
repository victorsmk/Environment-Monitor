# Backlog

Ideas, requests and open questions to come back to. Nothing here is decided or scheduled yet.
Move an item to the plan (see the status table in the README) when we pick it up.

## Requests

### Light slider on the same five-level scale (raised while building the level picker)
- The light slider is a straight 0–1000 lux scale, so a range like 0–50 lux (Dark to Dim) squeezes
  both thumbs against the left edge and is hard to see or drag. The light chart already gives each
  level the same height; the slider could use the same idea, so each of the five levels takes a
  fifth of the track.

### A small tidy-up on the light chart (raised while checking the charts)
- The level names (Dark ... Very bright) sit at the right edge inside each band, and the line can
  cross them when readings are high. They could move to the left or become fainter.

### A more attractive, more interesting look (raised after step 7)
- The app feels bland: the colours and designs are all the same.
- Open questions: how far to go (a colour and typography refresh, or also icons, illustrations,
  animations, a distinct look for each sensor and for Good / Fair / Poor)? Do we keep the teal
  brand colours from the specification slides, or change them? This probably belongs with the
  "polish" step (step 8), but could be done earlier.

## Done

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
- Dark mode has not been looked at on a real screen yet.

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
