# Cognisense Android project

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

## What is here

| Module | Contents | Verified by |
|---|---|---|
| `core/` | Pure-Kotlin engine, all five core tasks, config loader, scoring, export | 188-check simulation test suite (`core/run_tests.sh`) |
| `app/` | Android app: Expert Review, Session and Dashboard modes; all five core tasks playable | Compiles against the Android 15 API with no errors; storage codecs round-trip-tested. **Not yet run on a phone.** |

The app has **no third-party runtime dependencies**: only the Android framework and `:core`. It targets Android 15 (API 35) and runs on Android 8.0+ (API 26). On Android 14+ touch timestamps have nanosecond resolution; on older versions millisecond resolution. The dashboard shows which applied.

## No Android Studio?

Use the cloud build (`docs/cloud_build.md`): GitHub compiles the app for free and gives you APKs to download. There are two variants:

- `standard`: the full app.
- `reviewer`: Expert Review only, for the panel. It opens straight into Expert Review; Session mode and the dashboard are unreachable.

Locally: `./gradlew :app:assembleStandardDebug :app:assembleReviewerDebug`.

## Build and install

1. Install **Android Studio** (free, https://developer.android.com/studio). It includes the JDK and Android SDK.
2. Unzip the repository. In Android Studio choose **Open** and select the **`android/`** folder (not the repository root).
3. Wait for **Gradle sync** to finish. The first sync downloads Gradle and the Android build tools; it needs internet once, and can take several minutes on a slow connection.
4. On the phone: **Settings → About phone →** tap **Build number** seven times, then **Settings → Developer options →** turn on **USB debugging**.
5. Connect the phone by USB and accept the "Allow USB debugging?" prompt.
6. Select the phone in the device menu and press **Run ▶**.

Run the core tests without a phone: **Gradle panel → core → Tasks → verification → coreTests**, or `./gradlew :core:coreTests`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Studio offers to upgrade the Android Gradle Plugin | Accept. Versions live in `gradle/libs.versions.toml`. Note the new version in the CHANGELOG. |
| "SDK location not found" | Studio normally writes `local.properties` automatically; otherwise **File → Project Structure → SDK Location**. |
| "Unsupported class file major version" / JDK errors | **Settings → Build Tools → Gradle → Gradle JDK**: choose the bundled JetBrains Runtime (17 or newer). |
| Gradle wrapper error | In a terminal in `android/`, run `gradle wrapper --gradle-version 8.10.2` (if Gradle is installed), or let Studio fix the wrapper when it offers. |
| Phone not listed | Try another cable (some are charge-only); re-accept the USB-debugging prompt; on Windows, install the manufacturer's USB driver. |
| Build error in a `.kt` file | Copy the **complete** error text (file, line, message) and send it. The code was compile-checked but never built with Gradle, so first-build errors are possible. |

## First-device smoke test

Do this on your own phone, as an adult tester, before showing the app to anyone. Expected results are stated so a deviation is easy to spot. **Send back the values marked ➜**; they are the first real measurements of the platform.

| # | Step | Expected |
|---|---|---|
| 1 | Open the app | Disclaimer screen in Urdu; the toggle switches to English and back |
| 2 | Home → Settings → set a PIN | PIN accepted; brightness slider and marker switch shown |
| 3 | Turn on airplane mode | — |
| 4 | Home → Expert review | All 10 tasks listed; only T2 and T5 openable; the others say DESIGNED |
| 5 | Open T2 → Start → Start | About 1 s of fixation (refresh-rate probe), then practice: tick or cross after each trial |
| 6 | Finish practice → real block | No ticks or crosses; one 10 s break with a neutral bar, then tap to continue |
| 7 | Mid-block, press Home, then reopen the app | "Paused. Tap to continue." After the tap, 1 s of fixation before the next stimulus |
| 8 | Finish → summary | Counts by outcome and metrics with timing classes; no scores or judgements |
| 9 | Repeat 5–8 for T5 (Flanker) | Left and right pads; the stimulus disappears as soon as you respond |
| 10 | Switch the language to Urdu and open T5 | Pads stay physically left and right (they must not swap) |
| 10a | Open T6 (spatial span) | Forward part: squares light one at a time; after a black frame appears, taps light briefly; then the backward part with its own instructions |
| 10b | In T6, recall correctly up to some length, then wrong twice | The task stops after two failures at one length |
| 10c | Open T9 (time reproduction) | Circle, then a pad; while held, the pad shows a static pressed state (no progress bar); practice shows two bars |
| 10d | In T9, lift a second finger while holding with the first | Ignored; the trial ends only when the first finger lifts |
| 10e | Open T10 (choice-delay) | Practice: only one option active per trial; waiting shows only the chosen option, static; tokens appear, then clear; no running total anywhere |
| 11 | Home → Dashboard → the session | ➜ **device model, Android version, touch timestamp resolution, refresh nominal/measured, clock check result, dropped frames, interruptions** |
| 12 | Export trials (CSV) and session (JSON) | Files open on a computer; one CSV row per trial |
| 13 | **Merged Manifest** tab of `AndroidManifest.xml` in Studio | No INTERNET, CAMERA or RECORD_AUDIO permission |
| 14 | Settings → Onset marker on → run T2 | A small white square flashes in the top-left corner with each stimulus (calibration use only) |

If any step fails, note the step number and what happened, and send the exact error text where there is one.
