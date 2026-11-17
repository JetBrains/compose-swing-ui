# Module swing-ui-animation-core

The animation engine for Compose Swing UI. It provides the familiar Compose animation APIs - `animate*AsState`,
`Animatable`, `updateTransition` / `Transition`, `rememberInfiniteTransition`, easing curves (including
`CubicBezierEasing`), and the `spring` / `tween` / `keyframes` specs - for the `Float`, `Int`,
`java.awt.Dimension`, `java.awt.Point`, and generic (`TwoWayConverter`) value types.

## Usage

Animations run with no extra wiring inside a `setContent { ... }` composition: they are driven by the
window's frame clock automatically, advancing at the display's refresh rate while an animation is in
flight and resting otherwise.

```kotlin
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue

val alpha by animateFloatAsState(if (visible) 1f else 0f)
```

For value types beyond those, supply a `TwoWayConverter`. The animation APIs are documented in KDoc.

Opt in to experimental APIs with the annotation they declare, `ExperimentalAnimationSpecApi` or `ExperimentalTransitionApi`.

## Related

- [`README.md`](../README.md) - project overview and quick start.
- [`swing-ui-animation/README.md`](../swing-ui-animation/README.md) - the containers that animate a composable.
- [`swing-ui-core/README.md`](../swing-ui-core/README.md) - the core library.
