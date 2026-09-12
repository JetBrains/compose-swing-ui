package org.jetbrains.compose.swing.foundation.layout

import java.awt.ComponentOrientation

/**
 * An [Alignment.Horizontal] or [Alignment.Vertical] read without regard to the axis it belongs to, so a
 * Row or Column policy places a child across either axis through one call. Implementations compare by
 * value, which is what lets an unchanged declaration be recognized as the placement already in force.
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
