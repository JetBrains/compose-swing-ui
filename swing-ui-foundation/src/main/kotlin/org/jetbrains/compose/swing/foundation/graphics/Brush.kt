package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.geom.Point2D
import kotlin.math.nextDown
import kotlin.math.nextUp

/**
 * A fill resolved against the size of what it decorates: it is asked for its paint at the size its
 * [decorated box][DecoratedScope.decoration] stands at, with that box's top-left corner as the origin.
 *
 * The gradients below compare structurally, so a chain rebuilt from unchanged values declares the same
 * fill and nothing repaints. [of] wraps a paint of the caller's own and compares it by identity - hoist
 * such a paint into a `remember`. A lambda passed where this interface is expected has identity equality
 * and needs the same treatment.
 *
 * A gradient with a single stop, and one whose span collapses to nothing because the component has no
 * width or height along its axis, both resolve to a solid of the last stop's color.
 *
 * A gradient holds each stop's fraction between the stop before it and `1f`, and the first stop's between `0f` and
 * `1f`, as androidx's gradients do.
 */
public fun interface Brush {
    /**
     * The paint this brush takes at [width] x [height], with the decorated box's top-left corner as the origin.
     *
     * @param width the decorated box's current width.
     * @param height the decorated box's current height.
     * @return the paint to fill with.
     */
    public fun paint(
        width: Int,
        height: Int,
    ): Paint

    /** The brushes built from values: the gradients, and one built from a `java.awt.Paint`. */
    public companion object {
        /**
         * A brush that is always [paint], whatever size it is asked for.
         *
         * Compared by identity: a paint built inline is a new declaration on every recomposition, so
         * hoist it into a `remember`.
         *
         * @param paint the fill to use, with the decorated box's top-left corner as the origin.
         * @return a brush resolving to [paint] at every size.
         */
        public fun of(paint: Paint): Brush = FixedBrush(paint)

        /**
         * A gradient running left to right across the full width of what it decorates.
         *
         * @param colorStops where each color sits along the gradient, as a fraction of the width from
         *   `0f` to `1f`; at least one stop is required.
         * @return the gradient brush.
         * @see java.awt.LinearGradientPaint
         */
        public fun horizontalGradient(vararg colorStops: Pair<Float, Color>): Brush =
            AxisGradient(Axis.Horizontal, stops(colorStops))

        /**
         * A gradient running top to bottom down the full height of what it decorates.
         *
         * @param colorStops where each color sits along the gradient, as a fraction of the height from
         *   `0f` to `1f`; at least one stop is required.
         * @return the gradient brush.
         * @see java.awt.LinearGradientPaint
         */
        public fun verticalGradient(vararg colorStops: Pair<Float, Color>): Brush =
            AxisGradient(Axis.Vertical, stops(colorStops))

        /**
         * A gradient running from [start] to [end], both relative to the decorated box's top-left corner,
         * so it stays where it is declared whatever size the box takes.
         *
         * @param colorStops where each color sits between [start] and [end], as a fraction from `0f` to
         *   `1f`; at least one stop is required.
         * @param start where the first stop sits relative to the decorated box's top-left corner.
         * @param end where the last stop sits relative to the decorated box's top-left corner.
         * @return the gradient brush.
         * @see java.awt.LinearGradientPaint
         */
        public fun linearGradient(
            vararg colorStops: Pair<Float, Color>,
            start: Point2D,
            end: Point2D,
        ): Brush =
            FixedLinearGradient(Point2D.Double(start.x, start.y), Point2D.Double(end.x, end.y), stops(colorStops))

        /**
         * A gradient spreading from the center of what it decorates out to the nearer edge.
         *
         * @param colorStops where each color sits along the radius, as a fraction from `0f` at the
         *   center to `1f` at the edge; at least one stop is required.
         * @return the gradient brush.
         * @see java.awt.RadialGradientPaint
         */
        public fun radialGradient(vararg colorStops: Pair<Float, Color>): Brush = RadialGradient(stops(colorStops))
    }
}

/** The stops of a gradient, checked to hold at least one. */
private fun stops(colorStops: Array<out Pair<Float, Color>>): List<Pair<Float, Color>> {
    require(colorStops.isNotEmpty()) { "A gradient needs at least one color stop" }
    return colorStops.toList()
}

/** The axis an [AxisGradient] spans, which is the size it follows. */
private enum class Axis { Horizontal, Vertical }

/**
 * The paint [stops] describe between [start] and [end].
 *
 * A single stop has no span to run along, and two equal endpoints are what
 * [java.awt.LinearGradientPaint] refuses; both resolve to a solid of the last stop's color, as Skia
 * resolves its own degenerate gradients, so a component with no width or height paints rather than
 * throwing out of its own paint.
 */
private fun gradient(
    start: Point2D,
    end: Point2D,
    stops: Stops,
): Paint =
    if (stops.colors.size < 2 || start == end) {
        stops.colors.last()
    } else {
        LinearGradientPaint(start, end, stops.fractions, stops.colors)
    }

/**
 * A gradient's stops, split into the arrays the Java2D gradients take. Those copy the arrays they are
 * handed, so one pair serves every paint.
 *
 * Each fraction is pinned between the one before it and `1f`, the first between `0f` and `1f`, as Skia pins a
 * gradient's positions. Java2D takes only strictly increasing fractions, so a fraction equal to the one before it
 * is raised by the smallest step, and those run up past `1f` are lowered back under the one after them: equal
 * stops stay a hard edge.
 */
private class Stops(
    stops: List<Pair<Float, Color>>,
) {
    val fractions: FloatArray = FloatArray(stops.size) { stops[it].first }
    val colors: Array<Color> = Array(stops.size) { stops[it].second }

    init {
        var previous = 0f
        for (index in fractions.indices) {
            val pinned = if (fractions[index] > previous) fractions[index].coerceAtMost(1f) else previous
            fractions[index] = if (index > 0 && pinned == previous) previous.nextUp() else pinned
            previous = fractions[index]
        }
        var next = 1f
        for (index in fractions.indices.reversed()) {
            if (fractions[index] < next) break
            fractions[index] = next
            next = next.nextDown()
        }
    }
}

/**
 * The paint a gradient last resolved to, and the size it resolved it at, so repainting at a steady size
 * allocates no paint.
 */
private class Resolved(
    val width: Int,
    val height: Int,
    val paint: Paint,
)

/** [Brush.horizontalGradient] and [Brush.verticalGradient]: a gradient spanning one axis of the size. */
private data class AxisGradient(
    private val axis: Axis,
    private val stops: List<Pair<Float, Color>>,
) : Brush {
    private val arrays = Stops(stops)

    private var resolved: Resolved? = null

    override fun paint(
        width: Int,
        height: Int,
    ): Paint {
        resolved?.let { if (it.width == width && it.height == height) return it.paint }
        val end =
            when (axis) {
                Axis.Horizontal -> Point2D.Float(width.toFloat(), 0f)
                Axis.Vertical -> Point2D.Float(0f, height.toFloat())
            }
        return gradient(Point2D.Float(0f, 0f), end, arrays).also { resolved = Resolved(width, height, it) }
    }
}

/** [Brush.linearGradient]: a gradient between two points relative to the decorated box's top-left corner. */
private data class FixedLinearGradient(
    private val start: Point2D,
    private val end: Point2D,
    private val stops: List<Pair<Float, Color>>,
) : Brush {
    /** The paint, built on the first paint: it is the same at every size. */
    private var resolved: Paint? = null

    override fun paint(
        width: Int,
        height: Int,
    ): Paint = resolved ?: gradient(start, end, Stops(stops)).also { resolved = it }
}

/** [Brush.radialGradient]: a gradient from the center out to the nearer edge. */
private data class RadialGradient(
    private val stops: List<Pair<Float, Color>>,
) : Brush {
    private val arrays = Stops(stops)

    private var resolved: Resolved? = null

    override fun paint(
        width: Int,
        height: Int,
    ): Paint {
        resolved?.let { if (it.width == width && it.height == height) return it.paint }
        val radius = minOf(width, height) / 2f
        // A radius of zero is what RadialGradientPaint refuses, and a single stop has no span to run
        // along; both are the solid a zero-span linear gradient resolves to.
        val paint =
            if (arrays.colors.size < 2 || radius <= 0f) {
                arrays.colors.last()
            } else {
                RadialGradientPaint(Point2D.Float(width / 2f, height / 2f), radius, arrays.fractions, arrays.colors)
            }
        resolved = Resolved(width, height, paint)
        return paint
    }
}

/**
 * [Brush.of]: one paint whatever the size. Two are equal only when they wrap the same object - an
 * `equals` the caller wrote could call two distinct paints equal and leave a component filled with the
 * wrong one.
 */
private class FixedBrush(
    private val paint: Paint,
) : Brush {
    override fun paint(
        width: Int,
        height: Int,
    ): Paint = paint

    override fun equals(other: Any?): Boolean = other is FixedBrush && paint === other.paint

    override fun hashCode(): Int = System.identityHashCode(paint)

    override fun toString(): String = "Brush.of($paint)"
}
