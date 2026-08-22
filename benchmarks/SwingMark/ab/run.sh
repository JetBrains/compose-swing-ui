#!/bin/bash
#
# Checks this suite's raw Swing arm against the JDK's own SwingMark, which is how the translation is
# shown to be faithful: the raw arm is the original's tests in Kotlin, so the two must paint the same
# number of times and take times a ratio's interval cannot separate.
#
# Both are launched from the same java binary, alternating VM by VM, so that machine load - which is never
# absent - falls on both equally. A ratio taken that way survives load; an absolute timing does not.
#
#   JBR=<jdk-checkout> ab/run.sh [runs-per-vm] [rounds]
#
# Environment:
#   JBR     JDK or JetBrainsRuntime checkout holding test/jdk/performance/client/SwingMark  (required)
#   JAVA    java binary to run both suites with                          (default: `which java`)
#   WORK    scratch directory for the original's build and the raw logs  (default: build/ab)
set -euo pipefail

RUNS=${1:-4}
ROUNDS=${2:-5}
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${WORK:-$HERE/../build/ab}
JAVA=${JAVA:-$(command -v java)}
JAVA_BIN_DIR=$(dirname "$JAVA")

if [ -z "${JBR:-}" ]; then
    echo "set JBR to a JDK checkout holding test/jdk/performance/client/SwingMark" >&2
    exit 2
fi
ORIGINAL_SRC="$JBR/test/jdk/performance/client/SwingMark"
[ -d "$ORIGINAL_SRC/src" ] || { echo "no SwingMark sources under $ORIGINAL_SRC" >&2; exit 2; }

PORT_LIB="$HERE/../build/install/SwingMark/lib"
[ -d "$PORT_LIB" ] || { echo "run :benchmarks:SwingMark:installDist first" >&2; exit 2; }

# The original is built from a copy, so the checkout it came from is never written to.
mkdir -p "$WORK"
if [ ! -f "$WORK/original/dist/SwingMark.jar" ] || \
   [ "$ORIGINAL_SRC/src" -nt "$WORK/original/dist/SwingMark.jar" ]; then
    echo "building the original from $ORIGINAL_SRC"
    rm -rf "$WORK/original"
    mkdir -p "$WORK/original"
    cp -R "$ORIGINAL_SRC/src" "$ORIGINAL_SRC/Makefile" "$WORK/original/"
    if ! (cd "$WORK/original" && PATH="$JAVA_BIN_DIR:$PATH" make all) > "$WORK/build.log" 2>&1; then
        cat "$WORK/build.log" >&2
        exit 1
    fi
fi

RAW="$WORK/raw"
rm -rf "$RAW"; mkdir -p "$RAW"
echo "$("$JAVA" -version 2>&1 | head -1), $RUNS runs per VM, $ROUNDS interleaved rounds"

for round in $(seq 1 "$ROUNDS"); do
    (cd "$WORK/original/dist" && "$JAVA" -Xmx1g -jar SwingMark.jar -q -r "$RUNS") \
        > "$RAW/orig-$round.txt" 2>&1
    "$JAVA" -Xmx1g -cp "$PORT_LIB/*" org.jetbrains.compose.swing.swingmark.SwingMarkKt -q -r "$RUNS" \
        > "$RAW/port-$round.txt" 2>&1
    echo "round $round of $ROUNDS"
done

python3 "$HERE/report.py" "$RAW"
