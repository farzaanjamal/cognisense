# Hardware (DESIGNED — nothing in this folder is built)

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

Software interfaces and data contracts for both hardware units already exist in `android/core` (`HardwareTimingSource`, `ImuMotionSource`). Both stubs throw if called. This folder holds the physical design.

## 1. Timing unit

**Principle.** Stimulus onset and response are timestamped on the same microcontroller clock, so display, touch and radio latency are excluded from RT. See `docs/measurement_precision.md` §5.

| Part | Proposed component | Role | Status |
|---|---|---|---|
| Microcontroller | ESP32 dev board (WROOM-32) | µs timer, GPIO interrupts, BLE | DESIGNED |
| Light sensor | Photodiode (BPW34-class) or phototransistor, with an LM393 comparator module for a clean digital edge | Detect the on-screen onset marker | DESIGNED |
| Response input A | Tactile button, interrupt-driven; first edge timestamped, then a refractory period to ignore bounce | Hardware-timed response | DESIGNED |
| Response input B | Piezo disc on the device body, through a clamp and comparator | Timestamp the finger striking the touchscreen, so the response method is unchanged | DESIGNED, **unproven** — light taps may be undetectable |
| Actuator (validation only) | Solenoid with transistor driver and flyback diode, fired at programmed delays after the photodiode edge | Responses with known true RT | DESIGNED |
| Housing | Opaque cover over the marker and photodiode | Hides the flashing marker from the child | DESIGNED |

Constraints already identified:

- **Scan position.** The marker must sit at the same scan position as the stimulus, or the corner-to-centre offset must be measured per device model.
- **Brightness.** Calibrate at high fixed brightness, because OLED panels flicker at low brightness.
- **Use with children.** The marker is never visible to a child.

## 2. Motor-activity unit

**Rationale.** A meta-analysis of mechanically measured activity found excess motor activity in ADHD that is largest under high executive-function demand (Kofler et al., 2016). Activity data are therefore tagged with the task and block being performed.

**Open decision: body placement.** This should go to the expert panel.

| Option | For | Against |
|---|---|---|
| Waist-worn IMU | Common actigraphy site; unobtrusive | Misses limb fidgeting |
| Ankle-worn IMU | Captures leg movement while seated | Less studied in task settings [VERIFY] |
| Headband IMU | Head movement is what the camera-based QbTest records [VERIFY] | Most noticeable to the child; may alter behaviour |
| Seat pressure (force-sensitive resistors) | Nothing worn by the child | Indirect; depends on chair and posture |

**Proposed component:** a 6-axis IMU (accelerometer + gyroscope) on an ESP32, sampling at about 50 Hz, stored on the device and transferred after the session. The specific IMU model is to be chosen for availability in Pakistan [VERIFY: common low-cost modules include MPU-6050-class boards, whose supply may be clones].

**Safety for a wearable on a child:** fully enclosed, no exposed wiring, a protected battery pack, smooth edges, and it is never worn without parental consent and child assent.

## 3. Roadmap

| Phase | Content | Depends on |
|---|---|---|
| H0 (now) | Interfaces, data contracts, this design | — |
| H1 | Bench timing unit: photodiode + button + BLE result line; test against a known signal | Parts purchase |
| H2 | Solenoid-driven characterisation of several phones (timing-validation study) | H1, written acceptance criteria |
| H3 | Piezo tap detection feasibility test | H1 |
| H4 | IMU unit prototype; placement decided after expert review | Panel feedback |
| H5 | Use with children | Ethics approval (Protocol 2 or an amendment) |

For parts and approximate PKR costs, see the bill of materials in `docs/archive_build_plan_superseded.md`. Note that the earlier plan advised against buying an IMU; the current brief makes motion sensing part of the design, so an IMU module is added for H4.
