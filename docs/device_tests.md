# Device tests

Each run of the app on a real phone is recorded here. The procedure is the smoke test in [`android/README.md`](../android/README.md#first-device-smoke-test).

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

## What these tests can and cannot show

A smoke test checks that the app installs, runs every task, and records and exports sessions on a particular phone.

- **It is not a timing study.** The values below are what the app's own dashboard reported, plus the phone's specification. They say nothing about how accurately that phone times stimuli or touches; that needs the slow-motion video study and, later, the hardware timing unit.
- **It involves no children.** Every test so far is an adult testing on their own phone. Work with children requires an approved ethics application and a supervising psychologist.

## Test 1: Samsung Galaxy A15, Android 16

**One device, one adult self-test.** It shows that the app works on this phone. It says nothing about timing accuracy, about other phones, or about children.

| Item | Result |
|---|---|
| Date | TODO: date of the test |
| App build | The app reported version `0.1.0-dev`. Its version name had not been updated since the start of the project; it was fixed in 0.6.1-dev. |
| Tester | Farzaan Jamal Khan (adult self-test; no other participants) |
| Device model | Samsung Galaxy A15 |
| Android version | 16 |
| Result | All five tasks ran with no problems |
| Touch timestamps | Not transcribed exactly. On Android 16 the app uses nanosecond event timestamps; this is a design value, not a measurement (see note 1) |
| Refresh rate | 60 Hz requested by the app; the panel is rated 90 Hz adaptive; the measured rate was not recorded |
| Clock check | Pass; difference under 0.5 ms |
| Dropped frames | 2–10 per task |
| Interruptions | None |

**Notes**

1. The dashboard's "touch timestamp resolution" field is not a measurement. It is a label the app sets from the Android version: "ns" on Android 14 (API 34) and later, where Android provides touch times in nanoseconds, and "ms" on earlier versions. It describes the units Android reports touch times in, not how precise those times are.
2. The values in the table were read from the app's dashboard by the tester and are recorded as reported, except the panel's 90 Hz rating, which is the manufacturer's specification. No session data from this test is committed to the repository.
