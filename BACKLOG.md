# Backlog

Ideas, requests and open questions to come back to. Nothing here is decided or scheduled yet.
Move an item to the plan (see the status table in the README) when we pick it up.

## Requests

### Interactive charts (raised after step 7)
- Make each point on a chart tappable, so tapping it shows the exact value (and the time).
- If a chart has too many points to tap reliably, make the charts zoomable (pinch to zoom, and
  probably drag to pan) so the user can zoom in on a stretch of time.
- Things to think about when we get to it: a session can have thousands of readings (one every
  10 seconds), and charts currently draw at most about 400 averaged points, so tapping needs to
  snap to the nearest real reading, and zooming should show finer detail instead of just
  magnifying the averaged line.

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
