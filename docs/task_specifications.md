# Cognisense — Candidate Task Pool and Proposed Core Battery

| Field | Value |
|---|---|
| Document status | DRAFT v0.1, unreviewed. Prepared 2026-10-02. |
| Implementation status | Core battery (T2, T5, T6, T9, T10): logic **IMPLEMENTED** and simulation-tested; Android code complete; **not yet run on a device**. All other tasks: **DESIGNED**. |
| Parameter authority | For implemented tasks, `config/tasks.json` is authoritative; values here are mirrored for reading (see §10). |
| Intended readers | Expert panel (psychology, psychiatry, education), technical reviewers, the author |
| Supersedes | The two-task plan in `cognisense_build_plan.md` (Go/No-Go + Flanker only) |

> **Disclaimer (verbatim, used in every Cognisense document and the app's first screen):**
> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

**Conventions used in this document**

- **[VERIFY]** marks a citation or statement whose bibliographic details or content were not checked against the primary source during drafting. Before this document goes to any reviewer, every [VERIFY] must be resolved by reading the source, or the statement removed. Citations without the marker had their existence and abstract-level content checked against an indexed source (PubMed, publisher page, or institutional repository); their full texts still need reading before being quoted.
- **Proposed parameter** means a design value chosen for this specification on the stated rationale. None has been piloted. Time estimates are arithmetic from the trial structure, not measurements.
- No accuracy, reliability, or performance figure in this document describes Cognisense. All such figures describe published studies of other implementations, mostly in other populations.

---

## 1. Theoretical grounding

### 1.1 ADHD is neuropsychologically heterogeneous

Single-deficit accounts of ADHD (e.g., a core inhibitory deficit) have been challenged by evidence of distributional overlap between ADHD and control groups and of marked heterogeneity at the neuropsychological level, with only a small proportion of children with ADHD showing a deficit on any one task (Nigg, Willcutt, Doyle & Sonuga-Barke, 2005). Multiple-pathway models followed. The dual pathway model distinguished an executive (inhibitory) route from a motivational (delay aversion) route (Sonuga-Barke, 2002 [VERIFY]). A study of 71 ADHD probands aged 6–17, their siblings and 50 controls found that temporal processing, inhibitory control and delay-related deficits formed independent components, that their co-occurrence in ADHD was no greater than chance, and that substantial subgroups showed only one problem (Sonuga-Barke, Bitsakou & Thompson, 2010). Two further accounts relevant to task design are:

- **Intra-individual variability as a core feature.** Castellanos & Tannock (2002) [VERIFY] proposed reaction-time variability as a candidate endophenotype. A meta-analysis of 319 studies found greater RT variability in children with ADHD than typically developing peers (Hedges' g ≈ 0.76), persisting after accounting for mean RT, while mean RT differences did not persist after accounting for variability (Kofler et al., 2013). The same meta-analysis found variability elevated in other clinical groups, so it is transdiagnostic.
- **State regulation / cognitive-energetic model.** Performance in ADHD is proposed to depend on energetic state, so that event rate (very slow or very fast stimulus presentation) modulates group differences (Sergeant, 2005 [VERIFY]; event-rate meta-analysis: Metin et al., 2012 [VERIFY]).

**Design consequence.** The pool must sample several dimensions rather than one. The minimum set implied by this literature is: response inhibition, working memory, temporal processing, delay-related choice, and attentional/energetic regulation indexed by RT variability and event-rate effects. Set shifting and interference control are included as secondary dimensions with weaker evidence (§1.3).

### 1.2 The limits of discriminative validity — what this battery cannot do

A psychologist reviewer will expect this section to be explicit.

1. **Effect sizes are moderate and distributions overlap.** A meta-analysis of executive-function measures reported moderate group differences between ADHD and control samples (Willcutt et al., 2005). A later analysis notes that effect sizes of the size reported there (d ≈ 0.55) predict that only about 30–36% of children with ADHD would be classified as impaired on a given measure, and that most estimates place executive dysfunction in roughly 33–50% of children with ADHD (Kofler et al., 2019 [VERIFY year/journal]).
2. **The proportion "impaired on at least one domain" depends on method.** The same study, correcting for task unreliability with latent-variable methods, reported impairment on at least one executive function in 89% of its ADHD sample (62% working memory, 27% inhibitory control, 38% set shifting). This higher figure comes from a multi-task latent battery in a US clinical sample and should not be expected from a short field battery of single tasks. The literature on this proportion is therefore unsettled, and the dossier should present both figures and the reason they differ.
3. **Commercial CPTs have modest stand-alone classification accuracy.** A meta-analysis of commercially available CPTs reported pooled sensitivity 0.75 and specificity 0.71 for total/ADHD scores, and concluded they should only be used within a comprehensive diagnostic process (Arrondo et al., 2024).
4. **Task deficits are not specific to ADHD.** RT variability, CPT errors, and executive weaknesses are elevated in several other conditions (Kofler et al., 2013; Arrondo et al., 2024). Poor performance can also reflect low motivation, fatigue, sleep loss, anxiety, unfamiliarity with touchscreens, visual problems, or misunderstood instructions.
5. **Performance tasks and behavioural ratings correlate weakly.** Performance-based EF measures and EF rating scales are not interchangeable measures of the same construct (Toplak, West & Stanovich, 2013); one study found a test–rating association of r ≈ .30 (Soto et al., 2020 [VERIFY journal/year]). **Consequence for Protocol 2:** a modest correlation between Cognisense metrics and teacher-rated SDQ would be expected even if the tasks measure their constructs well, so a weak correlation cannot by itself be read as task failure, and a moderate one cannot be read as clinical utility.
6. **The reliability paradox.** Tasks with robust group-level effects often have poor test–retest reliability for individual differences, especially difference scores; in adult students, flanker interference cost had ICC ≈ .4, while go/no-go commission errors reached .76 (Hedge, Powell & Sumner, 2018). Comparable child data for most tasks here are sparse; Protocol 2 must measure reliability rather than assume it.

**Consequence for the whole project:** the output of the battery is a profile of task performance, reported per metric, with no composite score, cut-off, norm, or classification (see `design_rationale.md`, PLANNED).

### 1.3 Which metrics are best supported

| Metric | Strength of support in the ADHD literature | Notes |
|---|---|---|
| Intra-individual RT variability (ISD; ex-Gaussian τ) | Strongest and most replicated | Transdiagnostic; jitter-sensitive (§3) |
| Visuospatial working-memory span (esp. backward) | Moderate-to-large group effects | Martinussen et al., 2005 [VERIFY effect sizes]; Kasper et al., 2012 [VERIFY] |
| Go/No-Go commission errors | Moderate; best test–retest of the interference/inhibition metrics in Hedge et al. (2018), adult sample | Confounded with response speed |
| Stop-signal reaction time (SSRT) | Moderate group effects | Meta-analyses also found slower go RT, complicating specificity (Lijffijt et al., 2005 [VERIFY]; Alderson et al., 2007 [VERIFY]); offset-sensitive (§3) |
| CPT omissions/commissions | Moderate group effects; modest classification | Huang-Pollock et al., 2012 [VERIFY]; Arrondo et al., 2024 |
| Delay-related choice (proportion of larger-later choices) | Moderate group effects | Patros et al., 2016 [VERIFY]; dissociable from inhibition (Sonuga-Barke et al., 2010; Dalen et al., 2004) |
| Temporal processing (reproduction accuracy/precision) | Frequently reported, heterogeneous methods | Noreika, Falter & Rubia, 2013 [VERIFY]; Smith et al., 2002 [VERIFY] |
| Flanker/Simon interference cost | Weak and inconsistent | Mullane et al. (2009) found disadvantages on incongruent relative to congruent trials across 12 small studies; van Mourik et al. (2009) found none on a Simon task; interference scores have poor reliability and questionable convergent validity (Hedge et al., 2018; Paap et al., 2020 [VERIFY authorship]) |
| Task-switch cost | Weak to moderate, inconsistent | Difference score; reliability concerns |
| Mean/median RT | Weak once variability is accounted for | Offset-sensitive (§3) |

### 1.4 Coverage map

| Dimension | Candidate tasks | In proposed core? |
|---|---|---|
| Response inhibition (prepotent) | T2 Go/No-Go, T4 Stop-Signal | T2 |
| Sustained attention / vigilance | T2, T3 CPT | T2 (partial) |
| Energetic regulation (event rate) | T2 (event-rate manipulation) | T2 |
| RT variability | T1, T2, T3, T4, T5 | T2, T5 |
| Visuospatial working memory | T6 Spatial span, T7 Spatial n-back | T6 |
| Interference control | T5 Flanker | T5 |
| Set shifting | T8 Task switching | — |
| Temporal processing | T9 Time reproduction | T9 |
| Delay-related choice | T10 Choice-delay | T10 |
| Processing/motor speed (covariate) | T1 Simple RT | — (Go RT from T2 used instead) |

### 1.5 Hyperactivity and the sensor layer (DESIGNED, not specified here)

A meta-analysis of 63 studies of mechanically measured activity found excess motor activity in ADHD that depends substantially on environmental demands, with the largest effects under high executive-function demands and low stimulation (Kofler, Raiker, Sarver, Wells & Soto, 2016). A later study concluded that executive-function demands exacerbate but do not fully explain hyperactivity (Soto, Black & Kofler, 2024). **Implication for the battery:** motion data are most interpretable when time-locked to task blocks of known cognitive demand, so the data contract (PLANNED in `architecture.md`) will tag every motion sample with the active task and block. The backward spatial-span block (T6) is the highest-WM-demand block in the core and therefore the most informative period for activity measurement. Sensor hardware is specified separately in `hardware/`.

---

## 2. Selection criteria — how each candidate meets them

| Task | Established in ADHD research with child psychometric data | Language-free stimuli | Android touchscreen, offline, ages 8–14 | Fits time budget | Cultural transfer |
|---|---|---|---|---|---|
| T1 Simple RT | Yes (as RTV source) | Yes (shape) | Yes | ~2 min | No changes needed |
| T2 Go/No-Go (event rate) | Yes | Yes (shapes) | Yes | ~6.5 min | No changes needed |
| T3 Rare-target CPT | Yes (letter versions dominant) | Yes, after replacing letters with shapes — **this changes the validated paradigm** | Yes | ~9 min — **too long for core** | No changes needed |
| T4 Stop-Signal | Yes | Yes (arrows) | Yes; visual stop signal required (no sound) | ~8 min | Arrow convention needs demonstration |
| T5 Flanker | Yes (child versions exist) | Yes (arrows) | Yes | ~4 min | Arrow convention needs demonstration |
| T6 Spatial span | Yes | Yes | Yes | ~4–6 min | No changes needed |
| T7 Spatial n-back | Yes (letter versions dominant) | Yes (positions) | Yes; floor risk at 2-back for age 8 [VERIFY] | ~6 min | No changes needed |
| T8 Task switching | Moderate | Yes (shape/colour) | Yes; requires colour vision | ~7 min | No changes needed |
| T9 Time reproduction | Moderate; methods heterogeneous | Yes | Yes | ~3 min | Counting strategies may vary |
| T10 Choice-delay | Yes | Yes, if delay and reward size are shown as objects, not digits | Yes | 1.5–5.5 min (choice-dependent) | Token imagery to be chosen locally; **requires a reward — conflicts with the no-reward rule (§5.10)** |

Every stimulus set replaces letters/digits with shapes, positions, arrows or objects. Where the established version uses letters (CPT, n-back), the adapted version is not psychometrically equivalent to the published one; its properties must be re-established.

---

## 3. Timing model and the robustness of each metric

Software timing on Android records:

`measured RT = true RT + C + e`

where **C** is a device-specific constant offset (display pipeline latency + touch-input latency; tens of milliseconds on the devices tested by Pronk et al., 2020) and **e** is trial-to-trial jitter (frame quantisation, touch-controller sampling, scheduling). Every metric falls into one of three classes:

| Class | Definition | Effect of C | Effect of jitter e | Examples |
|---|---|---|---|---|
| **A — timing-independent** | Counts, proportions, choices, accuracy | None (if response windows are generous relative to C) | None | Commission rate, span, proportion larger-later choices |
| **B — offset-robust** | Within-subject differences of RT, dispersion in absolute units (SD, IQR), slopes over time, durations computed as the difference of two timestamps of the same kind | Cancels | Adds noise; inflates SD by approximately σ_e² in variance terms | Interference cost, ISD, event-rate RT effect, reproduced duration |
| **C — offset-sensitive** | Absolute RT location; ratios with absolute RT in the denominator; quantities defined by subtracting a scheduled interval from an RT; thresholds applied to absolute RT | Biased by C, and C differs between devices | Adds noise | Median RT, CV, SSRT, ex-Gaussian μ, anticipation flags |

Three consequences that the brief's wording ("variability measures are robust to constant offset") does not capture:

1. **The coefficient of variation is class C, not B.** CV = SD/mean; C inflates the mean and so deflates CV. Only absolute-unit dispersion (ISD, IQR) is offset-robust.
2. **Whether jitter matters for between-child comparisons depends mainly on whether jitter differs across devices.** Additive independent jitter inflates observed variance by σ_e². If σ_e is small relative to children's true RT SD, the inflation is small; if it differs between device models, it becomes a device confound in ISD. This must be measured in the timing-validation study, not assumed.
3. **Anticipation thresholds are offset-sensitive.** A 150 ms threshold applied to measured RT corresponds to a lower true RT on a device with large C, so software-timed anticipation flags are lenient on high-latency devices. Hardware timing removes this.

**Device heterogeneity rule (proposed):** class C metrics are only compared within a device model. Device model is logged for every session and treated as a covariate in all analyses.

---

## 4. Rules common to all tasks

| Rule | Specification |
|---|---|
| Feedback | None during scored trials; no score shown to the child at any point. Practice trials give informational visual feedback only (a static tick-shape or cross-shape for 500 ms; no sound, no animation). |
| Randomisation | A session seed (64-bit) is generated at session start and logged. Each task derives its sequence from (session seed, task ID). Spatial-span sequences are fixed per task version (§T6). |
| Sequence constraints | Stated per task; enforced by the generator; the generated sequence is logged in full before the task begins. |
| Anticipations | Any response with measured RT < 150 ms is recorded with outcome `anticipation`, excluded from RT metrics, counted separately, and never silently discarded. Responses before stimulus onset are recorded as `premature`. (Threshold is a proposed parameter; see §3 caveat 3.) |
| Response windows | Stated per task; responses after the window are recorded as `late` with their timestamp and treated as omissions in primary scoring. |
| Practice criterion | Each task states one. Up to 3 practice runs. If not met after 3 runs, the task is skipped, flagged `practice_criterion_not_met`, and the session continues. Children are never pushed through a task they have not understood. |
| Background | Solid neutral mid-grey, sRGB (128,128,128), identical for all tasks. |
| Fixation | Black "+" built from two bars, 24 dp, stroke 3 dp, at screen centre between trials. |
| Stimulus sizing | Specified in dp. Physical size depends on the device's true pixel density; `xdpi`/`ydpi` are logged so physical size in mm can be computed. Visual angle additionally depends on uncontrolled viewing distance. |
| Durations | Specified in ms; implemented as frame counts from the measured refresh period; displayed frame count logged; dropped frames flagged. |
| Orientation and layout | Landscape, locked. **Task screens must not auto-mirror in Urdu (RTL) locale** — on Android, layout mirroring would swap the left and right response pads. Task screens use fixed physical left/right. |
| Response pads | One-response tasks: one circular pad, 120 dp, bottom centre. Two-response tasks: two pads, 120 dp, bottom-left and bottom-right, operated with the two index fingers. A response is the first `ACTION_DOWN` inside a pad; other touches are logged as `off_target_touch`. |
| Breaks | Between tasks: minimum 30 s rest timer; within multi-block tasks: 10 s. |
| Child-facing names | Child-facing screens may call tasks "games" with simple names. Documentation never does. |

---

## 5. Task specifications

Each specification lists: construct · rationale · stimuli and trial structure · practice · scoring and metrics (with class from §3) · psychometric evidence · cultural/linguistic adaptation · administration time · build effort · known confounds.

### T1 — Simple Reaction Time with variable foreperiod

**Construct.** Speed and consistency of a simple detect-and-respond process. Not an executive-function measure. Included as (a) a motor/processing-speed covariate, (b) a source of RT variability with minimal cognitive load, and (c) the reference task for the timing-validation study.

**Stimuli and structure (proposed parameters).** Fixation, then a black filled circle (96 dp) at centre after a foreperiod drawn from {1000, 1500, 2000, 2500, 3000} ms (jittered to prevent rhythmic anticipation). Stimulus remains until response or 1500 ms. 500 ms blank, then next fixation. Single response pad. 30 scored trials, one block. Each foreperiod value appears 6 times.

**Practice.** 5 trials. Criterion: ≥ 4 valid responses (not premature, not anticipation, within window).

**Metrics.** Median RT (C); mean RT (C); ISD (B); CV (C); omissions (A); premature responses (A); ex-Gaussian μ (C), σ (B), τ (approximately B) — exploratory, since 30 trials is below what reliable ex-Gaussian fitting typically requires [VERIFY minimum].

**Evidence.** RT variability elevated in ADHD across task types (Kofler et al., 2013); simple-RT mean differences are small once variability is considered (same source). Child test–retest data for touchscreen simple RT: [VERIFY — not located during drafting].

**Adaptation.** None.

**Time.** ~2 min including practice. **Build effort.** Low (≈ ½ day once the engine exists).

**Confounds.** Touchscreen familiarity; hand posture (finger cannot rest on a touchscreen without triggering it, so movement time is included).

---

### T2 — Go/No-Go with event-rate manipulation

**Construct.** Inhibition of a prepotent motor response (commissions); sustained responding (omissions); energetic regulation (event-rate effects); RT variability.

**Rationale.** Go/No-Go is among the most widely used inhibition paradigms in ADHD research; commission errors showed the best test–retest reliability of the inhibition/interference metrics studied by Hedge et al. (2018, adult sample); manipulating event rate operationalises the cognitive-energetic account (Sergeant, 2005 [VERIFY]; Metin et al., 2012 [VERIFY]).

**Stimuli.** Go: black filled circle, 96 dp diameter. No-Go: black filled square, area-matched (≈ 85 dp side). Shape-only discrimination (no colour), so colour-vision deficiency is irrelevant.

**Structure (proposed parameters).**

| Block | Event rate | Stimulus duration | ISI (offset to next onset) | Trials | Go / No-Go |
|---|---|---|---|---|---|
| F1 | Fast | 300 ms | 1000 ms | 32 | 24 / 8 |
| S1 | Slow | 300 ms | 4000 ms | 32 | 24 / 8 |
| S2 | Slow | 300 ms | 4000 ms | 32 | 24 / 8 |
| F2 | Fast | 300 ms | 1000 ms | 32 | 24 / 8 |

Response window: 1000 ms from onset (extends past stimulus offset). Fixation shown during ISI. Go probability 0.75 in every block. Constraints: first 3 trials of each block are Go; no more than 2 consecutive No-Go. Fixed block order F1–S1–S2–F2.

**Why this design (task version 0.2, config 0.3).** Version 0.1 ran fast–slow–fast–slow with 48-trial fast and 24-trial slow blocks. That placed the slow blocks later on average, so steady drift (fatigue, practice) was confounded with event rate, and it gave fast and slow 24 and 12 No-Go trials respectively. The ABBA order (fast–slow–slow–fast) gives both rates the same mean block position, so a linear drift cancels in the slow − fast difference. Equal counts (64 trials and 16 No-Go per rate) give both commission rates the same precision. Slow blocks still take longer, because the gaps are longer, which is the manipulation itself.

**Late responses.** A response after the 1000 ms window counts as an omission, as before, but the latency of the first late response is now recorded (`late_rt_ms`). The window is judged in device time, so a slow device can push a slow response past it; the recorded latency lets an analysis re-apply the window after device correction. Whether the window itself should be longer is referred to the expert panel.

**Practice.** 12 trials at fast rate (9 Go / 3 No-Go). Criterion: ≥ 7 of 9 Go hits and ≥ 2 of 3 No-Go withheld.

**Metrics.**

| Metric | Formula | Class |
|---|---|---|
| Commission rate (per rate and overall) | responses on No-Go ÷ No-Go trials | A |
| Omission rate | Go trials without response in window ÷ Go trials | A |
| d′ | z(H) − z(FA), log-linear correction: H = (hits+0.5)/(Go+1), FA = (commissions+0.5)/(No-Go+1) (Hautus, 1995 [VERIFY]) | A |
| Median Go RT (correct) | median of hit RTs | C |
| ISD of Go RT | SD of hit RTs | B |
| CV | ISD / mean Go RT | C |
| Event-rate RT effect | median RT(slow) − median RT(fast) | B |
| Event-rate ISD effect | ISD(slow) − ISD(fast) | B |
| Event-rate error effects | rate(slow) − rate(fast), for omissions and commissions | A |
| Time-on-task | median RT(F2) − median RT(F1); omission(F2) − omission(F1) | B / A |
| Post-commission slowing (exploratory) | mean RT on Go trials after a commission − after a correct withhold | B |

**Evidence.** Commission-error ICC .76 (adult students; Hedge et al., 2018); child test–retest: [VERIFY]. Event-rate effects in ADHD: meta-analytic evidence that group differences vary with event rate (Metin et al., 2012 [VERIFY direction and magnitude]).

**Known weakness.** Only 12 No-Go trials per event rate: rate-specific commission scores take 13 possible values and will be coarse and unreliable. Increasing them costs time. This trade-off is put to the panel (§8).

**Adaptation.** None for stimuli. Instructions rely on demonstration.

**Time.** Scored ≈ 5.5 min (2 × 62 s + 2 × 103 s + pauses) + practice/instructions ≈ 1 min → **≈ 6.5 min**. **Build effort.** Moderate (1–2 days); it is the reference implementation for the engine.

**Confounds.** Speed–accuracy trade-off (commissions rise with speed; report RT alongside); boredom in slow blocks is the intended manipulation but also lowers motivation.

---

### T3 — Rare-target Continuous Performance Test (shape version)

**Construct.** Vigilance: detection of infrequent targets over time.

**Rationale.** CPTs are the most widely used attention measures in ADHD assessment. Distinct from T2 in that targets are rare, so omissions and vigilance decrement are the primary indices.

**Stimuli and structure (proposed).** Stream of black shapes (circle, square, triangle, diamond; 96 dp); target = triangle, p = 0.20. Stimulus 250 ms; ISI 1500 ms; response window 1250 ms. 4 blocks × 60 trials (12 targets each). No more than 2 consecutive targets.

**Practice.** 20 trials (4 targets). Criterion: ≥ 3 of 4 targets hit and ≤ 2 false alarms.

**Metrics.** Omission rate (A); false-alarm rate (A); d′ and criterion c (A); median hit RT (C); hit ISD (B); vigilance decrement = d′(block 4) − d′(block 1) (A) and hit-RT slope across blocks (B).

**Evidence.** Meta-analytic group differences on omission and commission errors (Huang-Pollock et al., 2012 [VERIFY]); stand-alone classification modest (Arrondo et al., 2024). Almost all evidence is from letter-based or proprietary versions; the shape version is not equivalent.

**Time.** ~9 min. **Not recommended for the core**: too long, largely redundant with T2, and vigilance decrement needs longer administration than the budget allows. **Build effort.** Low once T2 exists.

---

### T4 — Stop-Signal Task (visual stop signal)

**Construct.** Action cancellation: stopping a response that has already been initiated.

**Rationale.** SSRT is a theoretically specified latency of the stopping process under the independent race model (Logan & Cowan, 1984 [VERIFY]), with consensus guidance on design and scoring (Verbruggen et al., 2019).

**Stimuli.** Go: black arrowhead (chevron, 96 dp) pointing left or right; respond with the matching pad. Stop signal: the arrow turns vermillion **and** a thick black square frame (8 dp stroke) appears around it — a redundant cue so that colour vision is not required. The no-sound rule makes an auditory stop signal unavailable; visual stop signals are expected to yield different SSRTs from auditory ones [VERIFY], so published auditory values are not comparable.

**Structure (proposed).** Fixation 500 ms → arrow until response or 1500 ms → ITI 1000 ms. Stop trials p = 0.25. Single SSD staircase: start 250 ms, +1 step after successful stop, −1 step after failed stop, bounds 50–1150 ms. **Step = round(50 ms / measured frame period) frames**, so SSD is always an integer number of frames; the actually displayed SSD is logged. 4 blocks × 40 trials = 160 trials (40 stop trials). Constraint: no more than 2 consecutive stop trials; first 4 trials of each block are Go.

**Practice.** (a) 16 Go-only trials, criterion ≥ 14 correct direction; (b) 16 trials with 4 stop signals, criterion ≥ 1 successful stop and ≥ 10 of 12 Go correct. Instructions must state that waiting for the stop signal is not the goal; this concept is hard to convey without language and requires the administrator script.

**Metrics.**

| Metric | Formula | Class |
|---|---|---|
| SSRT (integration method with go-omission replacement) | nth Go RT − mean SSD, n = p(respond\|signal) × number of Go trials, omitted Go RTs replaced by the maximum Go RT (Verbruggen et al., 2019) | **C** — inflated by C because measured Go RT contains C while SSD does not |
| p(respond\|signal) | failed stops ÷ stop trials | A |
| Mean Go RT, Go ISD | — | C, B |
| Choice error rate, Go omission rate | — | A |
| Race-model check | mean signal-respond RT < mean Go RT | B |

**Pre-specified exclusion of SSRT:** p(respond|signal) outside .25–.75, or race-model check failed, or Go omissions > 25% [VERIFY thresholds against Verbruggen et al., 2019].

**Evidence.** Meta-analyses report moderately longer SSRT in ADHD, alongside slower and more variable Go RT (Lijffijt et al., 2005 [VERIFY]; Alderson et al., 2007 [VERIFY]). Consensus guidance recommends substantially more stop trials than 40 for reliable individual estimates [VERIFY exact recommendation]; this design is below it to fit time.

**Time.** ~8 min. **Not recommended for the core under software timing:** SSRT is the metric most damaged by device latency and frame quantisation, it is long, and inhibition is already covered by T2. **Recommended as the primary task of the hardware timing-validation phase.** **Build effort.** High (2–3 days).

---

### T5 — Flanker (arrow version)

**Construct.** Interference control: selecting a response to a target while ignoring conflicting adjacent stimuli (Eriksen & Eriksen, 1974 [VERIFY]).

**Rationale for inclusion despite weak ADHD evidence.** It yields a within-subject contrast that is robust to constant timing offset (class B), it is short and familiar to reviewers, and child versions are widely used. Its ADHD evidence is weak and its difference score is unreliable (§1.3); the panel may prefer to replace it (§8).

**Stimuli.** Row of five black arrowheads (each 48 dp, 8 dp gaps) at centre. Congruent: all point the same way. Incongruent: flankers opposite to the centre arrow. Target direction balanced.

**Structure (proposed).** Fixation 400–800 ms (uniform jitter) → stimulus until response or 2000 ms → ITI 500 ms. 2 blocks × 40 trials (20 congruent, 20 incongruent each). No more than 3 consecutive trials of the same condition. Previous-trial congruency logged for congruency-sequence analysis.

**Practice.** 12 trials. Criterion: ≥ 9 correct.

**Metrics.**

| Metric | Formula | Class |
|---|---|---|
| RT interference cost | median RT(incongruent, correct) − median RT(congruent, correct) | B |
| Accuracy interference | accuracy(congruent) − accuracy(incongruent) | A |
| Overall median RT, ISD | — | C, B |
| Inverse efficiency (exploratory) | mean correct RT ÷ proportion correct, per condition | C |

**Evidence.** Mullane et al. (2009): 12 studies, 272 children with ADHD, disadvantages on incongruent relative to congruent trials. Null findings exist for related interference tasks (van Mourik et al., 2009). Flanker cost ICC ≈ .4 in adult students (Hedge et al., 2018).

**Adaptation.** Arrow direction is a learned convention; demonstration before practice.

**Time.** ≈ 4 min. **Build effort.** Low (½–1 day).

---

### T6 — Spatial span, forward and backward (Corsi-type)

**Construct.** Forward: visuospatial short-term memory. Backward: visuospatial working memory (storage plus manipulation). **Construct caution:** backward span on a spatial array may be solved partly by reversing a remembered spatial path rather than manipulating a sequence; reviewers should rate whether "working memory" is a defensible label.

**Rationale.** Visuospatial working-memory deficits show among the larger group effects in ADHD meta-analyses (Martinussen et al., 2005 [VERIFY effect sizes]; Kasper, Alderson & Hudec, 2012 [VERIFY]), and working memory was the most frequently impaired domain in Kofler et al. (2019). Untimed accuracy scoring makes it class A.

**Stimuli.** Nine dark-grey squares (56 dp, sRGB (64,64,64)) at fixed irregular positions adapted from standard Corsi layouts [VERIFY coordinates source, e.g., Kessels et al., 2000], scaled to the landscape screen. Presentation: each square turns white for 700 ms, 300 ms between squares. After the sequence, a 500 ms pause, then a thick black frame appears around the board to signal "your turn" (language-free cue). Each tap briefly highlights the tapped square for 200 ms — this is **response registration, not performance feedback**, and is identical for correct and incorrect taps. The trial ends when the number of taps equals the sequence length.

**Structure (proposed).** Forward first, then backward. Start length 2; 2 trials per length; advance if ≥ 1 of 2 correct; discontinue when both trials at a length are incorrect; maximum length 9 (forward) and 8 (backward). Sequences are **fixed per task version** (generated once from a documented seed, identical for every child) so that difficulty is comparable across children; no square repeats within a sequence.

**Retest form.** A child's second session uses an alternate sequence set (config `retest`; task version suffix `-retest`). It contains no sequence from the standard form or practice. Each retest sequence has a path length on the board within 10% of its standard counterpart, which equates one known source of difficulty. Whether the two forms are truly equivalent must be checked in the pilot.

**Practice.** Forward: 2 trials at length 2, with administrator demonstration. Backward: demonstration of reversal, then 2 trials at length 2. Criterion for each: ≥ 1 correct.

**Metrics.** Span (longest length with ≥ 1 correct) (A); total correct trials (A); product score = span × total correct (Kessels et al., 2000 [VERIFY]) (A); backward − forward span (A); per-tap latencies (C, exploratory).

**Evidence.** Child normative data exist for paper administrations [VERIFY sources]; equivalence of touchscreen administration to the block-tapping original is not established and must not be assumed.

**Adaptation.** None for stimuli. Backward instructions are the hardest instruction in the battery for 8-year-olds and depend on demonstration.

**Time.** ≈ 4–6 min (adaptive). **Build effort.** Moderate (≈ 1 day).

---

### T7 — Spatial n-back (1-back and 2-back)

**Construct.** Working-memory updating and monitoring.

**Stimuli and structure (proposed).** 3 × 3 grid of outlined cells (centre cell holds fixation). A black square appears in one of the 8 outer cells for 500 ms; ISI 2000 ms; response window 2000 ms. Single "match" pad. Target probability 0.33. Block 1: 1-back, 30 + 1 trials. Block 2: 2-back, 30 + 2 trials.

**Practice.** 12 trials per load. Criterion: ≥ 70% correct responses overall.

**Metrics.** Hit rate, false-alarm rate, d′ per load (A); load cost = d′(1-back) − d′(2-back) (A); hit RT (C).

**Evidence.** Letter n-back versions dominate the ADHD literature; spatial versions are less studied [VERIFY]. 2-back may produce floor effects at age 8 [VERIFY].

**Time.** ≈ 6 min. **Not recommended for the core:** overlaps with T6, which has stronger child evidence and simpler instructions. **Build effort.** Low–moderate.

---

### T8 — Task switching (shape / colour)

**Construct.** Cognitive flexibility: reconfiguring task set when the relevant stimulus dimension changes.

**Stimuli.** One centred object varying in shape (circle / triangle, 96 dp) and colour (blue sRGB(0,114,178) / orange sRGB(230,159,0), chosen for distinguishability under common colour-vision deficiencies [VERIFY source: Okabe & Ito colour-universal-design palette]). These colours differ in luminance, so the colour task can be solved partly by brightness; acceptable but noted. Task cue shown 400 ms before and during the stimulus, above it: shape task = two small outline shapes; colour task = two small colour patches. Response pads display the mapping (left: circle + blue; right: triangle + orange), which lowers memory load.

**Structure (proposed).** Single-task shape (16), single-task colour (16), mixed (2 × 32; switch probability 0.5; no more than 3 consecutive repeats). Response window 3000 ms; ITI 600 ms.

**Practice.** 8 trials per single task, 12 mixed. Criterion: ≥ 75% correct in each.

**Metrics.** Switch cost RT = median RT(switch) − median RT(repeat), mixed blocks, correct trials (B); mixing cost = median RT(repeat, mixed) − median RT(single-task) (B); error switch cost (A); overall RT (C).

**Evidence.** Set-shifting group differences are smaller and less consistent than for working memory or inhibition (Willcutt et al., 2005); 38% of the ADHD sample were classified impaired in Kofler et al. (2019). Switch-cost reliability is limited by its difference-score form [VERIFY child data].

**Time.** ≈ 7 min. **Not recommended for the core:** long, instruction-heavy for younger children, weaker evidence, and colour-vision dependent. **Build effort.** Moderate–high.

---

### T9 — Time reproduction

**Construct.** Temporal processing: encoding a supra-second duration and reproducing it with a motor response.

**Rationale.** Temporal processing is the third dissociable component in the triple-pathway account (Sonuga-Barke et al., 2010); timing deficits in ADHD are frequently reported across paradigms (Noreika et al., 2013 [VERIFY]; Smith et al., 2002 [VERIFY]).

**Stimuli and structure (proposed).** A blue filled circle (96 dp) appears for target duration D; after offset and a 500 ms pause, an outlined response pad appears. The child presses and holds the pad for the same duration, then releases. While held, the pad changes to a static pressed state — no filling or progress display, which would give duration feedback. D ∈ {1000, 2000, 4000, 6000} ms, 4 repetitions each = 16 trials; no more than 2 consecutive trials with the same D. ITI 1000 ms.

**Practice.** 3 trials (2000, 4000, 1000 ms) with feedback **during practice only**: two horizontal bars showing target and reproduced length. Criterion: every practice reproduction between 0.3 × D and 3 × D (i.e., the procedure was understood, not that it was accurate).

**Metrics.**

| Metric | Formula | Class |
|---|---|---|
| Reproduction ratio per D | mean(R) / D | B* |
| Absolute error per D | mean(\|R − D\|) / D | B* |
| Reproduction precision per D | SD(R) / mean(R) | B* (R itself is offset-free, so this CV is not affected by C) |
| Vierordt slope | slope of R regressed on D | B* |
| Premature release | R < 200 ms, flagged | A |

\*R = release timestamp − press timestamp. Both are touch events, so C cancels **only if touch-down and touch-up latencies are equal**; this is untested and is a timing-validation question. Displayed duration is subject to ± 1 frame error at onset and offset.

**Confounds.** Counting strategies (cannot be prevented without verbal suppression; the administrator records "counted aloud: yes/no"); attention lapses during encoding; motor hold control.

**Evidence.** Child test–retest data for touchscreen reproduction: [VERIFY — not located].

**Time.** ≈ 3 min. **Build effort.** Low (½–1 day).

---

### T10 — Choice-delay task

**Status: EXPLORATORY.** Tokens are never exchanged for anything, so there is no real reward, and choices may not reflect delay aversion. Its measures are not primary outcomes in any planned analysis unless the expert panel and supervisor approve a real-reward version.

**Construct.** Delay-related choice: preference for a smaller immediate reward over a larger delayed one when choosing the latter extends waiting time.

**Rationale.** The motivational pathway of the dual/triple pathway models; dissociable from inhibitory deficits in preschool (Dalen et al., 2004) and school-age samples (Sonuga-Barke et al., 2010). Original paradigm: Sonuga-Barke et al. (1992) [VERIFY]. Meta-analytic support for choice impulsivity in ADHD: Patros et al. (2016) [VERIFY].

**Stimuli.** Two side-by-side panels per trial. Smaller-sooner (SS): one token; a short bar. Larger-later (LL): two tokens; a long bar. Reward size is shown as object count (not digits); delay is learned through forced-choice experience rather than shown numerically.

**Structure (proposed).** 4 forced-choice familiarisation trials (2 SS, 2 LL; only one panel active) so the child experiences both delays, then 12 free-choice trials. SS: 1 token after 2 s. LL: 2 tokens after 20 s. During the delay a static waiting display is shown (no animation). When the delay ends the token(s) appear for 1000 ms and are then cleared. **No post-reward delay**, so choosing SS shortens the task — the configuration in which delay aversion, rather than reward maximisation, predicts SS choices (Sonuga-Barke et al., 1992 [VERIFY]). Task duration therefore depends on the child's choices: ≈ 1.5 min if all SS, ≈ 5.5 min if all LL.

**Practice.** The 4 forced trials. Criterion: the child selects the active panel on each (understanding of the choice mechanic).

**Metrics.** Proportion of LL choices on free trials (A); first-half vs second-half LL proportion (A, exploratory); choice latency (C, exploratory).

**Conflict with the brief (unresolved — panel/author decision):** this task cannot measure delay-related choice without a reward outcome. Tokens are rewards, and their accumulation is effectively a score, which the brief prohibits. The specification above minimises this (tokens cleared after each trial, no tally, no exchange), but without tangible reward the choices may not reflect delay aversion at all, which weakens validity. Options: (a) accept the specification as a stated exception; (b) add a small non-contingent token of thanks at the end (does not restore validity); (c) use real contingent rewards (e.g., stickers), which strengthens validity but breaks the no-reward rule and needs ethics approval; (d) drop T10 and lose the motivational pathway.

**Further limitations.** 12 binary choices give 13 possible scores — a coarse measure. Hypothetical versus real rewards may behave differently in children [VERIFY].

**Adaptation.** Token imagery to be chosen with local input (neutral objects; avoid money connotation).

**Time.** 1.5–5.5 min. **Build effort.** Low–moderate.

---

## 6. Excluded candidates

| Task | Reason for exclusion |
|---|---|
| Colour–word Stroop | Requires reading. Non-verbal Stroop variants exist but are less established in ADHD research. |
| Digit span; letter n-back; letter CPT | Digits/letters as stimuli (brief requirement). Spatial/shape alternatives are included instead. |
| Wisconsin Card Sorting Test | Requires trial-by-trial correctness feedback by design (conflicts with the no-feedback rule); long; ambiguous rule discovery is a language-dependent instruction problem. |
| Tower of London/Hanoi | Drag-and-drop motor demands on small phone screens; long; planning effects modest. |
| Iowa Gambling Task | Monetary/numeric stimuli; long; weak child psychometrics. |
| Attention Network Test (child version) | ≈ 15–20 min; network scores are difference scores with poor reliability [VERIFY]. |

---

## 7. Proposed core battery

| Order | Task | Dimensions | Est. time (incl. practice & instructions) |
|---|---|---|---|
| 1 | T2 Go/No-Go (event rate) | Inhibition, sustained attention, energetic regulation, RT variability | ≈ 6.5 min |
| — | Break | | ≥ 30 s |
| 2 | T6 Spatial span (F + B) | Visuospatial short-term and working memory | ≈ 4–6 min |
| — | Break | | ≥ 30 s |
| 3 | T5 Flanker | Interference control; offset-robust RT contrast | ≈ 4 min |
| — | Break | | ≥ 30 s |
| 4 | T9 Time reproduction | Temporal processing | ≈ 3 min |
| — | Break | | ≥ 30 s |
| 5 | T10 Choice-delay | Delay-related choice | ≈ 1.5–5.5 min |
| | **Total** | | **≈ 19–25 min task time + ≈ 2 min breaks** (estimate; exceeds the 20 min target in the worst case) |

**Rationale.** One task per major pathway in §1.1, chosen by (i) strength of ADHD evidence, (ii) metric class under software timing (no core metric depends solely on class C), and (iii) time and build cost.

**Order rationale.** Go/No-Go first, because sustained attention is most fatigue-sensitive and the task sets the response-pad routine. Spatial span second, changing modality to an untimed task. Choice-delay last, because (a) exposure to rewards may change motivation in later tasks, and (b) its variable duration should not shift the timing of other tasks.

**Fixed order — limitation (stated, not mitigated).** At planned sample sizes, counterbalancing would split small samples into order groups too small to analyse. A fixed order makes every child's experience identical, which suits between-child comparison, but confounds task with position (fatigue, practice, motivation). Task-level comparisons between positions cannot separate the two.

**What the core does not measure.** Action cancellation (T4), set shifting (T8), long-duration vigilance (T3). The hardware phase is the appropriate place for T4.

**Time budget risk.** The estimate exceeds 20 minutes if a child chooses mostly larger-later rewards and has a long span. If piloting confirms the overrun, the first candidate cut is T5 (weakest ADHD evidence).

---

## 8. Questions for the expert panel

These will appear in the rating materials as free-text prompts alongside the per-task ratings. The rating form (PLANNED) will also record **"Build reviewed: Android app / browser preview / both"** for every panellist, because the two builds share task logic but not timing.

1. Should T5 (Flanker) be in the core, given its weak ADHD evidence, or should it be replaced (e.g., by T4 once hardware timing exists, or by T8)?
2. Is the reward handling in T10 acceptable, and which of options (a)–(d) in §T10 do you recommend?
3. Are 12 No-Go trials per event rate in T2 acceptable, or should the event-rate manipulation be dropped in favour of a single-rate task with more No-Go trials?
4. Is backward spatial span a defensible working-memory measure for 8–9-year-olds in this format?
5. Are durations of 1–6 s appropriate for T9 in ages 8–14, and how should counting strategies be handled?
6. Is skipping a task after 3 failed practice runs the right policy, or should the administrator be allowed to re-instruct?
7. Is a single battery appropriate across ages 8–14, or should parameters differ by age band?
8. Are any stimuli culturally inappropriate or ambiguous for Pakistani children?

---

## 10. Implementation notes for the core battery (decisions made while building; for panel review)

These choices were made during implementation. None changes a parameter in §5 unless stated; all are recorded in `config/tasks.json` and need expert review.

**All core tasks**
- Parameters live in `config/tasks.json`, and every session records the file's version and SHA-256.
- Expert Review variants are shortened (T2: one fast + one slow block of 16/8 trials; T5: 16 trials; T6: maximum length 5 forward / 4 backward; T9: one trial per duration; T10: 2 forced + 4 free trials). They carry version suffix `-review`.

**T6 Spatial span**
- *Layout.* The 9-square board is an adapted irregular layout, **not the original Corsi coordinates** [VERIFY against Kessels et al., 2000, if equivalence to the standard is wanted].
- *Added parameters.* Recall timeout 30 s (counts as an omission and as a failed trial for the discontinue rule); inter-trial interval 1 s.
- *Practice sequences* never duplicate a scored sequence. The generator originally produced a backward practice sequence identical to the first scored one, a 1-in-72 coincidence; the rule now prevents it.
- *Interruptions.* An interrupted trial is logged as INTERRUPTED and **the same sequence is re-administered** so the adaptive procedure is unchanged. The child may have seen part of it already, so the re-administered trial is flagged `readministered_after_interruption` in the data.
- *Order of parts.* Forward and backward run as two parts with separate instructions and practice. If forward practice fails, backward is not attempted.
- *Scoring.* Span is 0 if no sequence is ever recalled correctly.

**T9 Time reproduction**
- *Added parameters.* Press timeout 10 s (omission); maximum hold 20 s (outcome INCORRECT, flag `held_too_long`); premature-release threshold 200 ms (outcome ANTICIPATION).
- *Touch handling.* Only the release of the finger that pressed counts. A system-cancelled gesture is not treated as a release.
- *Interruptions.* Interrupted trials are not repeated.

**T10 Choice-delay**
- *Added parameters.* 500 ms fixation before options; choice timeout 30 s (omission). Which side shows the smaller-sooner option is balanced (exactly half the trials per phase, rounded down for odd counts) and seeded.
- *Responses.* Taps on the inactive option in forced trials are ignored and counted as off-target. Taps during the wait are counted (`wait_taps`, exploratory).
- *Interruptions.* An interruption during the wait ends the trial without reward; the task continues with the next trial.
- *Reward conflict.* Implemented as option (a) of §5 T10 pending the panel.

## 9. References

Verification status as defined at the top. Every entry needs full-text reading before quotation.

- Alderson, R. M., Rapport, M. D., & Kofler, M. J. (2007). Attention-deficit/hyperactivity disorder and behavioral inhibition: A meta-analytic review of the stop-signal paradigm. *Journal of Abnormal Child Psychology, 35*, 745–758. [VERIFY]
- Arrondo, G., Mulraney, M., Iturmendi-Sabater, I., et al. (2024). Systematic review and meta-analysis: Clinical utility of continuous performance tests for the identification of attention-deficit/hyperactivity disorder. *Journal of the American Academy of Child & Adolescent Psychiatry, 63*(2), 154–171.
- Castellanos, F. X., & Tannock, R. (2002). Neuroscience of attention-deficit/hyperactivity disorder: The search for endophenotypes. *Nature Reviews Neuroscience, 3*, 617–628. [VERIFY]
- Dalen, L., Sonuga-Barke, E. J. S., Hall, M., & Remington, B. (2004). Inhibitory deficits, delay aversion and preschool AD/HD: Implications for the dual pathway model. *Neural Plasticity, 11*(1–2), 1–11.
- Eriksen, B. A., & Eriksen, C. W. (1974). Effects of noise letters upon the identification of a target letter in a nonsearch task. *Perception & Psychophysics, 16*, 143–149. [VERIFY]
- Hautus, M. J. (1995). Corrections for extreme proportions and their biasing effects on estimated values of d′. *Behavior Research Methods, Instruments, & Computers, 27*, 46–51. [VERIFY]
- Hedge, C., Powell, G., & Sumner, P. (2018). The reliability paradox: Why robust cognitive tasks do not produce reliable individual differences. *Behavior Research Methods, 50*, 1166–1186.
- Huang-Pollock, C. L., Karalunas, S. L., Tam, H., & Moore, A. N. (2012). Evaluating vigilance deficits in ADHD: A meta-analysis of CPT performance. *Journal of Abnormal Psychology, 121*, 360–371. [VERIFY]
- Kasper, L. J., Alderson, R. M., & Hudec, K. L. (2012). Moderators of working memory deficits in children with ADHD: A meta-analytic review. *Clinical Psychology Review, 32*, 605–617. [VERIFY]
- Kessels, R. P. C., van Zandvoort, M. J. E., Postma, A., Kappelle, L. J., & de Haan, E. H. F. (2000). The Corsi block-tapping task: Standardization and normative data. *Applied Neuropsychology, 7*, 252–258. [VERIFY]
- Kofler, M. J., Irwin, L. N., Soto, E. F., Groves, N. B., Harmon, S. L., & Sarver, D. E. (2019). Executive functioning heterogeneity in pediatric ADHD. *Journal of Abnormal Child Psychology, 47*, 273–286. [VERIFY year, volume, pages — a 2018 online version exists]
- Kofler, M. J., Raiker, J. S., Sarver, D. E., Wells, E. L., & Soto, E. F. (2016). Is hyperactivity ubiquitous in ADHD or dependent on environmental demands? Evidence from meta-analysis. *Clinical Psychology Review, 46*, 12–24. [VERIFY volume/pages]
- Kofler, M. J., Rapport, M. D., Sarver, D. E., Raiker, J. S., Orban, S. A., Friedman, L. M., & Kolomeyer, E. G. (2013). Reaction time variability in ADHD: A meta-analytic review of 319 studies. *Clinical Psychology Review, 33*, 795–811.
- Lijffijt, M., Kenemans, J. L., Verbaten, M. N., & van Engeland, H. (2005). A meta-analytic review of stopping performance in attention-deficit/hyperactivity disorder: Deficient inhibitory motor control? *Journal of Abnormal Psychology, 114*, 216–222. [VERIFY]
- Logan, G. D., & Cowan, W. B. (1984). On the ability to inhibit thought and action: A theory of an act of control. *Psychological Review, 91*, 295–327. [VERIFY]
- Martinussen, R., Hayden, J., Hogg-Johnson, S., & Tannock, R. (2005). A meta-analysis of working memory impairments in children with attention-deficit/hyperactivity disorder. *Journal of the American Academy of Child & Adolescent Psychiatry, 44*, 377–384. [VERIFY]
- Metin, B., Roeyers, H., Wiersema, J. R., van der Meere, J., & Sonuga-Barke, E. (2012). A meta-analytic study of event rate effects on Go/No-Go performance in attention-deficit/hyperactivity disorder. *Biological Psychiatry, 72*, 990–996. [VERIFY]
- Mullane, J. C., Corkum, P. V., Klein, R. M., & McLaughlin, E. (2009). Interference control in children with and without ADHD: A systematic review of Flanker and Simon task performance. *Child Neuropsychology, 15*, 321–342. [VERIFY volume/pages]
- Nigg, J. T., Willcutt, E. G., Doyle, A. E., & Sonuga-Barke, E. J. S. (2005). Causal heterogeneity in attention-deficit/hyperactivity disorder: Do we need neuropsychologically impaired subtypes? *Biological Psychiatry, 57*(11), 1224–1230.
- Noreika, V., Falter, C. M., & Rubia, K. (2013). Timing deficits in attention-deficit/hyperactivity disorder (ADHD): Evidence from neurocognitive and neuroimaging studies. *Neuropsychologia, 51*, 235–266. [VERIFY]
- Paap, K. R., et al. (2020). Interference scores have inadequate concurrent and convergent validity: Should we stop using the flanker, Simon, and spatial Stroop tasks? *Cognitive Research: Principles and Implications.* [VERIFY authors, volume]
- Patros, C. H. G., Alderson, R. M., Kasper, L. J., Tarle, S. J., Lea, S. E., & Hudec, K. L. (2016). Choice-impulsivity in children and adolescents with ADHD: A meta-analytic review. *Clinical Psychology Review, 43*, 162–174. [VERIFY]
- Pronk, T., Wiers, R. W., Molenkamp, B., & Murre, J. (2020). Mental chronometry in the pocket? Timing accuracy of web applications on touchscreen and keyboard devices. *Behavior Research Methods, 52*, 1371–1382.
- Sergeant, J. A. (2005). Modeling attention-deficit/hyperactivity disorder: A critical appraisal of the cognitive-energetic model. *Biological Psychiatry, 57*, 1248–1255. [VERIFY]
- Smith, A., Taylor, E., Rogers, J. W., Newman, S., & Rubia, K. (2002). Evidence for a pure time perception deficit in children with ADHD. *Journal of Child Psychology and Psychiatry, 43*, 529–542. [VERIFY]
- Sonuga-Barke, E. J. S. (2002). Psychological heterogeneity in AD/HD — a dual pathway model of behaviour and cognition. *Behavioural Brain Research, 130*, 29–36. [VERIFY]
- Sonuga-Barke, E. J. S., Taylor, E., Sembi, S., & Smith, J. (1992). Hyperactivity and delay aversion — I. The effect of delay on choice. *Journal of Child Psychology and Psychiatry, 33*, 387–398. [VERIFY]
- Sonuga-Barke, E., Bitsakou, P., & Thompson, M. (2010). Beyond the dual pathway model: Evidence for the dissociation of timing, inhibitory, and delay-related impairments in attention-deficit/hyperactivity disorder. *Journal of the American Academy of Child & Adolescent Psychiatry, 49*(4), 345–355.
- Soto, E. F., Black, K., & Kofler, M. J. (2024). Is hyperactivity in children with ADHD a functional response to demands on specific executive functions or cognitive demands in general? *Neuropsychology, 38*(8), 699–713.
- Soto, E. F., Kofler, M. J., et al. (2020). Executive functioning rating scales: Ecologically valid or construct invalid? *Neuropsychology.* [VERIFY authors, volume, pages]
- Toplak, M. E., West, R. F., & Stanovich, K. E. (2013). Practitioner review: Do performance-based measures and ratings of executive function assess the same construct? *Journal of Child Psychology and Psychiatry, 54*(2), 131–143.
- van Mourik, R., Papanikolau, A., van Gellicum-Bijlhout, J., van Oostenbruggen, J., Veugelers, D., Post-Uiterweer, A., Sergeant, J. A., & Oosterlaan, J. (2009). Interference control in children with attention deficit/hyperactivity disorder. *Journal of Abnormal Child Psychology, 37*(2), 293–303. [VERIFY pages and full author list]
- Verbruggen, F., Aron, A. R., Band, G. P. H., et al. (2019). A consensus guide to capturing the ability to inhibit actions and impulsive behaviors in the stop-signal task. *eLife, 8*, e46323. [VERIFY]
- Willcutt, E. G., Doyle, A. E., Nigg, J. T., Faraone, S. V., & Pennington, B. F. (2005). Validity of the executive function theory of attention-deficit/hyperactivity disorder: A meta-analytic review. *Biological Psychiatry, 57*(11), 1336–1346.
