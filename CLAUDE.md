# Cognisense: instructions for Claude Code

Read this first in every session. It carries over the context of the claude.ai conversation in which the project was built (October 2026).

## The project

Cognisense is **Farzaan Jamal's** project: an open-source, offline research prototype that runs five standard cognitive tasks (Go/No-Go, spatial span, Flanker, time reproduction, choice-delay) in a browser and on low-cost Android phones, for research with children aged 8–14 in Pakistan. Every measure is labelled by how much device timing error can distort it:

- **class A:** timing-independent;
- **class B:** robust to a constant delay;
- **class C:** affected by delay.

Farzaan did the substantial intellectual work: the idea, the research question, the logic of how the system works (what to measure, why device timing matters, how each measure is treated), the task choices and every design decision. Claude implements and drafts **at his direction**; keep the "Authorship and contributions" wording in the README and paper consistent with this. He is not yet an experienced programmer, so explain code changes plainly when he asks.

## Rules that must not be broken

1. **Never claim diagnosis, screening, validation or accuracy.** This disclaimer must appear verbatim wherever results or the project are presented:
   > Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.
2. **No invented numbers or citations.** Only report figures that were measured or computed. Unchecked references are marked [VERIFY] until confirmed against the source.
3. **No children are tested** without an approved ethics application and a supervising psychologist. Never write code or text that implies otherwise.
4. **Disclose AI assistance accurately.** The README section "Authorship and contributions" states the agreed wording; keep it.
5. **Never commit secrets:** tokens, signing keys or passwords. `/data/` stays git-ignored, and no participant data is ever committed. Example sessions live in `examples/`.
6. **Config discipline** (`config/README.md`): any task-value change bumps that task's `version`; any change at all bumps `config_version` and needs a CHANGELOG entry.
7. **Task stage integrity** (web demo): nothing on the task stage is animated by CSS, stimuli appear in a single frame, and no feedback, sound or timer appears during scored trials.
8. **Protocol 1 is fixed** (`docs/protocol1_expert_review.md`). Do not change `analysis/cvi.py` decision bands. Any deviation is recorded in the protocol's "Deviations" section with a date and reason.

## Current state (7 October 2026)

- **Software 0.6.0-dev, task config 0.3.** All 220 core checks, 13 + 7 analysis tests and the browser end-to-end tests pass. The Android app builds in GitHub Actions but **has never run on a phone**.
- **GitHub (`farzaanjamal/cognisense`) holds an older version:** 0.5.0-dev plus the cloud-build fix. The 0.6.0 changes (balanced Go/No-Go, late-response latencies, spatial-span retest form, choice-delay exploratory, Protocol 1, decision bands, this file) were delivered as `cognisense.zip` and **still need to be committed and pushed**.
- **GitHub Pages** must be enabled once: Settings → Pages → Source: GitHub Actions, then run the "pages" workflow.
- **Documents kept on claude.ai** (export them into the repository when possible: the paper into `paper/`, the others into `docs/expert-review/`):
  - the research paper (preprint v0.5; 8 [VERIFY] markers left);
  - the expert-review task booklet, rating form, and information and consent sheet;
  - the one-page summary.

## Commands

```bash
# Core task logic: 220 checks (needs JDK 17+ and the Kotlin 2.0.21 compiler)
cd android/core && KOTLINC=/path/to/kotlinc/bin/kotlinc ./run_tests.sh
# Analysis (Python 3; numpy 2.4.4 for the simulation)
cd analysis && python -m unittest test_cvi.py test_timing_simulation.py
# Web demo: rebuild after changing anything in browser/, config/ or the core,
# then check that the committed file matches the sources
python3 browser/build.py --kotlin-home /path/to/kotlinc && python3 browser/build.py --check-current
# Browser end-to-end tests (each run under 5 min; 'guided' runs the full demo, about 10 min)
cd browser/test && npm install --no-save puppeteer-core@23 && CHROME_PATH=/path/to/chrome node preview_test.js T2
```

The cloud build (`.github/workflows/build.yml`) runs all of these on every push.

## Open tasks, in priority order

1. Commit and push the 0.6.0 changes; confirm the cloud build is green; enable Pages.
2. Phone smoke test (`android/README.md`): Farzaan installs the debug APK from the Actions artifacts. Fix whatever it finds.
3. Expert review per Protocol 1: fill in the placeholders ([your email address], [date], professor names), then send. Enter returned ratings into the CSV format `analysis/cvi.py` reads.
4. Resolve the remaining [VERIFY] markers in the paper.
5. Timing study with slow-motion video (no ethics approval needed; only phones are measured).
6. Find a supervising academic; then Protocol 2 (child pilot) and the ethics application.

## Tools in this workspace

- **Playwright MCP:** drive the web demo in a real browser to check changes visually. The committed end-to-end tests use puppeteer (`browser/test/preview_test.js`); keep them working.
- **Context7:** look up current library documentation (Kotlin, Android, Gradle) before changing build or API code.
- **graphify:** builds a knowledge graph of the project into `graphify-out/` (git-ignored). If `graphify claude install` adds its own rules to this file, keep every Cognisense rule above intact.
- **ECC (Everything Claude Code):** if installed, its rules and hooks must not override the rules in this file. This file wins.

## Where things are

`README.md` (overview and status) · `CHANGELOG.md` (history) · `docs/task_specifications.md` · `docs/measurement_precision.md` · `docs/architecture.md` · `docs/data_schema.md` · `docs/protocol1_expert_review.md` · `analysis/results/` (simulation outputs in the paper) · `config/tasks.json` (every parameter).
