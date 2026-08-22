"""Reports this suite's raw Swing arm against the JDK's own SwingMark, from a run of `ab/run.sh`.

The raw arm is the original's six tests translated into Kotlin, so the two drive the same widgets through
the same changes. Two things say whether that still holds.

Paint counts, which are the stronger evidence: both suites count by subclassing the widget under test, so
equal work paints an equal number of times. The original counts from the moment its window opens and this
suite from the start of each timed run, so the original reads one or two higher; anything beyond that is
work one of them does and the other does not.

Times, read through the ratio's interval: an interval covering 1.0 means the run did not separate the two,
which is what a faithful translation looks like. The estimator is the median over every warm run - run 1 of
each VM is the VM's own warm-up and is dropped - and the interval is bootstrapped from the two samples
independently. This suite runs its declared arm in the same VM, so its raw arm meets a warmer JIT and a
busier heap than the original does; read a separated interval as a reason to look, not as a verdict.

Drift is the original's own median in the first round against its median in the last. It says whether the
machine held still enough for the rounds to be pooled: a few percent either way is expected, a large
one-directional move means the ratios are reading the machine.
"""

import glob
import os
import random
import re
import statistics as st
import sys

RESAMPLES = 20_000
ORIGINAL_LINE = re.compile(r"^(.+?) = (\d+)\s+\(Paint = (\d+)\)")
# This suite prints figures the original does not, so the paint count is read up to whichever of the
# comma and the closing bracket follows it.
RAW_ARM_LINE = re.compile(r"^raw: (.+?) = (\d+)\s+\(Paint = (\d+)[,)]")
RUN_LINE = re.compile(r"\*\*\*\* Starting run (\d+)")
ORDER = ["Sliders", "Lists", "TextArea", "Table Rows", "Tree", "Sub-Menus"]

# One line of each suite's output, copied as that suite prints it, and the fields a pattern has to read
# out of it. A format that has moved on is caught here rather than as a report with no runs in it.
SAMPLE_FIELDS = ("Table Rows", "1234", "45")
SAMPLES = [
    (ORIGINAL_LINE, "Table Rows = 1234   (Paint = 45)"),
    (RAW_ARM_LINE, "raw: Table Rows = 1234   (Paint = 45, Dirty = 678900, Layout = 12)"),
]


def check_patterns():
    """Exits where a suite no longer prints what the pattern reading it expects."""
    for pattern, sample in SAMPLES:
        matched = pattern.match(sample)
        if not matched or matched.groups() != SAMPLE_FIELDS:
            sys.exit(f"{pattern.pattern} no longer reads {sample!r}: the suite's output has changed")


def load(raw, kind, line):
    """Every warm (round, time, paint) the suite printed, by test."""
    samples = {}
    for path in sorted(glob.glob(os.path.join(raw, f"{kind}-*.txt"))):
        round_index = int(re.search(r"-(\d+)\.txt$", path).group(1))
        run = 1
        for text in open(path):
            starting = RUN_LINE.search(text)
            if starting:
                run = int(starting.group(1))
                continue
            timed = line.match(text.strip())
            if timed and run >= 2:
                test, millis, paints = timed.group(1), int(timed.group(2)), int(timed.group(3))
                samples.setdefault(test, []).append((round_index, millis, paints))
    return samples


def interval(original, port):
    """A 95% interval for port/original, resampling each suite's runs independently."""
    ratios = sorted(
        st.median(random.choices(port, k=len(port))) / st.median(random.choices(original, k=len(original)))
        for _ in range(RESAMPLES)
    )
    return ratios[int(0.025 * RESAMPLES)], ratios[int(0.975 * RESAMPLES)]


def main(raw):
    check_patterns()
    original = load(raw, "orig", ORIGINAL_LINE)
    port = load(raw, "port", RAW_ARM_LINE)
    if not original or not port:
        sys.exit(f"no runs found under {raw}")
    random.seed(7)

    print(f"{'test':<12}{'orig ms':>9}{'raw ms':>9}{'ratio':>8}{'95% CI':>16}"
          f"{'o paint':>9}{'raw paint':>11}{'drift':>8}")
    print("-" * 82)
    for test in [t for t in ORDER if t in original] + [t for t in original if t not in ORDER]:
        o = [ms for _, ms, _ in original[test]]
        p = [ms for _, ms, _ in port[test]]
        rounds = [r for r, _, _ in original[test]]
        first = st.median([ms for r, ms, _ in original[test] if r == min(rounds)])
        last = st.median([ms for r, ms, _ in original[test] if r == max(rounds)])
        low, high = interval(o, p)
        print(f"{test:<12}{st.median(o):9.0f}{st.median(p):9.0f}"
              f"{st.median(p) / st.median(o):8.2f}   [{low:.2f}, {high:.2f}]".ljust(56)
              + f"{st.median([c for _, _, c in original[test]]):9.0f}"
              + f"{st.median([c for _, _, c in port[test]]):11.0f}"
              + f"{last / first:8.2f}")
    print(f"\n{len(next(iter(original.values())))} warm runs per suite. "
          f"Paint counts more than a couple apart mean the translation has drifted.")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "build", "ab", "raw"))
