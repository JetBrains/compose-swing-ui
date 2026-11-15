# Module benchmarks:benchmark

Times each change of the fixtures of the library modules with JMH.

## Usage

```bash
./gradlew :benchmarks:benchmark:run
```

See [`docs/PERFORMANCE.md`](../../docs/PERFORMANCE.md#timing) for running one fixture, the measured phases, and the profilers.

## Related

- [`docs/PERFORMANCE.md`](../../docs/PERFORMANCE.md) - the fixtures and the suites that measure them.
- [`benchmarks/benchmark-utils/README.md`](../benchmark-utils/README.md) - the shared harness.
