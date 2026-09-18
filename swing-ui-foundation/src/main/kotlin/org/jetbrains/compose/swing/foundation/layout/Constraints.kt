package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Stable
import java.awt.Dimension

/**
 * The extents a parent offers a child: the least it must occupy along each axis and the most it may.
 *
 * A container computes these for each of its children and measures the child under them; the child
 * answers with an extent inside them. [Int.MAX_VALUE] as a maximum means the axis is unbounded - the
 * parent imposes no ceiling there, which is what a container asked for the extent it prefers offers,
 * and what `maximumLayoutSize` already reports for a row.
 *
 * The extents are plain `Int`s, the unit every other geometry in this library is written in. AWT's
 * geometry is in user-space coordinates: a component 100 wide occupies 200 device pixels on a
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

    /** Whether every size satisfying these constraints has zero area. */
    @Stable
    public val isZero: Boolean get() = maxWidth == 0 || maxHeight == 0

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

    /** A copy with both minimum dimensions reset to zero. */
    @Stable
    public fun copyMaxDimensions(): Constraints = Constraints(maxWidth = maxWidth, maxHeight = maxHeight)

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
        "Constraints(minWidth = $minWidth, maxWidth = ${displayMaximum(maxWidth)}, " +
            "minHeight = $minHeight, maxHeight = ${displayMaximum(maxHeight)})"

    /** The standard constraints. */
    public companion object {
        // Keep the Compose-compatible spelling; this public constant is intentionally not screaming snake case.

        /** The value used for an unconstrained maximum width or height. */
        @Suppress("ktlint:standard:property-naming")
        public const val Infinity: Int = Int.MAX_VALUE

        /** Constraints that impose nothing: a child takes the extent it asks for on either axis. */
        @Stable
        public val Unbounded: Constraints = Constraints()

        /** Creates constraints for a fixed size in both dimensions. */
        @Stable
        public fun fixed(
            width: Int,
            height: Int,
        ): Constraints =
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = height,
                maxHeight = height,
            )

        /** Creates constraints for a fixed width and an unspecified height. */
        @Stable
        public fun fixedWidth(width: Int): Constraints =
            Constraints(
                minWidth = width,
                maxWidth = width,
            )

        /** Creates constraints for a fixed height and an unspecified width. */
        @Stable
        public fun fixedHeight(height: Int): Constraints =
            Constraints(
                minHeight = height,
                maxHeight = height,
            )

        /**
         * Creates constraints from all four dimensions.
         *
         * This compatibility entry point is retained for callers of Compose's packed-constraint
         * implementation. Swing constraints are already represented by four [Int]s, so neither
         * [prioritizeWidth] branch needs to trim the requested dimensions.
         */
        @Deprecated(
            "Use Constraints(minWidth, maxWidth, minHeight, maxHeight) instead",
            ReplaceWith("Constraints(minWidth, maxWidth, minHeight, maxHeight)"),
        )
        @Stable
        // CMP keeps this parameter for packed-constraint compatibility; Swing's four Ints need no prioritization.
        @Suppress("UnusedParameter")
        public fun restrictConstraints(
            minWidth: Int,
            maxWidth: Int,
            minHeight: Int,
            maxHeight: Int,
            prioritizeWidth: Boolean = true,
        ): Constraints = Constraints(minWidth, maxWidth, minHeight, maxHeight)
    }
}

/** Coerces both dimensions of [size] into this set of constraints. */
@Stable
public fun Constraints.constrain(size: Dimension): Dimension =
    Dimension(
        constrainWidth(size.width),
        constrainHeight(size.height),
    )

/** Coerces the ranges in [otherConstraints] into this set of constraints. */
@Stable
public fun Constraints.constrain(otherConstraints: Constraints): Constraints =
    Constraints(
        minWidth = otherConstraints.minWidth.coerceIn(minWidth, maxWidth),
        maxWidth = otherConstraints.maxWidth.coerceIn(minWidth, maxWidth),
        minHeight = otherConstraints.minHeight.coerceIn(minHeight, maxHeight),
        maxHeight = otherConstraints.maxHeight.coerceIn(minHeight, maxHeight),
    )

/** Returns whether [size] falls within this set of constraints. */
@Stable
public fun Constraints.isSatisfiedBy(size: Dimension): Boolean =
    size.width in minWidth..maxWidth && size.height in minHeight..maxHeight

/** Returns these constraints with the minimum dimensions shifted by [horizontal] and [vertical]. */
@Stable
public fun Constraints.offset(
    horizontal: Int = 0,
    vertical: Int = 0,
): Constraints =
    Constraints(
        minWidth = (minWidth + horizontal).coerceAtLeast(0),
        maxWidth = addMaxWithMinimum(maxWidth, horizontal),
        minHeight = (minHeight + vertical).coerceAtLeast(0),
        maxHeight = addMaxWithMinimum(maxHeight, vertical),
    )

private fun addMaxWithMinimum(
    max: Int,
    value: Int,
): Int = if (max == Constraints.Infinity) max else (max + value).coerceAtLeast(0)

/** Prints an unconstrained maximum with Compose's public spelling. */
private fun displayMaximum(max: Int): String = if (max == Constraints.Infinity) "Infinity" else max.toString()

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
