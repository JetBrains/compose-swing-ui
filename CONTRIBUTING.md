# Contributing

Thanks for contributing to Compose Swing UI. This guide covers how to build and test the project,
the quality gates every change must pass, and the code style.

## Prerequisites

- **JDK 21 or newer.** `buildSrc` compiles to JVM 21 and the build provisions no toolchain, so the
  JDK you run on is the one it uses. Published library binaries target Java 11 regardless.
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

# Run a sample application
./gradlew :samples:todo-app:run
./gradlew :samples:widgets-gallery:run
```

Compiling a module also writes the Compose compiler's reports - which composables are skippable, and
which parameters are unstable - to `<module>/build/compose-reports`, alongside summary counts in
`<module>/build/compose-metrics`. A build that is up to date or served from the cache does not
compile and leaves the reports it finds, so force compilation when you need to trust them. Kotlin's
incremental compiler recompiles only the sources a change touches, and the report reflects only that
increment - a short or empty report means little was recompiled, not that the module has nothing
unstable. Only a non-incremental compile produces a report covering the whole module:
`./gradlew --rerun-tasks :swing-ui:compileKotlin`, or clean the module first.

Scope a run while iterating; `./gradlew test` runs every module's suite. The modules test each other:
`:swing-ui-test` publishes the harness that `:swing-ui`'s own tests are written against, so a harness
change is exercised far more by the library's suite than by the harness's own, and both samples' tests
sit downstream of the two. A scoped run belongs to the edit loop; the gate below is what a change is
judged by.

Tests are deterministic and never sleep. Harness-driven tests never attach their root to a window,
so they run with or without a display. A test that realizes a real top-level peer gates itself with
`assumeFalse(GraphicsEnvironment.isHeadless(), ...)` so it reports skipped without one, and others
gate the same way on a capability the environment may withhold. A run with no failures is therefore
not necessarily a complete one: the ignored count in `<module>/build/reports/tests/test/index.html`
says what did not run. CI runs under a virtual framebuffer, so the display-gated tests skipped here
run and are judged there too; only the ones gated on a capability the framebuffer itself lacks (see
Quality gates below) still report skipped in CI.

See [`docs/TESTING-COMPONENTS.md`](docs/TESTING-COMPONENTS.md) for the harness guide.

## Quality gates (all must pass)

`build` runs every module's `check` - compilation, ktlint, detekt, the Compose lint checks, tests,
the coverage gates and `checkKotlinAbi` - plus `assemble`. One gate sits outside it: `buildSrc` is an
included build the root-level tasks do not reach. So the full gate is two commands, and they are what
to run locally before pushing:

```bash
./gradlew :buildSrc:ktlintCheck :buildSrc:detekt
./gradlew build
```

CI runs the same two - the second on a display a virtual framebuffer supplies, so tests that realize a
real top-level window run instead of skipping - and then `publishToMavenLocal` to check that the
publishing configuration still resolves. Nothing manages that display, and the toolkit answers for the
capabilities of whatever does, so the tests that need maximizing, always-on-top or a system tray gate
themselves on the capability and report as skipped there.

Piece by piece:

- **`ktlintCheck`** - formatting + lint. Auto-fix with:
  ```bash
  ./gradlew ktlintFormat
  ```
- **`detekt`** - static analysis (`config/detekt/detekt.yml`, on top of the default config). The gate
  runs it through `check`, which wires the type-resolution variants, `detektMain` and `detektTest`;
  rules that need the compile classpath only fire there. When scoping a run to one module while
  iterating, invoke `:module:detektMain` and `:module:detektTest` rather than the plain
  `:module:detekt` task, which runs without type resolution and stays silent on those rules. This
  does not apply to `buildSrc`, whose plain `detekt` task (below) is its own gate.
- **`checkKotlinAbi`** - the Kotlin Gradle plugin's built-in ABI validation, comparing the compiled
  surface against the committed `.api` dumps. If you change the **public API**, regenerate them and
  review the diff:
  ```bash
  ./gradlew updateKotlinAbi
  ```
  Commit the updated `swing-ui/api/swing-ui.api` (and `swing-ui-test/api/swing-ui-test.api` if the
  test module's surface changed). A public-API change should be intentional and reviewed.
- **`:buildSrc:ktlintCheck` / `:buildSrc:detekt`** - formatting, lint and static analysis for the
  convention plugins. Run them whenever you touch anything under `buildSrc/`.
- **`jacocoTestCoverageVerification`** - `swing-ui`, `swing-ui-test` and `swing-ui-animation` each
  enforce a line-coverage floor and a branch-coverage floor, measured per module against that
  module's own tests. A change that drops a ratio below its floor fails the build. Add tests for any
  new behavior you can reach through the test harness. The floors are held, not chased: if a branch is
  a defensive guard with no reachable behavior, leave it uncovered rather than writing a test that
  asserts nothing.

## Code style

- **Explicit API mode is on** for the library modules. Every public declaration needs an explicit
  visibility modifier and an explicit return type. Keep helpers `internal` rather than widening
  visibility to make something reachable.
- **KDoc every public declaration.** Document parameters and behavior, especially any threading
  (EDT) or lifecycle requirements. KDoc is the API catalog; documentation prose links out to it
  rather than re-listing the API.
- **Stay on the EDT.** Composition entry points and component mutations run on the Event Dispatch
  Thread. Use `Dispatchers.Swing` for coroutines that touch Swing.
- **Match the surrounding style.** Trailing commas, import ordering, and formatting are enforced by
  ktlint - run `ktlintFormat` rather than hand-tuning.

### Composable target

`@SwingComposable` (or `@SwingMenuComposable` for a menu) belongs on `SwingNode`, the menu-tree
primitives, `setContent`, and a `content`/slot lambda **parameter** forwarded to one of them by value -
not on an ordinary component or container. See
[`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) for the full explanation.

### Typed constants

Model a closed set of named JDK/Swing integer constants (a selection mode, a placement, a message
kind) as a `@MagicConstant`-annotated typedef - never an `enum class` and never a wrapper value class.
Declare an annotation class whose only job is to name the accepted constants, then use it as
`@Xxx Int` on the parameter (see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for what a typed
constant set gives a caller):

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

Retention is **`BINARY`** so the annotation survives into the compiled class files, and the IDE's
MagicConstant inspection can read it across the published-jar boundary - warning consumers in their
own IDE - while `org.jetbrains:annotations` stays a `compileOnly` dependency that never reaches the
runtime classpath. A constant string set (e.g. a MIME content type) uses `stringValues` instead of
`intValues`. When the same value lives in more than one Swing namespace (e.g. `SwingConstants` vs.
`FlowLayout` alignments), declare a distinct annotation per namespace so each names exactly its own
constants. Group a new typedef alongside the library's other typed constant sets.

## Adding a new component

[`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) is the authoritative, worked guide to writing
a component - it applies equally to library components and to components you build in your own app.
Follow it, then:

- Add a behavioral test under `swing-ui/src/test` exercising state -> recomposition -> visible change
  (and listener re-attach if relevant). Library tests use the `:swing-ui-test` harness, available as
  `testImplementation`.
- Run `./gradlew updateKotlinAbi` and commit the updated `.api`, since a new public component changes
  the surface.
- Run the full gate command above before opening a PR.
