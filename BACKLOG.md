# Backlog

Ideas, requests and open questions to come back to. Nothing here is decided or scheduled yet.
Move an item to the plan (see the status table in the README) when we pick it up.

## Requests

### Interactive charts (raised after step 7)
- (Partly addressed: both chart axes now show several values. Tapping a point for its exact value
  and zooming are still open.)
- Make each point on a chart tappable, so tapping it shows the exact value (and the time).
- If a chart has too many points to tap reliably, make the charts zoomable (pinch to zoom, and
  probably drag to pan) so the user can zoom in on a stretch of time.
- Things to think about when we get to it: a session can have thousands of readings (one every
  10 seconds), and charts currently draw at most about 400 averaged points, so tapping needs to
  snap to the nearest real reading, and zooming should show finer detail instead of just
  magnifying the averaged line.

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
- Both axes now carry several labelled lines instead of only the lowest and highest value: round
  values on the vertical axis (for example 16, 17, 18, 19 °C) and round clock times on the horizontal
  one (whole seconds, minutes or hours of the phone's local clock, depending on how long the session
  is). The score chart names its Good, Fair and Poor zones.
- The light chart splits its height into the five light levels, each named, so it reads like the
  level picker. Each level has the same height, so Dark and Dim are not squeezed into slivers.
