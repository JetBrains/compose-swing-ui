package org.jetbrains.compose.swing.foundation.layout

/**
 * A component that answers what extent it takes under given constraints, and its intrinsic sizes at a given extent.
 *
 * Swing's child protocol carries no constraints - `getPreferredSize()` takes no argument - so a container measuring a
 * child can only coerce what that child reports. A child implementing this is the exception: it is measured under the
 * constraints its parent computed for it, and asked its intrinsic sizes at the extent its parent names on the other
 * axis, as androidx asks an `IntrinsicMeasurable`.
 *
 * Every component that [Row], [Column], [Box] and [Layout] create implements it, so a [Row] inside a [Column] inside a
 * [Box] is asked a constrained question, and an intrinsic question at an extent, at every step; both stop at a stock
 * widget or at a container from elsewhere, which answers its preferred or minimum size.
 *
 * A stock component asked its height at an unbounded width answers at the width it holds, as Swing has no other
 * answer. A container that takes its width from that height, such as a [Column] holding the component at an
 * `aspectRatio`, then answers another width for each width it is laid out at, so a parent that lays it out at the
 * width it prefers lays it out again each time, as plain Swing does with the same layout. A component that can answer
 * its height for an unbounded width, as androidx's text does on one line, implements this interface.
 *
 * [measure] is asked only while a container is laying its children out. The intrinsic functions can be asked at any
 * time, as androidx's `IntrinsicMeasurable` can. Asked outside a layout pass, they start none, and leave every
 * component with the bounds and validity it had.
 */
public interface Constrainable {
    /** Measures under [constraints]; read the answer back from [constrainedWidth] and [constrainedHeight]. */
    public fun measure(constraints: Constraints)

    /** The width the last [measure] settled on. */
    public val constrainedWidth: Int

    /** The height the last [measure] settled on. */
    public val constrainedHeight: Int

    /**
     * The alignment lines the last [measure] provided, each measured from the top-left corner of the extent it
     * settled on, which a parent reads through `placeable[line]`. Empty by default.
     */
    public val alignmentLines: Map<AlignmentLine, Int> get() = emptyMap()

    /**
     * The smallest width at which the content paints correctly at [height], or at an unbounded height where it is
     * [Constraints.Infinity].
     */
    public fun minIntrinsicWidth(height: Int): Int

    /**
     * The smallest width beyond which a wider width never makes the height smaller, at [height], or at an unbounded
     * height where it is [Constraints.Infinity].
     */
    public fun maxIntrinsicWidth(height: Int): Int

    /**
     * The smallest height at which the content paints correctly at [width], or at an unbounded width where it is
     * [Constraints.Infinity].
     */
    public fun minIntrinsicHeight(width: Int): Int

    /**
     * The smallest height beyond which a taller height never makes the width smaller, at [width], or at an unbounded
     * width where it is [Constraints.Infinity].
     */
    public fun maxIntrinsicHeight(width: Int): Int
}
