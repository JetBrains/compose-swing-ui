# Contributing

Thanks for contributing to Compose Swing UI. This guide covers how to build and test the project,
the quality gates every change must pass, and the code style.

## Prerequisites

- **JDK 21.** The Kotlin toolchain and detekt are pinned to JVM 21; CI runs on JDK 21. (The Foojay
  toolchain plugin can download a matching JDK automatically.)
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

Scope a run while iterating; `./gradlew test` runs every module's suite. The modules test each other:
`:swing-ui-test` publishes the harness that `:swing-ui`'s own tests are written against, so a harness
change is exercised far more by the library's suite than by the harness's own, and both samples' tests
sit downstream of the two. A scoped run belongs to the edit loop; the gate below is what a change is
judged by.

Tests are deterministic and never sleep. Harness-driven tests never attach their root to a window,
so they run with or without a display. A test that realizes a real top-level peer declares that
requirement with a JUnit assumption (`Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
...)`), so it reports skipped without a display, and others gate the same way on a capability the
environment may withhold. A run with no failures is therefore not necessarily a complete one: the
ignored count in `<module>/build/reports/tests/test/index.html` says what did not run, and the
quality gates below cover which of those CI runs for you. Write UI tests with the `:swing-ui-test`
harness:

```kotlin
@Test
fun clickingTheButtonUpdatesTheLabel() = runComposeSwingTest {
    var clicks by mutableStateOf(0)
    setContent {
        Button(text = "Clicks: $clicks", onClick = { clicks++ })
    }
    onNodeWithText("Clicks: 0").performClick()
    onNodeWithText("Clicks: 1").assertExists()
}
```

Prefer `setContent { ... }` followed by assertions; `setContent` waits for the composition to settle
for you. Reach for `waitUntil { ... }` only when a condition genuinely depends on external timing. See
[`docs/TESTING-COMPONENTS.md`](docs/TESTING-COMPONENTS.md) for the full harness guide.

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

- **`ktlintCheck`** - formatting + lint, including Compose-aware rules
  (`io.nlopez.compose.rules`). Auto-fix with:
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
- **`:buildSrc:ktlintCheck` / `:buildSrc:detekt`** - `buildSrc` is an included build, so the
  root-level gates do **not** reach it; these must be invoked explicitly (CI does). Run them
  whenever you touch the convention plugins under `buildSrc/`.
- **`jacocoTestCoverageVerification`** - `swing-ui`, `swing-ui-test` and `swing-ui-animation` each enforce line- and
  branch-coverage floors, measured per module against that module's own tests. A change that drops
  either ratio below its floor fails the build. Add tests for any new behavior you can reach through
  the test harness. The floors are held, not chased: if a branch is a defensive guard with no
  reachable behavior, leave it uncovered rather than writing a test that asserts nothing.

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

`@SwingComposable` (or `@SwingMenuComposable` for a menu) marks Swing-target content so the compiler
can tell it apart from `compose.ui`'s own `@UiComposable`, catching a mixed composition at compile
time instead of at runtime. It belongs on `SwingNode`, the menu-tree primitives, `setContent`, and a
`content`/slot lambda **parameter** forwarded to one of them by value - not on an ordinary component or
container, whose target the compiler infers from what it calls. See
[`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) for the full explanation.

### Typed constants

Model a closed set of named JDK/Swing integer constants (a selection mode, a placement, a message
kind) as a `@MagicConstant`-annotated typedef - never an `enum class` and never a wrapper value class.
Declare an annotation class whose only job is to name the accepted constants, then use it as
`@Xxx Int` on the parameter; the value passed at runtime stays the **plain JDK constant** the wrapped
Swing API already expects, so there is no boxing, accessor, or translation layer:

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

Retention is **`BINARY`** so the annotation survives into the compiled class files and the IDE's
MagicConstant inspection can read it across the published-jar boundary - warning consumers in their
own IDE - while `org.jetbrains:annotations` stays a `compileOnly` dependency that never reaches the
runtime classpath. A constant string set (e.g. a MIME content type) uses `stringValues` instead of
`intValues`. When the same value lives in more than one Swing namespace (e.g. `SwingConstants` vs.
`FlowLayout` alignments), declare a distinct annotation per namespace so each names exactly its own
constants. Group a new typedef alongside the library's other typed constant sets.

## Adding a new component

A component is a `@Composable` function that emits a `SwingNode`: the `factory` builds the backing
Swing component once, `update` reactively pushes state onto it via `set` blocks, and a
`modifier: SwingModifier = SwingModifier` parameter carries caller styling. Domain callbacks
(`onClick`, `onValueChange`) stay ordinary parameters; install reactive listeners through the
`SwingModifier` listener builders so the runtime owns their lifecycle.

[`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) is the authoritative, worked guide to all of
this - it applies equally to library components and to components you build in your own app. Follow
it, then:

- Add a behavioral test under `swing-ui/src/test` exercising state -> recomposition -> visible change
  (and listener re-attach if relevant). Library tests use the `:swing-ui-test` harness, available as
  `testImplementation`.
- Run `./gradlew updateKotlinAbi` and commit the updated `.api`, since a new public component changes
  the surface.
- Run the full gate command above before opening a PR.
