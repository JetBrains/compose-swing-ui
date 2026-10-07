package org.jetbrains.compose.swing.foundation.layout

/**
 * A component that reports what extent it takes under given constraints, and its intrinsic sizes at a given extent.
 *
 * A child implementing this is measured under the constraints its parent computed for it. Its intrinsic
 * sizes are read at the extent its parent names on the other axis, as androidx reads
 * an [IntrinsicMeasurable]. A component that
 * does not implement it has its preferred or minimum size read, which a container holds inside the constraints.
 *
 * Every component that [Row], [Column], [Box] and [Layout] create implements it. A component that can return its
 * height for an unbounded width, as androidx's text does on one line, implements it too.
 *
 * [measure] is called only while a container is laying its children out. The intrinsic functions can be read at any
 * time, and an implementation returns them without changing its own bounds or
 * validity. A container whose height is read
 * while it is invalid, and a parent that is not a Foundation container lays it out next, first sizes its stock
 * children to the specified width, as that layout does.
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
