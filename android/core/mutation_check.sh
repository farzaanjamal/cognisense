#!/usr/bin/env bash
# Mutation check: plants six deliberate defects in the core, one at a time, and confirms that the core
# test suite (run_tests.sh) fails for each. It shows the tests can catch these specific mistakes;
# it does not show they catch every possible mistake.
#
# Each defect is an exact one-line text replacement. If the expected original line is not found
# (the code has changed), the script stops with an error rather than silently skipping a defect.
# The original file is always restored, also on error or Ctrl-C.
#
# Requires: JDK 17+, kotlinc 2.0.21, python3.
# Usage: KOTLINC=/path/to/kotlinc ./mutation_check.sh
# Exit code 0 = baseline passes and every defect is detected.
set -uo pipefail
cd "$(dirname "$0")"
SRC=src/main/kotlin/org/cognisense/core
ENGINE=$SRC/engine/TrialEngine.kt
SPAN=$SRC/tasks/SpatialSpanTask.kt
TRP=$SRC/tasks/TimeReproductionTask.kt
CDT=$SRC/tasks/ChoiceDelayTask.kt

# name | file | original text | defective text
DEFECTS=(
  "1. Engine: stimulus duration rounded up a frame (half-frame rounding removed)|$ENGINE|ft >= onset + ms(p.stimulusMs) - halfFrame|ft >= onset + ms(p.stimulusMs)"
  "2. Engine: responses classified by arrival order, not timestamp|$ENGINE|val sorted = c.responses.sortedBy { it.eventTimeNanos }|val sorted = c.responses.toList()"
  "3. Engine: pause gap after an interruption counted as dropped frames|$ENGINE|            lastFrame = null // the pause gap is not dropped frames|            // (defect) pause gap not cleared"
  "4. Spatial span: discontinue rule removed (never stops after two failures)|$SPAN|if (correctAtLength == 0 \|\| length >= c.maxLength) done = true|if (length >= c.maxLength) done = true"
  "5. Time reproduction: any finger's release ends the hold|$TRP|if (p != null && release == null && event.pointerId == p.pointerId && event.eventTimeNanos >= p.eventTimeNanos) release = event|if (p != null && release == null && event.eventTimeNanos >= p.eventTimeNanos) release = event"
  "6. Choice-delay: taps on the inactive option accepted|$CDT|if (option(event.key).active) { choice = event|if (true \|\| option(event.key).active) { choice = event"
)

BACKUP="$(mktemp -d)"
restore() { for f in "$ENGINE" "$SPAN" "$TRP" "$CDT"; do cp "$BACKUP/$(basename "$f")" "$f"; done; }
for f in "$ENGINE" "$SPAN" "$TRP" "$CDT"; do cp "$f" "$BACKUP/"; done
trap 'restore; rm -rf "$BACKUP"' EXIT INT TERM

# Runs the core tests. Prints PASS, FAIL (tests ran and failed) or NOCOMPILE.
run_suite() {
  local out
  out="$(./run_tests.sh 2>&1)"; local code=$?
  if ! grep -q '^passed: ' <<<"$out"; then echo NOCOMPILE; echo "$out" | grep -E 'error:' | head -5 >&2
  elif [ $code -eq 0 ]; then echo PASS
  else echo FAIL; fi
}

echo "Baseline (no defect):"
base="$(run_suite)"
echo "  $base"
[ "$base" = PASS ] || { echo "The unmodified core tests must pass first." >&2; exit 2; }

detected=0; total=${#DEFECTS[@]}; problems=0
for d in "${DEFECTS[@]}"; do
  # Split on unescaped "|" (a literal "|" in code is written "\|").
  IFS=$'\t' read -r name file orig repl < <(python3 -c '
import re,sys
parts=[p.replace("\\|","|") for p in re.split(r"(?<!\\)\|", sys.argv[1])]
print("\t".join(parts))' "$d")
  restore
  python3 - "$file" "$orig" "$repl" <<'PY' || { echo "$name: original line not found; update this script" >&2; problems=$((problems+1)); continue; }
import sys
path, orig, repl = sys.argv[1:]
s = open(path, encoding="utf-8").read()
if s.count(orig) != 1:
    sys.exit(1)
open(path, "w", encoding="utf-8").write(s.replace(orig, repl))
PY
  result="$(run_suite)"
  case "$result" in
    FAIL) echo "DETECTED      $name"; detected=$((detected+1));;
    PASS) echo "NOT DETECTED  $name";;
    *)    echo "DID NOT BUILD $name" >&2; problems=$((problems+1));;
  esac
done
restore

echo "Detected $detected of $total planted defects."
[ $problems -eq 0 ] && [ $detected -eq $total ]
