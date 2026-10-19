# Changelog

Format: Keep a Changelog. Versions: semantic, `-dev` until the first tagged release.

## [0.6.2-dev] — 2026-10-19 (config 0.3, unchanged)
### Added
- **Storage round-trip test** (`android/app/src/test/.../CodecRoundTripTest.kt`): a JVM unit test in the app module, run by the cloud build (`:app:testStandardDebugUnitTest`). It covers:
  - every trial record from scripted runs of all five tasks (every part, practice and scored, standard and review variants; 396 records);
  - a record with a late-response latency;
  - the session metadata.

  Each item is encoded and decoded through the app's storage codec and must come back identical. With the codec's late-latency field deliberately dropped, the test fails. Test-only dependencies: JUnit 4.13.2 and org.json 20240303. The earlier one-off check of 175 records from the claude.ai session was never committed; this test replaces it.
- `CrossPlatformCheck.run(...)` returns the scripted run's records, so the test can reuse the scripted child. `digest(...)` is unchanged: all 72 digests are identical before and after.

### Changed
- **Name:** Farzaan Jamal Khan (first name Farzaan, middle name Jamal, family name Khan), used consistently in `CITATION.cff` (given names "Farzaan Jamal", family name "Khan"), the README, `CLAUDE.md`, Protocol 1 and this changelog.
- **Contact email** (README, `CITATION.cff`): farzaanjamal2009@gmail.com.
- **Expert review status** (README, `CLAUDE.md`): invitations were sent to several psychology professors; ratings have been received; the analysis is pending. No ratings, reviewer names or comments are in the repository.
- App version 0.6.2-dev.

### Fixed
- **Device test record** (`docs/device_tests.md`, README and other docs), corrected to match the paper's device-run section:
  - **Device:** Samsung Galaxy A15, Android 16; all five tasks ran with no problems.
  - **Touch timestamps:** not transcribed exactly; on Android 16 the app uses nanosecond event timestamps (a design value).
  - **Refresh rate:** 60 Hz requested; the panel is rated 90 Hz adaptive; the measured rate was not recorded. 0.6.1-dev wrongly said "60 Hz measured".
  - **Clock check:** pass, under 0.5 ms.
  - **Dropped frames:** 2–10 per task (0.6.1-dev said 0–10).
  - **Interruptions:** none.
  - **App version:** the app reported 0.1.0-dev at the time (its version name was fixed in 0.6.1-dev).

## [0.6.1-dev] — 2026-10-18 (config 0.3, unchanged)
### Added
- **First phone smoke test** (`docs/device_tests.md`): Samsung Galaxy A15, Android 16, adult self-test by Farzaan Jamal Khan, no other participants. The test completed with no problems seen. Some of the values first recorded here were corrected in 0.6.2-dev; see that entry.

  This is one device and one adult self-test: it shows that the app works on that phone, and says nothing about timing accuracy or about children.
- **Protocol 1 deviation** (18 October 2026): one invited reviewer is the investigator's parent. Her ratings are excluded from the content validity indices, and her comments are used informally. Reviewers with a close personal relationship to the investigator are not counted toward the minimum of 6.
- **Mutation check** (`android/core/mutation_check.sh`): makes the README's "6 of 6 planted defects detected" reproducible. It plants six defects one at a time, runs the core tests, confirms they fail, and restores the code:
  1. stimulus-duration frame rounding;
  2. classifying responses by arrival order instead of timestamp;
  3. counting the pause gap after an interruption as dropped frames;
  4. the spatial-span discontinue rule;
  5. time-reproduction release handling (only the pressing finger's release counts);
  6. choice-delay ignoring taps on the inactive option.

  On its first run the existing tests caught only 5 of 6. Defect 2 went undetected, because the test harness always delivers touches in time order. A new core check delivers two touches out of order and requires the reaction time to come from the earlier one. With it, the script detects 6 of 6, and the core suite has 221 checks.
- README: a "Contact" section; TODO placeholders for the contact email, the demo video link and the paper link (also `LINKS.paper` in the web demo and a comment in `CITATION.cff`).

### Changed
- **Release workflow:** on a version tag without the signing secrets, the "Signed reviewer APK on a version tag" job no longer fails. It shows a notice naming the missing secrets, skips the build and release steps, and finishes green. `docs/release_signing.md` describes this.
- Every "not yet run on a phone/device" statement now states the smoke-test result accurately (README, `android/README.md`, `docs/architecture.md`, `docs/measurement_precision.md`, `docs/task_specifications.md`, `docs/cloud_build.md`, `docs/release_signing.md`, `TrialEngine.kt` comment, `CLAUDE.md`). The web demo was rebuilt because that comment is part of its sources.

### Fixed (consistency check)
- **Versions:**
  - The Android app reported version `0.1.0-dev` (its `versionName` had never been updated); it now reports `0.6.1-dev`.
  - `CITATION.cff` said `0.5.0-dev`; it now says `0.6.1-dev`.
- **Test counts:**
  - README: content-validity tests 12 → 13 (the actual count).
  - `android/README.md`: core checks 188 → 221 (188 was the count at 0.4.0-dev).
- **Component status:**
  - `docs/architecture.md`: scoring and the Android app are complete for all five core tasks, not just T2 and T5.
  - Smoke-test step 4 in `android/README.md`: all five core tasks open, not only T2 and T5.
  - `config/README.md`: the web demo already embeds the config, and Protocol 1 exists; neither is PLANNED any more.
  - `docs/cloud_build.md`: removed the out-of-date "the first build will probably fail" section.
- **README status table:**
  - The device timing study (phones only) and the child pilot (supervisor, psychologist and ethics approval) are now separate rows with their own requirements.
  - The expert review row points to Protocol 1.
- **Example sessions** (`examples/`): re-recorded with the current build (task config 0.3). The old files were recorded under config 0.2.
- **Smaller fixes:**
  - CHANGELOG versioning note: `-dev` until the first tagged release (the old trigger, "the first device build", has happened).
  - `CLAUDE.md`: the cloud build runs on pushes to `main` and on pull requests, not on every push.

## [0.6.0-dev] — 2026-10-07 (config 0.3)
Design fixes in response to an expert-style critique, made before the expert review so the panel rates the improved battery.

### Changed (interface; task stage untouched)
Nothing that a child sees during a task changed: stimuli, colours, timing, the grey stage and the task configuration (`config/` and its hash) are as before.
- **Android app, shell screens:**
  - The app now bundles the web demo's fonts (Atkinson Hyperlegible; Noto Nastaliq Urdu, so Urdu shows in Nastaliq even on phones without an Urdu font). The fonts are converted to TrueType, with the glyphs unchanged, and their OFL licence is in `assets/fonts/`.
  - The colours now match the web demo.
  - Disabled buttons now look disabled; previously the locked "Continue" on the session checklist looked active.
  - Menus (home, expert review, dashboard) use tappable cards.
  - Facts, measures and run details are shown as aligned rows, with timing-class badges.
  - The checklist is grouped into required and optional items.
  - The ready, rest and completion screens are centred.
  - Content width is capped on tablets.
  - Pages now pad themselves by the system bars. With target SDK 35, Android 15 draws apps edge to edge, which would otherwise put the top of each screen under the status bar.
  - The status and navigation bars match the page colour.
- **Web demo:**
  - Timing-class badges in the results tables and in "How to read what it records".
  - On the landing page, the whole task row can be clicked.
  - With more than one task, the results page has a contents list.
  - Each part's "All recorded measures" now sits with that part.
  - Action buttons are full-width on narrow phones.
  - The results page has a print style.

### Added (expert review, before any invitation)
- README and paper: authorship section rewritten to state Farzaan Jamal Khan's substantial contribution (idea, research question, system logic, task choices, design decisions, direction), with accurate AI disclosure.
- `CLAUDE.md`: handoff instructions so Claude Code sessions start with the project's context, rules, state and open tasks.
- `docs/protocol1_expert_review.md`: Protocol 1 fixes the panel, materials, decision rules, second round, missing-data rules and reporting, all before any rating exists.
- `analysis/cvi.py`: the protocol's decision bands are set (retain at or above the Lynn criterion; revise at .70–.77; remove below .70). Decisions use the same two-decimal rounding as the criterion, so 7 of 9 experts can no longer be both "meets criterion" and "revise". Only relevance can remove a task. With 5 or fewer experts, retaining requires full agreement. 13 tests.

### Changed
- **Go/No-Go (task version 0.2):** blocks are now fast–slow–slow–fast, 32 trials each, giving both rates 64 trials and 16 No-Go trials (previously fast–slow–fast–slow with 48/24-trial blocks, so 24 vs 12 No-Go and slow blocks later on average). Full task ≈ 6.5 min (392 s simulated). Review variant: fast 12, slow 12.
- **Choice-delay:** marked EXPLORATORY in the config and documents (no real reward).
- **Web demo:** example trials now show the fixation cross that the real task displays between shapes.

### Added
- **Late-response latency** (`late_rt_ms`): the first response after the response window is now timed and stored (CSV, app storage, browser download), not only counted.
- **Spatial span retest form** (task version 0.2; variant `-retest`): alternate sequences for a child's second session, generated by `SpanSequences.generateMatched` (seed 20261002). They share no sequence with the standard form or practice, and each path length is within 10% of its standard counterpart. The Android Session checklist has a "second session (retest)" box, recorded as `session_form`.
- Tests (220 checks): block balance and order, late latency (exact), retest provenance, separation and path matching; storage round trip including late latencies; 72/72 identical digests across JVM and JavaScript.
- `analysis/timing_simulation.py --gng-go/--gng-nogo`, plus `results/timing_simulation_v03_counts.json`. The new counts change device-related agreement by at most .005.
- README: an authorship and contributions section.

## [0.5.0-dev] — 2026-10-06
### Changed
- **The browser preview is now the web demo, the primary way to see Cognisense.**
  - A landing page with a plain explanation and a live stage showing the real Go/No-Go stimuli at their configured timing.
  - A guided five-task flow with numbered progress, and a single-task option.
  - Task introductions with a looping example trial, plus the child-facing instructions with the Urdu switch.
  - Neutral between-task screens.
  - A results page with one chart per task, drawn from the recorded trials, with key measures and their timing class.
  - A "For researchers" drawer holding the versions, the configuration hash and the full measures.
  - The five unimplemented candidate tasks no longer appear in the demo; they remain in the specifications.
- Visual system: Atkinson Hyperlegible and Noto Nastaliq Urdu, embedded (SIL Open Font License; `browser/web/fonts/OFL.txt`). The content-security policy now allows `data:` fonts only.
- During tasks: the screen is kept awake where supported, pull-to-refresh is blocked, and leaving asks for confirmation. Nothing on the task stage is animated by CSS, and shell animations never run during a task.
- The end-to-end test follows the new flow and adds a guided mode (`guided`, or `guided:T5,T9` for a subset). It also checks the between-task screen and that there is one chart per task part.
- The cloud build runs the simulation tests, checks that `analysis/results/` matches a fresh run (numpy pinned), and runs the full guided demo.

### Added
- `README.md` rewritten: what, why, tasks, screenshots, architecture, research, status, limitations, ethics, development, AI use.
- `CITATION.cff` (author: Farzaan Jamal Khan).
- `analysis/results/`: the simulation outputs used in the paper, recording the numpy version that produced them.
- `examples/`: session files from the test's scripted participant (not a person), to show the data format. They are kept out of `/data/`, which stays ignored so real participant data can never be committed.
- `docs/images/`: screenshots of the demo.

## [0.4.0-dev] — 2026-10-04
### Added
- `analysis/timing_simulation.py` and `analysis/test_timing_simulation.py` (7 tests): a Monte Carlo study of how device timing error propagates into six battery metrics.
  - 500 replicates × 200 simulated children; seed 20261004; about 20 s to run.
  - Device offsets of 35–140 ms (the range reported by Nicosia et al., 2023) plus per-trial jitter.
  - Sensitivity option `--gng-window`.

### Fixed
- **Browser preview tasks ran in a 300 × 150 box in the top-left corner (about 0.42× scale).** The safe-area change in 0.3.0 positioned the canvas with insets only, which does not stretch a replaced element. The canvas now sizes to the viewport minus the safe-area insets. The end-to-end test now checks that the stage fills the viewport, and it was confirmed to fail on the old bug.

### Found (design issue, not yet changed)
- The Go/No-Go response window is judged in device time, so device offsets censor slow responses. In simulation this biased RT ISD by about −10 ms and raised omission rates by about 2.5 percentage points.
- Proposed remedy for the next config version: record late responses and apply the window after device correction. Referred to the expert panel.

## [0.3.0-dev] — 2026-10-03
### Added
- **Browser preview** (`browser/`): review tool only. The unchanged Kotlin core is compiled to JavaScript.
  - 72/72 scripted runs byte-identical with the JVM build.
  - Single-file HTML with a strict CSP (no network connections), `noindex`, no cookies or storage.
  - Review variants only; bilingual; keyboard on desktop.
  - End-to-end tested in headless Chrome for all five core tasks; deterministic build.
- **Reviewer APK flavour** (`reviewer`): opens in Expert Review; Session mode and dashboard unreachable. Release signing from environment variables only.
- **Cloud build** (`.github/workflows/build.yml`): core, analysis and browser tests; preview rebuild check; Android build of both flavours; merged-manifest permission check; APK artifacts; signed reviewer pre-release on version tags. Plus `pages.yml` for GitHub Pages.
- `config/strings.json`: all interface text shared by app and preview.
- `core/util/Portable.kt`: pure-Kotlin SHA-256 (FIPS vectors; matches the JDK at block boundaries) and locale-free formatting. The core now has no JVM-only dependencies.
- `core/verify/CrossPlatformCheck.kt`: deterministic scripted-child digest for cross-platform verification.
- Docs:
  - `docs/cloud_build.md`, `docs/reviewer_apk_install.md`, `docs/release_signing.md`;
  - `measurement_precision.md` §9 (why browser timing is never data);
  - rating-form requirement "Build reviewed".

### Fixed
- 76 Urdu drafts were corrupted to '?' while moving strings into `config/strings.json` (a non-UTF-8 console in the tooling). All were restored from the previous release. A permanent test now fails if any Urdu string loses its Arabic-script text.

## [0.2.0-dev] — 2026-10-02 (third tranche)
### Added
- Core battery complete in code:
  - spatial span forward/backward (T6): adaptive, fixed sequences;
  - time reproduction (T9): press-and-hold with release events;
  - choice-delay (T10): forced familiarisation, balanced sides.
- `config/tasks.json`: single source of truth for all task parameters, display geometry, the task pool and the fixed span sequences. Strict loader (`BatteryConfig`) with JSON-path errors; SHA-256 recorded per session. `config/README.md` sets the versioning rules.
- `CustomRunner` base and `TaskRun` interface: every task runs through one interface.
- Dependency-free JSON parser in the core.
- Data schema 0.2: `details` column; `task_config_version`, `task_config_sha256`.
- App:
  - all five core tasks playable; spatial span runs as two parts;
  - drawing fully config-driven;
  - finger-release events;
  - span re-administration flagged in data and on the dashboard.
- Tests: 188 checks, including simulated children for the three new tasks, config validation and sequence provenance.

### Changed
- Go/No-Go and Flanker parameters moved from Kotlin defaults to the config file. Behaviour unchanged; the existing tests pass with their original expected values.

### Fixed
- The generated backward-span practice sequence duplicated the first scored sequence. The generator now excludes scored sequences, and the loader rejects duplicates.

## [0.1.0-dev] — 2026-10-02 (second tranche)
### Added
- Android app (`android/app`), framework-only, minSdk 26 / targetSdk 35:
  - Expert Review, Session and Dashboard modes; Go/No-Go and Flanker playable;
  - Choreographer-driven task view with a refresh-rate probe;
  - MotionEvent timestamps (ns on API 34+);
  - immersive mode, fixed brightness and 60 Hz mode request; interruption handling;
  - SQLite storage with lossless JSON codecs;
  - CSV/JSON export through the system file picker;
  - administrator PIN (salted hash); pre-session checklist;
  - neutral dashboard charts and data-quality flags;
  - Urdu-first bilingual strings (drafts, unvalidated).
- Gradle build (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.10.2 wrapper); `:core:coreTests` task.
- Manifest privacy guarantees: no permissions requested; INTERNET, CAMERA and RECORD_AUDIO stripped from merges; backup and device transfer disabled.
- `android/README.md`: build instructions, troubleshooting, first-device smoke test.
- Core:
  - practice feedback state (tick/cross, practice only; scored phases reject it);
  - attempt number on trial records;
  - Expert Review task variants with distinct versions (`0.1-review`);
  - 1 s fixation lead-in after an interruption;
  - duplicate interruptions counted once;
  - checklist field in session metadata.

### Fixed
- `.gitignore` would have excluded the Gradle wrapper jar and both source packages named `data`.
- Core tests wrote to `/tmp`, which fails on Windows.

## [0.1.0-dev] — 2026-10-02 (first tranche)
### Added
- `docs/task_specifications.md`: 10-task candidate pool, proposed core battery, timing-robustness classes, references with [VERIFY] markers.
- `analysis/cvi.py` with tests: I-CVI, S-CVI/Ave, S-CVI/UA, modified kappa.
- `android/core`: seeded RNG; frame-driven trial engine; Go/No-Go and Flanker definitions and scoring; TimingSource (software implemented, hardware designed); MotionSource (none implemented, IMU designed); session metadata; participant ID generator; CSV/JSON export; 100-check simulation test suite.
- `docs/architecture.md`, `docs/measurement_precision.md`, `docs/data_schema.md`, `hardware/README.md`.

### Superseded
- `docs/archive_build_plan_superseded.md` (planning document, renamed): its two-task scope, adult-only plan and "do not buy an accelerometer" advice are superseded by the consolidated brief. Its bill of materials remains a useful price reference.
