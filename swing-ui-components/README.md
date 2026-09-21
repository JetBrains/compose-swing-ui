# Module swing-ui-components

The Swing widget catalog and widget-specific modifiers for Compose Swing UI. Built against `:swing-ui-core`'s public API only.

## Usage

Use composable wrappers over standard Swing components:

```kotlin
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() = SwingUtilities.invokeLater {
    val frame = JFrame("Counter").apply { defaultCloseOperation = JFrame.EXIT_ON_CLOSE }
    frame.setContent {
        var count by remember { mutableIntStateOf(0) }
        Panel {
            Label("Count: $count")
            Button("Increment", onClick = { count++ })
        }
    }
    frame.setSize(400, 200)
    frame.isVisible = true
}
```

Set a component's properties with its `style: SwingStyle<S> = SwingStyle` parameter. Add listeners, parent data and decoration with its `modifier: SwingModifier = SwingModifier` parameter. Domain callbacks like `onClick` stay ordinary parameters. See [`docs/STYLES.md`](../docs/STYLES.md).

`ScrollState.animateScrollTo` and `animateScrollBy` travel a scroll pane to a position over time instead
of in one write, holding the pane's position until they arrive.

## Related

- [`README.md`](../README.md) — project overview and quick start.
- [`docs/COMPONENTS.md`](../docs/COMPONENTS.md) — complete component catalog.
- [`docs/CUSTOM-COMPONENTS.md`](../docs/CUSTOM-COMPONENTS.md) — building custom components.
- [`swing-ui-core/README.md`](../swing-ui-core/README.md) — core composition engine.
- [`swing-ui-animation-core/README.md`](../swing-ui-animation-core/README.md) - the animation engine behind animated scrolling.
