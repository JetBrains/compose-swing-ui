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

package org.jetbrains.compose.swing.foundation.layout

import java.awt.ComponentOrientation
import java.awt.Dimension
import kotlin.math.roundToInt

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

/**
 * An absent minimum, or one of Int.MAX_VALUE, which cannot name a finite minimum, is zero; any
 * other is held in 0..[maximum].
 */
private fun Int?.normalizedMinimum(maximum: Int): Int =
    this?.coerceAtLeast(0)?.takeUnless { it == Int.MAX_VALUE }?.coerceAtMost(maximum) ?: 0

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
