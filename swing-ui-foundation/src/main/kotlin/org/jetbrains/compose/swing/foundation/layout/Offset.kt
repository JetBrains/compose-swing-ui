package org.jetbrains.compose.swing.foundation.layout

/**
 * The move `ConstrainedScope.offset` applies to the child's placement, without changing the space it
 * measures into: [x] moves it toward the trailing edge under a left-to-right reading order and toward
 * the leading edge under a right-to-left one.
 */
internal data class OffsetElement(
    val x: Int,
    val y: Int,
) : LayoutModifier {
    override val name: String get() = "offset"

    override val declaredValues: Map<String, Any?> get() = mapOf("x" to x, "y" to y)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeRelative(x, y) }
    }
}

/**
 * The move `ConstrainedScope.absoluteOffset` applies to the child's placement: ([x], [y]), the same
 * under either reading order.
 */
internal data class AbsoluteOffsetElement(
    val x: Int,
    val y: Int,
) : LayoutModifier {
    override val name: String get() = "absoluteOffset"

    override val declaredValues: Map<String, Any?> get() = mapOf("x" to x, "y" to y)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(x, y) }
    }
}
