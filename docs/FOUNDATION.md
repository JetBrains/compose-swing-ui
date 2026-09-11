# Foundation

`swing-ui-foundation` brings androidx's parent-driven layout and its drawing surface to real Swing components: a
parent offers constraints, a child reports a size, and the parent places it; drawing and decorations paint through
Java2D. Swing still owns the component tree, the layout and paint cycles, and the final bounds.

Depend on `swing-ui-foundation` for `Row`, `Column`, `Box`, `Layout`, `Canvas` and the decoration modifiers. See
[`COMPONENTS.md`](COMPONENTS.md#containers-and-layout) for the standard Swing containers,
[`CUSTOM-CONTAINERS.md`](CUSTOM-CONTAINERS.md) to implement a container, and [`ARCHITECTURE.md`](ARCHITECTURE.md)
for the runtime.

<!--- INCLUDE .*foundation-layout-content.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.components.*
import org.jetbrains.compose.swing.components.button.*
import org.jetbrains.compose.swing.components.layout.*
import org.jetbrains.compose.swing.foundation.graphics.*
import org.jetbrains.compose.swing.foundation.layout.*
import org.jetbrains.compose.swing.modifier.*
import org.jetbrains.compose.swing.modifier.layout.*
import java.awt.Color

@Composable
fun FoundationLayoutContentExample() {
----- SUFFIX .*foundation-layout-content.*
}
-->

## Layout

### The layout model

A layout pass has three steps:

1. The parent measures the children it needs under `Constraints` that it chooses.
2. The parent chooses its own size from the measured children and its incoming constraints.
3. The parent places each measured child inside its own inner rectangle.

This is the parent-measures-child model of Compose UI, run inside Swing's `LayoutManager2` lifecycle on the Swing
Event Dispatch Thread (EDT).

### Constraints

`Constraints` gives the minimum and maximum width and height that a child may occupy. An axis is:

- bounded when its maximum is finite;
- unbounded when its maximum is `Int.MAX_VALUE`; and
- exact when its minimum and maximum are equal. An exact axis is bounded.

Minimum values must be non-negative and cannot exceed their matching maximum. `Constraints.Unbounded`
uses zero minimums and unbounded maximums.

Under an exact offer, a component takes that size whatever its preferred, minimum or maximum size.

Sizes and positions are AWT's integer user-space coordinates, as Swing's own sizes are; the graphics transform
maps them to device pixels.

### Measuring and placing

A `MeasurePolicy` receives the container's children in declaration order as `Measurable` objects. It
measures each child, decides the container's size, and returns a `MeasureResult` with a placement
block:

```kotlin
layout(width, height) {
    first.placeRelative(0, 0)
    second.placeRelative(first.width, 0)
}
```

<!--- CLEAR -->

`Measurable.measure(constraints)` returns an immutable `Placeable` with the measured `width` and
`height`. Each call returns an independent result, so a policy may retain an earlier result while it
measures the same child again. Place the result that corresponds to the constraints the policy chose.

The placement block runs inside the container's inner rectangle, after its insets:

- `parentWidth` gives the container's inner width, and `isLeftToRight` reports its orientation.
- `place(x, y)` measures `x` from the left edge.
- `placeRelative(x, y)` measures `x` from the leading edge. It mirrors the position when the
  container's `ComponentOrientation` is right-to-left.

A policy must return a non-negative size. It should use `constraints.constrainWidth` and
`constraints.constrainHeight` when its size comes from child measurements.

### Intrinsic size

Swing asks a container for its preferred and minimum sizes without offering a width or height. A preferred size
query asks the policy's `maxIntrinsicWidth` and `maxIntrinsicHeight`, and a minimum size query its
`minIntrinsicWidth` and `minIntrinsicHeight`, each with the other axis unbounded. A child answers the max functions
with its preferred size and the min functions with its minimum size, through the intrinsic functions of its layout
modifiers. By default, the intrinsic functions of a policy run its `measure`, as
androidx's do, against a stand-in whose extent along the asked axis is the child's intrinsic size, whatever constraints
it is measured under. A policy that divides bounded space, such as a weighted linear layout, overrides all four. A
container's maximum size is unbounded unless one is set.

### Where constraints stop

An explicit `preferredSize` on a constraint-based container is authoritative: it answers a constrained
measurement without running the container's policy. Without one, constraints continue through any depth of
`Row`, `Column`, `Box` and `Layout`. A component of your own can implement `Constrainable` to answer for the
offered constraints.

A stock Swing widget or a foreign Swing container answers with its preferred or minimum size, held inside the
offered constraints; it cannot reflow for an offered width, since Swing has no width-for-height query. Constraints
also stop at a `Panel` backed by a Swing layout manager. Put a constraint-based container inside that panel when
its descendants need layout modifiers:

```kotlin
Panel(PanelLayout.Flow()) {
    Box {
        Label("Preview", modifier = SwingModifier.aspectRatio(16f / 9f))
    }
}
```

<!--- KNIT example-foundation-layout-content-01.kt -->

### Layout modifiers

`Layout`, `Row`, `Column` and `Box` expose `ConstrainedScope`, whose modifiers take part in measurement. Order
matters: constraints travel from the outermost modifier toward the component, while measured sizes and placement
offsets travel back out. `start` and `end` mirror under a right-to-left orientation; the `absolute` variants use
`left` and `right` and never mirror. `padding` and `offset` move the component's baseline too, so
`alignByBaseline()` stays correct through them.

Parent-data modifiers such as `weight` and `align` fold in declaration order and reach a policy as
`Measurable.parentData`: `weight(2f).weight(1f)` uses `1f`, while
`weight(1f).align(Alignment.Bottom)` keeps both.
[Parent data and layout modifiers](CUSTOM-CONTAINERS.md#parent-data-and-layout-modifiers) describes these types.

### Observing layout

`onSizeChanged(callback)` delivers the component's size after its parent places it at a size that differs from the
last report, and `onPlaced(callback)` delivers its bounds in the parent after they change.

```kotlin
Box {
    Label(
        "Preview",
        modifier = SwingModifier
            .onSizeChanged { size -> println("Size: $size") }
            .onPlaced { bounds -> println("Bounds in parent: $bounds") },
    )
}
```

<!--- KNIT example-foundation-layout-content-02.kt -->

### `Row` and `Column`

`Row` arranges children horizontally in reading order. `Column` arranges them vertically from top to
bottom.

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8), horizontalAlignment = Alignment.Start) {
    Row(modifier = SwingModifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button("Back", onClick = {})
        Button("Forward", onClick = {})
    }
    Panel(PanelLayout.Flow(hgap = 8, vgap = 4), modifier = SwingModifier.weight(1f)) {
        Label("Content")
    }
    Label("Status", modifier = SwingModifier.align(Alignment.CenterHorizontally))
}
```

<!--- KNIT example-foundation-layout-content-03.kt -->

In both containers:

- unweighted children normally take the size they prefer, limited by the space left;
- `weight(value, fill)` divides remaining main-axis space in proportion to positive weights;
- `fill = true` makes a weighted child occupy its full share, while `false` lets it settle for the
  size it prefers and leaves the rest to the arrangement;
- the arrangement places unused main-axis space;
- the container alignment places children on the cross axis; and
- a child's `align` overrides the container's cross-axis alignment.

An explicit Swing `maximumSize` caps the extent offered to a child. It applies to the child as a whole, outside
its layout modifiers: the outermost modifier receives the capped offer.

On an unbounded main axis, weighted children share only what the container's minimum extent leaves after the other
children; the container's preferred size is large enough for each weighted child to get its preferred extent at its
weight.

#### Filling bounded space

`fillMaxWidth(fraction)`, `fillMaxHeight(fraction)` and `fillMaxSize(fraction)` request a fraction from `0f`
through `1f` of the offered maximum, `1f` by default, while staying within the offered bounds. An unbounded axis
is left unchanged. Like other layout modifiers, their position in the chain changes the constraints that later
modifiers and the component receive.

#### Arrangements and alignments

`Arrangement` distributes leftover main-axis space and `Alignment` positions children on the cross axis, under
androidx's names. `Arrangement.Absolute` keeps a row's children packed left to right under either orientation.


### `Box`

`Box` stacks children in declaration order. Its size comes from the greatest width and height among
children that do not use `matchParentSize()`. Children align according to the box's
`contentAlignment` unless they declare an individual `align`:

```kotlin
Box(contentAlignment = Alignment.Center) {
    Panel(PanelLayout.Border(), modifier = SwingModifier.matchParentSize()) {}
    Label("Layered content")
    Label("Badge", modifier = SwingModifier.align(Alignment.TopEnd).zIndex(1f))
}
```

<!--- KNIT example-foundation-layout-content-04.kt -->

In `BoxScope`:

- `matchParentSize()` does not influence the box's own size. After the other children determine that
  size, the box measures the child again with fixed constraints for the resolved extent. An explicit
  Swing `maximumSize` may keep the child smaller.
- `fillMaxWidth()` and `fillMaxHeight()` expand the child to fill one bounded axis, up to an explicit
  `maximumSize`, while contributing its preferred size along the other. `fillMaxSize()` does both. On
  an unbounded fill axis, the child also keeps its preferred size.
- `zIndex(value)` controls paint and hit-test order. Children with higher values sit above lower
  values regardless of declaration order. Equal values preserve declaration order, with the later
  child on top. Multiple `zIndex` declarations add their values.

### Visibility

`Row`, `Column`, `Box`, and custom `Layout` policies measure and place a child whose Swing
`isVisible` value is `false`. The child keeps its layout space but does not paint or receive input.
To close the gap, don't compose the child. Regular Swing managers differ: some reserve hidden children and others
collapse them.

### Scoped modifiers

Foundation's layout modifiers resolve only in content whose container honors them. The content of each
container offers these scopes:

| Content of | Scopes |
|---|---|
| `Row` | `RowScope`, `ConstrainedScope` |
| `Column` | `ColumnScope`, `ConstrainedScope` |
| `Box` | `BoxScope`, `ConstrainedScope` |
| `Layout` | `ConstrainedScope` |

A container's scopes hide the scopes of the containers around it: a label in a `Box` inside a `Row`
cannot declare the row's `weight`. The content of `setContent` and of a Swing container, such as a `Panel`, a
`ToolBar`, a `Window` or a `SwingNode` container, offers none of these either, because the layout manager placing
that content reads none of them. A modifier that reaches a container unable to honor it is refused when it is
applied.

A modifier of your own, for a container you also write, goes in a scope of your own extending
`ConstrainedScope`, as the `StackScope` example in [`CUSTOM-CONTAINERS.md`](CUSTOM-CONTAINERS.md) shows;
it builds on `ConstrainedScope`'s own modifiers the same way theirs do.

### Choosing a container

- `Row`, `Column` or `Box` for single-axis or stacked layouts with Compose-style weights, arrangements and
  alignments.
- `Layout` for a placement policy of your own; [`CUSTOM-CONTAINERS.md`](CUSTOM-CONTAINERS.md) develops one, with
  custom content scopes and layout constraints.
- `Panel(PanelLayout.Xxx)` for a Swing layout manager's behavior, with a `Box` or another constraint-based
  container inside it wherever its descendants need layout modifiers.

## Graphics

`Canvas`, `DrawScope`, brushes and shapes draw through Java2D in the same user space as layout. The underlying
`Graphics2D` stays available for whatever the declarative helpers do not cover.

<!--- INCLUDE .*foundation-graphics-content.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Dimension

@Composable
fun FoundationGraphicsContentExample() {
----- SUFFIX .*foundation-graphics-content.*
}
-->

### Drawing with `Canvas` and `DrawScope`

`Canvas` is a real Swing component. Its trailing block runs on the EDT while the component paints,
with a `DrawScope` receiver. The scope supplies the current `size`, `width`, `height` and `center`,
declarative primitives such as `drawLine`, `drawRect`, `drawCircle`, `drawPath`, `drawImage` and
`drawText`, plus the underlying `Graphics2D` as `graphics`. Primitives take `Float` coordinates; the
positioned ones also take a `Point2D`, and the sized ones a `Dimension2D`.

```kotlin
@Composable
fun CanvasExample() {
    Canvas(modifier = SwingModifier.preferredSize(Dimension(120, 120))) {
        drawCircle(
            Color.BLUE,
            radius = minOf(width, height) / 4f,
            centerX = width / 2f,
            centerY = height / 2f,
        )
    }
}
```

<!--- KNIT example-foundation-graphics-content-01.kt -->

State read directly in the draw block is observed. Changing it repaints this component without
recomposing or laying it out again. Drawing happens inside the area left after the component's
border.

The scope takes Java2D values: `Color` and other `Paint` implementations supply fills, `Stroke` supplies line
style, `java.awt.Shape` supplies paths, and `TextLayout` supplies measured text. Use `graphics` directly for
operations specific to Java2D.

### Drawing modifiers

`SwingModifier.drawBehind { ... }` paints into a decorated component before the content declared after
the modifier, without reserving space. `SwingModifier.drawWithContent { ... }` exposes `drawContent()`
so the block can choose where that content is painted. Both observe state read by the drawing block and
require `Decoratable`.

### Brushes and shapes

`Brush` is a size-aware fill. Use `Brush.of(paint)` for a fixed Java2D `Paint`, or use one of the
gradient factories: `horizontalGradient`, `verticalGradient`, `linearGradient` and
`radialGradient`. The first two span the decorated box; `linearGradient` places its points relative to
the decorated box's top-left corner, and `radialGradient` spreads from its center.

Brushes compare their value inputs structurally. A caller-owned `Paint` passed to `Brush.of` is
compared by identity, so remember or otherwise hoist it when rebuilding a modifier during
recomposition.

`Shape` is the corresponding size-aware outline used by clipping and shaped borders. Foundation
provides `RectangleShape`, `CircleShape` and `RoundedCornerShape`; `Shape.of(awtShape)` adapts a
caller-owned `java.awt.Shape` whose coordinates do not change with the component's size. A custom
shape implements `outline(width, height)` and returns an outline relative to the decorated box's
top-left corner.

### Decorations

The decoration modifiers `clip`, `background`, `border`, `alpha`, `blur` and `shadow`, and the drawing modifiers,
wrap what a component paints in declaration order: the first declared is outermost, and later declarations paint
inside earlier ones. Unlike the layout modifiers in [Scoped modifiers](#scoped-modifiers), they are plain
`SwingModifier` extensions that resolve wherever a modifier does, but reach only a component that implements
`Decoratable`. `Canvas` and the `Row`, `Column`, `Box` and `Layout` containers do. A stock widget or a `Panel`
refuses them with an error naming `Decoratable`;
[Making a component decoratable](#making-a-component-decoratable) opts a component of your own in.

A decoration paints inside the component's bounds, and a padding, in either order, sits outside them.

- `background(color)` without a shape is the Swing property; pass a `Shape` or a `Brush` for the decoration.
- A `clip` declared before a `border` only cuts its line; pass the clip's shape to the border for the line to
  follow it.
- A clip outside a blur can cut its halo.

A modifier of your own can combine decorations, as `card` does:

<!--- INCLUDE .*foundation-card.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.components.*
import org.jetbrains.compose.swing.components.layout.*
import org.jetbrains.compose.swing.foundation.graphics.*
import org.jetbrains.compose.swing.foundation.layout.*
import org.jetbrains.compose.swing.modifier.*
import java.awt.Color

-->

```kotlin
fun SwingModifier.card(): SwingModifier =
    border(width = 1, color = Color.GRAY, shape = RoundedCornerShape(8f)).background(Color.WHITE, RoundedCornerShape(8f))

@Composable
fun PaddedCard() {
    Panel(PanelLayout.Flow()) {
        Box {
            Box(modifier = SwingModifier.padding(16).card()) {
                Label("Padded")
            }
        }
    }
}
```

<!--- KNIT example-foundation-card-01.kt -->

### Writing a decorator or draw node

A `Decorator` paints one step of a decoration: write it as a `data class`, call the continuation to paint the
content, and declare it with `decoration`:

<!--- INCLUDE .*foundation-outlined.*
import org.jetbrains.compose.swing.foundation.graphics.*
import org.jetbrains.compose.swing.modifier.*

-->

```kotlin
fun SwingModifier.outlined(outline: Decorator): SwingModifier = decoration(outline)
```

<!--- KNIT example-foundation-outlined-01.kt -->

An effect that reads the content's pixels calls the continuation inside `ImageLayer.record` and draws the layer.

A step that keeps state across paints extends `DecorationModifierNode`, or `DrawModifierNode` to draw through a
`ContentDrawScope`, and declares its element through the same `decoration` function. State read in `draw()` is
observed, and a change repaints the component without laying it out again. The library gathers a step's
`isOpaque` after each modifier pass, so an element's `update` needs no call for it. Between passes, a node that
changes a plain field it paints from calls `invalidateDraw()`, or `invalidateDecoration()` when its outsets or `isOpaque`
change.

`ImageLayer` is an offscreen raster: `record` replaces its recording, `draw` draws it, and `filter` post-processes
it. `alpha`, `scaleX`, `scaleY`, `translationX`, `translationY`, `rotationZ`, `pivotOffset` and `renderEffect` are
androidx's `GraphicsLayer` properties of the same names and defaults, and apply at the next `draw`; `BlurEffect` is
a `RenderEffect`. `DrawScope.record(layer) { ... }` records drawing aligned to the device pixels the scope draws on,
so inside `drawWithContent`, `record(layer) { this@drawWithContent.drawContent() }` records the content, which the
block can then filter or blur and draw as one image. Create a layer with `rememberImageLayer()` in composition,
which releases it when the composition leaves, or call `release()` on one you construct.

### Making a component decoratable

To make a component of your own decoratable, implement `Decoratable`. The library writes its `decoration`, and the
component stores it and applies it, as it applies its `Border`:

<!--- INCLUDE .*foundation-decoratable.*
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import java.awt.Graphics
import javax.swing.JComponent
-->

```kotlin
class Card :
    JComponent(),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)
}
```

<!--- KNIT example-foundation-decoratable-01.kt -->

The decoration contract is the `decoration` property plus three overrides: `paint`, `contains` and `isOpaque`. The
component's sizes answer as for any Swing component, and its decoration is clipped at its bounds. A container adds
`override fun isPaintingOrigin(): Boolean = decoration.isDecorated`, as `JLayer` does, so a child repainting itself
alone is painted through the decoration. Its children can declare decorations regardless of what scope, if any, the
container hands its content.

## Relationship to Compose UI/Foundation

`Row`, `Column`, `Box` and their shared row/column measurement policy are ported from AndroidX's
`foundation-layout`; see [`swing-ui-foundation`'s `META-INF/NOTICE`](../swing-ui-foundation/src/main/resources/META-INF/NOTICE)
for the synced version.

The model follows Compose UI, adapted to Swing:

- Geometry uses AWT integer user-space coordinates instead of `Dp` and `IntSize`.
- Layout direction comes from `ComponentOrientation`.
- Intrinsic measurement maps to Swing's argument-less `preferredSize` and `minimumSize` queries.
- Placement ends in `Component.setBounds` on a real Swing component.
