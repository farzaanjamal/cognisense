# Data Schema (v0.2)

STATUS: the fields and export formats below are IMPLEMENTED in `android/core/src/main/kotlin/org/cognisense/core/data/SessionData.kt` and exercised by the core tests. On-device collection of the device fields and SQLite storage are DESIGNED.

## Conventions

| Convention | Rule |
|---|---|
| Timestamps | Integer nanoseconds on CLOCK_MONOTONIC (device uptime base). Not wall-clock time; comparable only within a session. |
| Durations and RT | Milliseconds, 3 decimal places in CSV |
| Missing values | Empty field in CSV, `null` in JSON. Never 0, never imputed. |
| Session seed | 64-bit signed integer; written as a **string** in JSON (JavaScript loses precision above 2^53) |
| Identity | Participant ID `CS-XXXXXX`, generated, from a vowel-free alphabet. No names, dates of birth, schools or locations anywhere in the data. |
| Character set | UTF-8; CSV per RFC 4180 with CRLF line endings |

## Session record

| Field | Type | Description |
|---|---|---|
| schema_version | string | "0.1" |
| participant_id | string | Generated pseudonymous ID |
| session_id | string | Random UUID |
| mode | string | `EXPERT_REVIEW` or `SESSION` |
| started_at_utc | string | ISO-8601 UTC start time (the only wall-clock field) |
| device_manufacturer, device_model | string | `Build.MANUFACTURER`, `Build.MODEL` |
| os_version, sdk_int | string, int | Android release and API level |
| screen_width_px, screen_height_px | int | Physical pixels, landscape |
| xdpi, ydpi | float | Reported pixel density, used to compute physical stimulus size |
| nominal_refresh_hz | float | Display mode refresh rate |
| measured_refresh_hz | float | Median of frame intervals during the session |
| app_version, git_commit | string | Embedded at build |
| task_config_version, task_config_sha256 | string | `config/tasks.json` version and SHA-256 of the exact file bytes |
| session_seed | int64 | Source of every trial sequence |
| brightness | float 0–1 | Window brightness fixed at session start |
| ui_language | string | `ur` or `en` |
| onset_marker_enabled | bool | Whether the photodiode marker was drawn |
| primary_timing_source | string | Source of scored RTs (`software` in the demo) |
| timing_sources | list | id, version, status, clock of each registered source |
| motion_source | object | id, status, placement |
| clock_check_ns | int64 | Uptime clock minus nanoTime clock at start; expected ≈ 0 |
| do_not_disturb_active | bool | System interruption filter state at start |
| checklist | map | Administrator pre-session checklist answers (fixed keys, yes/no or numeric values; never free text) |

Touch timestamp resolution is not stored separately; it follows from `sdk_int`: nanoseconds when `sdk_int` ≥ 34, milliseconds otherwise.

## On-device storage (app, SQLite, app-private)

| Table | Contents |
|---|---|
| sessions | One row per session; full metadata as JSON; status `in_progress`, `completed` or `abandoned` |
| task_runs | Every phase run, including practice attempts and skipped tasks, with result, measured refresh rate, dropped frames, interruptions and unassigned touches |
| trials | Every trial record, stored losslessly as JSON with indexed key columns |
| metrics | Scored metrics with unit, timing class and n |

## Trial record (CSV columns, in order)

| Column | Description |
|---|---|
| schema_version, participant_id, session_id, device_model, app_version, git_commit | Repeated so each row is self-describing |
| task_id, task_version | e.g. `GNG`, `0.1` |
| phase | `PRACTICE` or `SCORED` |
| attempt | Practice attempt (1–3); 1 for scored phases |
| trial_seq | Session-unique trial number; key for matching hardware results |
| trial_index, block, block_condition, condition | From the plan |
| stimulus | Asset identifier |
| expected_response | `SINGLE`, `LEFT`, `RIGHT` or `WITHHOLD` |
| planned_foreperiod_ms, planned_stimulus_ms, response_window_ms | Planned values (stimulus empty = response-terminated) |
| trial_start_ns, onset_ns, offset_ns, end_ns | Frame times |
| measured_stimulus_ms, stimulus_frames | Displayed duration and frame count |
| response_ns, response_key, touch_x, touch_y | First in-window response |
| rt_ms | Software-timed RT |
| outcome | CORRECT, COMMISSION, OMISSION, INCORRECT, ANTICIPATION, INTERRUPTED |
| premature_responses, extra_responses, late_responses, off_target_touches | Counts; nothing discarded |
| late_rt_ms | Software-timed latency (ms from onset) of the first response after the response window; empty if none. Added in 0.6.0 (config 0.3) so the window can be re-applied after device correction |
| dropped_frames | Missed vsyncs during the trial |
| interrupted | bool |
| timing_sources | Sources with a measurement for this trial |
| hw_rt_us | Hardware RT in µs (empty in the demo) |
| details | JSON object of task-specific fields (below); `{}` for Go/No-Go and Flanker |

### Task-specific `details` keys and outcome meanings

| Task | Keys | Outcome meaning |
|---|---|---|
| SPF / SPB (spatial span) | length, slot, sequence, expected, taps, tap_latencies_ms, first_tap_latency_ms, readministered_after_interruption | CORRECT = exact recall; INCORRECT = wrong order or item; OMISSION = recall timeout. `rt_ms` is empty (latencies are in details, measured from the recall cue). `offset_ns` = end of presentation. |
| TRP (time reproduction) | target_ms, displayed_ms, press_latency_ms, reproduced_ms, ratio, flag | CORRECT = completed; ANTICIPATION = premature release; INCORRECT = held too long; OMISSION = no press. `rt_ms` is empty. |
| CDT (choice-delay) | kind, ss_side, choice, delay_ms, tokens, wait_taps | CORRECT = a choice was made; OMISSION = timeout. `rt_ms` = choice latency from option onset. |

## JSON export

```json
{
  "session": { "...all session fields..." },
  "tasks": {
    "GNG": {
      "metrics": [
        {"name": "isd_go_rt", "value": "<number or null>", "unit": "ms", "timing_class": "B", "n": "<trials used>", "note": ""}
      ],
      "trial_count": "<trials logged>"
    }
  }
}
```

Every metric carries its timing class (A/B/C; see `task_specifications.md` §3), so downstream analysis can restrict cross-device comparisons to robust metrics.

## Not in the schema, deliberately

Name, date of birth, school, class, address, phone number, photographs, audio, free-text notes about the child. Age band and sex, if needed for Protocol 2, will be added only after ethics approval and only as coarse categories.

## Browser preview downloads (not instrument data)

The browser preview lets a reviewer download their own session as JSON. Format:

- Top-level fields: `browser_preview: true`, the "not a measurement" statement, the disclaimer, `user_agent`, timestamps, `config_version`, `config_sha256`, `strings_version`, `session_seed`, and `runs`.
- Each run holds: `part`, `version` (always `-review`), `phase`, `attempt`, `measured_hz`, `practice_criterion_met`, `dropped_frames`, `interruptions`, `records` and `metrics`.
- Records use the field `rt_ms_not_a_measurement`.

These files are review material only and must never be merged with app data (see `measurement_precision.md` §9).

## Retest sessions (0.6.0)

A session's checklist records `session_form`: `first` or `retest`. In a retest session, spatial-span parts run their retest variant (task version suffix `-retest`: alternate sequences matched for path length); every other task is identical to a first session. Analyses of test–retest reliability should pair a child's `first` and `retest` sessions.
