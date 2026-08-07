package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.awt.ComponentOrientation
import kotlin.math.roundToInt

/**
 * Where a child sits across the axis its container arranges children along: an [Alignment.Horizontal]
 * positions a child across a [Column], an [Alignment.Vertical] across a [Row].
 *
 * An alignment answers one question - given the child's extent and the extent available to it, how far
 * from the container's leading edge does the child start. [Column] and [Row] apply the one they are
 * declared with to every child, and a child names its own through `align` on [ColumnScope] / [RowScope].
 */
@Immutable
public object Alignment {
    /**
     * Horizontal placement of a child within the width available to it, resolved against the container's
     * `ComponentOrientation`.
     */
    @Stable
    public fun interface Horizontal {
        /**
         * The child's offset from the left of [space], for a child [size] pixels wide in a container
         * laid out under [orientation].
         */
        public fun align(
            size: Int,
            space: Int,
            orientation: ComponentOrientation,
        ): Int
    }

    /** Vertical placement of a child within the height available to it. */
    @Stable
    public fun interface Vertical {
        /** The child's offset from the top of [space], for a child [size] pixels tall. */
        public fun align(
            size: Int,
            space: Int,
        ): Int
    }

    /** Places a child at the leading edge - the left under a left-to-right orientation. */
    public val Start: Horizontal = FractionalHorizontal(LEADING_FRACTION)

    /** Places a child halfway across the width available to it. */
    public val CenterHorizontally: Horizontal = FractionalHorizontal(CENTER_FRACTION)

    /** Places a child at the trailing edge - the right under a left-to-right orientation. */
    public val End: Horizontal = FractionalHorizontal(TRAILING_FRACTION)

    /** Places a child at the top of the height available to it. */
    public val Top: Vertical = FractionalVertical(LEADING_FRACTION)

    /** Places a child halfway down the height available to it. */
    public val CenterVertically: Vertical = FractionalVertical(CENTER_FRACTION)

    /** Places a child at the bottom of the height available to it. */
    public val Bottom: Vertical = FractionalVertical(TRAILING_FRACTION)
}

/**
 * Places a child [fraction] of the way through the space it leaves free, mirroring the fraction under a
 * right-to-left orientation. The whole offset is rounded once, so the two halves of a centered child
 * differ by at most a pixel.
 */
private data class FractionalHorizontal(
    private val fraction: Float,
) : Alignment.Horizontal {
    override fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
    ): Int {
        val resolved = if (orientation.isLeftToRight) fraction else TRAILING_FRACTION - fraction
        return ((space - size) * resolved).roundToInt()
    }
}

/** The vertical counterpart of [FractionalHorizontal]; a vertical axis reads the same either way. */
private data class FractionalVertical(
    private val fraction: Float,
) : Alignment.Vertical {
    override fun align(
        size: Int,
        space: Int,
    ): Int = ((space - size) * fraction).roundToInt()
}

// How much of the space a child leaves free precedes it, for the three standard alignments.
private const val LEADING_FRACTION: Float = 0f
private const val CENTER_FRACTION: Float = 0.5f
private const val TRAILING_FRACTION: Float = 1f
