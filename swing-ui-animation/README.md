# Module swing-ui-animation

The animation engine for Compose Swing UI, and the containers that animate a composable in, out and between states. It provides the familiar Compose animation APIs.

## Usage

Animations run with no extra wiring inside a `setContent { ... }` composition: they advance on their own while an animation is in flight and rest otherwise.

```kotlin
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier

val alpha by animateFloatAsState(if (visible) 1f else 0f)
Box(SwingModifier.alpha(alpha)) { Label("Saved") }
```

A `java.awt.Color` animates through `animateColorAsState`, which interpolates in Oklab and returns an sRGB color. For value types beyond those, supply a `TwoWayConverter`. The animation APIs are documented in KDoc.

`ScrollState.animateScrollTo` and `animateScrollBy` travel a scroll pane to a position over time instead of in one write, holding the pane's position until they arrive. Both are suspend functions; launch them from `rememberCoroutineScope()` on the Event Dispatch Thread.

Opt in to experimental APIs with the annotation they declare, such as `ExperimentalAnimationApi`, `ExperimentalAnimationSpecApi`, or `ExperimentalTransitionApi`.

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

- [Compose Swing UI](../README.md) - project overview and quick start.
- [`swing-ui`](../swing-ui/README.md) - the core library.
