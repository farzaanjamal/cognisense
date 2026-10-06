# config/ — the single source of truth for task parameters

`tasks.json` holds every task parameter:

- stimuli, sizes, colours and layouts;
- durations, intervals, trial counts and block structure;
- practice criteria and sequence constraints;
- the fixed spatial-span sequences;
- the shared timing values (anticipation threshold, breaks, rest, practice attempts).

The Android app packages this file unchanged as an asset and builds every task from it; none of these parameters are hard-coded in Kotlin. The browser preview (PLANNED) will read the same file.

For the implemented tasks, **this file is authoritative**. `docs/task_specifications.md` explains and justifies the values; if the two ever disagree, this file is what ran, and the document must be corrected.

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

## Rules

1. **Any change to a task's values requires bumping that task's `version`.** Expert Review variants get `-review` appended automatically, so review data can never be mistaken for standard data.
2. **Any change at all requires bumping `config_version`** and a CHANGELOG entry.
3. **Every session records `config_version` and the SHA-256 of the exact file bytes.** If someone edits the file without bumping versions, the hash still identifies exactly which parameters produced a data set. The dashboard flags sessions whose hash differs from the app's current config.
4. **The spatial-span sequences are generated, never hand-edited.** They come from `SpanSequences.generate` / `generatePractice` (seed 20261002). Practice sequences are drawn so that they never duplicate a scored sequence. `CoreTests` fails if the file no longer matches the generator. To change them, change the generator inputs, regenerate, and bump the versions.
5. **Validation is strict and happens at load.** Missing keys, wrong types, inconsistent counts (e.g. a No-Go fraction that does not give whole trials), unknown task kinds, and practice sequences that repeat scored ones all stop the app at start-up with the JSON path of the problem. A session never starts on a bad config.
6. **No comments in the file** (JSON has none). Explanations go in `note` fields, which the loader ignores, or in the docs.

## Every value is a proposed parameter

None of these values has been piloted or expert-reviewed. They are the starting point the expert panel will rate (Protocol 1, PLANNED).
