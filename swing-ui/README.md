# Module swing-ui

The core library: Jetpack Compose's composition runtime wired to Swing, plus composable wrappers
over Swing components and layouts. Your UI is real `JButton`/`JLabel`/`JPanel` widgets, laid out by
Swing's own `LayoutManager`s; Compose drives state and composition.

## Usage

Drive composition into any Swing container with `setContent`, or use the `application` / `Window`
entry points:

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

Set a component's properties with its `style: SwingStyle<S> = SwingStyle` parameter. Add listeners, parent
data and decoration with its `modifier: SwingModifier = SwingModifier` parameter. Domain callbacks like
`onClick` stay ordinary parameters. See [`docs/STYLES.md`](../docs/STYLES.md). To wrap your own Swing component, use the public
`SwingNode` API — see [`docs/CUSTOM-COMPONENTS.md`](../docs/CUSTOM-COMPONENTS.md).

The complete component, layout, style, modifier, window, dialog, and menu API is documented in KDoc.

## Related

- [`README.md`](../README.md) — project overview and quick start.
- [`docs/CUSTOM-COMPONENTS.md`](../docs/CUSTOM-COMPONENTS.md) — building your own components.
- [`swing-ui-test/README.md`](../swing-ui-test/README.md) — testing your components.
