package org.jetbrains.compose.swing.foundation.layout

/**
 * This non-negative padding side plus [other], held to the largest geometry extent [Constraints] can
 * represent. A pair beyond that extent reserves all finite space instead of overflowing
 * negative and giving the child more space than its parent offered.
 */
private fun Int.reservedWith(other: Int): Int = (this.toLong() + other).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/**
 * Why a padding declaring [sides] reserves no space at all, for one whose edges are not all above zero.
 *
 * Narrowing subtracts the space reserved, so an edge below zero would hand the child a maximum larger
 * than its parent offered and then place it outside the parent.
 */
private fun spaceBelowZero(sides: String): String =
    "A padding reserves space along the edges it names, and there is no space below none, but this one " +
        "declares $sides. To move a child outward from where its container places it, declare " +
        "offset() instead."

/**
 * The space `ConstrainedScope.padding` reserves along the child's edges, leading-edge relative: [start]
 * leads and [end] trails the child along the reading order, swapping places under a right-to-left one.
 */
internal data class PaddingElement(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
) : LayoutModifier {
    init {
        require(start >= 0 && top >= 0 && end >= 0 && bottom >= 0) {
            spaceBelowZero("start $start, top $top, end $end, bottom $bottom")
        }
    }

    override val name: String get() = "padding"

    override val declaredValues: Map<String, Any?>
        get() = mapOf("start" to start, "top" to top, "end" to end, "bottom" to bottom)

    private val horizontalSpace: Int get() = start.reservedWith(end)

    private val verticalSpace: Int get() = top.reservedWith(bottom)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.shrunkBy(horizontalSpace, verticalSpace))
        return layout(
            constraints.constrainWidth(placeable.width.grownBy(horizontalSpace)),
            constraints.constrainHeight(placeable.height.grownBy(verticalSpace)),
        ) {
            placeable.placeRelative(start, top)
        }
    }
}

/**
 * The space `ConstrainedScope.absolutePadding` reserves along the child's edges: [left], [top], [right]
 * and [bottom], the same under either reading order.
 */
internal data class AbsolutePaddingElement(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) : LayoutModifier {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
            spaceBelowZero("left $left, top $top, right $right, bottom $bottom")
        }
    }

    override val name: String get() = "absolutePadding"

    override val declaredValues: Map<String, Any?>
        get() = mapOf("left" to left, "top" to top, "right" to right, "bottom" to bottom)

    private val horizontalSpace: Int get() = left.reservedWith(right)

    private val verticalSpace: Int get() = top.reservedWith(bottom)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.shrunkBy(horizontalSpace, verticalSpace))
        return layout(
            constraints.constrainWidth(placeable.width.grownBy(horizontalSpace)),
            constraints.constrainHeight(placeable.height.grownBy(verticalSpace)),
        ) {
            placeable.place(left, top)
        }
    }
}
