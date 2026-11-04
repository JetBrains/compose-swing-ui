# Contributing

Thanks for contributing to Compose Swing UI. This guide covers how to build and test the project, the quality gates every change must pass, and the code style.

## Prerequisites

- **JDK 21 or newer.** `buildSrc` compiles to JVM 21 and the build provisions no toolchain, so the JDK you run on is the one it uses. Published library binaries target Java 11 regardless.
- Use the Gradle wrapper (`./gradlew`). Do not rely on a system Gradle.

## Build and test

```bash
# Full build: compile, run all gates and tests
./gradlew build

# Tests only
./gradlew test

# One module, or one test class, while iterating
./gradlew :swing-ui:test
./gradlew :swing-ui:test --tests '*ComboBoxEditableTest*'

# The tests that need the window system to itself (focus, minimization)
./gradlew :swing-ui:exclusiveWindowSystemTest :swing-ui-foundation:exclusiveWindowSystemTest

# Run a sample application
./gradlew :samples:todo-app:run
./gradlew :samples:widgets-gallery:run

# API reference for every published module, one site at build/dokka/html
./gradlew :dokkaGenerate
```

Compiling a module also writes the Compose compiler's reports - which composables are skippable, and which parameters are unstable - to `<module>/build/compose-reports`, alongside summary counts in `<module>/build/compose-metrics`. A build that is up to date or served from the cache does not compile and leaves the reports it finds, so force compilation when you need to trust them. Kotlin's incremental compiler recompiles only the sources a change touches, and the report reflects only that increment - a short or empty report means little was recompiled. Only a non-incremental compile produces a report covering the whole module: `./gradlew --rerun-tasks :swing-ui:compileKotlin`, or clean the module first.

`:swing-ui` and `:swing-ui-foundation` each split their suite across two tasks, and `check` runs both. `test` holds everything that asserts nothing about which window the window system is attending to, and runs it across parallel JVMs; `exclusiveWindowSystemTest` holds the classes and cases tagged `ExclusiveWindowSystem` and runs them one at a time, because two of them at once take that state from each other and skip. Write a new test into whichever it belongs to by tagging it, not by where the file sits; `ExclusiveWindowSystem` says which tests need the tag and where it goes. Note that a scoped `test` run of either module also pulls in its `exclusiveWindowSystemTest`, which the coverage report that follows a test run depends on.

Scope a run while iterating; `./gradlew test` runs every module's suite. The modules test each other: `:swing-ui-test` publishes the harness that `:swing-ui`'s own tests are written against, so a harness change is exercised far more by the library's suite than by the harness's own, and both samples' tests sit downstream of the two. A scoped run belongs to the edit loop; the gate below is what a change is judged by.

Harness-driven tests are deterministic, never sleep, and never attach their root to a window, so they run with or without a display. Only a wait on something the composition does not drive - the window system, or a quiet period that proves nothing happened - polls against a real deadline. A test that realizes a real top-level peer gates itself with `assumeFalse(GraphicsEnvironment.isHeadless(), ...)` so it reports skipped without one, and others gate the same way on a capability the environment may withhold. A run with no failures is therefore not necessarily a complete one: the ignored count in `<module>/build/reports/tests/test/index.html` says what did not run. CI runs under a virtual framebuffer, so the display-gated tests skipped here run and are judged there too; only the ones gated on a capability the framebuffer itself lacks (see Quality gates below) still report skipped in CI.

On macOS the tests that need a focused window skip; CI runs them.

See [Testing components](docs/TESTING-COMPONENTS.md) for the harness guide.

## Benchmark suites

See [Measuring performance](docs/PERFORMANCE.md) for the fixtures and the suites that measure them.

## Quality gates (all must pass)

`build` runs each module's `check` and `assemble` tasks. The module's conventions select its tests, static analysis, ABI validation, and coverage gates. `staticAnalysis` runs ktlint and Detekt with type resolution, including the Compose runtime checks. The vendored animation engine and copied compose-rules helpers retain their upstream formatting. `samples/docs` compiles the Kotlin examples from the documentation.

Run the module gates and the included `buildSrc` checks before pushing.

```bash
./gradlew :buildSrc:ktlintCheck :buildSrc:detekt build
```

CI runs the same gates, plus `dokkaGenerate` and `publishToMavenLocal`, which `build` does not reach; run them locally too when a change touches the API reference or the publishing configuration. CI has no window manager, so tests that need maximizing, always-on-top or a system tray report as skipped there.

Piece by piece:

- **`ktlintCheck`** - formatting + lint. Auto-fix with:
  ```bash
  ./gradlew ktlintFormat
  ```
- **`detekt`** - static analysis (`config/detekt/detekt.yml`, on top of the default config). Rules that need the compile classpath fire only in the type-resolution variants, `detektMain`, `detektTest`, `detektTestFixtures` and `detektFixtures`, which `staticAnalysis` runs for every source set; `check` reaches them only through it. When scoping a run to one module while iterating, invoke `:module:detektMain` and `:module:detektTest` rather than the plain `:module:detekt` task, which runs without type resolution, stays silent on those rules and is not part of `check`. This does not apply to `buildSrc`, whose plain `detekt` task (below) is its own gate.
- **`checkKotlinAbi`** - the Kotlin Gradle plugin's built-in ABI validation, comparing the compiled surface against the committed `.api` dumps. If you change the **public API**, regenerate them and review the diff:
  ```bash
  ./gradlew updateKotlinAbi
  ```
  Commit whichever of the published modules' `<module>/api/<module>.api` dumps the change moved. A public-API change should be intentional and reviewed.
- **`:buildSrc:ktlintCheck` / `:buildSrc:detekt`** - formatting, lint and static analysis for the convention plugins. Run them whenever you touch anything under `buildSrc/`.
- **`jacocoTestCoverageVerification`** - each module that applies the coverage convention enforces a line-coverage floor and a branch-coverage floor, measured per module against that module's own tests. A change that drops a ratio below its floor fails the build. Add tests for any new behavior you can reach through the test harness. If a branch is a defensive guard with no reachable behavior, leave it uncovered rather than writing a test that asserts nothing.

## Documentation snippets

Kotlin snippets in `README.md` and `docs/` are compiled by the build, and marking one is opt-in: write `<!--- KNIT example-<document>-01.kt -->` on its own line after the closing fence, and Knit merges the block into a file under `samples/docs/build/generated/knit/example/`, taking the number from the document's own sequence. A snippet that is a fragment rather than a whole file gets its imports and its enclosing function from an `INCLUDE`/`SUFFIX` preamble at the top of the document, matched against the example file name; an unmarked block is followed by `<!--- CLEAR -->` so it does not merge into the next marked one.

Compile the documentation examples while editing:

```bash
./gradlew :samples:docs:compileKotlin
```

## Code style

- **Explicit API mode is on** for the library modules. Every public declaration needs an explicit visibility modifier and an explicit return type. Keep helpers `internal` rather than widening visibility to make something reachable.
- **KDoc every public declaration.** Document parameters and behavior, especially any threading (EDT) or lifecycle requirements. KDoc is the API catalog; documentation prose links out to it rather than re-listing the API.
- **Stay on the EDT.** Composition entry points and component mutations run on the Event Dispatch Thread. Use `Dispatchers.Swing` for coroutines that touch Swing.
- **Match the surrounding style.** Trailing commas, import ordering, and formatting are enforced by ktlint - run `ktlintFormat` rather than hand-tuning.
- **Pass arguments in the order the component declares them.** A named argument may sit anywhere, but a call that lists them out of order has to be read against the signature one name at a time to see what was passed and what was left defaulted. Write `Button("Save", onClick = ::save, modifier = m)`, not `Button("Save", modifier = m, onClick = ::save)`.

### Typed constants

Model a closed set of named JDK/Swing integer constants (a selection mode, a placement, a message kind) as a `@MagicConstant`-annotated typedef - never an `enum class` and never a wrapper value class. Declare an annotation class whose only job is to name the accepted constants, then use it as `@Xxx Int` on the parameter (see [Architecture](docs/ARCHITECTURE.md) for what a typed constant set gives a caller):

```kotlin
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED.toLong(),
        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS.toLong(),
        JScrollPane.VERTICAL_SCROLLBAR_NEVER.toLong(),
    ],
)
public annotation class VerticalScrollbarPolicy

// call site passes the plain JDK constant; the IDE flags anything outside the set
public fun ScrollPane(
    /* ... */
    @VerticalScrollbarPolicy verticalScrollbar: Int = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
    /* ... */
)
```

Retention is **`BINARY`** so the annotation survives into the compiled class files, and the IDE's MagicConstant inspection can read it across the published-jar boundary - warning consumers in their own IDE - while `org.jetbrains:annotations` stays a `compileOnly` dependency that never reaches the runtime classpath. A constant string set (e.g. a MIME content type) uses `stringValues` instead of `intValues`. When the same value lives in more than one Swing namespace (e.g. `SwingConstants` vs. `FlowLayout` alignments), declare a distinct annotation per namespace so each names exactly its own constants. Group a new typedef alongside the library's other typed constant sets.

## Adding a new component

Use the public component-author API for built-in components and for components you write in an application. See [Writing components](docs/CUSTOM-COMPONENTS.md) for designing and implementing one.

- Add a behavioral test under `swing-ui/src/test` exercising state -> recomposition -> visible change (and listener re-attach if relevant). Library tests use the `:swing-ui-test` harness, available as `testImplementation`.
- Run `./gradlew updateKotlinAbi` and commit the updated `.api`, since a new public component changes the surface.
- Run the full gate command above before opening a PR.
