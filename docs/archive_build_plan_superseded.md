# Cognisense — Build Plan (September MVP → 14-Day Version)

## 0. What this plan builds

Two cognitive tasks on Android — **Go/No-Go** and **Flanker** — with full trial-level logging, plus a low-cost **ESP32 calibration rig** (photodiode + physical button over Bluetooth Low Energy) that measures true stimulus-onset and response latency on each device. The cognitive domains are chosen because they're relevant to attention and executive function (the ADHD angle). The system outputs a cognitive/behavioral **profile** — never a diagnosis.

**Deliberately NOT in this plan** (these are the later, supervised roadmap): the informant/rating-scale module, extra cognitive domains, the incremental-validity study, and any testing on minors.

---

## 1. Who does what

- **Claude produces:** all source code (Android/Kotlin, ESP32/Arduino C++, Python), wiring pinouts, this plan, test procedures, and docs — handed over one subsystem at a time.
- **You do:** procure and wire the hardware, install the dev tools, flash the ESP32, build and run the app on real phones, run sessions, collect data, run the analysis.
- **Reality check:** the code can't be compiled or tested inside chat, and Android timing is fiddly. Each file's first version is a strong draft to debug against real hardware. Budget time for a build→flash→run→fix loop at every phase, and paste errors as you hit them.

---

## 2. What to buy or borrow (bill of materials)

Prices are rough PKR and move around — verify locally. Sources: daraz.pk, digilog.pk, hallroad.org.pk, and local electronics markets. Nothing here needs a specialist supplier.

| Item | Qty | Purpose | ~PKR | Mandatory? |
|---|---|---|---|---|
| Android phones (different models) | 2–4 | run the app; device variety **is** the study | own / borrow | Yes (≥2) |
| ESP32 dev board (DevKit V1 / WROOM-32) | 1 | ground-truth timing clock | 1,200–2,000 | Yes |
| Photodiode (BPW34) or phototransistor | 1–2 | detect true stimulus onset on the screen | 50–200 | Yes |
| Tactile push buttons | 2 | physical response buttons | 20–50 ea | Yes |
| Breadboard (half or full) | 1 | wiring | 200–500 | Yes |
| Jumper wires (M-M + M-F packs) | 1 set | wiring | 200–400 | Yes |
| Resistor assortment (incl. 10 kΩ, 220 Ω) | few | pull-ups / current limiting | 100–300 | Yes |
| USB **data** cable for ESP32 (micro-USB or USB-C, match your board) | 1 | flashing & power | own / 150–400 | Yes |
| LM393 comparator module | 1 | optional cleaner light-edge detection | 100–250 | Optional |
| Micro servo (SG90) *or* small solenoid + driver transistor + flyback diode | 1 | automated repeatable "press" (Phase 6) | 300–800 | Optional (later) |
| LED + resistor | 1 | optional reverse-timing test / indicator | 30–80 | Optional |

**Do NOT buy:** EEG, PPG/heart-rate, GSR, camera/mic modules, temperature/humidity — none serve the core question. The phone is already your main sensor.

**Correctness note:** get a **photodiode/phototransistor**, *not* an **LDR/photoresistor** (the KY-018-style modules). LDRs respond in tens of milliseconds — far too slow to time a screen flash.

---

## 3. Software to install (on your computer)

- **Android Studio** (free) — builds and runs the app. Wants ~8 GB+ RAM and several GB of free disk; slow on a weak laptop. *(Tell Claude your specs and the approach can be adjusted.)*
- **Arduino IDE** (free) — flashes the ESP32. Add ESP32 board support via Boards Manager. (PlatformIO/VS Code is an alternative.)
- **USB-serial driver** for your board's chip — CP2102 or CH340 (check which your ESP32 uses).
- **Python 3** + `pip install pandas numpy scipy matplotlib pingouin` — for analysis.
- A phone with **USB debugging** enabled (Developer Options) to install the app.

---

## 4. The build, phase by phase

Each phase: **goal → what Claude hands you → what you do → how you know it worked.**

**Phase 0 — Environment (½–1 day).**
Goal: toolchain works end to end.
Claude hands you: setup steps + a "blink" ESP32 sketch + a minimal hello-world app.
You do: install everything; flash blink; install the app on your phone.
✅ Works when: the ESP32 LED blinks and the app opens on your phone.

**Phase 1 — Go/No-Go task + logger (2–3 days).**
Goal: a real, timed task that records every trial.
Claude hands you: the Android task (practice + assessment stages, randomized trials, vsync-aligned stimulus rendering, a white "sync patch" flashed in a screen corner at stimulus onset), SQLite trial logger, CSV export.
You do: run a full session; export the CSV.
✅ Works when: the CSV has one row per trial with stimulus, response, correctness, and onset/response timestamps.

**Phase 2 — ESP32 calibration firmware (2–3 days).**
Goal: measure true stimulus onset with the photodiode.
Claude hands you: ESP32 firmware that samples the photodiode fast and timestamps each screen-flash, plus the exact wiring pinout.
You do: wire the photodiode + button on the breadboard; flash; tape the photodiode over the app's sync patch; capture onset events during a session.
✅ Works when: the ESP32 logs a timestamp per flash, and you can see the gap between when the app *thinks* it drew the stimulus and when the light actually changed.

**Phase 3 — BLE button link (1–2 days).**
Goal: physical responses timestamped on the ESP32 clock and sent to the app.
Claude hands you: BLE firmware (ESP32) + BLE client code (app).
You do: pair; press the button during a session.
✅ Works when: button presses appear in the app's log with timestamps, next to touchscreen responses — so you can compare both input paths against the photodiode ground truth.

**Phase 4 — Device timing characterization (1–2 days).**
Goal: your actual research result.
You do: repeat the Phase 2–3 capture on each of your 2–4 phones.
✅ Works when: you have a per-device table of stimulus-onset latency, input latency, and jitter (SD).

**Phase 5 — Python analysis (1–2 days).**
Goal: turn CSVs into metrics + plots.
Claude hands you: a script computing median RT, RT variability/CV, commission/omission errors, interference cost, per-device latency/jitter, and corrected RTs, with plots and a summary table.
You do: run it on your exported data.
✅ Works when: you get clean per-participant and per-device summaries and figures.

**Phase 6 — If time (Flanker + automation).**
Add the Flanker task (gives **interference cost** — a difference score that's robust to a constant device offset). Add the servo/solenoid so calibration presses are automated and repeatable with zero human variability.

---

## 5. Ethics for any human testing (even now)

For the September build, test hardware and — if you want behavioral data — **consenting adult volunteers only**. No minors, no clinical claims. Use pseudonymous IDs (P001…), record only what the schema needs, and tell participants they can stop anytime. The child/ADHD validation is the supervised, ethics-approved later phase.

---

## 6. When you're stuck

Paste the exact error text (and what you did), or a photo of your wiring, and Claude will debug it. Version conflicts, BLE not connecting, timing looking weird — all normal parts of the loop.
