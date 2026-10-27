package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.ChildMeasurables
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.foundation.util.fastForEach
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.floor

/**
 * What a [Decoratable] component paints through, and how far it paints past its layout bounds: the steps its modifier
 * declares and the paint outsets its parent gives it. The library creates each value for one component and writes it
 * to [Decoratable.decoration]; a value never changes, and a change is a new value.
 *
 * Each step is a [Decorator], the first outermost: it paints at the layout bounds, or, when a layout modifier is
 * declared after it, at the box of the first such modifier. The *layout bounds* are the box measurement, placement,
 * alignment, `onPlaced`, `onSizeChanged` and hit testing see. The component's *bounds*, Swing's rectangle, are the
 * layout bounds plus the *paint outsets*, how far the component paints past them on each side. A Foundation
 * container gives a component all its paint outsets: the steps' [outsets][Decorator.outsets], the box of a layout
 * modifier a step paints at, what a rotating or scaling placement layer adds and, for a Foundation container, what its
 * children paint past it. Any other parent gives only the part of the steps' outsets the component's
 * [PaintOutsets][org.jetbrains.compose.swing.foundation.layout.PaintOutsets] value leaves in layout.
 *
 * The *layout box* is the box a Foundation container sizes and aligns. A layout modifier node reads it with
 * [layoutBoxInParent][org.jetbrains.compose.swing.foundation.layout.LayoutModifierNode.layoutBoxInParent].
 */
public class Decoration internal constructor(
    /** The steps the component's modifier declares. */
    internal val steps: DecorationSteps,
    /** The paint outsets: [NoPaintOutsets], the steps' own outsets, or insets of its own; never modified. */
    internal val heldPaintOutsets: Insets,
    /** The records of the Foundation container holding the component; null under any other parent. */
    internal val parentMeasurables: ChildMeasurables?,
    /** The records of the component's own children, where it is a Foundation container; null otherwise. */
    internal val childMeasurables: ChildMeasurables?,
    /**
     * Whether the steps move what the component paints: a placement layer rotates or scales it, or the steps paint
     * content the size of the layout bounds past them further than their outsets reach, as a step translating it
     * does. Also true where this holds for a descendant a Foundation container inside it placed.
     */
    internal val holdsTransform: Boolean,
    /** Whether every step answered [Decorator.isOpaque] with `true` when the library last gathered the steps. */
    internal val hasOpaqueSteps: Boolean,
) {
    /** Whether the component's modifier declares any decoration step. */
    public val isDecorated: Boolean get() = !steps.isEmpty

    /**
     * Returns [graphics], the superclass `getGraphics()` result of [decoratable], unchanged. Makes the steps' image
     * layers record on their next draw, as the caller can draw on it outside paint, with the exception that
     * [DecorationModifierNode.invalidateLayerProperties] names. Requests no repaint.
     * Call this from `getGraphics()` of a `Decoratable`, on the event dispatch thread.
     *
     * @throws IllegalArgumentException if [decoratable] is not a `java.awt.Component`.
     * @throws IllegalStateException if called off the event dispatch thread.
     */
    public fun getGraphics(
        decoratable: Decoratable,
        graphics: Graphics?,
    ): Graphics? {
        decoratable.requireComponent()
        checkEventDispatchThread()
        steps.onContentMayChange(outsidePaint = true)
        return graphics
    }

    /**
     * Whether [decoratable], painted through this decoration, still covers every pixel of its bounds: `false` where it
     * has paint outsets, or where a step paints translucently, cuts the content ([Decorator.isOpaque]) or paints at a
     * box smaller than the layout bounds. The steps' own opacity is the one the library last gathered; the boxes are
     * read at each call.
     *
     * @throws IllegalArgumentException if [decoratable] is not a `java.awt.Component`.
     */
    public fun isOpaque(decoratable: Decoratable): Boolean {
        val component = decoratable.requireComponent()
        return heldPaintOutsets == NoPaintOutsets && hasOpaqueSteps &&
            steps.paintsAtLayoutBox(component.width, component.height)
    }

    /**
     * How far the component paints past its layout bounds on each side; a new [Insets] the caller owns. Under a parent
     * that is not a Foundation container, the part of its decoration its `paintOutsets` value leaves in layout, none
     * by default.
     */
    public fun paintOutsets(): Insets =
        Insets(heldPaintOutsets.top, heldPaintOutsets.left, heldPaintOutsets.bottom, heldPaintOutsets.right)

    /**
     * [decoratable]'s layout bounds in its own coordinates: its bounds less the paint outsets; a new [Rectangle]. Named
     * as `SwingUtilities.getLocalBounds` is.
     *
     * @throws IllegalArgumentException if [decoratable] is not a `java.awt.Component`.
     */
    public fun localLayoutBounds(decoratable: Decoratable): Rectangle {
        val component = decoratable.requireComponent()
        return Rectangle(heldPaintOutsets.left, heldPaintOutsets.top, layoutWidth(component), layoutHeight(component))
    }

    /** [base] plus the paint outsets: [base] itself where there are none, otherwise a new [Insets]. */
    public fun insets(base: Insets): Insets =
        if (heldPaintOutsets == NoPaintOutsets) base else insets(base, Insets(0, 0, 0, 0))

    /**
     * Writes [base] plus the paint outsets into [into] and returns it: what `getInsets(Insets)` answers without
     * allocating. [base] may be [into]; a border's own [Insets] passed as [base] is left unchanged.
     */
    public fun insets(
        base: Insets,
        into: Insets,
    ): Insets {
        val outsets = heldPaintOutsets
        into.set(
            base.top + outsets.top,
            base.left + outsets.left,
            base.bottom + outsets.bottom,
            base.right + outsets.right,
        )
        return into
    }

    /**
     * Paints [content], the component itself in its own coordinates, through the decoration. The steps paint at the
     * layout bounds, or at the box of the layout modifier declared after them. Without a clip on [graphics], as in a
     * capture or a print, the whole component is painted.
     *
     * @throws IllegalArgumentException where a decorated component is handed a graphics that is not a Graphics2D.
     * @throws IllegalArgumentException if [decoratable] is not a `java.awt.Component`.
     */
    public fun paint(
        decoratable: Decoratable,
        graphics: Graphics,
        content: (Graphics) -> Unit,
    ) {
        val component = decoratable.requireComponent()
        val steps = steps
        if (steps.isEmpty) return content(graphics)
        require(graphics is Graphics2D) {
            "A decoration paints through Graphics2D, which is what Swing hands a component's paint; this one was " +
                "handed a ${graphics.javaClass.name}"
        }
        steps.onPaintBegun()
        val decorated = graphics.create() as Graphics2D
        try {
            if (decorated.clip == null) decorated.clipRect(0, 0, component.width, component.height)
            val start = decorated.transform
            val originX = heldPaintOutsets.left
            val originY = heldPaintOutsets.top
            decorated.translate(originX, originY)
            steps.paint(start, decorated, layoutWidth(component), layoutHeight(component)) { inner, _, _ ->
                val swing = inner.create()
                try {
                    swing.translate(-originX, -originY)
                    content(swing)
                } finally {
                    swing.dispose()
                }
            }
        } finally {
            decorated.dispose()
        }
    }

    /**
     * Whether ([x], [y]), in [decoratable]'s coordinates, hits it: inside the layout bounds, as its placement layers
     * rotate, scale and clip them, or, inside its bounds, where a visible child's own `contains` takes the point, as
     * for a child placed or painting past the layout bounds. A step that cuts its children to its box, such as a clip
     * or a fade, cuts the point too. Other paint outsets, such as a shadow's, are not hit.
     *
     * @throws IllegalArgumentException if [decoratable] is not a `java.awt.Component`.
     */
    public fun contains(
        decoratable: Decoratable,
        x: Int,
        y: Int,
    ): Boolean {
        val component = decoratable.requireComponent()
        if (inLayoutBounds(component, x, y)) return true
        val children = childMeasurables?.layoutPass
        val inBounds = !children.isNullOrEmpty() && x in 0 until component.width && y in 0 until component.height
        val width = layoutWidth(component)
        val height = layoutHeight(component)
        val point = if (inBounds) contentPoint(x, y) { steps.toChildContent(it, width, height) } else null
        return point != null &&
            children?.fastAny {
                val child = it.component
                !it.isLeftUnplaced && child.isVisible && child.contains(point.x - child.x, point.y - child.y)
            } == true
    }

    /**
     * Hands [repaint], the superclass repaint, [tm] and the area at ([x], [y]) of [width] by [height], in
     * [decoratable]'s coordinates, grown to all a change in it repaints: by how far the steps spread a change, such as
     * a blur's or a shadow's reach, held inside the bounds, or to the whole bounds while the steps move what
     * [decoratable] paints, as a rotating layer does. On the event dispatch thread, the area is the whole bounds until
     * the paint that [DecorationModifierNode.invalidateLayerProperties] asked for runs. Content changes must request a
     * repaint for decoration steps to show them on the next paint.
     * Call this from `repaint(long, int, int, int, int)` of a `Decoratable`.
     *
     * @throws IllegalArgumentException if [decoratable] is not a `java.awt.Component`.
     */
    @Suppress("LongParameterList") // JComponent.repaint's parameters, as forwarded.
    public fun repaint(
        decoratable: Decoratable,
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        repaint: (Long, Int, Int, Int, Int) -> Unit,
    ) {
        val component = decoratable.requireComponent()
        val redrawOwed = steps.onContentMayChange(outsidePaint = false)
        if (redrawOwed || stepsMoveContent(component)) {
            repaint(tm, 0, 0, component.width, component.height)
            return
        }
        val reach = steps.outsets
        val left = grownStart(x, reach.left)
        val top = grownStart(y, reach.top)
        val right = grownEnd(x, width, reach.right, component.width)
        val bottom = grownEnd(y, height, reach.bottom, component.height)
        repaint(tm, left, top, right - left, bottom - top)
    }

    // This expansion preserves coverage of every required pixel; direct and descendant requests still need it.

    /**
     * Hands [paint], `super.paintImmediately`, the area at ([x], [y]) of [width] by [height], in [decoratable]'s
     * coordinates, grown as [repaint] grows it. In a Foundation container, the area grows first by what each visible
     * child it touches spreads its part of the area into, if the child has a transform, steps with outsets or a step
     * that does not repaint within its outsets, or holds such a child: by the steps' reach, held inside the child's
     * bounds, or to the whole child if its steps move content or a step does not [repaint within its
     * outsets][Decorator.repaintsWithinOutsets]. Until the paint that
     * [DecorationModifierNode.invalidateLayerProperties] asked for runs, the area is the whole bounds, so no paint
     * shows the component at two values of what that paint changes. Decoration steps show content changes in this
     * paint. Call this from `paintImmediately(int, int, int, int)` of a `Decoratable`.
     *
     * @throws IllegalArgumentException if [decoratable] is not a `java.awt.Component`.
     */
    @Suppress("ForbiddenComment", "LongParameterList")
    public fun paintImmediately(
        decoratable: Decoratable,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        paint: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    ) {
        val component = decoratable.requireComponent()
        val redrawOwed = steps.onContentMayChange(outsidePaint = false)
        if (redrawOwed || stepsMoveContent(component)) return paint(0, 0, component.width, component.height)
        val area = Rectangle(x, y, width, height)
        val children = childMeasurables
        if (children != null && children.hasChildSpreadingRepaint) {
            children.addChildrenSpread(Rectangle(area), area, 0, 0, Rectangle())
        }
        // TODO Grow an already-expanded repaint region only once,
        // while still expanding direct paintImmediately requests.
        val reach = steps.outsets
        val left = grownStart(area.x, reach.left)
        val top = grownStart(area.y, reach.top)
        val right = grownEnd(area.x, area.width, reach.right, component.width)
        val bottom = grownEnd(area.y, area.height, reach.bottom, component.height)
        paint(left, top, right - left, bottom - top)
    }

    /** Whether a repaint that touches the component may have to grow. */
    internal val holdsSpreadingRepaint: Boolean
        get() =
            holdsTransform ||
                steps.outsets != NoPaintOutsets ||
                !steps.repaintsWithinOutsets ||
                childMeasurables?.hasChildSpreadingRepaint == true

    /**
     * The point whose content the placement layers paint at ([x], [y]), both in the component's own coordinates: the
     * layers are read back from the pixel's center, outermost first. Null where [clipped] cuts it away or no content
     * paints there; see [DecorationSteps.toContent].
     */
    internal fun contentPoint(
        x: Int,
        y: Int,
        clipped: Boolean,
    ): Point? = contentPoint(x, y) { steps.toContent(it, clipped) }

    /** Holds [None]. */
    public companion object {
        /** No decoration and no paint outsets: what a component holds until the library writes one. */
        public val None: Decoration =
            Decoration(DecorationSteps.None, NoPaintOutsets, null, null, holdsTransform = false, hasOpaqueSteps = true)
    }
}

/**
 * What [component]'s innermost clipping placed layer lets paint, in the component's own coordinates, as the steps
 * outside that layer map it; null where no placed layer clips.
 */
internal fun Decoration.clippedPaintBounds(component: Component): Rectangle? =
    steps.placedClipBounds(layoutWidth(component), layoutHeight(component))?.apply {
        translate(heldPaintOutsets.left, heldPaintOutsets.top)
    }

/** The width of [component]'s layout bounds: its width less the paint outsets. */
internal fun Decoration.layoutWidth(component: Component): Int =
    (component.width - heldPaintOutsets.left - heldPaintOutsets.right).coerceAtLeast(0)

/** The height of [component]'s layout bounds: its height less the paint outsets. */
internal fun Decoration.layoutHeight(component: Component): Int =
    (component.height - heldPaintOutsets.top - heldPaintOutsets.bottom).coerceAtLeast(0)

/**
 * Whether ([x], [y]), in [component]'s coordinates, is inside the layout rectangle, rotated, scaled and clipped as
 * the placement layers paint it.
 */
private fun Decoration.inLayoutBounds(
    component: Component,
    x: Int,
    y: Int,
): Boolean {
    val point = if (steps.isClippedOrTransformed) contentPoint(x, y, clipped = true) ?: return false else null
    val boxX = (point?.x ?: x) - heldPaintOutsets.left
    val boxY = (point?.y ?: y) - heldPaintOutsets.top
    return boxX in 0 until layoutWidth(component) && boxY in 0 until layoutHeight(component)
}

/**
 * Whether [component]'s own steps move what it paints: a layer rotates or scales it, or the steps paint content the
 * size of its layout bounds past them further than their outsets reach. [Decoration.holdsTransform] answers for a
 * component that is not a Foundation container; a Foundation container holds it for its descendants too, so its steps
 * are asked again.
 */
private fun Decoration.stepsMoveContent(component: Component): Boolean =
    holdsTransform && !steps.isEmpty &&
        (
            steps.isTransformed || childMeasurables == null ||
                steps.movesContent(layoutWidth(component), layoutHeight(component))
        )

/** Where an area starting at [start] starts, grown by [reach] and held at 0. */
private fun grownStart(
    start: Int,
    reach: Int,
): Int = maxOf(start.toLong() - reach, 0L).toInt()

/**
 * Where an area starting at [start] of [length] ends, grown by [reach] and held at [limit]; in Long, so an area as
 * large as Int allows is not wrapped around by the growth.
 */
private fun grownEnd(
    start: Int,
    length: Int,
    reach: Int,
    limit: Int,
): Int = minOf(start.toLong() + length + reach, limit.toLong()).toInt()

/**
 * Grows [area] by what each visible child that [asked] touches spreads its part of [asked] into.
 *
 * A child's reach is its steps' outsets, not its paint outsets: a clip declared before a blur leaves the child no
 * paint outsets, but the blur still reaches as far inside the clip.
 *
 * The part is grown even where the repaint is a sibling's over the child: a repaint Swing merged into this
 * container's, or one a stock component inside the child recorded, arrives in the same call, ungrown.
 *
 * Each child is tested against [asked], the area the repaint names, and not against [area] as it grows: every
 * decoration step paints from its own component's content alone, so a child the repaint does not touch paints the
 * same pixels as before wherever the grown area reaches it. A decorated container above this one is asked for the
 * area this one grew and adds what that area spreads into in turn, which changes no pixel.
 *
 * Both rectangles are in the coordinates of the component being repainted, where this container stands at
 * ([x], [y]). [scratch] is any rectangle; every level shares it.
 */
private fun ChildMeasurables.addChildrenSpread(
    asked: Rectangle,
    area: Rectangle,
    x: Int,
    y: Int,
    scratch: Rectangle,
) {
    layoutPass.fastForEach {
        val child = it.component
        val decoration = it.decoratable?.decoration
        if (!child.isVisible || decoration?.holdsSpreadingRepaint != true) return@fastForEach
        child.getBounds(scratch)
        scratch.translate(x, y)
        if (!asked.intersects(scratch)) return@fastForEach
        if (!decoration.steps.repaintsWithinOutsets || decoration.stepsMoveContent(child)) {
            return@fastForEach area.add(scratch)
        }
        val boundsX = scratch.x
        val boundsY = scratch.y
        val boundsWidth = scratch.width
        val boundsHeight = scratch.height
        Rectangle2D.intersect(asked, scratch, scratch)
        val children = decoration.childMeasurables
        // The level below shares the scratch, so what it adds to goes in the rectangle its container holds.
        val spread =
            if (children != null && children.hasChildSpreadingRepaint) {
                children.repaintSpread.also { region ->
                    region.setBounds(scratch)
                    children.addChildrenSpread(asked, region, boundsX, boundsY, scratch)
                }
            } else {
                scratch
            }
        val reach = decoration.steps.outsets
        val left = grownStart(spread.x - boundsX, reach.left)
        val top = grownStart(spread.y - boundsY, reach.top)
        val right = grownEnd(spread.x - boundsX, spread.width, reach.right, boundsWidth)
        val bottom = grownEnd(spread.y - boundsY, spread.height, reach.bottom, boundsHeight)
        spread.setBounds(boundsX + left, boundsY + top, right - left, bottom - top)
        area.add(spread)
    }
}

/**
 * The point [toContent] maps ([x], [y]) to, both in the component's own coordinates, read from the pixel's center in
 * layout coordinates; null where [toContent] answers false.
 */
private inline fun Decoration.contentPoint(
    x: Int,
    y: Int,
    toContent: (Point2D.Double) -> Boolean,
): Point? {
    val outsets = heldPaintOutsets
    // 0.5: the offset from a pixel's corner, where its integer coordinates point, to its center.
    val point = Point2D.Double(x - outsets.left + 0.5, y - outsets.top + 0.5)
    if (!toContent(point)) return null
    return Point(floor(point.x).toInt() + outsets.left, floor(point.y).toInt() + outsets.top)
}

/** The bounds of this shape grown by [outsets] on each side; this shape itself where [outsets] is none. */
internal fun Shape.outsetBy(outsets: Insets): Shape {
    if (outsets == NoPaintOutsets) return this
    val bounds = bounds2D
    return Rectangle2D.Double(
        bounds.x - outsets.left,
        bounds.y - outsets.top,
        bounds.width + outsets.left + outsets.right,
        bounds.height + outsets.top + outsets.bottom,
    )
}

/**
 * [component]'s layout bounds, in the layout coordinates of its parent; a new [Rectangle]. This decoration is the one
 * written to [component]. A Foundation container holding it moves its layout origin off its bounds' origin by its
 * paint outsets; any other parent places its children against `getInsets()`, which carry its outsets already.
 */
internal fun Decoration.layoutBoundsInParent(component: Component): Rectangle {
    val origin = parentMeasurables?.panel?.decoration?.heldPaintOutsets ?: NoPaintOutsets
    return Rectangle(
        component.x + heldPaintOutsets.left - origin.left,
        component.y + heldPaintOutsets.top - origin.top,
        layoutWidth(component),
        layoutHeight(component),
    )
}
