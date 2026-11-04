# Module swing-ui

The core library: Jetpack Compose's composition runtime wired to Swing, plus composable wrappers over Swing components and layouts. Your UI is real `JButton`/`JLabel`/`JPanel` widgets, laid out by Swing's own `LayoutManager`s; Compose drives state and composition.

## Usage

Drive composition into a `JComponent`, `Window`, `JMenuBar` or `JPopupMenu` with `setContent`, called on the Event Dispatch Thread. It returns a `DisposableHandle` that disposes the composition. Or use the `application` / `Window` entry points:

```kotlin
frame.setContent {
    var count by remember { mutableIntStateOf(0) }
    Panel {
        Label("Count: $count")
        Button("Increment", onClick = { count++ })
    }
}
```

Set a component's properties with its `style: SwingStyle<S> = SwingStyle` parameter. Add listeners, parent data and decoration with its `modifier: SwingModifier = SwingModifier` parameter. Domain callbacks like `onClick` stay ordinary parameters. See [Styles](../docs/STYLES.md). To wrap your own Swing component, use the public `SwingNode` API — see [Writing components](../docs/CUSTOM-COMPONENTS.md).

The API is documented in KDoc.

## Related

- [Compose Swing UI](../README.md) — project overview and quick start.
- [Writing components](../docs/CUSTOM-COMPONENTS.md) — building your own components.
- [`swing-ui-test`](../swing-ui-test/README.md) — testing your components.
