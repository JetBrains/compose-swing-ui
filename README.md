# Compose Swing UI

[![Team](https://jb.gg/badges/team-plastic.svg)](https://github.com/JetBrains#jetbrains-on-github) [![Version](https://img.shields.io/github/v/release/JetBrains/compose-swing-ui?sort=semver)](https://github.com/JetBrains/compose-swing-ui/releases/latest) [![Snapshot](https://github.com/JetBrains/compose-swing-ui/actions/workflows/snapshot.yml/badge.svg)](https://github.com/JetBrains/compose-swing-ui/actions/workflows/snapshot.yml) [![API reference](https://img.shields.io/badge/API-reference-blue)](https://jetbrains.github.io/compose-swing-ui/)

A declarative, reactive way to build **Swing** UIs using Jetpack Compose's composition model - built on **Compose Runtime only**. No skiko, no Compose Multiplatform UI, no Skia renderer. Your components are real `JButton`/`JLabel`/`JPanel` widgets, laid out by Swing's own `LayoutManager`s and painted by the platform look-and-feel; Compose drives state and composition.

Inspired by [Compose HTML](https://github.com/JetBrains/compose-multiplatform) (DOM target) and [Mosaic](https://github.com/JakeWharton/mosaic) (terminal target).

## Quick start

Use Compose state as usual. When snapshot state changes, Compose recomposes the scopes that read it. The resulting declarations update the Swing UI.

<!--- INCLUDE .*readme-01.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.style.horizontalAlignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.window.*
import javax.swing.SwingConstants
-->

```kotlin
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }

        Panel(PanelLayout.Border()) {
            Label(
                "Count: $count",
                modifier = SwingModifier.north(),
                style = { horizontalAlignment(SwingConstants.CENTER) },
            )
            Panel {
                Button("Increment", onClick = { count++ })
                Button("Decrement", onClick = { count-- })
            }
        }
    }
}
```

<!--- KNIT example-readme-01.kt -->

## AndroidX-like layout and animation

Compose Swing UI adapts AndroidX layouts and animations to Swing widgets.

<!--- INCLUDE .*readme-02.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.foundation.layout.*
import org.jetbrains.compose.swing.modifier.SwingModifier

@Composable
fun TaskControls(onAdd: (CharSequence) -> Unit) {
----- SUFFIX .*readme-02.*
}
-->

```kotlin
val title = rememberDocumentState()
Row(horizontalArrangement = Arrangement.spacedBy(8)) {
    Label("Task")
    TextField(state = title, modifier = SwingModifier.fillMaxWidth())
}
AnimatedVisibility(visible = title.text.isNotBlank()) {
    Button("Add", onClick = { onAdd(title.text) })
}
```

<!--- KNIT example-readme-02.kt -->

See the [layout and drawing guide](docs/FOUNDATION.md), the [animation guide](swing-ui-animation/README.md), the [animation engine guide](swing-ui-animation-core/README.md) and the [SVG guide](swing-ui-svg/README.md).

## Working with existing Swing code

You can [add Compose to an existing Swing app](docs/ARCHITECTURE.md#mounting-a-composition) one panel at a time and [use custom Swing components in Compose](docs/CUSTOM-COMPONENTS.md).

## Explore the samples

- [Todo app sample](samples/todo-app/README.md#run) shows an editable task list with completion and progress tracking.
- [Widgets gallery sample](samples/widgets-gallery/README.md#run) lets you try custom drawing, animated layouts, and a composable menu bar.

## Guides and stability

The API is pre-1.0, and minor releases may contain breaking changes. Using the libraries requires Kotlin 2.2 or newer.

- Find widgets in the [components guide](docs/COMPONENTS.md). The [API reference](https://jetbrains.github.io/compose-swing-ui/) documents the public API on `master`.
- Configure widgets with [styles](docs/STYLES.md) and [modifiers](docs/MODIFIERS.md).
- See [Writing components](docs/CUSTOM-COMPONENTS.md) to design and build reusable components.
- Verify UI behavior with the [testing guide](docs/TESTING-COMPONENTS.md) and check usage patterns with [static analysis](docs/STATIC-ANALYSIS.md).
- See the [contributing guide](CONTRIBUTING.md) for building and checking the project.

## License

Licensed under the Apache License, Version 2.0 - see [LICENSE](LICENSE).

See each module's `META-INF/NOTICE` and source headers for third-party attribution.
