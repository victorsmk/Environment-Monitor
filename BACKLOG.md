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

## Open questions

### Is lux the right unit for light? (raised after step 7)
- Temperature and humidity are easy to understand. Light in lux feels less intuitive.
- Things to weigh when we come back to it:
  - Lux is the real physical unit and is what the sensor produces, so it is accurate and matches
    published guidelines (for example for offices).
  - Most people have no feel for the numbers (a dark bedroom is a few lux, a lit office a few
    hundred, direct sunlight tens of thousands).
  - Options: keep lux but add a plain-language label next to the number (for example "Dim",
    "Bright"), show a 0–100 level like the sound sensor does, or let the user choose. The profile
    editor and the scoring rules would need to stay consistent whichever we pick.
- Decision still to make.

## Done

### A different colour for each chart
- Each sensor has its own colour: temperature orange, humidity blue, light gold, sound purple. The
  score chart stays neutral. The colour appears on the chart line and its shaded ideal range, and
  as a dot next to the sensor's name on the Dashboard tiles, the session summary and chart titles.
- Each colour has a darker variant for light mode and a lighter one for dark mode, and a unit test
  checks that they keep at least a 3:1 contrast against the card colours. The names stay next to
  the dots, so colour is never the only clue.
- Dark mode has not been looked at on a real screen yet.
