# Measuring performance

The suites below measure what Compose Swing UI costs. Both run the composable fixtures of the library modules. A fixture pairs a public composable with the raw Swing component it must match.

| Suite | Question | Command |
| --- | --- | --- |
| Memory | How many bytes does a widget keep alive, and how many does a change allocate? | `./gradlew :benchmarks:benchmark-utils:memoryTest` |
| Timing | How long does each phase take? | `./gradlew :benchmarks:benchmark:run` |

## Fixtures

Each suite builds a fixture at 1, 100 and 1000 instances. A change switches only the first instance between the fixture's initial values and one of its alternatives, so a cost that grows with the number of unchanged siblings shows up as a difference between the sizes. A change is named after the properties that differ between the alternative and the initial values, with a `#2` suffix on a repeated name, and the fixture is named after its class without `Fixture`.

The memory suite measures these steps of every fixture, on the composed tree and on the raw Swing tree:

- `noChange` changes nothing, so it shows the baseline of the measurement.
- `firstComposition` builds all instances.
- Each alternative changes the first instance.
- `dispose` removes all instances.

A fixture whose raw Swing is a window has no raw Swing tree to compare. A fixture that needs a display is skipped in a headless JVM.

## Memory

Per fixture, the memory suite reports the bytes that stay reachable per widget, and the difference from raw Swing. Per step and size, it reports the bytes the event dispatch thread allocates on both trees. Results appear in Gradle's test report, with a histogram of every class whose retained bytes grew, largest first. The bytes per widget are the slope between two sizes, so a change that adds a few bytes per widget can move the figure by tens of bytes at some sizes.

A fixture fails when, after its content is disposed, the live instances of a class grow by at least one per added widget between the sizes, beyond the growth in raw Swing.

Pass `-PbenchmarkFixtures=Label,Padding` to measure only the named fixtures.

## Timing

The timing suite times each change of every fixture with JMH, in Compose and in raw Swing, at 1, 100 and 1000 widgets. A `fixtureChange` parameter reads `fixture/alternative/arm`, as in `Label/text/rawSwing`. A fixture that declares no alternatives has no timing results. A fixture whose raw Swing side is a window, or needs a toolkit a headless run lacks, has only the Compose arm. A fixture whose composed side opens a window has no timing results.

```bash
# Build the JMH jar
./gradlew :benchmarks:benchmark:mainBenchmarkJar

# Run all fixtures and alternatives
./gradlew :benchmarks:benchmark:run

# One fixture and alternative, with the JMH allocation profiler
./gradlew :benchmarks:benchmark:run -PjmhArgs='.*toggleDraw -p fixtureChange=Label/text/compose,Label/text/rawSwing -p widgets=1 -f 1 -wi 1 -i 1 -prof gc'
```

- `firstCompose`, `firstLayout` and `firstDraw` measure fresh content up to the named phase.
- `toggleRecompose`, `toggleLayout` and `toggleDraw` change the first subject once per invocation, on either arm, and measure the work up to that phase. A draw paints the whole host into an image.
- `-PjmhArgs` passes options to JMH.

JMH writes the results to `benchmarks/benchmark/build/results/jmh.json`. Time depends on machine load, so compare runs from one quiet machine.
