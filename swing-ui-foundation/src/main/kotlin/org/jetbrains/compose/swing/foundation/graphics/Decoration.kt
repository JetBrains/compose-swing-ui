package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.ChildMeasurables
import org.jetbrains.compose.swing.foundation.util.fastAny
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
 * declares and the paint outsets its Foundation container worked out for it. The library creates each value for one
 * component and writes it to [Decoratable.decoration]; a value never changes, and a change is a new value.
 *
 * Each step is a [Decorator], the first outermost: it paints at the layout bounds, or, when a layout modifier is
 * declared after it, at the box of the first such modifier. The *layout bounds* are the box measurement, placement,
 * alignment, `onPlaced`, `onSizeChanged` and hit testing see. The component's *bounds*, Swing's rectangle, are the
 * layout bounds plus the *paint outsets*, how far the component paints past them on each side. Only a Foundation
 * container gives a component paint outsets: the steps' [outsets][Decorator.outsets], the box of a layout modifier a
 * step paints at, what a rotating or scaling placement layer adds and, for a Foundation container, what its children
 * paint past it.
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
     * Whether a placement layer rotates or scales the component, or a descendant a Foundation container inside it
     * placed.
     */
    internal val holdsTransform: Boolean,
    /** Whether every step answered [Decorator.isOpaque] with `true` when the library last gathered the steps. */
    internal val hasOpaqueSteps: Boolean,
) {
    /** Whether the component's modifier declares any decoration step. */
    public val isDecorated: Boolean get() = !steps.isEmpty

    /**
     * Whether [component], painted through this decoration, still covers every pixel of its bounds: `false` where it
     * has paint outsets, or where a step paints translucently, cuts the content ([Decorator.isOpaque]) or paints at a
     * box smaller than the layout bounds. The steps' own opacity is the one the library last gathered; the boxes are
     * read at each call.
     */
    public fun isOpaque(component: Component): Boolean =
        heldPaintOutsets == NoPaintOutsets && hasOpaqueSteps &&
            steps.paintsAtLayoutBox(component.width, component.height)

    /**
     * How far the component paints past its layout bounds on each side; a new [Insets] the caller owns. None under a
     * parent that is not a Foundation container.
     */
    public fun paintOutsets(): Insets =
        Insets(heldPaintOutsets.top, heldPaintOutsets.left, heldPaintOutsets.bottom, heldPaintOutsets.right)

    /**
     * [component]'s layout bounds in its own coordinates: its bounds less the paint outsets; a new [Rectangle]. Named
     * as `SwingUtilities.getLocalBounds` is.
     */
    public fun localLayoutBounds(component: Component): Rectangle =
        Rectangle(heldPaintOutsets.left, heldPaintOutsets.top, layoutWidth(component), layoutHeight(component))

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
     */
    public fun paint(
        component: Component,
        graphics: Graphics,
        content: (Graphics) -> Unit,
    ) {
        val steps = steps
        if (steps.isEmpty) return content(graphics)
        require(graphics is Graphics2D) {
            "A decoration paints through Graphics2D, which is what Swing hands a component's paint; this one was " +
                "handed a ${graphics.javaClass.name}"
        }
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
     * Whether ([x], [y]), in [component]'s coordinates, hits it: inside the layout bounds, as its placement layers
     * rotate, scale and clip them, or, while a placement layer on it or a descendant rotates or scales, where a
     * visible child's own `contains` takes the point. Other paint outsets, such as a shadow's, are not hit.
     */
    public fun contains(
        component: Component,
        x: Int,
        y: Int,
    ): Boolean {
        if (inLayoutBounds(component, x, y)) return true
        val inBounds = holdsTransform && x in 0 until component.width && y in 0 until component.height
        val point = if (inBounds) contentPoint(x, y, clipped = true) else null
        return point != null &&
            childMeasurables?.layoutPass?.fastAny {
                val child = it.component
                !it.isLeftUnplaced && child.isVisible && child.contains(point.x - child.x, point.y - child.y)
            } == true
    }

    /** The width of [component]'s layout bounds: its width less the paint outsets. */
    internal fun layoutWidth(component: Component): Int =
        (component.width - heldPaintOutsets.left - heldPaintOutsets.right).coerceAtLeast(0)

    /** The height of [component]'s layout bounds: its height less the paint outsets. */
    internal fun layoutHeight(component: Component): Int =
        (component.height - heldPaintOutsets.top - heldPaintOutsets.bottom).coerceAtLeast(0)

    /** Whether its Foundation parent gathers its paint bounds: it has paint outsets or holds a transform. */
    internal val needsGathering: Boolean get() = holdsTransform || heldPaintOutsets != NoPaintOutsets

    /**
     * The point whose content the placement layers paint at ([x], [y]), both in the component's own coordinates: the
     * layers are read back from the pixel's center, outermost first. Null where [clipped] cuts it away or no content
     * paints there; see [DecorationSteps.toContent].
     */
    internal fun contentPoint(
        x: Int,
        y: Int,
        clipped: Boolean,
    ): Point? {
        // 0.5: the offset from a pixel's corner, where its integer coordinates point, to its center.
        val point = Point2D.Double(x - heldPaintOutsets.left + 0.5, y - heldPaintOutsets.top + 0.5)
        if (!steps.toContent(point, clipped)) return null
        return Point(floor(point.x).toInt() + heldPaintOutsets.left, floor(point.y).toInt() + heldPaintOutsets.top)
    }

    /**
     * Whether ([x], [y]), in [component]'s coordinates, is inside the layout rectangle, rotated, scaled and clipped as
     * the placement layers paint it.
     */
    private fun inLayoutBounds(
        component: Component,
        x: Int,
        y: Int,
    ): Boolean {
        val point = if (steps.isClippedOrTransformed) contentPoint(x, y, clipped = true) ?: return false else null
        val boxX = (point?.x ?: x) - heldPaintOutsets.left
        val boxY = (point?.y ?: y) - heldPaintOutsets.top
        return boxX in 0 until layoutWidth(component) && boxY in 0 until layoutHeight(component)
    }

    /** Holds [None]. */
    public companion object {
        /** No decoration and no paint outsets: what a component holds until the library writes one. */
        public val None: Decoration =
            Decoration(DecorationSteps.None, NoPaintOutsets, null, null, holdsTransform = false, hasOpaqueSteps = true)
    }
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
 * This component's layout bounds, in its parent's layout coordinates; a new [Rectangle]. A component that is not
 * [Decoratable] takes its bounds as its layout bounds. A Foundation parent's paint outsets move its layout origin off
 * its bounds' origin; any other parent places its children against `getInsets()`, which carry its outsets already.
 */
internal val Component.layoutBounds: Rectangle
    get() {
        val outsets = (this as? Decoratable)?.decoration?.heldPaintOutsets ?: NoPaintOutsets
        val parentDecoration = (parent as? Decoratable)?.decoration
        val origin =
            if (parentDecoration?.childMeasurables == null) {
                NoPaintOutsets
            } else {
                parentDecoration.heldPaintOutsets
            }
        return Rectangle(
            x + outsets.left - origin.left,
            y + outsets.top - origin.top,
            (width - outsets.left - outsets.right).coerceAtLeast(0),
            (height - outsets.top - outsets.bottom).coerceAtLeast(0),
        )
    }
