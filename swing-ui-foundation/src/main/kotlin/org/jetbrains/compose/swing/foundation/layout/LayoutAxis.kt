package org.jetbrains.compose.swing.foundation.layout

import java.awt.ComponentOrientation
import java.awt.Dimension

/**
 * The axis a [LinearLayout] arranges its children along, and with it the axis-neutral reading of a
 * geometry: the *main* extent runs along the axis and the *cross* extent across it. One algorithm then
 * serves a [Row] and a [Column] alike.
 */
internal enum class LayoutAxis(
    private val horizontal: Boolean,
) {
    Horizontal(horizontal = true),
    Vertical(horizontal = false),
    ;

    /** The extent of [size] along the axis. */
    fun main(size: Dimension): Int = if (horizontal) size.width else size.height

    /** The extent of [size] across the axis. */
    fun cross(size: Dimension): Int = if (horizontal) size.height else size.width

    /** The extent [placeable] settled on along the axis. */
    fun main(placeable: Placeable): Int = if (horizontal) placeable.width else placeable.height

    /** The extent [placeable] settled on across the axis. */
    fun cross(placeable: Placeable): Int = if (horizontal) placeable.height else placeable.width

    /** The least extent [constraints] allows along the axis. */
    fun mainMin(constraints: Constraints): Int = if (horizontal) constraints.minWidth else constraints.minHeight

    /** The most extent [constraints] allows along the axis. */
    fun mainMax(constraints: Constraints): Int = if (horizontal) constraints.maxWidth else constraints.maxHeight

    /** The most extent [constraints] allows across the axis. */
    fun crossMax(constraints: Constraints): Int = if (horizontal) constraints.maxHeight else constraints.maxWidth

    /** Constraints offering [mainMin] to [mainMax] along the axis and [crossMin] to [crossMax] across it. */
    fun constraints(
        mainMin: Int,
        mainMax: Int,
        crossMin: Int,
        crossMax: Int,
    ): Constraints =
        if (horizontal) {
            Constraints(mainMin, mainMax, crossMin, crossMax)
        } else {
            Constraints(crossMin, crossMax, mainMin, mainMax)
        }

    /** The x of a placement [main] along the axis and [cross] across it. */
    fun x(
        main: Int,
        cross: Int,
    ): Int = if (horizontal) main else cross

    /** The y of a placement [main] along the axis and [cross] across it. */
    fun y(
        main: Int,
        cross: Int,
    ): Int = if (horizontal) cross else main

    /** A size whose extent along the axis is [main] and across it [cross]. */
    fun dimension(
        main: Int,
        cross: Int,
    ): Dimension = if (horizontal) Dimension(main, cross) else Dimension(cross, main)
}

/** Whether [constraints] has a finite maximum along this axis. */
internal fun LayoutAxis.hasBoundedMain(constraints: Constraints): Boolean =
    when (this) {
        LayoutAxis.Horizontal -> constraints.hasBoundedWidth
        LayoutAxis.Vertical -> constraints.hasBoundedHeight
    }

/** Whether [constraints] has a finite maximum across this axis. */
internal fun LayoutAxis.hasBoundedCross(constraints: Constraints): Boolean =
    when (this) {
        LayoutAxis.Horizontal -> constraints.hasBoundedHeight
        LayoutAxis.Vertical -> constraints.hasBoundedWidth
    }

/**
 * An [Alignment.Horizontal] or [Alignment.Vertical] read without regard to the axis it belongs to, so a
 * [LinearLayout] places a child across either axis through one call. Implementations compare by value,
 * which is what lets an unchanged declaration be recognized as the placement already in force.
 */
internal interface AxisAlignment {
    fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
    ): Int
}

/** An [Alignment.Horizontal] as an [AxisAlignment]; it is the one that reads the orientation. */
internal data class HorizontalAxisAlignment(
    val alignment: Alignment.Horizontal,
) : AxisAlignment {
    override fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
    ): Int = alignment.align(size, space, orientation)
}

/** An [Alignment.Vertical] as an [AxisAlignment]; a vertical axis reads the same either way. */
internal data class VerticalAxisAlignment(
    val alignment: Alignment.Vertical,
) : AxisAlignment {
    override fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
    ): Int = alignment.align(size, space)
}

/**
 * An [Arrangement] read without regard to the axis it belongs to, the counterpart of [AxisAlignment].
 */
internal interface AxisArrangement {
    val spacing: Int

    fun arrange(
        totalSize: Int,
        sizes: IntArray,
        orientation: ComponentOrientation,
        outPositions: IntArray,
    )
}

/** An [Arrangement.Horizontal] as an [AxisArrangement]; it is the one that reads the orientation. */
internal data class HorizontalAxisArrangement(
    val arrangement: Arrangement.Horizontal,
) : AxisArrangement {
    override val spacing: Int get() = arrangement.spacing

    override fun arrange(
        totalSize: Int,
        sizes: IntArray,
        orientation: ComponentOrientation,
        outPositions: IntArray,
    ): Unit = arrangement.arrange(totalSize, sizes, orientation, outPositions)
}

/** An [Arrangement.Vertical] as an [AxisArrangement]; a vertical axis reads the same either way. */
internal data class VerticalAxisArrangement(
    val arrangement: Arrangement.Vertical,
) : AxisArrangement {
    override val spacing: Int get() = arrangement.spacing

    override fun arrange(
        totalSize: Int,
        sizes: IntArray,
        orientation: ComponentOrientation,
        outPositions: IntArray,
    ): Unit = arrangement.arrange(totalSize, sizes, outPositions)
}
