# Protocol 1: expert content review

**Status:** fixed on 7 October 2026, **before any invitation was sent or any rating collected**. The repository commit that adds this file timestamps it. Any later change is listed under "Deviations" in section 10, with its reason.

**Investigator:** Farzaan Jamal. **Supervisor:** [name and institution, once confirmed].

> Cognisense is a research prototype that records performance on computerised cognitive tasks; it does not diagnose, detect or identify ADHD or any other condition, has not been validated for screening, and its output must not be used to make decisions about any individual child.

## 1. Question

Do experts judge each of the six core task parts to be relevant to the skill it claims to measure, clear to children aged 8–14, suitable for children in Pakistan, and feasible in a school setting?

| Task ID | Task part | Claimed skill | Task version |
|---|---|---|---|
| T2 | Go/No-Go | Stopping a habitual response; sustained attention | GNG 0.2 |
| T6F | Spatial span, forwards | Visuospatial short-term memory | SPF 0.2 |
| T6B | Spatial span, backwards | Visuospatial working memory | SPB 0.2 |
| T5 | Flanker | Interference control | FLK 0.1 |
| T9 | Time reproduction | Reproducing durations of 1–6 s | TRP 0.1 |
| T10 | Choice-delay (exploratory) | Preferring smaller-sooner over larger-later rewards | CDT 0.1 |

Materials: task configuration v0.3 (SHA-256 `f1a78d3fd146a38214e4e4554806075dd364eb5299af508a6235f257ca4a6615`) and the web demo built from it.

## 2. Panel

- **Size:** at least 6 completed rating forms (the minimum for the I-CVI criterion below). Invite 10–12 people to allow for non-response.
- **Eligible:** clinical, educational or developmental psychologists; child psychiatrists or paediatricians; teachers of children aged 8–14 with at least 5 years' experience; researchers who use cognitive tests with children.
- **Target mix:** at least 3 psychologists or physicians, and at least 2 teachers.
- **Engineers** complete a separate technical review form. Their comments inform the design, but they are not part of the content validity indices.
- **Consent:** each panellist returns the information and consent sheet. Anyone who does not is excluded.

## 3. Materials (frozen for round 1)

1. The web demo at its current version (the configuration hash is shown in its "For researchers" section).
2. The task booklet.
3. The rating form.
4. The information and consent sheet.
5. The invitation email.

Each panellist records which build they tried: web demo, Android app, both, or descriptions only.

## 4. Ratings

Each task part is rated on four scales from 1 to 4: relevance, clarity, cultural fit and feasibility (definitions on the rating form). Ratings are entered into a CSV file in the format `analysis/cvi.py` reads: one row per panellist, task part and scale, under pseudonymous panellist IDs (E01, E02, …). Names are kept only on the consent sheets.

## 5. Decision rules

Computed by `analysis/cvi.py` (SHA-256 `db46691983873cf6afb2465c1e4dfa94199b45c8979b9c9025f7f6674acf8e1a`), whose `DECISION_BANDS` hold exactly these rules.

- **I-CVI** for each task part and scale = proportion of panellists rating 3 or 4 (Lynn, 1986). It is reported with the modified kappa (Polit, Beck & Owen, 2007).
- **Rounding:** I-CVI is rounded to two decimals before applying any threshold. This follows published tables, which report 7 of 9 (.7778) as .78.

| Relevance I-CVI | Decision |
|---|---|
| Meets the Lynn criterion: ≥ .78 with 6 or more panellists; 1.00 with 5 or fewer | Retain |
| .70–.77 | Revise and re-rate in round 2 |
| Below .70 | Remove from the core battery |

- **Clarity, cultural fit and feasibility** use the same thresholds, labelled *acceptable*, *revise* and *major revision*. **Only relevance can remove a task;** the other scales trigger revision.
- **Scale level:** S-CVI/Ave is reported against the .90 benchmark (Polit & Beck, 2006), as a description, not a decision.
- **Choice-delay** stays exploratory whatever its ratings, unless a real-reward version is approved by the supervisor.

## 6. Round 2

- **Who:** every task part with any "revise" or "major revision" result is revised, using the panellists' comments.
- **How:** it is re-rated by the same panel, under the same rules.
- **Limit:** there is no round 3. A part still below the criterion after round 2 is removed or kept as exploratory, and the paper gives the reason.

## 7. Open design questions

The rating form's design questions (for example the Go/No-Go response window and the choice-delay reward) are summarised qualitatively. They are not scored. Decisions on them are made by the investigator with the supervisor, and reported with the panel's views.

## 8. Missing data and timing

- **Missing ratings:** excluded item by item; N is reported for every item.
- **Deadline:** three weeks from invitation, with one reminder after two weeks.
- **Fewer than 6 forms by the deadline:** the deadline is extended once, by two weeks. If there are still fewer than 6, the indices are reported descriptively and no retain/revise/remove decisions are made.

## 9. Reporting

Every task part and scale is reported (I-CVI, modified kappa, N), including those that fail. Panel composition is reported only in summary. Comments are quoted only with permission and without names unless the panellist agreed.

## 10. Deviations

None so far. Any change after the first invitation is listed here with its date and reason.

## References

- Lynn, M. R. (1986). Determination and quantification of content validity. *Nursing Research, 35*(6), 382–385.
- Polit, D. F., & Beck, C. T. (2006). The content validity index: Are you sure you know what's being reported? Critique and recommendations. *Research in Nursing & Health, 29*(5), 489–497.
- Polit, D. F., Beck, C. T., & Owen, S. V. (2007). Is the CVI an acceptable indicator of content validity? Appraisal and recommendations. *Research in Nursing & Health, 30*(4), 459–467.
