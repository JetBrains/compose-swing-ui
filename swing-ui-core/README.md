# Module swing-ui-core

The core composition engine over Swing: Jetpack Compose's composition runtime wired to Swing, `setContent`, windowing, layout, decoration, styles, generic modifiers, and the test harness runtime. Your UI is real `java.awt.Component`s; Compose drives state and composition.

## Usage

Drive composition into any Swing container with `setContent`, or use the `application` / `Window` entry points:

```kotlin
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
        // Core engine supports raw Swing nodes or host applications with their own widget set.
    }
    frame.setSize(400, 200)
    frame.isVisible = true
}
```

Styling and interaction flow through generic modifiers (`background`, `foreground`, `font`, `border`, `onPointerEvent`, `onKeyEvent`, etc.). To wrap custom components, use the public `SwingNode` API — see [`docs/CUSTOM-COMPONENTS.md`](../docs/CUSTOM-COMPONENTS.md).

## Related

- [`README.md`](../README.md) — project overview and quick start.
- [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) — runtime architecture.
- [`swing-ui-components/README.md`](../swing-ui-components/README.md) — Swing widget catalog.
- [`swing-ui-test/README.md`](../swing-ui-test/README.md) — testing your components.
