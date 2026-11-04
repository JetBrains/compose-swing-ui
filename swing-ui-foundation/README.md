# Module swing-ui-foundation

Foundation APIs for Compose Swing UI: constraint-based layout, drawing and decorations over real Swing components.

The public packages are under `org.jetbrains.compose.swing.foundation`.

## Usage

A `Row` or `Column` lays out its children and takes decoration modifiers. `weight` and `align` resolve only inside the scope of their container; see [Foundation](../docs/FOUNDATION.md).

```kotlin
Column(modifier = SwingModifier.padding(8).background(Color.WHITE, RectangleShape)) {
    Label("Title")
    Button("Close", onClick = onClose, modifier = SwingModifier.align(Alignment.End))
}
```

## Related

- [Compose Swing UI](../README.md) — project overview.
- [Foundation](../docs/FOUNDATION.md) — layout, drawing and decorations.
- [Containers and item cells](../docs/CUSTOM-CONTAINERS.md) — custom constraint-based containers.
