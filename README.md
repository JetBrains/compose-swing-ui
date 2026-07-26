# Compose Swing UI

A declarative, reactive way to build **Swing** UIs using Jetpack Compose's composition model —
built on **Compose Runtime only**. No skiko, no Compose Multiplatform UI, no Skia renderer. Your
components are real `JButton`/`JLabel`/`JPanel` widgets, laid out by Swing's own `LayoutManager`s
and painted by the platform look-and-feel; Compose drives state and composition.

Inspired by [Compose HTML](https://github.com/JetBrains/compose-multiplatform) (DOM target) and
[Mosaic](https://github.com/JakeWharton/mosaic) (terminal target).

## Quick start

A minimal app using the `application` entry point, a `Window`, a couple of components, and
`BorderPanel`'s region slots:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.application
import javax.swing.SwingConstants

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }

        BorderPanel {
            north {
                Label("Compose Swing UI", horizontalAlignment = SwingConstants.CENTER)
            }
            center {
                FlowPanel {
                    Label("Count: $count")
                    Button("Increment", onClick = { count++ })
                    Button("Decrement", onClick = { count-- })
                }
            }
            south {
                Label("Status: ready", horizontalAlignment = SwingConstants.CENTER)
            }
        }
    }
}
```

`BorderPanel` exposes each `BorderLayout` region as a declarative slot in a receiver DSL: an absolute
compass family (`north`/`south`/`east`/`west`/`center`) and an orientation-aware family
(`pageStart`/`pageEnd`/`lineStart`/`lineEnd`, resolved against the panel's component orientation).
Placement is parent-driven; declare only the regions you need, and declaring a region again replaces
it. Prefer one family per edge.

## Mounting into existing Swing (`setContent`)

You can also drive composition into any container without the `application`/`Window` entry points.
`setContent` is an extension on `java.awt.Container`, with `java.awt.Window.setContent` (covering
`JFrame`, `JDialog`, and `JWindow`) and `JMenuBar.setContent` provided too. A single
`import org.jetbrains.compose.swing.setContent` resolves all of them:

```kotlin
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent // Container/Window/JMenuBar.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("My App")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.size = Dimension(600, 400)

        frame.setContent {
            var count by remember { mutableIntStateOf(0) }
            FlowPanel {
                Label("Count: $count")
                Button("Increment", onClick = { count++ })
            }
        }

        frame.isVisible = true
    }
}
```

`setContent` is called on the Event Dispatch Thread and returns a `DisposableHandle`; dispose it to
tear the composition down. Nesting works: a `setContent` whose ancestor already hosts a composition
joins that composition and shares its recomposition scope.

## Menus

`JMenuBar.setContent` drives a menu tree from the same import:

```kotlin
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.setContent // JMenuBar.setContent
import javax.swing.JMenuBar

val menuBar = JMenuBar()
menuBar.setContent {
    Menu("File") {
        MenuItem("New", onClick = { println("New") })
        MenuItem("Open", onClick = { println("Open") })
        MenuSeparator()
        MenuItem("Exit", onClick = { /* … */ })
    }
}
frame.jMenuBar = menuBar
```

## Styling & interaction with `SwingModifier`

Components take an optional `modifier: SwingModifier = SwingModifier` parameter for visual and
interaction concerns — colors, fonts, borders, tooltips, focus, hover. Build a chain with the
extension builders and the framework diffs it across recompositions, restoring the original value of
any element you remove:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.interaction.onHover
import java.awt.Color
import javax.swing.BorderFactory

var hovered by mutableStateOf(false)
Button(
    text = "Save",
    onClick = { /* … */ },
    modifier = SwingModifier
        .foreground(Color.WHITE)
        .border(BorderFactory.createLineBorder(if (hovered) Color.BLUE else Color.GRAY))
        .onHover(onEnter = { hovered = true }, onExit = { hovered = false }),
)
```

Domain callbacks like `onClick` and `onValueChange` stay ordinary parameters; only cross-cutting
styling and interaction flow through `modifier`. Builders are grouped by concern: appearance, layout,
metadata, interaction, and accessibility. See
[`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) for writing your own modifier elements and
listeners.

## Bring your own Swing component

Any Swing `Component` can be hosted directly with `SwingNode` — a first-class, supported way to bring
custom Swing components into a composition. Every built-in wrapper is built on `SwingNode` the same
way. See [`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) for a step-by-step guide, and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for how the composition drives the Swing tree.

## Animation

`swing-ui-animation` provides the familiar `animate*AsState`, `Animatable`, `updateTransition` /
`Transition`, `rememberInfiniteTransition`, easing curves (including `CubicBezierEasing`) and the
`spring` / `tween` / `keyframes` specs, for the `Float`, `Int` and generic (`TwoWayConverter`) value
types — supply a `TwoWayConverter` for your own type (e.g. `java.awt.Color`).

Animations run with no extra wiring: any `animate*` used inside a `setContent { … }` composition is
driven by the window's frame clock automatically, advancing at the display's refresh rate while an
animation is in flight. See [`swing-ui-animation/README.md`](swing-ui-animation/README.md).

## Testing

Add `:swing-ui-test` and write plain `@Test` methods — the harness is synchronous and deterministic
(off-screen, never sleeps):

```kotlin
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.components.button.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.test.Test

class CounterTest {
    @Test
    fun clickingIncrements() = runComposeSwingTest {
        var clicks by mutableStateOf(0)
        setContent {
            Button(text = "Clicks: $clicks", onClick = { clicks++ })
        }
        onNodeWithText("Clicks: 0").performClick()
        onNodeWithText("Clicks: 1").assertExists()
    }
}
```

See [`docs/TESTING-COMPONENTS.md`](docs/TESTING-COMPONENTS.md) for the finders, assertions, actions,
and screenshot comparison the harness offers.

## Build, run, test

```bash
./gradlew build                          # compile + all quality gates + tests
./gradlew :samples:todo-app:run          # run the to-do sample
./gradlew :samples:widgets-gallery:run   # run the widgets gallery
./gradlew test                           # tests only
```

Full quality-gate command (what CI runs):

```bash
./gradlew build checkKotlinAbi ktlintCheck detekt test :buildSrc:ktlintCheck :buildSrc:detekt
```

## Modules

- `swing-ui` — the library: composition runtime wired to Swing, plus composable wrappers over Swing
  components and layouts. See [`swing-ui/README.md`](swing-ui/README.md).
- `swing-ui-animation` — the animation engine. See
  [`swing-ui-animation/README.md`](swing-ui-animation/README.md).
- `swing-ui-test` — the test harness. See [`swing-ui-test/README.md`](swing-ui-test/README.md).
- `samples/todo-app`, `samples/widgets-gallery` — runnable showcases.

## Stability

Pre-1.0: breaking API changes may land in any minor release. Kotlin 2.1 or newer is required to
consume the libraries.

## License

Licensed under the Apache License, Version 2.0 — see [LICENSE](LICENSE).

`swing-ui-animation` additionally redistributes source code from the Android Open Source Project's
Jetpack Compose `animation-core` under the same license; see that module's `META-INF/NOTICE` and the
per-file headers for attribution.
