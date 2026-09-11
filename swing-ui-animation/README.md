# Module swing-ui-animation

The animation engine for Compose Swing UI, and the containers that animate a composable in, out and
between states. It provides the familiar Compose animation APIs - `animate*AsState`, `Animatable`,
`updateTransition` / `Transition`, `rememberInfiniteTransition`, easing curves (including
`CubicBezierEasing`), and the `spring` / `tween` / `keyframes` specs - for the `Float`, `Int`,
`java.awt.Color`, and generic (`TwoWayConverter`) value types.

## Usage

Animations run with no extra wiring inside a `setContent { ... }` composition: they are driven by the
window's frame clock automatically, advancing at the display's refresh rate while an animation is in
flight and resting otherwise.

```kotlin
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue

val alpha by animateFloatAsState(if (visible) 1f else 0f)
```

A `java.awt.Color` animates through `animateColorAsState`, which interpolates in Oklab and returns an
sRGB color. For value types beyond those, supply a `TwoWayConverter`. The animation APIs are
documented in KDoc.

`ScrollState.animateScrollTo` and `animateScrollBy` travel a scroll pane to a position over time instead
of in one write, holding the pane's position until they arrive.

## Animating a composable

`AnimatedVisibility` runs content through an enter and an exit transition as a boolean flips.
`AnimatedContent` settles on a target state, running the content it leaves out while the content it
moves to runs in, and traveling between their sizes. `Crossfade` fades between the contents of its
states and animates no size. `SwingModifier.animateContentSize` leaves a layout's content alone and
animates only the size the content asks for. Transitions combine with `+`.

```kotlin
AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically()) {
    Label(text = "details")
}
```

`AnimatedContent` and `Crossfade` compose each visible state's content separately. Use the state passed
to the content lambda to choose what to show, so outgoing content keeps its own state while it leaves.
For `AnimatedContent`, `contentKey` identifies states that should reuse the same content.

The content is laid out by a Foundation layout, so it takes `fillMaxWidth` or `fillMaxHeight` to fill
the container. `SwingModifier.animateEnterExit` gives one part of the content an enter and an exit of its
own, on the same transition, so the container waits for it. The part must be `Decoratable` and under a
Foundation layout parent; `Box` is one example.

## Related

- [`README.md`](../README.md) - project overview and quick start.
- [`swing-ui/README.md`](../swing-ui/README.md) - the core library.
