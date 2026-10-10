/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.foundation.layout.SizeNode in AndroidX's foundation-layout; see
 * this module's META-INF/NOTICE for the synced version. The non-enforced coercion branch, and
 * the FillNode/WrapContentNode/UnspecifiedConstraintsNode family, are upstream's.
 */

// Every size modifier stays in this one file, as androidx foundation-layout's Size.kt keeps them, so the two
// read side by side.
@file:Suppress("TooManyFunctions")
@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension

/**
 * Prefers an exact [width], while still allowing the constraints the parent offers to override it.
 *
 * @return this modifier with the preferred width declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.width(width: Int): SwingModifier =
    with(scope) { layout(SizeElement(minWidth = width, maxWidth = width, enforceIncoming = true, name = "width")) }

/**
 * Prefers an exact [height], while still allowing the constraints the parent offers to override it.
 *
 * @return this modifier with the preferred height declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.height(height: Int): SwingModifier =
    with(scope) { layout(SizeElement(minHeight = height, maxHeight = height, enforceIncoming = true, name = "height")) }

/**
 * Prefers an exact square [size], while still allowing the constraints the parent offers to override it.
 *
 * @return this modifier with the preferred size declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.size(size: Int): SwingModifier = size(size, size)

/**
 * Prefers an exact [width] by [height], while still allowing the constraints the parent offers to
 * override either extent.
 *
 * @return this modifier with the preferred size declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.size(
    width: Int,
    height: Int,
): SwingModifier =
    with(scope) {
        layout(
            SizeElement(
                minWidth = width,
                minHeight = height,
                maxWidth = width,
                maxHeight = height,
                enforceIncoming = true,
                name = "size",
            ),
        )
    }

/**
 * Prefers a width between [min] and [max], with either bound absent when it is `null`.
 * The constraints the parent offers still take precedence.
 *
 * @return this modifier with the preferred width range declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.widthIn(
    min: Int? = null,
    max: Int? = null,
): SwingModifier =
    with(scope) {
        layout(SizeElement(minWidth = min, maxWidth = max, enforceIncoming = true, name = "widthIn"))
    }

/**
 * Prefers a height between [min] and [max], with either bound absent when it is `null`.
 * The constraints the parent offers still take precedence.
 *
 * @return this modifier with the preferred height range declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.heightIn(
    min: Int? = null,
    max: Int? = null,
): SwingModifier =
    with(scope) {
        layout(SizeElement(minHeight = min, maxHeight = max, enforceIncoming = true, name = "heightIn"))
    }

/**
 * Prefers a size inside the bounds named here, with any `null` bound absent. The constraints the
 * parent offers still take precedence.
 *
 * @return this modifier with the preferred size range declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.sizeIn(
    minWidth: Int? = null,
    minHeight: Int? = null,
    maxWidth: Int? = null,
    maxHeight: Int? = null,
): SwingModifier =
    with(scope) {
        layout(
            SizeElement(
                minWidth = minWidth,
                minHeight = minHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                enforceIncoming = true,
                name = "sizeIn",
            ),
        )
    }

/**
 * Requires an exact [width], even where it is outside the constraints the parent offers.
 *
 * @return this modifier with the required width declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredWidth(width: Int): SwingModifier =
    with(scope) {
        layout(SizeElement(minWidth = width, maxWidth = width, enforceIncoming = false, name = "requiredWidth"))
    }

/**
 * Requires an exact [height], even where it is outside the constraints the parent offers.
 *
 * @return this modifier with the required height declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredHeight(height: Int): SwingModifier =
    with(scope) {
        layout(SizeElement(minHeight = height, maxHeight = height, enforceIncoming = false, name = "requiredHeight"))
    }

/**
 * Requires an exact square [size], even where it is outside the constraints the parent offers.
 *
 * @return this modifier with the required size declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredSize(size: Int): SwingModifier = requiredSize(size, size)

/**
 * Requires an exact [width] by [height], even where either is outside the constraints the parent offers.
 *
 * @return this modifier with the required size declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredSize(
    width: Int,
    height: Int,
): SwingModifier =
    with(scope) {
        layout(
            SizeElement(
                minWidth = width,
                minHeight = height,
                maxWidth = width,
                maxHeight = height,
                enforceIncoming = false,
                name = "requiredSize",
            ),
        )
    }

/**
 * Requires a width inside the bounds named here, with either bound absent when it is `null`.
 *
 * @return this modifier with the required width range declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredWidthIn(
    min: Int? = null,
    max: Int? = null,
): SwingModifier =
    with(scope) {
        layout(SizeElement(minWidth = min, maxWidth = max, enforceIncoming = false, name = "requiredWidthIn"))
    }

/**
 * Requires a height inside the bounds named here, with either bound absent when it is `null`.
 *
 * @return this modifier with the required height range declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredHeightIn(
    min: Int? = null,
    max: Int? = null,
): SwingModifier =
    with(scope) {
        layout(SizeElement(minHeight = min, maxHeight = max, enforceIncoming = false, name = "requiredHeightIn"))
    }

/**
 * Requires a size inside the bounds named here, with any `null` bound absent.
 *
 * @return this modifier with the required size range declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredSizeIn(
    minWidth: Int? = null,
    minHeight: Int? = null,
    maxWidth: Int? = null,
    maxHeight: Int? = null,
): SwingModifier =
    with(scope) {
        layout(
            SizeElement(
                minWidth = minWidth,
                minHeight = minHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                enforceIncoming = false,
                name = "requiredSizeIn",
            ),
        )
    }

/**
 * Makes the child occupy [fraction] of the greatest bounded width its parent offers it. The result
 * is held between the offered minimum and maximum width. An unbounded width is left unchanged.
 *
 * @param fraction the fraction of the offered maximum width to occupy.
 * @return this modifier with the width fill declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.fillMaxWidth(
    @FloatRange(from = 0.0, to = 1.0) fraction: Float = 1f,
): SwingModifier = with(scope) { layout(FillMaxElement(Direction.Horizontal, fraction)) }

/**
 * Makes the child occupy [fraction] of the greatest bounded height its parent offers it. The result
 * is held between the offered minimum and maximum height. An unbounded height is left unchanged.
 *
 * @param fraction the fraction of the offered maximum height to occupy.
 * @return this modifier with the height fill declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.fillMaxHeight(
    @FloatRange(from = 0.0, to = 1.0) fraction: Float = 1f,
): SwingModifier = with(scope) { layout(FillMaxElement(Direction.Vertical, fraction)) }

/**
 * Makes the child occupy [fraction] of the greatest bounded width and height its parent offers it. Each
 * result is held between the offered minimum and maximum. Either unbounded axis is left unchanged.
 *
 * @param fraction the fraction of each offered maximum extent to occupy.
 * @return this modifier with the width and height fill declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.fillMaxSize(
    @FloatRange(from = 0.0, to = 1.0) fraction: Float = 1f,
): SwingModifier = with(scope) { layout(FillMaxElement(Direction.Both, fraction)) }

/**
 * Lets the child choose its width without the offered minimum and, where [unbounded], maximum;
 * it is placed within the resulting wrapper by [align].
 *
 * @return this modifier with the wrapped width declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.wrapContentWidth(
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
    unbounded: Boolean = false,
): SwingModifier = with(scope) { layout(WrapContentElement(Direction.Horizontal, align, null, null, unbounded)) }

/**
 * Lets the child choose its height without the offered minimum and, where [unbounded], maximum;
 * it is placed within the resulting wrapper by [align].
 *
 * @return this modifier with the wrapped height declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.wrapContentHeight(
    align: Alignment.Vertical = Alignment.CenterVertically,
    unbounded: Boolean = false,
): SwingModifier = with(scope) { layout(WrapContentElement(Direction.Vertical, null, align, null, unbounded)) }

/**
 * Lets the child choose both extents without the offered minima and, where [unbounded], maxima;
 * it is placed within the resulting wrapper by [align].
 *
 * @return this modifier with the wrapped size declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.wrapContentSize(
    align: Alignment = Alignment.Center,
    unbounded: Boolean = false,
): SwingModifier = with(scope) { layout(WrapContentElement(Direction.Both, null, null, align, unbounded)) }

/**
 * Raises the child's minimum size to [minWidth] by [minHeight] along whichever axis its incoming
 * constraints leave a minimum of zero on. An axis already claiming a minimum is left as it is,
 * and the minimum raised to is held between nothing and the space the child was offered, so a
 * child in a container smaller than the minimum takes the container.
 *
 * @return this modifier with the default minimum size declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.defaultMinSize(
    minWidth: Int? = null,
    minHeight: Int? = null,
): SwingModifier = with(scope) { layout(DefaultMinSizeElement(minWidth, minHeight)) }

/** The axes a fill or wrap-content modifier applies to. */
private enum class Direction {
    Vertical,
    Horizontal,
    Both,
}

/** A `fillMax*` declaration, applied to every bounded axis named by [direction]. */
private data class FillMaxElement(
    private val direction: Direction,
    val fraction: Float,
) : LayoutModifierNodeElement<FillMaxNode>() {
    override val name: String
        get() =
            when (direction) {
                Direction.Horizontal -> "fillMaxWidth"
                Direction.Vertical -> "fillMaxHeight"
                Direction.Both -> "fillMaxSize"
            }

    override val declaredValues: Map<String, Any?> get() = mapOf("fraction" to fraction)

    override fun create(): FillMaxNode = FillMaxNode(direction, fraction)

    override fun update(node: FillMaxNode) {
        node.direction = direction
        node.fraction = fraction
    }
}

/** Fixes every bounded axis [direction] names to [fraction] of the maximum it receives. */
private class FillMaxNode(
    var direction: Direction,
    var fraction: Float,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val width =
            constraints.maxWidth
                .takeIf { direction != Direction.Vertical && constraints.hasBoundedWidth }
                ?.let { Math.round(it * fraction).coerceIn(constraints.minWidth, it) }
        val height =
            constraints.maxHeight
                .takeIf { direction != Direction.Horizontal && constraints.hasBoundedHeight }
                ?.let { Math.round(it * fraction).coerceIn(constraints.minHeight, it) }
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
}

/**
 * The numeric constraints a `ConstrainedScope` size modifier applies before measuring its child.
 * A `null` bound is androidx's `Dp.Unspecified` in this Int-based geometry API. Equal to another for the same
 * bounds whichever builder [name]s it, so `width(10)` replacing `widthIn(10, 10)` updates nothing.
 */
private class SizeElement(
    val minWidth: Int? = null,
    val minHeight: Int? = null,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val enforceIncoming: Boolean,
    override val name: String,
) : LayoutModifierNodeElement<SizeNode>() {
    override fun equals(other: Any?): Boolean =
        other is SizeElement &&
            minWidth == other.minWidth &&
            minHeight == other.minHeight &&
            maxWidth == other.maxWidth &&
            maxHeight == other.maxHeight &&
            enforceIncoming == other.enforceIncoming

    override fun hashCode(): Int {
        var result = minWidth.hashCode()
        result = 31 * result + minHeight.hashCode()
        result = 31 * result + maxWidth.hashCode()
        result = 31 * result + maxHeight.hashCode()
        return 31 * result + enforceIncoming.hashCode()
    }

    override val declaredValues: Map<String, Any?>
        get() =
            mapOf(
                "minWidth" to minWidth,
                "minHeight" to minHeight,
                "maxWidth" to maxWidth,
                "maxHeight" to maxHeight,
            )

    override fun create(): SizeNode = SizeNode(minWidth, minHeight, maxWidth, maxHeight, enforceIncoming)

    override fun update(node: SizeNode) {
        node.minWidth = minWidth
        node.minHeight = minHeight
        node.maxWidth = maxWidth
        node.maxHeight = maxHeight
        node.enforceIncoming = enforceIncoming
    }
}

/** Measures the child under the bounds a [SizeElement] declares, held to the incoming ones where [enforceIncoming]. */
private class SizeNode(
    var minWidth: Int?,
    var minHeight: Int?,
    var maxWidth: Int?,
    var maxHeight: Int?,
    var enforceIncoming: Boolean,
) : LayoutModifierNode() {
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

    /**
     * This measurable under the offer from the minimum width this node declares, or under the incoming offer where it
     * declares none or holds to the incoming constraints.
     */
    private fun IntrinsicMeasurable.offeredAsMeasured(target: Constraints): IntrinsicMeasurable =
        if (enforceIncoming || minWidth == null) this else withOfferedMinWidth(target.minWidth)

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

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        val constraints = targetConstraints()
        return if (constraints.hasFixedWidth) {
            constraints.maxWidth
        } else {
            val childHeight = if (enforceIncoming) height else constraints.constrainHeight(height)
            constraints.constrainWidth(measurable.minIntrinsicWidth(childHeight))
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        val constraints = targetConstraints()
        return if (constraints.hasFixedHeight) {
            constraints.maxHeight
        } else {
            val childWidth = if (enforceIncoming) width else constraints.constrainWidth(width)
            constraints.constrainHeight(measurable.offeredAsMeasured(constraints).minIntrinsicHeight(childWidth))
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int {
        val constraints = targetConstraints()
        return if (constraints.hasFixedWidth) {
            constraints.maxWidth
        } else {
            val childHeight = if (enforceIncoming) height else constraints.constrainHeight(height)
            constraints.constrainWidth(measurable.maxIntrinsicWidth(childHeight))
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int {
        val constraints = targetConstraints()
        return if (constraints.hasFixedHeight) {
            constraints.maxHeight
        } else {
            val childWidth = if (enforceIncoming) width else constraints.constrainWidth(width)
            constraints.constrainHeight(measurable.offeredAsMeasured(constraints).maxIntrinsicHeight(childWidth))
        }
    }
}

/** Compose accepts negative Dp bounds and resolves them to zero before building its constraints. */
private fun Int?.normalizedMaximum(): Int = this?.coerceAtLeast(0) ?: Int.MAX_VALUE

/**
 * An absent minimum, or one of Int.MAX_VALUE, which cannot name a finite minimum, is zero; any
 * other is held in 0..[maximum].
 */
private fun Int?.normalizedMinimum(maximum: Int): Int =
    this?.coerceIn(0, maximum)?.takeUnless { it == Int.MAX_VALUE } ?: 0

/** The `wrapContent*` modifier, including its alignment inside the wrapper it reports. */
private data class WrapContentElement(
    private val direction: Direction,
    private val horizontalAlignment: Alignment.Horizontal?,
    private val verticalAlignment: Alignment.Vertical?,
    private val alignment: Alignment?,
    val unbounded: Boolean,
) : LayoutModifierNodeElement<WrapContentNode>() {
    override val name: String
        get() =
            when (direction) {
                Direction.Horizontal -> "wrapContentWidth"
                Direction.Vertical -> "wrapContentHeight"
                Direction.Both -> "wrapContentSize"
            }

    override val declaredValues: Map<String, Any?>
        get() = mapOf("align" to (alignment ?: horizontalAlignment ?: verticalAlignment), "unbounded" to unbounded)

    override fun create(): WrapContentNode =
        WrapContentNode(direction, horizontalAlignment, verticalAlignment, alignment, unbounded)

    override fun update(node: WrapContentNode) {
        node.direction = direction
        node.horizontalAlignment = horizontalAlignment
        node.verticalAlignment = verticalAlignment
        node.alignment = alignment
        node.unbounded = unbounded
    }
}

/** Measures the child with the axes [direction] names relaxed, and aligns it inside the wrapper it reports. */
private class WrapContentNode(
    var direction: Direction,
    var horizontalAlignment: Alignment.Horizontal?,
    var verticalAlignment: Alignment.Vertical?,
    var alignment: Alignment?,
    var unbounded: Boolean,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val wrappedConstraints =
            Constraints(
                minWidth = if (direction == Direction.Vertical) constraints.minWidth else 0,
                maxWidth = if (direction != Direction.Vertical && unbounded) Int.MAX_VALUE else constraints.maxWidth,
                minHeight = if (direction == Direction.Horizontal) constraints.minHeight else 0,
                maxHeight =
                    if (direction != Direction.Horizontal && unbounded) Int.MAX_VALUE else constraints.maxHeight,
            )
        val placeable = measurable.measure(wrappedConstraints)
        val wrapperWidth = constraints.constrainWidth(placeable.width)
        val wrapperHeight = constraints.constrainHeight(placeable.height)
        return layout(wrapperWidth, wrapperHeight) {
            val horizontalSpace = wrapperWidth - placeable.width
            val verticalSpace = wrapperHeight - placeable.height
            val aligned = alignment?.align(Dimension(0, 0), Dimension(horizontalSpace, verticalSpace), orientation)
            val x = aligned?.x ?: horizontalAlignment?.align(0, horizontalSpace, orientation) ?: 0
            val y = aligned?.y ?: verticalAlignment?.align(0, verticalSpace) ?: 0
            placeable.place(x, y)
        }
    }
}

/**
 * The minimum `defaultMinSize` raises the child's constraints to, along each axis
 * whose incoming minimum is zero. A constraint that already claims a minimum along an axis is left as
 * it is, and the minimum raised to is held between nothing and the incoming maximum.
 */
private data class DefaultMinSizeElement(
    val minWidth: Int?,
    val minHeight: Int?,
) : LayoutModifierNodeElement<DefaultMinSizeNode>() {
    override val name: String get() = "defaultMinSize"

    override val declaredValues: Map<String, Any?> get() = mapOf("minWidth" to minWidth, "minHeight" to minHeight)

    override fun create(): DefaultMinSizeNode = DefaultMinSizeNode(minWidth, minHeight)

    override fun update(node: DefaultMinSizeNode) {
        node.minWidth = minWidth
        node.minHeight = minHeight
    }
}

/** Raises the child's zero minimum along each axis to the one a [DefaultMinSizeElement] declares. */
private class DefaultMinSizeNode(
    var minWidth: Int?,
    var minHeight: Int?,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val minWidth =
            if (constraints.minWidth == 0) {
                minWidth?.normalizedMinimum(constraints.maxWidth) ?: 0
            } else {
                constraints.minWidth
            }
        val minHeight =
            if (constraints.minHeight == 0) {
                minHeight?.normalizedMinimum(constraints.maxHeight) ?: 0
            } else {
                constraints.minHeight
            }
        val measuredConstraints = Constraints(minWidth, constraints.maxWidth, minHeight, constraints.maxHeight)
        val placeable = measurable.measure(measuredConstraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ) = measurable.minIntrinsicWidth(height).coerceAtLeast(minWidth ?: 0)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ) = measurable.maxIntrinsicWidth(height).coerceAtLeast(minWidth ?: 0)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ) = measurable.minIntrinsicHeight(width).coerceAtLeast(minHeight ?: 0)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ) = measurable.maxIntrinsicHeight(width).coerceAtLeast(minHeight ?: 0)
}
