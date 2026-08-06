package org.jetbrains.compose.swing.components.layout

import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle

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

    /** The extent of [bounds] along the axis. */
    fun main(bounds: Rectangle): Int = if (horizontal) bounds.width else bounds.height

    /** The extent of [bounds] across the axis. */
    fun cross(bounds: Rectangle): Int = if (horizontal) bounds.height else bounds.width

    /** Where [bounds] starts along the axis. */
    fun mainOrigin(bounds: Rectangle): Int = if (horizontal) bounds.x else bounds.y

    /** Where [bounds] starts across the axis. */
    fun crossOrigin(bounds: Rectangle): Int = if (horizontal) bounds.y else bounds.x

    /** A size whose extent along the axis is [main] and across it [cross]. */
    fun dimension(
        main: Int,
        cross: Int,
    ): Dimension = if (horizontal) Dimension(main, cross) else Dimension(cross, main)

    /** Puts [component] at [main] along the axis and [cross] across it, at the extents given. */
    fun place(
        component: Component,
        main: Int,
        cross: Int,
        mainSize: Int,
        crossSize: Int,
    ) {
        if (horizontal) {
            component.setBounds(main, cross, mainSize, crossSize)
        } else {
            component.setBounds(cross, main, crossSize, mainSize)
        }
    }
}

/**
 * An [Alignment] read without regard to the axis it belongs to, so a [LinearLayout] places a child across
 * either axis through one call. Implementations compare by value, which is what lets an unchanged
 * declaration be recognised as the placement already in force.
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
