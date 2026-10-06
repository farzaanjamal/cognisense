#!/usr/bin/env python3
"""
cvi.py — Content-validity indices for the Cognisense expert panel.

Status: IMPLEMENTED (tested against worked values from Polit, Beck & Owen, 2007).
Written before any ratings were collected, so that the analysis is fixed in advance.

WHAT IT COMPUTES
----------------
For every (round, dimension, task):
  N        number of experts who gave a rating (missing ratings are excluded)
  A        number of experts rating 3 or 4 ("relevant" / "acceptable")
  I-CVI    A / N                                   (Lynn, 1986)
  Pc       probability of chance agreement = C(N, A) * 0.5**N
  kappa*   modified kappa = (I-CVI - Pc) / (1 - Pc) (Polit, Beck & Owen, 2007)
  label    kappa* > .74 excellent; .60-.74 good; .40-.59 fair; < .40 poor
           (evaluation criteria as cited by Polit, Beck & Owen, 2007)
  meets_lynn   I-CVI = 1.00 when N <= 5; I-CVI >= .78 when N >= 6 (Lynn, 1986)

For every (round, dimension), across tasks:
  S-CVI/Ave  mean of the I-CVIs                    (Polit & Beck, 2006)
  S-CVI/UA   proportion of tasks with I-CVI = 1.00 (universal agreement)
  Benchmark: S-CVI/Ave >= .90 (Polit & Beck, 2006). Reported, not enforced.

The four rated dimensions follow the rating form: relevance, clarity,
cultural, feasibility (all 1-4). The CVI is conventionally defined for
relevance; applying the same arithmetic to the other dimensions is a
reasonable descriptive extension, but say so when reporting.

Revision rules (retain / revise / remove bands) are decided in Protocol 1,
not here. Set them in DECISION_BANDS below BEFORE data arrive, and record the
commit hash of this file in the protocol.

INPUT FORMAT (CSV, UTF-8, one row per rating; lines starting with # ignored)
-------------------------------------------------------------------------
round,panelist_id,task_id,dimension,rating
1,E01,T2,relevance,4
1,E01,T2,clarity,3
...
  round        integer Delphi round (1 or 2)
  panelist_id  pseudonymous expert ID (no names in this file)
  task_id      e.g. T1..T10
  dimension    one of DIMENSIONS
  rating       integer 1-4, or empty for "no rating"

USAGE
-----
  python cvi.py ratings.csv --out results/
Outputs results/item_level.csv, results/scale_level.csv, results/report.md.
Standard library only; Python 3.8+.

REFERENCES [VERIFY all before quoting]
  Lynn, M. R. (1986). Determination and quantification of content validity.
    Nursing Research, 35(6), 382-385.
  Polit, D. F., & Beck, C. T. (2006). The content validity index: Are you sure
    you know what's being reported? Research in Nursing & Health, 29, 489-497.
  Polit, D. F., Beck, C. T., & Owen, S. V. (2007). Is the CVI an acceptable
    indicator of content validity? Research in Nursing & Health, 30, 459-467.
"""

from __future__ import annotations

import argparse
import csv
import math
import os
import sys
from collections import defaultdict
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Tuple

DIMENSIONS = ("relevance", "clarity", "cultural", "feasibility")
RELEVANT_MIN = 3          # ratings >= 3 count as agreement
LYNN_SMALL_PANEL_MAX = 5  # N <= 5 requires I-CVI = 1.00
LYNN_ICVI_MIN = 0.78      # N >= 6 requires I-CVI >= .78
SCVI_AVE_BENCHMARK = 0.90

# Decision bands are a Protocol 1 decision. Leave as None until the protocol
# fixes them; the script then reports indices without a retain/revise call.
# Example of the form expected: [(0.78, "retain"), (0.70, "revise"), (0.0, "remove")]
DECISION_BANDS: Optional[List[Tuple[float, str]]] = None


class RatingError(ValueError):
    """Raised for malformed or duplicated ratings."""


@dataclass(frozen=True)
class ItemResult:
    round: int
    dimension: str
    task_id: str
    n: int
    agree: int
    i_cvi: float
    pc: float
    kappa: float
    kappa_label: str
    meets_lynn: bool
    decision: str


def chance_agreement(n: int, a: int) -> float:
    """Pc = C(n, a) * 0.5**n (Polit, Beck & Owen, 2007)."""
    if n <= 0:
        raise ValueError("n must be positive")
    if not 0 <= a <= n:
        raise ValueError("a must be between 0 and n")
    return math.comb(n, a) * (0.5 ** n)


def modified_kappa(i_cvi: float, pc: float) -> float:
    """kappa* = (I-CVI - Pc) / (1 - Pc)."""
    if pc >= 1.0:
        raise ValueError("Pc must be < 1")
    return (i_cvi - pc) / (1.0 - pc)


def kappa_label(k: float) -> str:
    if k > 0.74:
        return "excellent"
    if k >= 0.60:
        return "good"
    if k >= 0.40:
        return "fair"
    return "poor"


def meets_lynn(n: int, i_cvi: float) -> bool:
    """Lynn (1986) criterion as summarised by Polit & Beck (2006) [VERIFY].

    DECISION (record in Protocol 1): the .78 criterion is applied to I-CVI
    rounded to two decimals, because published tables report 7/9 = .7778 as
    ".78" and treat it as meeting the criterion. Without rounding, 7 of 9
    experts would fail.
    """
    if n <= LYNN_SMALL_PANEL_MAX:
        return math.isclose(i_cvi, 1.0)
    return round(i_cvi, 2) >= LYNN_ICVI_MIN


def decide(i_cvi: float) -> str:
    if DECISION_BANDS is None:
        return "not_set"
    for threshold, decision in sorted(DECISION_BANDS, reverse=True):
        if i_cvi >= threshold - 1e-12:
            return decision
    return DECISION_BANDS[-1][1]


def item_result(round_: int, dimension: str, task_id: str,
                ratings: Iterable[int]) -> ItemResult:
    ratings = list(ratings)
    n = len(ratings)
    if n == 0:
        raise RatingError(f"No ratings for round {round_}, {dimension}, {task_id}")
    a = sum(1 for r in ratings if r >= RELEVANT_MIN)
    i_cvi = a / n
    pc = chance_agreement(n, a)
    k = modified_kappa(i_cvi, pc)
    return ItemResult(round_, dimension, task_id, n, a, i_cvi, pc, k,
                      kappa_label(k), meets_lynn(n, i_cvi), decide(i_cvi))


def read_ratings(path: str) -> Dict[Tuple[int, str, str], Dict[str, Optional[int]]]:
    """Return {(round, dimension, task_id): {panelist_id: rating or None}}."""
    data: Dict[Tuple[int, str, str], Dict[str, Optional[int]]] = defaultdict(dict)
    with open(path, newline="", encoding="utf-8") as f:
        lines = (line for line in f if not line.lstrip().startswith("#"))
        reader = csv.DictReader(lines)
        required = {"round", "panelist_id", "task_id", "dimension", "rating"}
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise RatingError(f"Missing columns: {sorted(missing)}")
        for line_no, row in enumerate(reader, start=2):
            try:
                round_ = int(row["round"])
            except ValueError:
                raise RatingError(f"Row {line_no}: round must be an integer")
            dim = row["dimension"].strip().lower()
            if dim not in DIMENSIONS:
                raise RatingError(f"Row {line_no}: unknown dimension '{dim}'")
            task = row["task_id"].strip()
            panelist = row["panelist_id"].strip()
            if not task or not panelist:
                raise RatingError(f"Row {line_no}: empty task_id or panelist_id")
            raw = row["rating"].strip()
            rating: Optional[int]
            if raw == "":
                rating = None
            else:
                try:
                    rating = int(raw)
                except ValueError:
                    raise RatingError(f"Row {line_no}: rating '{raw}' is not an integer")
                if rating not in (1, 2, 3, 4):
                    raise RatingError(f"Row {line_no}: rating {rating} outside 1-4")
            key = (round_, dim, task)
            if panelist in data[key]:
                raise RatingError(
                    f"Row {line_no}: duplicate rating by {panelist} for {key}")
            data[key][panelist] = rating
    return data


def compute(data) -> Tuple[List[ItemResult], List[dict]]:
    items: List[ItemResult] = []
    for (round_, dim, task), by_panelist in sorted(data.items()):
        given = [r for r in by_panelist.values() if r is not None]
        if not given:
            continue
        items.append(item_result(round_, dim, task, given))

    grouped: Dict[Tuple[int, str], List[ItemResult]] = defaultdict(list)
    for it in items:
        grouped[(it.round, it.dimension)].append(it)

    scales: List[dict] = []
    for (round_, dim), its in sorted(grouped.items()):
        icvis = [it.i_cvi for it in its]
        s_ave = sum(icvis) / len(icvis)
        s_ua = sum(1 for v in icvis if math.isclose(v, 1.0)) / len(icvis)
        panelists = set()
        for (r, d, _t), by_p in data.items():
            if r == round_ and d == dim:
                panelists.update(p for p, v in by_p.items() if v is not None)
        scales.append({
            "round": round_,
            "dimension": dim,
            "n_tasks": len(its),
            "n_panelists": len(panelists),
            "s_cvi_ave": round(s_ave, 4),
            "s_cvi_ua": round(s_ua, 4),
            "meets_s_cvi_ave_benchmark": s_ave >= SCVI_AVE_BENCHMARK - 1e-12,
            "tasks_meeting_lynn": sum(1 for it in its if it.meets_lynn),
        })
    return items, scales


def write_outputs(items: List[ItemResult], scales: List[dict], out_dir: str) -> None:
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "item_level.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["round", "dimension", "task_id", "n", "agree", "i_cvi", "pc",
                    "modified_kappa", "kappa_label", "meets_lynn", "decision"])
        for it in items:
            w.writerow([it.round, it.dimension, it.task_id, it.n, it.agree,
                        f"{it.i_cvi:.4f}", f"{it.pc:.4f}", f"{it.kappa:.4f}",
                        it.kappa_label, it.meets_lynn, it.decision])
    with open(os.path.join(out_dir, "scale_level.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(scales[0].keys()) if scales else ["round"])
        w.writeheader()
        for s in scales:
            w.writerow(s)
    with open(os.path.join(out_dir, "report.md"), "w", encoding="utf-8") as f:
        f.write("# Content-validity results\n\n")
        f.write("Generated by analysis/cvi.py. Ratings of 3-4 count as agreement. ")
        f.write("Missing ratings are excluded, so N can differ between tasks.\n\n")
        for s in scales:
            f.write(f"## Round {s['round']} — {s['dimension']}\n\n")
            f.write(f"S-CVI/Ave = {s['s_cvi_ave']:.2f} "
                    f"(benchmark >= {SCVI_AVE_BENCHMARK:.2f}: "
                    f"{'met' if s['meets_s_cvi_ave_benchmark'] else 'not met'}); "
                    f"S-CVI/UA = {s['s_cvi_ua']:.2f}; "
                    f"panelists = {s['n_panelists']}; tasks = {s['n_tasks']}\n\n")
            f.write("| Task | N | Agree | I-CVI | Pc | kappa* | Label | Meets Lynn | Decision |\n")
            f.write("|---|---|---|---|---|---|---|---|---|\n")
            for it in items:
                if it.round == s["round"] and it.dimension == s["dimension"]:
                    f.write(f"| {it.task_id} | {it.n} | {it.agree} | {it.i_cvi:.2f} | "
                            f"{it.pc:.3f} | {it.kappa:.2f} | {it.kappa_label} | "
                            f"{'yes' if it.meets_lynn else 'no'} | {it.decision} |\n")
            f.write("\n")


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("ratings", help="ratings CSV (see module docstring)")
    parser.add_argument("--out", default="cvi_results", help="output directory")
    args = parser.parse_args(argv)
    try:
        data = read_ratings(args.ratings)
        items, scales = compute(data)
    except RatingError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1
    if not items:
        print("ERROR: no ratings found", file=sys.stderr)
        return 1
    write_outputs(items, scales, args.out)
    print(f"Wrote {len(items)} item results and {len(scales)} scale results to {args.out}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
