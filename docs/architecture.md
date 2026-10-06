# Architecture

| Field | Value |
|---|---|
| Document status | DRAFT v0.1, 2026-10-02 |
| Code it describes | `android/core/` (IMPLEMENTED, simulation-tested); Android app (DESIGNED); hardware (DESIGNED) |

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

## 1. Component status

| Component | Status | Where |
|---|---|---|
| Seeded random generator, per-task seed derivation | IMPLEMENTED | `core/rng/SeededRandom.kt` |
| Frame-driven trial engine | IMPLEMENTED (simulation-tested; not yet on a device) | `core/engine/TrialEngine.kt` |
| Core battery task definitions: Go/No-Go (T2), Flanker (T5), spatial span (T6), time reproduction (T9), choice-delay (T10) | IMPLEMENTED (core logic, simulation-tested) | `core/tasks/` |
| Task definitions: other five pool tasks | DESIGNED (specification only) | `docs/task_specifications.md` |
| Shared task configuration (`config/tasks.json`), strict loader, SHA-256 | IMPLEMENTED | `config/`, `core/config/` |
| Scoring with timing class per metric | IMPLEMENTED for T2, T5 | `core/tasks/`, `core/scoring/` |
| TimingSource interface; SoftwareTimingSource | IMPLEMENTED | `core/timing/` |
| HardwareTimingSource | DESIGNED (interface + data contract; stub throws) | `core/timing/`, `hardware/` |
| MotionSource interface; NoMotionSource | IMPLEMENTED | `core/motion/` |
| ImuMotionSource | DESIGNED (stub throws; body placement undecided) | `core/motion/`, `hardware/` |
| Session metadata, generated participant IDs, CSV/JSON export | IMPLEMENTED | `core/data/` |
| Android app: rendering, input bridge, storage, three modes, Urdu/English UI | Code complete for T2/T5; compiles; **not yet run on a device** | `android/app/` |
| ESP32 firmware | DESIGNED (data contract only) | `hardware/` |

## 2. Layers

**Dependency decision.** The app uses only the Android framework: no Jetpack Compose, Room, AndroidX or other libraries. Screens are built in code (no XML layouts), storage is framework SQLite, and JSON uses the framework's `org.json`. Reasons:

- a small APK for low-cost phones;
- no supply-chain route by which a library could add network access;
- the language toggle stays inside the app rather than following the system locale;
- the code can be compile-checked outside Android Studio.

The cost is more verbose UI code and less visual polish.

```
┌──────────────────────────────────────────────────────────────────────┐
│ android/app  (Android-specific, DESIGNED)                             │
│  Shell screens (Compose): welcome, admin login, participant setup,    │
│    instructions, breaks, completion, Expert Review menu, dashboard    │
│  TaskSurface: full-screen view; draws FrameContent; nothing else      │
│  FrameBridge: Choreographer.FrameCallback -> engine.onFrame(t)        │
│  InputBridge: ACTION_DOWN -> pad hit-test -> engine.onResponse(ev)    │
│  Lifecycle: onPause -> engine.onInterruption(t)                       │
│  Storage: SQLite (app-private); export via Storage Access Framework   │
├──────────────────────────────────────────────────────────────────────┤
│ android/core  (pure Kotlin, no Android imports, IMPLEMENTED)          │
│  engine/  TrialEngine, TrialPlan, FrameContent, ResponseEvent, ...    │
│  tasks/   one TaskDefinition per task                                 │
│  timing/  TimingSource  ◄── hardware timing attaches here             │
│  motion/  MotionSource  ◄── motion sensing attaches here              │
│  scoring/ Stats, Metric(timingClass)                                  │
│  data/    SessionMetadata, ParticipantId, CSV/JSON export             │
│  rng/     SeededRandom                                                │
├──────────────────────────────────────────────────────────────────────┤
│ hardware  (ESP32 firmware + circuits, DESIGNED)                       │
│  photodiode + button/piezo → RT on one clock → BLE result line        │
│  wearable IMU → stored samples + sync markers                         │
└──────────────────────────────────────────────────────────────────────┘
```

**Why a separate pure-Kotlin core.** All task logic, timing logic, scoring and export can be compiled and tested on any computer, without a phone. A technical reviewer can run `android/core/run_tests.sh` and see the engine's behaviour verified at 60/90/120/144 Hz. The Android layer is then limited to drawing, input and storage.

## 3. The engine

### 3.1 Clock-free and frame-driven

The engine never reads a clock. It has exactly two inputs, and each carries its own timestamp:

| Input | Called from | Timestamp |
|---|---|---|
| `onFrame(frameTimeNanos)` | `Choreographer.FrameCallback.doFrame` | vsync time of the frame being produced |
| `onResponse(ResponseEvent)` | touch listener, `ACTION_DOWN` only | `MotionEvent` event time |

It returns a `FrameContent` for every frame: the stimulus identifier or fixation, the onset-marker state, and break/pause/finished flags. The task surface draws exactly that and nothing else.

Consequences:

1. **Testable off-device.** The test suite drives the engine with simulated frames and touches. It checks stimulus frame counts, onset times, outcome classification, dropped-frame detection, interruption handling and break timing. Deliberately introduced bugs in the engine were each caught by the suite.
2. **Replayable.** A session's logged plan, frame times and touch times reproduce its records exactly.
3. **No hidden timing.** Nothing depends on when a callback happened to run.

### 3.2 Scheduling rule

An event targeted at time *T* happens on the first frame whose time is ≥ *T* − ½ frame, i.e. the frame nearest *T*. Durations are whole frames; the actual frame count and the displayed duration are logged per trial.

### 3.3 Trial structure

Every trial is a `TrialPlan` generated before the phase starts, entirely from a seeded random stream derived from (session seed, task, version, phase, attempt). The full plan list is logged. Two end modes cover the timeline tasks:

- `FIXED_AFTER_ONSET` — fixed event rate (Go/No-Go, CPT, n-back).
- `AFTER_RESPONSE` — response-terminated with an inter-trial interval (Flanker, simple RT, task switching).

Tasks that do not fit a fixed timeline (spatial span, time reproduction, choice-delay, stop-signal's adaptive staircase) will get their own runners implementing the same input/output contract. This is DESIGNED and not built.

### 3.4 Response classification

Responses are stored with their timestamps and classified when the trial ends:

- **premature:** before onset;
- **first in-window:** the scored response;
- **extra:** further in-window responses;
- **late:** after the window closes;
- **off-target:** touches outside every pad.

Outcome is one of CORRECT, COMMISSION, OMISSION, INCORRECT, ANTICIPATION (first in-window RT < 150 ms) or INTERRUPTED. Nothing is discarded; exclusions are applied at scoring, and every exclusion is counted.

### 3.4a Task-specific runners and the shared config

Spatial span (adaptive), time reproduction (press-and-hold, needs finger-lift events) and choice-delay (duration depends on choices) do not fit a fixed timeline. Each has its own runner built on `CustomRunner`, which shares the engine's guarantees:

- clock-free and frame-driven, scheduling on the nearest frame;
- dropped-frame counting;
- interruption handling with a single count and a 1 s lead-in on resume;
- timing-source hand-off.

The platform drives every task through one interface, `TaskRun`.

Every parameter comes from `config/tasks.json` through `BatteryConfig`, which builds all task definitions (standard and Expert Review) at start-up and fails with the JSON path of any bad value. The app and, later, the browser preview read the same file; see `config/README.md`.

### 3.5 Adding a task

Implement `TaskDefinition`, which has four members: `plan()`, `practiceCriterionMet()`, `score()` and an ID/version. Add the task's stimulus assets to the app's asset map. The engine, logging, storage and export do not change. The Flanker task was added this way without modifying the engine.

## 4. Where the sensors attach

### 4.1 TimingSource

The engine calls every registered `TimingSource` at three moments in each trial: `armTrial`, `onOnsetFrame` and `onResponseEvent`. At the end of the phase it collects `measurements()` and attaches them to the trial records by trial sequence number.

- Each trial record carries a map from source ID to measurement. A session can therefore hold software and hardware timing for the same trials, which is what the validation study needs.
- `SessionMetadata.primaryTimingSource` states which source the scored RTs come from. In the demo it is always `software`.
- `HardwareTimingSource` exists with its full data contract in KDoc, and every method throws `NotImplementedError`. It cannot be switched on by accident.

### 4.2 MotionSource

The session layer calls `start()`, then `mark()` at every task and block boundary (labels such as `GNG:block=2:start`), then `stop()`. The engine's `onEvent` hook emits block-start, break and interruption events with timestamps for this purpose. The IMU data contract specifies on-device storage, a sync-marker protocol and a linear clock mapping with stored residuals, so alignment error is reportable.

## 5. Privacy and offline guarantees

| Guarantee | How it is enforced | How a reviewer verifies it |
|---|---|---|
| No network access | `AndroidManifest.xml` declares no `INTERNET` permission. A `tools:node="remove"` entry strips it if any library tries to add it through manifest merging. | Read the merged manifest (Android Studio → Merged Manifest) |
| No camera or microphone | No `CAMERA` or `RECORD_AUDIO` permission; no code paths | Same |
| No names | Participant IDs are generated (`CS-` + 6 characters from a vowel-free alphabet), never typed. `SessionMetadata` rejects any other format. There is no name field anywhere in the schema. | `core/data/SessionData.kt`; tests |
| Local storage only | SQLite in app-private storage. Export goes only to a location the administrator chooses through the system file picker. | Code review |
| Data never shown to children | Dashboard and export sit behind an administrator PIN; child-facing screens show no score at any point | Code review; Expert Review walkthrough |

Planned, not designed in detail yet: encryption of exported files, and an administrator PIN policy.

## 6. App modes (DESIGNED)

1. **Expert Review.** A menu of every implemented task. Each task opens with one screen stating the construct, what is recorded and the expected duration, then runs practice, a shortened scored block, and a plain summary of what was recorded (counts and raw values, no interpretation). Data from this mode carry `mode = EXPERT_REVIEW` and are kept apart from child data.
2. **Session.** Generate participant ID → pre-session checklist → fixed brightness → core battery in fixed order, each task with instructions, practice (up to 3 attempts), scored block and breaks → completion screen with no score.
3. **Researcher dashboard.** Behind the administrator PIN:
   - session list with device, app version, date and completion status;
   - per-task metrics with their timing class;
   - trial-level plots (RT across trials, accuracy by condition, RT distribution);
   - data-quality flags;
   - CSV/JSON export.

   It shows **no norms, percentiles, risk labels, colour coding or cut-offs**, because no normative data exist and any such display would be an unfounded diagnostic interpretation.

## 7. Versioning and reproducibility

- **Builds.** App version and git commit hash are embedded at build time and written to every session.
- **Tasks.** Each `TaskDefinition` has a version string. Changing any parameter means a new version, and old data keep the old version label.
- **Data.** The schema version is written to every export.
- **Randomisation.** The session seed is stored, as a string in JSON to avoid 64-bit precision loss. Together with the task versions, it regenerates every trial sequence.

## 8. Browser preview (review tool)

The core is pure Kotlin with no `java.*` dependencies: SHA-256 and number formatting are implemented in `core/util/Portable.kt`. So the same sources compile for the JVM (Android) and, with `kotlinc-js`, for JavaScript.

`browser/src/PreviewApi.kt` is a thin, JavaScript-only facade that can start **only Expert Review variants**. `browser/web/app.js` draws frames from the same config and feeds pointer and keyboard events to the core, exactly as the Android `TaskView` does. `core/verify/CrossPlatformCheck.kt` runs a deterministic scripted child on both platforms; identical digests show identical behaviour.

Distribution:
- **Reviewer APK:** Gradle flavour `reviewer` (`BuildConfig.REVIEWER`). It opens in Expert Review, and Session mode and the dashboard are unreachable.
- **Browser preview:** a single HTML file, published by the cloud build to GitHub Pages and attached to releases.
