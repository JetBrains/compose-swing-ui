# Module swing-ui-animation

The containers that animate a composable in, out and between states, on the animation engine of
[`swing-ui-animation-core`](../swing-ui-animation-core/README.md). A `java.awt.Color` animates through
`animateColorAsState`, which interpolates in Oklab and returns an sRGB color. Opt in to the experimental
animation APIs with `ExperimentalAnimationApi`.

## Animating a composable

`AnimatedVisibility` runs content through an enter and an exit transition as a boolean flips. `AnimatedContent` settles on a target state, running the content it leaves out while the content it moves to runs in, and traveling between their sizes. `Crossfade` fades between the contents of its states and takes the room both need while both are on screen. `SwingModifier.animateContentSize` animates the size the content asks for, placing the content inside the animated box and clipping it to that box. The component must be `Decoratable`. Transitions combine with `+`.

```kotlin
var expanded by remember { mutableStateOf(false) }
Button("Details", onClick = { expanded = !expanded })
AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically()) {
    Label(text = "details")
}
```

`AnimatedContent` and `Crossfade` compose each visible state's content separately. Use the state passed to the content lambda to choose what to show, so outgoing content keeps its own state while it leaves. For `AnimatedContent`, `contentKey` identifies states that should reuse the same content.

The content is laid out by a Foundation layout, so it takes `fillMaxWidth` or `fillMaxHeight` to fill the container. `SwingModifier.animateEnterExit` gives one part of the content an enter and an exit of its own, on the same transition, so the container waits for it. The part must be `Decoratable` and under a Foundation layout parent; `Box` is one example.

## Related

- [`swing-ui-animation-core/README.md`](../swing-ui-animation-core/README.md) - the animation engine.
- [Compose Swing UI](../README.md) - project overview and quick start.
- [`swing-ui-core`](../swing-ui-core/README.md) - the core library.
