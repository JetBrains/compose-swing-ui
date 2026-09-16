package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.ChildMeasurables
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.geom.Point2D
import kotlin.math.floor

/**
 * What a [Decoratable] component paints through: the steps its modifier declares. The library creates each value for
 * one component and writes it to [Decoratable.decoration]; a value never changes, and a change is a new value.
 *
 * Each step is a [Decorator], the first outermost: it paints at the layout bounds, and a layout modifier's own
 * decorator at the box of that modifier. The *layout bounds* are the box measurement, placement, alignment, `onPlaced`,
 * `onSizeChanged` and hit testing see. A decoration is clipped at the component's bounds.
 */
public class Decoration internal constructor(
    /** The steps the component's modifier declares. */
    internal val steps: DecorationSteps,
    /** The paint outsets: [NoPaintOutsets]; never modified. */
    internal val heldPaintOutsets: Insets,
    /** The records of the component's own children, where it is a Foundation container; null otherwise. */
    internal val childMeasurables: ChildMeasurables?,
    /** Whether every step answered [Decorator.isOpaque] with `true` when the library last gathered the steps. */
    internal val hasOpaqueSteps: Boolean,
) {
    /** Whether the component's modifier declares any decoration step. */
    public val isDecorated: Boolean get() = !steps.isEmpty

    /**
     * Whether [component], painted through this decoration, still covers every pixel of its bounds: `false` where a
     * step paints translucently, cuts the content ([Decorator.isOpaque]) or paints at a box smaller than the layout
     * bounds. The steps' own opacity is the one the library last gathered; the boxes are read at each call.
     */
    public fun isOpaque(component: Component): Boolean =
        heldPaintOutsets == NoPaintOutsets && hasOpaqueSteps &&
            steps.paintsAtLayoutBox(component.width, component.height)

    /**
     * Paints [content], the component itself in its own coordinates, through the decoration. The steps paint at the
     * layout bounds, and a layout modifier's own decorator at the box of that modifier. Without a clip on [graphics],
     * as in a capture or a print, the whole component is painted.
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
     * rotate, scale and clip them.
     */
    public fun contains(
        component: Component,
        x: Int,
        y: Int,
    ): Boolean {
        val point = if (steps.isClippedOrTransformed) contentPoint(x, y, clipped = true) ?: return false else null
        val boxX = (point?.x ?: x) - heldPaintOutsets.left
        val boxY = (point?.y ?: y) - heldPaintOutsets.top
        return boxX in 0 until layoutWidth(component) && boxY in 0 until layoutHeight(component)
    }

    /** The width of [component]'s layout bounds: its width less the paint outsets. */
    internal fun layoutWidth(component: Component): Int =
        (component.width - heldPaintOutsets.left - heldPaintOutsets.right).coerceAtLeast(0)

    /** The height of [component]'s layout bounds: its height less the paint outsets. */
    internal fun layoutHeight(component: Component): Int =
        (component.height - heldPaintOutsets.top - heldPaintOutsets.bottom).coerceAtLeast(0)

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

    /** Holds [None]. */
    public companion object {
        /** No decoration and no paint outsets: what a component holds until the library writes one. */
        public val None: Decoration =
            Decoration(DecorationSteps.None, NoPaintOutsets, null, hasOpaqueSteps = true)
    }
}
