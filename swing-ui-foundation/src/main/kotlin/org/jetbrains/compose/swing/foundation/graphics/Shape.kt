package org.jetbrains.compose.swing.foundation.graphics

import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.Shape as AwtShape

/**
 * An outline resolved against the size of what it decorates - what [clip] cuts a component's painting to. It is
 * asked for its outline at the size its [decorated box][decoration] stands at then, with that box's
 * top-left corner as the origin. How often depends on the decorator: [clip] and [background] ask again on every
 * paint, while [border] keeps the outline it built and asks again when the size or the border's width, brush or
 * shape changes.
 *
 * The values below compare structurally, so a chain rebuilt from unchanged values declares the same
 * shape and nothing repaints. [of] wraps a shape of the caller's own and compares it by identity - hoist
 * such a shape into a `remember`. A lambda passed where this interface is expected has identity equality
 * and needs the same treatment.
 */
public fun interface Shape {
    /**
     * The outline this shape takes at [width] x [height], with the decorated box's top-left corner as the origin.
     *
     * @param width the decorated box's current width.
     * @param height the decorated box's current height.
     * @return the outline, which the caller only reads.
     */
    public fun outline(
        width: Int,
        height: Int,
    ): AwtShape

    /** The shapes that are not values of their own: one built from a `java.awt.Shape`. */
    public companion object {
        /**
         * A shape that is always [shape], whatever size it is asked for.
         *
         * Compared by identity: a shape built inline is a new declaration on every recomposition, so
         * hoist it into a `remember`.
         *
         * @param shape the outline to use, with the decorated box's top-left corner as the origin.
         * @return a shape resolving to [shape] at every size.
         */
        public fun of(shape: AwtShape): Shape = FixedShape(shape)
    }
}

/** The full bounds of what it decorates. */
public object RectangleShape : Shape {
    override fun outline(
        width: Int,
        height: Int,
    ): AwtShape = Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat())

    override fun toString(): String = "RectangleShape"
}

/**
 * The full bounds with each corner rounded by half the shorter side: a circle on a square component and a pill on
 * any other, as androidx's `CircleShape` is.
 */
public object CircleShape : Shape {
    override fun outline(
        width: Int,
        height: Int,
    ): AwtShape = roundedRectangle(width, height, minOf(width, height) / 2f)

    override fun toString(): String = "CircleShape"
}

/**
 * The full bounds with each corner rounded to [size], or to half the shorter side where that is less, so a
 * radius too large for the size is a pill, as androidx's `RoundedCornerShape` is.
 *
 * @property size the corner radius; `0f` is [RectangleShape].
 * @throws IllegalArgumentException if [size] is negative.
 */
public class RoundedCornerShape(
    public val size: Float,
) : Shape {
    init {
        require(size >= 0) { "A corner radius can't be negative, but was $size." }
    }

    override fun outline(
        width: Int,
        height: Int,
    ): AwtShape = roundedRectangle(width, height, minOf(size, minOf(width, height) / 2f))

    override fun equals(other: Any?): Boolean =
        other is RoundedCornerShape && size.toRawBits() == other.size.toRawBits()

    override fun hashCode(): Int = size.hashCode()

    override fun toString(): String = "RoundedCornerShape(size=$size)"
}

/** [width] x [height] with each corner rounded to [radius]. */
private fun roundedRectangle(
    width: Int,
    height: Int,
    radius: Float,
): AwtShape =
    // A round rectangle is given the arc's full width and height, which spans two corner radii.
    RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), radius * 2, radius * 2)

/**
 * [Shape.of]: one shape whatever the size. Two are equal only when they wrap the same object - an
 * `equals` the caller wrote could call two distinct outlines equal and leave a component clipped to the
 * wrong one.
 */
private class FixedShape(
    private val shape: AwtShape,
) : Shape {
    override fun outline(
        width: Int,
        height: Int,
    ): AwtShape = shape

    override fun equals(other: Any?): Boolean = other is FixedShape && shape === other.shape

    override fun hashCode(): Int = System.identityHashCode(shape)

    override fun toString(): String = "Shape.of($shape)"
}
