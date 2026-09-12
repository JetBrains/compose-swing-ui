package org.jetbrains.compose.swing.foundation.layout

/**
 * The extents a parent offers a child: the least it must occupy along each axis and the most it may.
 *
 * A container computes these for each of its children and measures the child under them; the child
 * answers with an extent inside them. [Int.MAX_VALUE] as a maximum means the axis is unbounded - the
 * parent imposes no ceiling there, which is what a container asked for the extent it prefers offers,
 * and what `maximumLayoutSize` already reports for a row.
 *
 * The extents are plain `Int`s, the unit every other geometry in this library is written in. AWT's
 * coordinates are density-independent already: a component 100 wide occupies 200 device pixels on a
 * 2x display, because the graphics configuration's default transform scales user space onto the
 * device. There is nothing for a unit type to convert.
 *
 * @property minWidth the least width the child must occupy
 * @property maxWidth the most width the child may occupy, [Int.MAX_VALUE] for an unbounded axis
 * @property minHeight the least height the child must occupy
 * @property maxHeight the most height the child may occupy, [Int.MAX_VALUE] for an unbounded axis
 * @throws IllegalArgumentException if either minimum is negative or greater than its own maximum
 */
public class Constraints(
    public val minWidth: Int = 0,
    public val maxWidth: Int = Int.MAX_VALUE,
    public val minHeight: Int = 0,
    public val maxHeight: Int = Int.MAX_VALUE,
) {
    init {
        require(minWidth in 0..maxWidth) {
            "A width ranges from a minimum of zero or more up to its maximum, but minWidth is " +
                "$minWidth and maxWidth is $maxWidth."
        }
        require(minHeight in 0..maxHeight) {
            "A height ranges from a minimum of zero or more up to its maximum, but minHeight is " +
                "$minHeight and maxHeight is $maxHeight."
        }
    }

    /** [width] held inside [minWidth] and [maxWidth]. */
    public fun constrainWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

    /** [height] held inside [minHeight] and [maxHeight]. */
    public fun constrainHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

    /** Whether the width has a finite maximum. */
    public val hasBoundedWidth: Boolean get() = maxWidth != Int.MAX_VALUE

    /** Whether the height has a finite maximum. */
    public val hasBoundedHeight: Boolean get() = maxHeight != Int.MAX_VALUE

    /** Whether the width can take exactly one value. */
    public val hasFixedWidth: Boolean get() = minWidth == maxWidth

    /** Whether the height can take exactly one value. */
    public val hasFixedHeight: Boolean get() = minHeight == maxHeight

    /** Whether both axes can take only zero. */
    public val isZero: Boolean get() = hasFixedWidth && minWidth == 0 && hasFixedHeight && minHeight == 0

    /**
     * A new constraint with every extent left unchanged unless this call supplies a replacement.
     *
     * @throws IllegalArgumentException if a replacement makes either axis invalid
     */
    public fun copy(
        minWidth: Int = this.minWidth,
        maxWidth: Int = this.maxWidth,
        minHeight: Int = this.minHeight,
        maxHeight: Int = this.maxHeight,
    ): Constraints =
        Constraints(
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight,
        )

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Constraints &&
                    minWidth == other.minWidth &&
                    maxWidth == other.maxWidth &&
                    minHeight == other.minHeight &&
                    maxHeight == other.maxHeight
            )

    override fun hashCode(): Int {
        var result = minWidth
        result = 31 * result + maxWidth
        result = 31 * result + minHeight
        result = 31 * result + maxHeight
        return result
    }

    override fun toString(): String =
        "Constraints(width=${extent(minWidth, maxWidth)}, height=${extent(minHeight, maxHeight)})"

    /** The standard constraints. */
    public companion object {
        /** Constraints that impose nothing: a child takes the extent it asks for on either axis. */
        public val Unbounded: Constraints = Constraints()
    }
}

/** One axis of a [Constraints] as its string prints it, naming an unbounded maximum rather than its number. */
private fun extent(
    min: Int,
    max: Int,
): String = "$min..${if (max == Int.MAX_VALUE) "unbounded" else max.toString()}"

/**
 * These constraints with [horizontal] taken off both widths and [vertical] off both heights, for a
 * container measuring inside its insets or a modifier insetting a child.
 *
 * **An unbounded axis stays unbounded**: taking 16 off a maximum of [Int.MAX_VALUE] yields
 * [Int.MAX_VALUE] again, not a number just below it. A policy recognizes an axis it has nothing to
 * divide by with [Constraints.hasBoundedWidth] or [Constraints.hasBoundedHeight]; a maximum a
 * subtraction moved is a finite extent of about two billion, which it would divide among its weighted
 * children.
 *
 * Neither minimum falls below zero, and neither rises above the maximum left beside it.
 */
internal fun Constraints.shrunkBy(
    horizontal: Int,
    vertical: Int,
): Constraints {
    val maxWidth = if (hasBoundedWidth) narrowed(maxWidth, horizontal) else maxWidth
    val maxHeight = if (hasBoundedHeight) narrowed(maxHeight, vertical) else maxHeight
    return Constraints(
        minWidth = narrowed(minWidth, horizontal).coerceAtMost(maxWidth),
        maxWidth = maxWidth,
        minHeight = narrowed(minHeight, vertical).coerceAtMost(maxHeight),
        maxHeight = maxHeight,
    )
}

/** [extent] less [taken], never below zero. */
private fun narrowed(
    extent: Int,
    taken: Int,
): Int = (extent - taken).coerceAtLeast(0)
