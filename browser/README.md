# Web demo (demonstration and review tool)

`dist/cognisense-preview.html` is the web demo: a single self-contained file that anyone can open in a browser to see Cognisense, including professors, reviewers and competition judges, on any laptop, tablet or phone. There is nothing to install.

It offers:

- a guided demo of all five tasks, about 10 minutes;
- a single-task option;
- a results page that shows what each task records.

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

**It is not the measurement instrument.** It runs the shortened Expert Review blocks, and its reaction times are browser-timed, so they are not measurements (`docs/measurement_precision.md` §9). The Android app is the planned field instrument.

### Design rules

- **The task stage is never themed or animated.** It uses the config's neutral grey, and stimuli appear in a single frame. Shell animations, such as the landing page's live stage and the example trials, stop before any task starts.
- **Stimuli stay the published research forms.** Polish goes into everything around them, never into the stimuli.
- **Two kinds of text.** Visitor-facing text is English and lives in `web/app.js`. Child-facing text (task names, instructions, keyboard hints) comes from `config/strings.json` with its Urdu drafts.
- **No individual results for children.** The results page exists only in this demo, shows no scores, norms or comparisons, and is labelled as browser-timed.

## What it is

| Property | How |
|---|---|
| Same task logic as the app | The unchanged Kotlin core (`android/core/src/main/kotlin`) is compiled to JavaScript with `kotlinc-js`. 72 of 72 scripted runs gave byte-identical results on the JVM and in JavaScript (all task parts, both phases, both variants, three seeds). |
| Same parameters and text | `config/tasks.json` and `config/strings.json` are embedded byte-for-byte; the page shows the config's SHA-256. |
| Review only | The JavaScript API can start only the shortened Expert Review variants. There is no session mode and no participant entry, so it cannot be used to test children. |
| Same visual rules | Neutral grey task stage from the config; no animation or sound during trials; fixed physical left/right; no RTL mirroring on task screens; stimuli in config units (CSS px), scaled down uniformly below 640 × 360. |
| Keyboard on desktop | Space = single button (hold Space for time reproduction); F / ← = left, J / → = right; spatial span needs touch or mouse. Stated on each construct card. |
| Privacy | A Content-Security-Policy forbids every network connection (`default-src 'none'; connect-src 'none'`). No analytics, cookies or storage; inline favicon so the browser requests none; `noindex`. Data stay in the tab. Reviewers can download their own session JSON, marked `browser_preview: true`. |

## Build

Needs Python 3 and the Kotlin 2.0.21 compiler (github.com/JetBrains/kotlin/releases):

```bash
python3 browser/build.py --kotlin-home /path/to/kotlinc
```

The build is deterministic: a clean build in a different directory produced a byte-identical file. The cloud build rebuilds it on every push and fails if the committed file is out of date.

## Test

```bash
cd browser/test && npm install --no-save puppeteer-core@23
CHROME_PATH=/path/to/chrome node preview_test.js all
```

A scripted reviewer drives every core task through the real page with real input events. The test checks:

- trial counts and review-variant versions;
- the download;
- the disclaimer, `noindex`, the CSP and the absence of cookies;
- **zero network requests after load**;
- no page errors.

It also saves screenshots.

## Sharing it with reviewers

- **GitHub Pages** (`.github/workflows/pages.yml`): a stable link. The page makes no requests after loading, but GitHub's servers see each page load as for any website.
- **Release attachment:** each tagged release attaches the HTML file.
- **Send the file directly:** it works offline on laptops. iPhones generally will not run scripts from a file opened in the Files app, so use the link there.

## Known limits

- Browser timing: see `docs/measurement_precision.md` §9.
- Urdu fonts come from the reviewer's device, so rendering varies (Nastaliq where installed, otherwise Naskh). No web fonts are downloaded, by design.
- Fullscreen is requested but not available on iPhone Safari.
- Fonts are embedded (about 190 KB), so text renders identically everywhere; the file is about 0.9 MB.
