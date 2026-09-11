@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Reserves [all] along every edge of the child.
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.padding(all: Int): SwingModifier = this then PaddingElement(all, all, all, all)

/**
 * Reserves [start] before the child and [end] after it along the reading order, and [top] and
 * [bottom] above and below it, each edge left unreserved by default. [start] and [end] swap edges
 * under a right-to-left reading order; see [absolutePadding] for a padding that never does.
 *
 * A padding reserves space, so none of the four is ever below zero; declare [offset] to move a
 * child outward from where its container places it.
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.padding(
    start: Int = 0,
    top: Int = 0,
    end: Int = 0,
    bottom: Int = 0,
): SwingModifier = this then PaddingElement(start, top, end, bottom)

/**
 * Reserves [horizontal] before and after the child along the reading order, and [vertical] above
 * and below it, either pair left unreserved by default.
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.padding(
    horizontal: Int = 0,
    vertical: Int = 0,
): SwingModifier = padding(horizontal, vertical, horizontal, vertical)

/**
 * Reserves [left], [top], [right] and [bottom] along the child's edges, each left unreserved by
 * default, the same under a right-to-left reading order as under a left-to-right one; see
 * [padding] for a padding that follows the reading order instead.
 *
 * None of the four is ever below zero, the same as for [padding].
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.absolutePadding(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): SwingModifier = this then AbsolutePaddingElement(left, top, right, bottom)

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
