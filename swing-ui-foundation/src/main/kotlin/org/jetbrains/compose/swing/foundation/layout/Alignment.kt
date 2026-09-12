package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import kotlin.math.roundToInt

/**
 * Where a child sits within the extent its container offers it: an [Alignment] places a child across both
 * axes of a [Box], an [Alignment.Horizontal] across a [Column], an [Alignment.Vertical] across a [Row].
 *
 * An alignment answers one question - given the child's extent and the extent available to it, how far
 * from the top left of that extent does the child start. [Column], [Row] and [Box] apply the one they
 * are declared with to every child, and a child names its own through `align` on [ColumnScope],
 * [RowScope] or [BoxScope].
 *
 * @see AbsoluteAlignment
 * @see BiasAlignment
 */
@Stable
public fun interface Alignment {
    /**
     * The child's offset from the top left of [space], for a child of extent [size] in a container laid
     * out under [orientation].
     *
     * @param size the child's own extent, which an alignment reads and never changes.
     * @param space the extent offered, inside which the child is placed.
     * @param orientation the container's reading order; a right-to-left one mirrors the horizontal half
     *   of the placement, so [Alignment.TopStart] resolves to the top right corner.
     * @return the offset in pixels, applied without clamping.
     */
    public fun align(
        size: Dimension,
        space: Dimension,
        orientation: ComponentOrientation,
    ): Point

    /**
     * Horizontal placement of a child within the width available to it, resolved against the container's
     * `ComponentOrientation`.
     */
    @Stable
    public fun interface Horizontal {
        /**
         * The child's offset from the left of [space], for a child [size] pixels wide in a container
         * laid out under [orientation].
         *
         * @param size the child's own width, which an alignment reads and never changes.
         * @param space the width offered, which is not always the container's: an arrangement placing a
         *   group of children passes the width left over.
         * @param orientation the container's reading order; a right-to-left one mirrors the placement,
         *   so [Alignment.Start] resolves to the right edge.
         * @return the offset in pixels, applied without clamping.
         */
        public fun align(
            size: Int,
            space: Int,
            orientation: ComponentOrientation,
        ): Int

        /** Combines this horizontal placement with the vertical [other] into a two-dimensional [Alignment]. */
        public operator fun plus(other: Vertical): Alignment = CombinedAlignment(this, other)
    }

    /** Vertical placement of a child within the height available to it. */
    @Stable
    public fun interface Vertical {
        /**
         * The child's offset from the top of [space], for a child [size] pixels tall.
         *
         * @param size the child's own height, which an alignment reads and never changes.
         * @param space the height offered, which is not always the container's: an arrangement placing a
         *   group of children passes the height left over.
         * @return the offset in pixels, applied without clamping.
         */
        public fun align(
            size: Int,
            space: Int,
        ): Int

        /** Combines this vertical placement with the horizontal [other] into a two-dimensional [Alignment]. */
        public operator fun plus(other: Horizontal): Alignment = CombinedAlignment(other, this)
    }

    /** The standard alignments: nine placing a child on both axes, three across a height, three across a width. */
    public companion object {
        /** Places a child at the top of the leading edge - the top left under a left-to-right orientation. */
        @Stable
        public val TopStart: Alignment = BiasAlignment(-1f, -1f)

        /** Places a child at the top, halfway across the width available to it. */
        @Stable
        public val TopCenter: Alignment = BiasAlignment(0f, -1f)

        /** Places a child at the top of the trailing edge - the top right under a left-to-right orientation. */
        @Stable
        public val TopEnd: Alignment = BiasAlignment(1f, -1f)

        /** Places a child at the leading edge, halfway down the height available to it. */
        @Stable
        public val CenterStart: Alignment = BiasAlignment(-1f, 0f)

        /** Places a child halfway across the width and halfway down the height available to it. */
        @Stable
        public val Center: Alignment = BiasAlignment(0f, 0f)

        /** Places a child at the trailing edge, halfway down the height available to it. */
        @Stable
        public val CenterEnd: Alignment = BiasAlignment(1f, 0f)

        /** Places a child at the bottom of the leading edge. */
        @Stable
        public val BottomStart: Alignment = BiasAlignment(-1f, 1f)

        /** Places a child at the bottom, halfway across the width available to it. */
        @Stable
        public val BottomCenter: Alignment = BiasAlignment(0f, 1f)

        /** Places a child at the bottom of the trailing edge. */
        @Stable
        public val BottomEnd: Alignment = BiasAlignment(1f, 1f)

        /** Places a child at the top of the height available to it. */
        @Stable
        public val Top: Vertical = BiasAlignment.Vertical(-1f)

        /** Places a child halfway down the height available to it. */
        @Stable
        public val CenterVertically: Vertical = BiasAlignment.Vertical(0f)

        /** Places a child at the bottom of the height available to it. */
        @Stable
        public val Bottom: Vertical = BiasAlignment.Vertical(1f)

        /** Places a child at the leading edge - the left under a left-to-right orientation. */
        @Stable
        public val Start: Horizontal = BiasAlignment.Horizontal(-1f)

        /** Places a child halfway across the width available to it. */
        @Stable
        public val CenterHorizontally: Horizontal = BiasAlignment.Horizontal(0f)

        /** Places a child at the trailing edge - the right under a left-to-right orientation. */
        @Stable
        public val End: Horizontal = BiasAlignment.Horizontal(1f)
    }
}

/**
 * Places a child at a bias through the space it leaves free: `-1` at the leading edge, `0` centered, `1`
 * at the trailing edge, and any other value proportionally, which puts a bias outside `-1..1` partly or
 * wholly outside that space. A right-to-left orientation mirrors the horizontal bias.
 *
 * Each offset is worked out as a float and rounded once, so the two halves of a centered child differ by
 * at most a pixel.
 *
 * @see AbsoluteAlignment
 */
@Immutable
public class BiasAlignment(
    /** The bias across the width available to the child. */
    public val horizontalBias: Float,
    /** The bias down the height available to the child. */
    public val verticalBias: Float,
) : Alignment {
    override fun align(
        size: Dimension,
        space: Dimension,
        orientation: ComponentOrientation,
    ): Point {
        val centerX = (space.width - size.width).toFloat() / 2f
        val centerY = (space.height - size.height).toFloat() / 2f
        val resolvedHorizontalBias = if (orientation.isLeftToRight) horizontalBias else -horizontalBias
        return Point(
            (centerX * (1 + resolvedHorizontalBias)).roundToInt(),
            (centerY * (1 + verticalBias)).roundToInt(),
        )
    }

    override fun equals(other: Any?): Boolean =
        other is BiasAlignment &&
            horizontalBias.toBits() == other.horizontalBias.toBits() &&
            verticalBias.toBits() == other.verticalBias.toBits()

    override fun hashCode(): Int = 31 * horizontalBias.hashCode() + verticalBias.hashCode()

    override fun toString(): String = "BiasAlignment(horizontalBias=$horizontalBias, verticalBias=$verticalBias)"

    /** The horizontal half of a [BiasAlignment]; it is the one that reads the orientation. */
    @Immutable
    public class Horizontal(
        /** The bias across the width available to the child. */
        public val bias: Float,
    ) : Alignment.Horizontal {
        override fun align(
            size: Int,
            space: Int,
            orientation: ComponentOrientation,
        ): Int {
            val center = (space - size).toFloat() / 2f
            val resolvedBias = if (orientation.isLeftToRight) bias else -bias
            return (center * (1 + resolvedBias)).roundToInt()
        }

        override fun equals(other: Any?): Boolean = other is Horizontal && bias.toBits() == other.bias.toBits()

        override fun hashCode(): Int = bias.hashCode()

        override fun toString(): String = "BiasAlignment.Horizontal(bias=$bias)"

        override fun plus(other: Alignment.Vertical): Alignment =
            if (other is Vertical) BiasAlignment(bias, other.bias) else super.plus(other)
    }

    /** The vertical half of a [BiasAlignment]; a vertical axis reads the same either way. */
    @Immutable
    public class Vertical(
        /** The bias down the height available to the child. */
        public val bias: Float,
    ) : Alignment.Vertical {
        override fun align(
            size: Int,
            space: Int,
        ): Int {
            val center = (space - size).toFloat() / 2f
            return (center * (1 + bias)).roundToInt()
        }

        override fun equals(other: Any?): Boolean = other is Vertical && bias.toBits() == other.bias.toBits()

        override fun hashCode(): Int = bias.hashCode()

        override fun toString(): String = "BiasAlignment.Vertical(bias=$bias)"

        override fun plus(other: Alignment.Horizontal): Alignment =
            if (other is Horizontal) BiasAlignment(other.bias, bias) else super.plus(other)
    }
}

/** An [Alignment] built by pairing an [Alignment.Horizontal] with an [Alignment.Vertical] through `plus`. */
private data class CombinedAlignment(
    private val horizontal: Alignment.Horizontal,
    private val vertical: Alignment.Vertical,
) : Alignment {
    override fun align(
        size: Dimension,
        space: Dimension,
        orientation: ComponentOrientation,
    ): Point =
        Point(
            horizontal.align(size.width, space.width, orientation),
            vertical.align(size.height, space.height),
        )
}

/**
 * The standard alignments unaware of [ComponentOrientation]: a horizontal bias of `-1` always resolves to
 * the left and `1` to the right, neither mirrored under a right-to-left orientation the way [Alignment]'s
 * own constants are.
 */
public object AbsoluteAlignment {
    /** Places a child at the top left, regardless of orientation. */
    @Stable
    public val TopLeft: Alignment = absolute(-1f, -1f)

    /** Places a child at the top right, regardless of orientation. */
    @Stable
    public val TopRight: Alignment = absolute(1f, -1f)

    /** Places a child at the left, halfway down the height available to it, regardless of orientation. */
    @Stable
    public val CenterLeft: Alignment = absolute(-1f, 0f)

    /** Places a child at the right, halfway down the height available to it, regardless of orientation. */
    @Stable
    public val CenterRight: Alignment = absolute(1f, 0f)

    /** Places a child at the bottom left, regardless of orientation. */
    @Stable
    public val BottomLeft: Alignment = absolute(-1f, 1f)

    /** Places a child at the bottom right, regardless of orientation. */
    @Stable
    public val BottomRight: Alignment = absolute(1f, 1f)

    /** Places a child at the left of the width available to it, regardless of orientation. */
    @Stable
    public val Left: Alignment.Horizontal = absoluteHorizontal(-1f)

    /** Places a child at the right of the width available to it, regardless of orientation. */
    @Stable
    public val Right: Alignment.Horizontal = absoluteHorizontal(1f)

    private fun absolute(
        horizontalBias: Float,
        verticalBias: Float,
    ): Alignment {
        val delegate = BiasAlignment(horizontalBias, verticalBias)
        return Alignment { size, space, _ -> delegate.align(size, space, ComponentOrientation.LEFT_TO_RIGHT) }
    }

    private fun absoluteHorizontal(bias: Float): Alignment.Horizontal {
        val delegate = BiasAlignment.Horizontal(bias)
        return Alignment.Horizontal { size, space, _ ->
            delegate.align(size, space, ComponentOrientation.LEFT_TO_RIGHT)
        }
    }
}
