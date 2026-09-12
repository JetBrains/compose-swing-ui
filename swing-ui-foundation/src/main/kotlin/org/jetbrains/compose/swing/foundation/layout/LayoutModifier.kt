package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol

/**
 * A modifier that measures one child between the constraints its parent offers and the result it
 * reports back.
 *
 * Layout modifiers nest in declaration order: the first modifier is outermost and measures the next
 * one as its [measurable]. Use [MeasureScope.layout] to return this modifier's size and to place the
 * placeable measured from that child.
 */
public interface LayoutModifier : ParentLayoutElement {
    /** The capability a Foundation [Layout] must support to interpret this modifier. */
    override val parentProtocol: ParentProtocol get() = LayoutModifierParentProtocol

    /** Layout modifiers wrap one another, so every declaration remains in order. */
    override val additive: Boolean get() = true

    /** Measures and places [measurable] under [constraints]. */
    public fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult

    /** The least width this modifier reports when its child is [height] tall. */
    public fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height)

    /** The greatest useful width this modifier reports when its child is [height] tall. */
    public fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height)

    /** The least height this modifier reports when its child is [width] wide. */
    public fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(width)

    /** The greatest useful height this modifier reports when its child is [width] wide. */
    public fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(width)
}
