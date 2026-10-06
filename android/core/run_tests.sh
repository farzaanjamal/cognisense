#!/usr/bin/env bash
# Compiles the pure-Kotlin core and runs its tests without Gradle or Android.
# Requires: JDK 17+ and kotlinc (https://github.com/JetBrains/kotlin/releases).
# Usage: KOTLINC=/path/to/kotlinc ./run_tests.sh
set -euo pipefail
cd "$(dirname "$0")"
KOTLINC="${KOTLINC:-kotlinc}"
OUT="$(mktemp -d)"
"$KOTLINC" src/main/kotlin src/test/kotlin -include-runtime -d "$OUT/core-tests.jar" 2>&1 | grep -v "^warning" || true
java -cp "$OUT/core-tests.jar" org.cognisense.core.CoreTestsKt
