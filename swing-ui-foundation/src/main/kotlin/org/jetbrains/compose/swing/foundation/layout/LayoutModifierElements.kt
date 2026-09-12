// This file collects internal Foundation layout modifier element implementations and their
// measurement helpers to maintain a clean internal structural boundary. Splitting them into
// multiple files would fragment the element implementations without changing public API or behavior.
@file:Suppress("TooManyFunctions")

package org.jetbrains.compose.swing.foundation.layout

import java.awt.ComponentOrientation
import java.awt.Dimension
import kotlin.math.roundToInt

/** [this] plus [amount], coerced into an extent that never overflows past [Int.MAX_VALUE] or below zero. */
private fun Int.grownBy(amount: Int): Int = (this.toLong() + amount).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/**
 * This non-negative padding side plus [other], held to the largest geometry extent [Constraints] can
 * represent. A pair beyond that extent reserves all finite room instead of overflowing
 * negative and giving the child more room than its parent offered.
 */
private fun Int.reservedWith(other: Int): Int = (this.toLong() + other).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** Which bounded axes a [FillMaxElement] fixes to a fraction of the maximum it receives. */
internal enum class FillDirection {
    Width,
    Height,
    Both,
}

/** A `ConstrainedScope.fillMax*` declaration, applied to every bounded axis named by [direction]. */
internal data class FillMaxElement(
    private val direction: FillDirection,
    val fraction: Float,
    override val name: String,
) : LayoutModifier {
    init {
        require(fraction in 0f..1f) { "A fill fraction must be between zero and one, but was $fraction." }
    }

    override val declaredValues: Map<String, Any?> get() = mapOf("fraction" to fraction)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val width =
            if (direction != FillDirection.Height && constraints.hasBoundedWidth) {
                (constraints.maxWidth * fraction).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
            } else {
                null
            }
        val height =
            if (direction != FillDirection.Width && constraints.hasBoundedHeight) {
                (constraints.maxHeight * fraction).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
            } else {
                null
            }
        val measuredConstraints =
            Constraints(
                minWidth = width ?: constraints.minWidth,
                maxWidth = width ?: constraints.maxWidth,
                minHeight = height ?: constraints.minHeight,
                maxHeight = height ?: constraints.maxHeight,
            )
        val placeable = measurable.measure(measuredConstraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    companion object {
        fun width(fraction: Float): FillMaxElement = FillMaxElement(FillDirection.Width, fraction, "fillMaxWidth")

        fun height(fraction: Float): FillMaxElement = FillMaxElement(FillDirection.Height, fraction, "fillMaxHeight")

        fun size(fraction: Float): FillMaxElement = FillMaxElement(FillDirection.Both, fraction, "fillMaxSize")
    }
}

/**
 * The numeric constraints a `ConstrainedScope` size modifier applies before measuring its child.
 * A `null` bound is Compose's `Dp.Unspecified` in this Int-based geometry API.
 */
internal data class SizeElement(
    val minWidth: Int? = null,
    val minHeight: Int? = null,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val enforceIncoming: Boolean,
    override val name: String,
) : LayoutModifier {
    override val declaredValues: Map<String, Any?>
        get() =
            mapOf(
                "minWidth" to minWidth,
                "minHeight" to minHeight,
                "maxWidth" to maxWidth,
                "maxHeight" to maxHeight,
            )

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val target = targetConstraints()
        val measuredConstraints =
            if (enforceIncoming) {
                constraints.constrain(target)
            } else {
                Constraints(
                    minWidth = minWidth?.let { target.minWidth } ?: constraints.minWidth.coerceAtMost(target.maxWidth),
                    maxWidth = maxWidth?.let { target.maxWidth } ?: constraints.maxWidth.coerceAtLeast(target.minWidth),
                    minHeight =
                        minHeight?.let { target.minHeight } ?: constraints.minHeight.coerceAtMost(target.maxHeight),
                    maxHeight =
                        maxHeight?.let { target.maxHeight } ?: constraints.maxHeight.coerceAtLeast(target.minHeight),
                )
            }
        val placeable = measurable.measure(measuredConstraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    private fun targetConstraints(): Constraints {
        val maxWidth = maxWidth.normalizedMaximum()
        val maxHeight = maxHeight.normalizedMaximum()
        return Constraints(
            minWidth = minWidth.normalizedMinimum(maxWidth),
            maxWidth = maxWidth,
            minHeight = minHeight.normalizedMinimum(maxHeight),
            maxHeight = maxHeight,
        )
    }
}

/** Compose accepts negative Dp bounds and resolves them to zero before building its constraints. */
private fun Int?.normalizedMaximum(): Int = this?.coerceAtLeast(0) ?: Int.MAX_VALUE

/** An unbounded `Int.MAX_VALUE` cannot also name a finite minimum. */
private fun Int?.normalizedMinimum(maximum: Int): Int =
    this?.coerceAtLeast(0)?.takeUnless { it == Int.MAX_VALUE }?.coerceAtMost(maximum) ?: 0

/** Which of a child's two intrinsic answers an intrinsic size modifier uses. */
public enum class IntrinsicSize {
    /** The least extent that lets the child paint correctly. */
    Min,

    /** The extent beyond which growing the child brings no further benefit. */
    Max,
}

/** An intrinsic width declaration, optionally letting the parent override its exact result. */
internal data class IntrinsicWidthElement(
    val intrinsicSize: IntrinsicSize,
    val enforceIncoming: Boolean,
    override val name: String,
) : LayoutModifier {
    override val declaredValues: Map<String, Any?> get() = mapOf("intrinsicSize" to intrinsicSize)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val width = measurable.intrinsicWidth(intrinsicSize, constraints.maxHeight).coerceAtLeast(0)
        val contentConstraints = Constraints.fixedWidth(width)
        val measuredConstraints = if (enforceIncoming) constraints.constrain(contentConstraints) else contentConstraints
        val placeable = measurable.measure(measuredConstraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.intrinsicWidth(intrinsicSize, height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.intrinsicWidth(intrinsicSize, height)
}

/** An intrinsic height declaration, optionally letting the parent override its exact result. */
internal data class IntrinsicHeightElement(
    val intrinsicSize: IntrinsicSize,
    val enforceIncoming: Boolean,
    override val name: String,
) : LayoutModifier {
    override val declaredValues: Map<String, Any?> get() = mapOf("intrinsicSize" to intrinsicSize)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val height = measurable.intrinsicHeight(intrinsicSize, constraints.maxWidth).coerceAtLeast(0)
        val contentConstraints = Constraints.fixedHeight(height)
        val measuredConstraints = if (enforceIncoming) constraints.constrain(contentConstraints) else contentConstraints
        val placeable = measurable.measure(measuredConstraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.intrinsicHeight(intrinsicSize, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.intrinsicHeight(intrinsicSize, width)
}

private fun IntrinsicMeasurable.intrinsicWidth(
    intrinsicSize: IntrinsicSize,
    height: Int,
): Int = if (intrinsicSize == IntrinsicSize.Min) minIntrinsicWidth(height) else maxIntrinsicWidth(height)

private fun IntrinsicMeasurable.intrinsicHeight(
    intrinsicSize: IntrinsicSize,
    width: Int,
): Int = if (intrinsicSize == IntrinsicSize.Min) minIntrinsicHeight(width) else maxIntrinsicHeight(width)

/** Which axes a wrap-content modifier relaxes before it measures its child. */
internal enum class WrapDirection {
    Width,
    Height,
    Both,
}

/** The `ConstrainedScope.wrapContent*` modifier, including its alignment inside the wrapper it reports. */
internal data class WrapContentElement(
    private val direction: WrapDirection,
    private val horizontalAlignment: Alignment.Horizontal?,
    private val verticalAlignment: Alignment.Vertical?,
    private val alignment: Alignment?,
    val unbounded: Boolean,
    override val name: String,
) : LayoutModifier {
    override val declaredValues: Map<String, Any?>
        get() = mapOf("align" to (alignment ?: horizontalAlignment ?: verticalAlignment), "unbounded" to unbounded)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val wrappedConstraints =
            Constraints(
                minWidth = if (direction == WrapDirection.Height) constraints.minWidth else 0,
                maxWidth = if (direction != WrapDirection.Height && unbounded) Int.MAX_VALUE else constraints.maxWidth,
                minHeight = if (direction == WrapDirection.Width) constraints.minHeight else 0,
                maxHeight = if (direction != WrapDirection.Width && unbounded) Int.MAX_VALUE else constraints.maxHeight,
            )
        val placeable = measurable.measure(wrappedConstraints)
        val wrapperWidth = constraints.constrainWidth(placeable.width)
        val wrapperHeight = constraints.constrainHeight(placeable.height)
        return layout(wrapperWidth, wrapperHeight) {
            val horizontalSpace = wrapperWidth - placeable.width
            val verticalSpace = wrapperHeight - placeable.height
            val orientation =
                if (isLeftToRight) ComponentOrientation.LEFT_TO_RIGHT else ComponentOrientation.RIGHT_TO_LEFT
            val x =
                alignment?.align(Dimension(0, 0), Dimension(horizontalSpace, verticalSpace), orientation)?.x
                    ?: horizontalAlignment?.align(0, horizontalSpace, orientation)
                    ?: 0
            val y =
                alignment?.align(Dimension(0, 0), Dimension(horizontalSpace, verticalSpace), orientation)?.y
                    ?: verticalAlignment?.align(0, verticalSpace)
                    ?: 0
            placeable.place(x, y)
        }
    }

    companion object {
        fun width(
            align: Alignment.Horizontal,
            unbounded: Boolean,
        ): WrapContentElement =
            WrapContentElement(WrapDirection.Width, align, null, null, unbounded, "wrapContentWidth")

        fun height(
            align: Alignment.Vertical,
            unbounded: Boolean,
        ): WrapContentElement =
            WrapContentElement(WrapDirection.Height, null, align, null, unbounded, "wrapContentHeight")

        fun size(
            align: Alignment,
            unbounded: Boolean,
        ): WrapContentElement = WrapContentElement(WrapDirection.Both, null, null, align, unbounded, "wrapContentSize")
    }
}

/**
 * Why a padding declaring [sides] reserves no room at all, for one whose edges are not all above zero.
 *
 * Narrowing subtracts the room reserved, so an edge below zero would hand the child a maximum larger
 * than its parent offered and then place it outside the parent.
 */
private fun roomBelowZero(sides: String): String =
    "A padding reserves room along the edges it names, and there is no room below none, but this one " +
        "declares $sides. To move a child outward from where its container places it, declare " +
        "offset() instead."

/**
 * The room `ConstrainedScope.padding` reserves along the child's edges, leading-edge relative: [start]
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
            roomBelowZero("start $start, top $top, end $end, bottom $bottom")
        }
    }

    override val name: String get() = "padding"

    override val declaredValues: Map<String, Any?>
        get() = mapOf("start" to start, "top" to top, "end" to end, "bottom" to bottom)

    private val horizontalRoom: Int get() = start.reservedWith(end)

    private val verticalRoom: Int get() = top.reservedWith(bottom)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.shrunkBy(horizontalRoom, verticalRoom))
        return layout(
            constraints.constrainWidth(placeable.width.grownBy(horizontalRoom)),
            constraints.constrainHeight(placeable.height.grownBy(verticalRoom)),
        ) {
            placeable.placeRelative(start, top)
        }
    }
}

/**
 * The room `ConstrainedScope.absolutePadding` reserves along the child's edges: [left], [top], [right]
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
            roomBelowZero("left $left, top $top, right $right, bottom $bottom")
        }
    }

    override val name: String get() = "absolutePadding"

    override val declaredValues: Map<String, Any?>
        get() = mapOf("left" to left, "top" to top, "right" to right, "bottom" to bottom)

    private val horizontalRoom: Int get() = left.reservedWith(right)

    private val verticalRoom: Int get() = top.reservedWith(bottom)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.shrunkBy(horizontalRoom, verticalRoom))
        return layout(
            constraints.constrainWidth(placeable.width.grownBy(horizontalRoom)),
            constraints.constrainHeight(placeable.height.grownBy(verticalRoom)),
        ) {
            placeable.place(left, top)
        }
    }
}

/**
 * The move `ConstrainedScope.offset` applies to the child's placement, without changing the room it
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

/**
 * The minimum `ConstrainedScope.defaultMinSize` raises the child's constraints to, along each axis
 * whose incoming minimum is zero. A constraint that already claims a minimum along an axis is left as
 * it is, and the minimum raised to is held between nothing and the incoming maximum.
 */
internal data class DefaultMinSizeElement(
    val minWidth: Int?,
    val minHeight: Int?,
) : LayoutModifier {
    override val name: String get() = "defaultMinSize"

    override val declaredValues: Map<String, Any?> get() = mapOf("minWidth" to minWidth, "minHeight" to minHeight)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val minWidth =
            if (constraints.minWidth == 0 && minWidth != null) {
                minWidth.normalizedMinimum(constraints.maxWidth)
            } else {
                constraints.minWidth
            }
        val minHeight =
            if (constraints.minHeight == 0 && minHeight != null) {
                minHeight.normalizedMinimum(constraints.maxHeight)
            } else {
                constraints.minHeight
            }
        val measuredConstraints = Constraints(minWidth, constraints.maxWidth, minHeight, constraints.maxHeight)
        val placeable = measurable.measure(measuredConstraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * The `ConstrainedScope.aspectRatio` the child is sized under: [ratio] width per unit height, taken
 * from the extents its incoming constraints name in the order [findSizeAt] tries them. Where none of
 * those names a size at all, the child is measured under the constraints unchanged.
 */
internal data class AspectRatioElement(
    val ratio: Float,
    val matchHeightConstraintsFirst: Boolean,
) : LayoutModifier {
    init {
        require(ratio.isFinite() && ratio > 0) { "aspectRatio $ratio must be finite and greater than zero" }
    }

    override val name: String get() = "aspectRatio"

    override val declaredValues: Map<String, Any?>
        get() = mapOf("ratio" to ratio, "matchHeightConstraintsFirst" to matchHeightConstraintsFirst)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val size = constraints.findSizeAt(ratio, matchHeightConstraintsFirst)
        val measuredConstraints =
            if (size == null) constraints else Constraints(size.width, size.width, size.height, size.height)
        val placeable = measurable.measure(measuredConstraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** An extent an aspect ratio can take a size from, the other following from the ratio. */
private enum class RatioExtent {
    MaxWidth,
    MaxHeight,
    MinWidth,
    MinHeight,
}

/** The extents tried in turn, widest first, and the same order with the two axes swapped. */
private val WIDTH_FIRST =
    listOf(RatioExtent.MaxWidth, RatioExtent.MaxHeight, RatioExtent.MinWidth, RatioExtent.MinHeight)
private val HEIGHT_FIRST =
    listOf(RatioExtent.MaxHeight, RatioExtent.MaxWidth, RatioExtent.MinHeight, RatioExtent.MinWidth)

/**
 * The size satisfying [ratio]: each extent tried in turn while the size it yields must satisfy the
 * constraints, then the same round again with that requirement dropped. `null` where none yields a
 * size at all, which leaves the child measured under the constraints it was offered.
 */
private fun Constraints.findSizeAt(
    ratio: Float,
    matchHeightConstraintsFirst: Boolean,
): Dimension? {
    val order = if (matchHeightConstraintsFirst) HEIGHT_FIRST else WIDTH_FIRST
    for (enforce in ENFORCED_THEN_NOT) {
        for (extent in order) {
            sizeAt(extent, ratio, enforce)?.let { return it }
        }
    }
    return null
}

/** The size the constraints yield at [extent], held to them where [enforce]. */
private fun Constraints.sizeAt(
    extent: RatioExtent,
    ratio: Float,
    enforce: Boolean,
): Dimension? =
    when (extent) {
        RatioExtent.MaxWidth -> tryMaxWidth(ratio, enforce)
        RatioExtent.MaxHeight -> tryMaxHeight(ratio, enforce)
        RatioExtent.MinWidth -> tryMinWidth(ratio, enforce)
        RatioExtent.MinHeight -> tryMinHeight(ratio, enforce)
    }

/** A size must satisfy the constraints on the first round and need not on the second. */
private val ENFORCED_THEN_NOT = booleanArrayOf(true, false)

private fun Constraints.tryMaxWidth(
    ratio: Float,
    enforce: Boolean,
): Dimension? {
    if (!hasBoundedWidth) return null
    val height = (maxWidth / ratio).roundToInt()
    return if (height > 0 && (!enforce || satisfies(maxWidth, height))) Dimension(maxWidth, height) else null
}

private fun Constraints.tryMaxHeight(
    ratio: Float,
    enforce: Boolean,
): Dimension? {
    if (!hasBoundedHeight) return null
    val width = (maxHeight * ratio).roundToInt()
    return if (width > 0 && (!enforce || satisfies(width, maxHeight))) Dimension(width, maxHeight) else null
}

private fun Constraints.tryMinWidth(
    ratio: Float,
    enforce: Boolean,
): Dimension? {
    val height = (minWidth / ratio).roundToInt()
    return if (height > 0 && (!enforce || satisfies(minWidth, height))) Dimension(minWidth, height) else null
}

private fun Constraints.tryMinHeight(
    ratio: Float,
    enforce: Boolean,
): Dimension? {
    val width = (minHeight * ratio).roundToInt()
    return if (width > 0 && (!enforce || satisfies(width, minHeight))) Dimension(width, minHeight) else null
}

/** Whether ([width], [height]) falls inside these constraints. */
private fun Constraints.satisfies(
    width: Int,
    height: Int,
): Boolean = width in minWidth..maxWidth && height in minHeight..maxHeight
