---
name: verify-code-quality
description: Run lint, static analysis, ABI validation and coverage gates for compose-swing-ui. Use when checking code quality, fixing style issues, or before committing.
---

# Verify Code Quality

## Order of work

1. Run the scoped checks for what changed (below), fixing findings as they surface.
2. `./gradlew :buildSrc:ktlintFormat ktlintFormat` to auto-fix formatting, then check again.
3. `./gradlew updateKotlinAbi` if the public API changed, and review the dump diff.
4. Run the full gate once before committing:

```bash
./gradlew :buildSrc:ktlintCheck :buildSrc:detekt
./gradlew build --continue
```

Two commands, because `buildSrc` is an included build the root-level tasks do not reach. `build` runs
every module's `check` — compile, ktlint, detekt, the Compose lint checks, tests, `checkKotlinAbi` and
the coverage floors — plus `assemble`. `--continue` reports every failure in one run instead of
stopping at the first.

## Scoping a run

Work with the narrowest tasks that cover the change; the full gate is what settles it before a commit.
Do not add `clean` or `--rerun-tasks`: Gradle's up-to-date checks are correct here, and discarding them
turns a targeted check into a full rebuild.

```bash
./gradlew :swing-ui:ktlintCheck :swing-ui:detektMain :swing-ui:detektTest :swing-ui:lint
./gradlew :swing-ui:checkKotlinAbi
./gradlew :swing-ui:jacocoTestCoverageVerification
```

`jacocoTestCoverageVerification` runs the module's tests to produce the report it measures.

**Within a module, scope detekt as `detektMain`/`detektTest`, never `detekt`.** `buildSrc` is the
exception: it configures detekt itself, and plain `:buildSrc:detekt` is its gate. `CONTRIBUTING.md`
explains what the plain task misses everywhere else.

## What applies where

| Module               | ktlint / detekt / lint                     | ABI dump |
|----------------------|---------------------------------------------|----------|
| `buildSrc`           | ktlint and detekt only, invoked explicitly | no       |
| `swing-ui`           | yes                                        | yes      |
| `swing-ui-test`      | yes                                        | yes      |
| `swing-ui-animation` | **no**                                     | yes      |
| `samples/*`          | yes                                        | no       |

`swing-ui-animation` redistributes the Android Open Source Project's Jetpack Compose `animation-core`
verbatim, so it is exempt from the shared style gates by design. Keep it comparable to that upstream:
do not restyle it, and do not bring it under those gates.

Which modules carry a coverage floor, and each one's line and branch minimums, is set in that
module's own `build.gradle.kts` - read it there rather than here. `CONTRIBUTING.md` explains how the
floors are meant to be treated when a change moves one.

## Public API

`checkKotlinAbi` compares the compiled surface against the dumps committed under `<module>/api/`. When
the public API changes intentionally, regenerate them and review the diff before committing:

```bash
./gradlew updateKotlinAbi
```

Two kinds of declaration are outside the dumps, so the gate cannot answer for them:

- anything annotated `@InternalSwingUiApi`, which the validation filters out by configuration;
- **public inline functions with a reified type parameter**, which compile to synthetic methods that
  the dump skips. Changing one of their signatures breaks every caller while `checkKotlinAbi` stays
  green. Enumerate the current set rather than trusting a fixed list — it grows as the API does:
  ```bash
  git grep -n 'public inline fun <reified' swing-ui/src/main swing-ui-test/src/main
  ```
  `SwingNode`, `MenuNode` and `SwingModifier.listener` are examples, not the whole set. Diff whatever
  the grep finds against its committed form by hand before concluding the surface is unchanged.

## Why the build rejects code that compiles in an IDE

`allWarningsAsErrors` is on, so a warning fails the build. Published binaries target Java 11 and
Kotlin language and API level 2.1, independent of the toolchain the build runs on, so a newer language
feature or standard-library API fails to compile even on a current JDK.

## Configuration

- detekt: `config/detekt/detekt.yml`, layered on the default config.
- Android Lint, hosting the Compose lint checks: `config/lint/lint.xml`. The file states the reason
  for each check it disables.
- Coverage floors and ABI filters: each module's `build.gradle.kts`.
- The gates and the code style in prose: `CONTRIBUTING.md`.
