# Device tests

Each run of the app on a real phone is recorded here. The procedure is the smoke test in [`android/README.md`](../android/README.md#first-device-smoke-test).

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

## What these tests can and cannot show

A smoke test checks that the app installs, runs every task, and records and exports sessions on a particular phone.

- **It is not a timing study.** The values below are what the app's own dashboard reported. They say nothing about how accurately that phone times stimuli or touches; that needs the slow-motion video study and, later, the hardware timing unit.
- **It involves no children.** Every test so far is an adult testing on their own phone. Work with children requires an approved ethics application and a supervising psychologist.

## Test 1: Samsung A15, Android 16

**One device, one adult self-test.** It shows that the app works on this phone. It says nothing about timing accuracy, about other phones, or about children.

| Item | Result |
|---|---|
| Date | TODO: date of the test |
| App build | TODO: version and commit shown on the app's home screen, or the GitHub Actions run the debug APK came from |
| Tester | Farzaan Jamal Khan (adult self-test; no other participants) |
| Device model | Samsung A15 |
| Android version | 16 |
| Touch timestamp resolution | As reported by the tester: "almost a millisecond, basically a few nanoseconds" (see note 1) |
| Refresh rate, nominal / measured | 60 Hz / 60 Hz |
| Clock check | Pass; difference under 0.5 ms |
| Dropped frames per task part (Go/No-Go, spatial span forwards, spatial span backwards, Flanker, time reproduction, choice-delay) | Between 0 and 10 in each task part; the per-part counts were not recorded (see note 2) |
| Interruptions | None |
| Problems seen | None |

**Notes**

1. The dashboard's "touch timestamp resolution" field is not a measurement. It is a label the app sets from the Android version: "ns" on Android 14 (API 34) and later, where Android provides touch times in nanoseconds, and "ms" on earlier versions. On Android 16 the field should read "ns". It describes the units Android reports touch times in, not how precise those times are. TODO: confirm what the dashboard showed.
2. The exported session file holds the exact dropped-frame count for each run. TODO: if the export from this test was kept, enter the per-part counts here.
3. The values in the table were read from the app's dashboard by the tester and are recorded as reported. No session data from this test is committed to the repository.
