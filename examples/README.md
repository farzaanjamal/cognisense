# Example session files

**These files were not produced by a person.** Each was downloaded from the web demo during an automated end-to-end test (`browser/test/preview_test.js`), in which a scripted participant responds with fixed, deliberately simple behaviour. For example, it always answers at the same delay and recalls spatial sequences correctly only up to length 2.

They exist to show the data format: every trial record, every metric with its timing class, and the session metadata. The format is documented in `docs/data_schema.md` (section "Browser preview downloads").

| File | Task |
|---|---|
| `example-gonogo.json` | Go/No-Go (practice and shortened scored block) |
| `example-spatial-span.json` | Spatial span, forwards and backwards |
| `example-flanker.json` | Flanker |
| `example-time-reproduction.json` | Time reproduction |
| `example-choice-delay.json` | Choice-delay |

Every file is marked `browser_preview: true`. Its reaction-time field is named `rt_ms_not_a_measurement`: browser timing is not a measurement, and these values must never be analysed as data.
