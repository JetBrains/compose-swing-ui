# Module swing-ui-animation

The animation engine for Compose Swing UI. It provides the familiar Compose animation APIs -
`animate*AsState`, `Animatable`, `updateTransition` / `Transition`, `rememberInfiniteTransition`,
easing curves (including `CubicBezierEasing`), and the `spring` / `tween` / `keyframes` specs - for
the `Float`, `Int`, and generic (`TwoWayConverter`) value types.

## Usage

Animations run with no extra wiring inside a `setContent { ... }` composition: they are driven by the
window's frame clock automatically, advancing at the display's refresh rate while an animation is in
flight and resting otherwise.

```kotlin
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue

val alpha by animateFloatAsState(if (visible) 1f else 0f)
```

For value types beyond `Float` / `Int`, supply a `TwoWayConverter` (e.g. to animate
`java.awt.Color`). The animation APIs are documented in KDoc.

## Related

- [`README.md`](../README.md) - project overview and quick start.
- [`swing-ui/README.md`](../swing-ui/README.md) - the core library.
