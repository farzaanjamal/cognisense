# Measurement Precision

| Field | Value |
|---|---|
| Document status | DRAFT v0.1, unreviewed. 2026-10-02. |
| Scope | Timing of stimulus onset and response on Android, in the demo (software timing) and the designed hardware layer |
| Related | `task_specifications.md` §3 (metric classes), `architecture.md` §4 (TimingSource), `hardware/README.md` |

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

**No timing figure in this document describes Cognisense.** Nothing has been measured on a device yet. Figures from other studies are cited as context, and every planned measurement is listed without values.

---

## 1. What is being measured

A reaction time (RT) is the interval between two physical events: light from the stimulus reaching the child's eye, and the child's finger contacting the response surface. Software can only timestamp two proxies: the moment the app *produced* the frame containing the stimulus, and the moment the operating system *registered* the touch. Every source of error lives in the gap between those physical events and their proxies.

```
measured RT = true RT + C + e
```

- **C** is a constant offset for a given device and configuration (display latency + touch latency).
- **e** is trial-to-trial jitter.

`task_specifications.md` §3 classifies every metric by which of these terms it is sensitive to.

## 2. Sources of timing error on Android

### 2.1 Stimulus side: from "frame produced" to "light emitted"

| Stage | What happens | Effect |
|---|---|---|
| Frame callback | The app decides frame content in a `Choreographer` callback; `frameTimeNanos` is the vsync timestamp at which work on this frame began | This is the onset timestamp the demo logs |
| Rendering and composition | The app draws; the system compositor combines layers | Typically adds one or more frames of delay; depends on buffering depth [VERIFY typical values] |
| Panel scan-out | The panel is refreshed line by line, top to bottom of its native orientation | A stimulus at screen centre appears part of a frame later than one at the scan-start edge. **This matters for the onset marker (§5.2).** |
| Pixel response | Liquid-crystal pixels take milliseconds to change; OLED pixels are faster | Adds to C; differs between panel technologies |
| Brightness control | Many OLED panels dim by pulse-width modulation at low brightness [VERIFY] | Light output flickers; invisible to most viewers but visible to a photodiode |

Frame quantisation: onset can only occur on a vsync. If the intended onset time is uniformly distributed within a frame, quantisation error has SD = period/√12 — 4.8 ms at 60 Hz, 2.4 ms at 120 Hz. The engine removes most of this for scheduled events by targeting the nearest frame and logging the frame it actually used. What remains is the stimulus duration: the displayed duration is always a whole number of frames, and that number is logged per trial.

Variable refresh rate: many current phones switch between 60, 90 and 120 Hz adaptively. If the rate changes mid-session, frame durations change. The app will request a fixed refresh rate for task screens [VERIFY the API behaviour on target devices] and log both the nominal rate and the rate measured from frame intervals.

Dropped frames: if the app misses a vsync (heavy load, thermal throttling, background activity), a frame is shown for longer than intended. The engine detects gaps longer than 1.5 frame periods, counts the missed frames per trial, and flags them.

### 2.2 Response side: from finger contact to event timestamp

| Stage | Effect |
|---|---|
| Touch controller scanning | The panel's touch sensor is sampled at a fixed rate (it varies across devices); contact is detected at the next scan |
| Controller firmware filtering | Some controllers filter or debounce before reporting |
| Driver and input system | Assigns the event timestamp |
| Delivery to the app | Variable delay before the app's callback runs |

The demo uses the **event timestamp** (`MotionEvent.getEventTime()`, or `getEventTimeNanos()` on Android 14 / API 34 and later; confirmed by inspecting the API 33 and 34 platform classes), so the last row does not affect measured RT. The first three rows do, and they form the input part of C and e.

Contact detection also depends on contact area and pressure, finger moisture, and screen protectors. These are physical factors no software can remove.

### 2.3 Clock domains

`Choreographer` frame times use the `System.nanoTime()` base; `MotionEvent` times use the `SystemClock.uptimeMillis()` base. On Android both are expected to derive from `CLOCK_MONOTONIC` [VERIFY on each target device]. The app will check this at every session start by reading both clocks back-to-back and logging the difference (`clock_check_ns`). A difference of more than 1 ms would invalidate software RTs on that device, and the session would be flagged.

### 2.4 Published magnitudes, for context only

Using a photodiode and a robotic actuator, Pronk et al. (2020) found that touchscreen RTs in *web browser* applications were overestimated by roughly 57–70 ms on the phones they tested, with trial-to-trial SDs of a few milliseconds. Native Android apps using event timestamps may behave differently. Whether they do is the purpose of the characterisation in §6 and §7.

### 2.5 Task-specific timing notes

- **Time reproduction (T9).** Reproduced duration is release time minus press time, both touch-event timestamps. A constant touch latency cancels only if press and release latencies are equal. Lift detection may differ from contact detection; this is untested and is a question for the timing-validation study. The displayed target duration is logged per trial (`displayed_ms`) and is quantised to whole frames.
- **Spatial span (T6).** Scoring is accuracy-based (class A). Tap latencies are logged as exploratory class C measures.
- **Choice-delay (T10).** The scored measure is a choice proportion (class A). Delays of 2 s and 20 s are scheduled from the choice timestamp and realised on the nearest frame; frame-level error is negligible at these durations.

## 3. Constant offset versus jitter

| | Constant offset C | Jitter e |
|---|---|---|
| Absolute RT (median, mean) | Biased by C; C differs between devices | Adds noise |
| Within-subject RT differences (interference cost, event-rate effect) | Cancels, provided C does not differ between conditions | Adds noise; lowers reliability |
| RT dispersion in ms (ISD, IQR) | Unaffected | **Inflates it**: observed variance ≈ true variance + var(e), if independent |
| Coefficient of variation (ISD/mean) | **Biased** through the mean | Inflated through the ISD |
| SSRT (nth RT minus mean SSD) | **Biased** by C, because RT contains C and SSD does not | Adds noise |
| Thresholds on absolute RT (anticipation flags) | **Biased**: lenient on high-latency devices | Blurs the boundary |
| Counts and accuracy | Unaffected if response windows are generous relative to C | Unaffected |

Two points matter for interpretation:

1. **RT variability is the metric most often cited in ADHD research, and it is jitter-sensitive.** How much jitter inflates ISD depends on var(e) relative to children's true RT variance. Between-child comparisons are compromised mainly when jitter *differs between devices*. That difference is an empirical question for §7.
2. **Device model is a confound for class C metrics.** Until hardware timing exists, class C metrics are compared only within a device model, and device model is a covariate in every analysis.

## 4. Mitigations in the demo

| Mitigation | Status |
|---|---|
| Response time taken from the MotionEvent timestamp, not callback time | IMPLEMENTED in core; Android code written (`TaskView.eventTimeNanos`; ns on API 34+, ms below); not yet run on a device |
| Onset = frame time of the first frame containing the stimulus | IMPLEMENTED in core; Android code written (`TaskRunner`, Choreographer); not yet run on a device |
| Engine never reads a clock; all scheduling targets the nearest frame | IMPLEMENTED; tested at 60, 90, 120 and 144 Hz in simulation |
| Displayed stimulus duration and frame count logged per trial | IMPLEMENTED in core |
| Dropped-frame detection and per-trial flags | IMPLEMENTED in core |
| Responses classified by timestamp, not arrival order | IMPLEMENTED in core |
| Interruptions end the trial as INTERRUPTED; the pause gap is not miscounted as dropped frames | IMPLEMENTED in core |
| Onset marker that mirrors stimulus visibility (rising edge = onset, falling edge = offset) | IMPLEMENTED in core; drawing code written, inset 24 dp from the corner so rounded corners cannot clip it |
| Logging of device model, OS, refresh rate (nominal and measured), pixel density, brightness, Do-Not-Disturb state, clock check | Fields IMPLEMENTED in core; collection code written (`DeviceInfo`, `TaskRunner` refresh probe) |
| Fixed window brightness, immersive full screen, locked landscape, 60 Hz display mode requested, no permissions requested | Code written (`WindowControl`, manifest); not yet verified on a device |
| Pre-session checklist (quiet mode, table, consent on paper, screen protector, battery) stored in session metadata | Code written |
| 1 s fixation lead-in after an interruption, so no stimulus appears on the first frame back | IMPLEMENTED in core |

"IMPLEMENTED in core" means the logic exists in `android/core` and passes simulation tests. It has not been run on a phone. Simulation verifies the engine's own logic; it cannot reveal device latency.

## 5. The hardware solution (DESIGNED)

### 5.1 Single-clock principle

An ESP32 timestamps two physical events on its own microsecond timer:

- the onset marker brightening, via a photodiode taped over it;
- the response, via a physical button or a piezo disc.

RT is computed on the microcontroller, and Bluetooth only carries the finished number. Display latency, touch latency and radio latency are all outside the measured interval. The data contract is in `android/core/.../timing/TimingSource.kt` (`HardwareTimingSource`).

### 5.2 Design issues already identified

1. **Response modality.** A physical button measures a button press, not a touchscreen tap, so hardware-timed sessions use a different response method. A piezo disc on the device that detects the finger striking the glass would keep the same response method. It is untested; light taps may produce signals too weak to detect reliably.
2. **Marker position and scan-out.** A corner marker and a centred stimulus are scanned out at different times within a frame. Options: place the marker at the same scan position as the stimulus, or measure the corner-to-centre offset once per device model and correct for it.
3. **The marker is a visible cue.** A patch that flashes with every stimulus is a peripheral alerting signal, which would change the task. During any session with children, the photodiode housing must cover the marker completely. It is disabled by default in field mode.
4. **OLED brightness flicker.** Calibration should run at high fixed brightness, and firmware should use a threshold with hysteresis rather than a single level.
5. **Remaining hardware error.** Photodiode rise time, comparator threshold setting, button bounce (handled by taking the first edge and ignoring a refractory period) and interrupt latency. All are expected to be small relative to C [VERIFY by bench test].

## 6. Planned characterisation of the demo with high-frame-rate video (PLANNED)

**Purpose.** Estimate C and the SD of e per device before any hardware exists, using only a second phone's slow-motion camera.

**Procedure (draft).**

1. Mount a second phone recording slow-motion video (240 fps on many recent mid-range phones [VERIFY]) so that the test device's stimulus area and the tapping finger are both in frame.
2. Run the simple RT task with logging on.
3. For each trial, two raters independently mark the video frame of stimulus appearance and the frame of finger contact; disagreements are resolved by a third look.
4. Video RT = (contact frame − appearance frame) × 4.17 ms. Compare with the logged software RT.
5. Per device, report the mean of (software − video) as the estimate of C, the SD as the estimate of jitter, the number of trials, and inter-rater agreement.

**Limitations.** Video resolution is ±4.17 ms per event. The exact moment of contact is ambiguous on video. The camera's rolling shutter skews timing across the image. This method can show whether offsets are tens of milliseconds and whether they differ between devices. It cannot resolve differences of a few milliseconds.

## 7. Planned timing-validation study (PLANNED)

**Aim.** Quantify touchscreen timing error on Android phones across price points, and its effect on each Cognisense metric.

**Design (draft).**

- Several Android devices spanning low, middle and upper price tiers; the exact number depends on what can be borrowed.
- Each device runs T1 (simple RT), T2 (Go/No-Go), T5 (Flanker) and, once built, T4 (Stop-Signal), with software and hardware timing recorded simultaneously for the same trials.
- An automated actuator (a solenoid driven by the ESP32 at programmed delays after the photodiode edge) produces responses with known true RTs. This allows offset and jitter to be measured without human variability.

**Outcomes per device.**

- Mean and SD of (software RT − hardware RT).
- Displayed-duration error.
- Dropped-frame rate.
- For each metric: the difference between its software-timed and hardware-timed values on identical trial streams.

**Acceptance criteria.** To be written and fixed before data collection, not after.

## 8. What remains uncontrolled

Even with hardware timing, the following are not controlled. They will be recorded where possible and otherwise stated as limitations:

- **Child and posture:** viewing distance and posture; grip; which finger is used.
- **Environment:** ambient light and screen glare; room temperature.
- **Device condition:** screen protectors; finger moisture; device temperature and thermal throttling; battery level; background operating-system activity.
- **Physical stimulus size:** varies slightly between devices despite dp units, because reported pixel density is approximate. Actual xdpi/ydpi are logged so physical size can be computed.

## 9. Why browser timing is never used for data

The browser preview (`browser/`) runs the same task logic as the app, verified byte-identical in scripted runs. Its **timing**, however, is not comparable and is never used as data.

**Onset.** `requestAnimationFrame` timestamps mark when the browser started preparing a frame, not when light left the screen. Browser compositing adds latency that varies by browser, operating system and power state.

**Response.** `event.timeStamp` resolution is deliberately coarsened in some browsers for security reasons [VERIFY current values per browser]. Touch events may be coalesced or delayed by gesture handling.

**Control.** A web page cannot:
- fix screen brightness;
- request a display mode or refresh rate;
- enable Do Not Disturb;
- stop background-tab throttling.

Fullscreen is unavailable on iPhone Safari.

**Published magnitudes.** Browser-based touchscreen RTs were overestimated by roughly 57–70 ms on the phones tested by Pronk et al. (2020), and by up to about 133 ms in one desktop browser. Bridges et al. (2020) and Anwyl-Irvine et al. (2021) report substantial variation across browser/OS combinations.

**Consequences, built into the preview:**
- It runs only the shortened Expert Review variants.
- Its reaction-time field is named `rt_ms_not_a_measurement`.
- Every screen carries the "not a measurement" banner.
- Downloaded files are marked `browser_preview: true`.

Preview data must never be combined with app data. The analysis pipeline (PLANNED) will reject any file carrying that flag.

**What the preview is valid for:** content review, meaning seeing the stimuli, instructions, task flow, practice criteria and what is recorded. That is what the expert panel rates. It says nothing about timing.
