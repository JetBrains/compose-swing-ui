# Compose Swing UI

[![Version](https://img.shields.io/github/v/release/MatkovIvan/compose-swing-ui?sort=semver)](https://github.com/MatkovIvan/compose-swing-ui/releases/latest)
[![Snapshot](https://github.com/MatkovIvan/compose-swing-ui/actions/workflows/snapshot.yml/badge.svg)](https://github.com/MatkovIvan/compose-swing-ui/actions/workflows/snapshot.yml)
[![API reference](https://img.shields.io/badge/API-reference-blue)](https://matkovivan.github.io/compose-swing-ui/)

A declarative, reactive way to build **Swing** UIs using Jetpack Compose's composition model -
built on **Compose Runtime only**. No skiko, no Compose Multiplatform UI, no Skia renderer. Your
components are real `JButton`/`JLabel`/`JPanel` widgets, laid out by Swing's own `LayoutManager`s
and painted by the platform look-and-feel; Compose drives state and composition.

Inspired by [Compose HTML](https://github.com/JetBrains/compose-multiplatform) (DOM target) and
[Mosaic](https://github.com/JakeWharton/mosaic) (terminal target).

## Quick start

A minimal app using the `application` entry point, a `Window`, a couple of components, and
`BorderPanel`'s regions:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.application
import javax.swing.SwingConstants

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }

        BorderPanel {
            Label(
                "Compose Swing UI",
                modifier = SwingModifier.north().horizontalAlignment(SwingConstants.CENTER),
            )
            FlowPanel {
                Label("Count: $count")
                Button("Increment", onClick = { count++ })
                Button("Decrement", onClick = { count-- })
            }
            Label(
                "Status: ready",
                modifier = SwingModifier.south().horizontalAlignment(SwingConstants.CENTER),
            )
        }
    }
}
```

<!--- KNIT example-readme-01.kt -->

A child of `BorderPanel` names its `BorderLayout` region on its own modifier: `north()`, `south()`,
`center()`, and the rest of `BorderLayout`'s. A child that names no region occupies the center, so
the panel's main content is written plainly.

Every component family the library ships - text inputs, buttons, selection, layout containers,
windows, dialogs and menus - is cataloged with the parameters that decide how it behaves in
[`docs/COMPONENTS.md`](docs/COMPONENTS.md). Every public declaration is documented in the
[API reference](https://matkovivan.github.io/compose-swing-ui/), which tracks `master`.

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

<!--- KNIT example-readme-02.kt -->

`setContent` is called on the Event Dispatch Thread and returns a `DisposableHandle`; dispose it to
tear the composition down. Nesting works: a `setContent` whose ancestor already hosts a composition
joins that composition and shares its recomposition scope.

## Menus

`MenuBar` declares the menu bar of the window whose content it is composed in, driven by the state its
content reads. It is declared on the window scope a `Window` and a `Dialog` give their content, so a
menu bar with no window to carry it does not compile:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.application

fun main() = application {
    var opened by remember { mutableStateOf("nothing") }
    Window(onCloseRequest = ::exitApplication, title = "Editor") {
        MenuBar {
            Menu("File") {
                MenuItem("New", onClick = { opened = "a new file" })
                MenuItem("Open", onClick = { opened = "an existing file" })
                MenuSeparator()
                MenuItem("Exit", onClick = ::exitApplication)
            }
        }
        Label("Opened: $opened")
    }
}
```

<!--- KNIT example-readme-03.kt -->

The same tree fills a context menu through `SwingModifier.contextMenu { ... }` and a tray icon's menu
through `Tray(menu = { ... })`. On a `JMenuBar` the application builds itself, `JMenuBar.setContent { ... }`
takes it too.

## Styling & interaction with `SwingModifier`

Components take an optional `modifier: SwingModifier = SwingModifier` parameter for visual and
interaction concerns - colors, fonts, borders, tooltips, focus, hover. Build a chain with the
extension builders:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.interaction.onHover
import java.awt.Color

@Composable
fun SaveButton(onSave: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Button(
        text = "Save",
        onClick = onSave,
        modifier =
            SwingModifier
                .foreground(Color.WHITE)
                .lineBorder(if (hovered) Color.BLUE else Color.GRAY)
                .onHover(onEnter = { hovered = true }, onExit = { hovered = false }),
    )
}
```

<!--- KNIT example-readme-04.kt -->

`lineBorder` and `emptyBorder` declare a border by its values and rebuild it only when they change.
Hoist the other value objects a chain carries - a `Font` or an `Icon` - into `remember`.

Domain callbacks like `onClick` and `onValueChange` stay ordinary parameters; only cross-cutting
styling and interaction flow through `modifier`. Builders are grouped by concern, one package each:
appearance, layout, interaction, listener, keyboard, data transfer, and accessibility. See
[`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) for what an unhoisted instance costs, and
for writing your own modifier elements and listeners.

## Bring your own Swing component

Any Swing `Component` can be hosted directly with `SwingNode` - a first-class, supported way to bring
custom Swing components into a composition. Every built-in wrapper is built on `SwingNode` the same
way. See [`docs/CUSTOM-COMPONENTS.md`](docs/CUSTOM-COMPONENTS.md) for a step-by-step guide, and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for how the composition drives the Swing tree.

## Animation

`swing-ui-animation` provides Compose's animation-core APIs - `animate*AsState`, `Animatable`,
`updateTransition`, easing curves, and the `spring` / `tween` / `keyframes` specs - for `Float`,
`Int`, and any type you supply a `TwoWayConverter` for. See
[`swing-ui-animation/README.md`](swing-ui-animation/README.md).

## Testing

Add `:swing-ui-test` and write plain `@Test` methods whose body is a `runComposeSwingTest { ... }`
block - the harness is synchronous and deterministic (off-screen, never sleeps).

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
./gradlew :buildSrc:ktlintCheck :buildSrc:detekt
./gradlew build
```

[`CONTRIBUTING.md`](CONTRIBUTING.md) walks through what each gate covers.

## Modules

- `swing-ui` - the library: composition runtime wired to Swing, plus composable wrappers over Swing
  components and layouts. See [`swing-ui/README.md`](swing-ui/README.md).
- `swing-ui-animation` - the animation engine. See
  [`swing-ui-animation/README.md`](swing-ui-animation/README.md).
- `swing-ui-test` - the test harness. See [`swing-ui-test/README.md`](swing-ui-test/README.md).
- `samples/todo-app`, `samples/widgets-gallery` - runnable showcases.
- `samples/docs` - the Kotlin snippets in this repository's Markdown, compiled by the build.

## Stability

Pre-1.0: breaking API changes may land in any minor release. Kotlin 2.1 or newer is required to
consume the libraries.

## License

Licensed under the Apache License, Version 2.0 - see [LICENSE](LICENSE).

`swing-ui-animation` additionally redistributes source code from the Android Open Source Project's
Jetpack Compose `animation-core` under the same license; see that module's `META-INF/NOTICE` and the
per-file headers for attribution.
