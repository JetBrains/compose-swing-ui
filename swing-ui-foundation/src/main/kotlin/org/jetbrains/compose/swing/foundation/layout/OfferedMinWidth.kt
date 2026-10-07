/*
 * Copyright 2026 JetBrains s.r.o. and contributors.
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

 */

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension

/**
 * This measurable's [intrinsicSize] height under an offer of a width from [minWidth] to [maxWidth]: a stock child
 * returns a result at the width its measure takes under that offer, and a
 * Foundation container and a layout modifier through
 * their intrinsic functions under that offer. The stand-ins that forward every
 * preference read to the measurable they wrap
 * forward the offer too.
 */
internal fun IntrinsicMeasurable.intrinsicHeightUnder(
    intrinsicSize: IntrinsicSize,
    minWidth: Int,
    maxWidth: Int,
): Int =
    when (this) {
        is ChildMeasurable -> {
            val outer = outerMeasurable
            if (outer != null) {
                outer.intrinsicHeightUnder(intrinsicSize, minWidth, maxWidth)
            } else {
                unmodifiedIntrinsicHeight(intrinsicSize, minWidth, maxWidth)
            }
        }

        is UnmodifiedChildMeasurable -> {
            child.unmodifiedIntrinsicHeight(intrinsicSize, minWidth, maxWidth)
        }

        is LayoutModifierMeasurable -> {
            val offered = measurable.withOfferedMinWidth(minWidth)
            with(widthOfferScope(minWidth.coerceAtMost(maxWidth))) {
                with(modifier) {
                    if (intrinsicSize == IntrinsicSize.Min) {
                        minIntrinsicHeight(offered, maxWidth)
                    } else {
                        maxIntrinsicHeight(offered, maxWidth)
                    }
                }
            }
        }

        is WidthOfferMeasurable -> {
            measurable.intrinsicHeightUnder(intrinsicSize, minWidth, maxWidth)
        }

        is IntrinsicMeasurableAdapter -> {
            source.intrinsicHeightUnder(intrinsicSize, minWidth, maxWidth)
        }

        is IntrinsicLineMeasurable -> {
            source.intrinsicHeightUnder(intrinsicSize, minWidth, maxWidth)
        }
    }

private fun ChildMeasurable.unmodifiedIntrinsicHeight(
    intrinsicSize: IntrinsicSize,
    minWidth: Int,
    maxWidth: Int,
): Int =
    if (intrinsicSize == IntrinsicSize.Min) {
        unmodifiedMinIntrinsicHeight(minWidth, maxWidth)
    } else {
        unmodifiedMaxIntrinsicHeight(minWidth, maxWidth)
    }

/**
 * [measurable] as a layout modifier is handed it for an intrinsic height read read
 * under a width offer from [minWidth] up to
 * the specified width. Its height is read at a width, it returns a result under the
 * offer from [minWidth] up to that width, so a
 * modifier reading its child with the same arguments keeps the offer. Its intrinsic width reads are [measurable]'s own.
 */
internal class WidthOfferMeasurable(
    val measurable: IntrinsicMeasurable,
    val minWidth: Int,
) : IntrinsicMeasurable {
    override val parentData: Any? get() = measurable.parentData

    override fun minIntrinsicWidth(height: Int): Int = measurable.minIntrinsicWidth(height)

    override fun maxIntrinsicWidth(height: Int): Int = measurable.maxIntrinsicWidth(height)

    override fun minIntrinsicHeight(width: Int): Int =
        measurable.intrinsicHeightUnder(IntrinsicSize.Min, minWidth.coerceAtMost(width), width)

    override fun maxIntrinsicHeight(width: Int): Int =
        measurable.intrinsicHeightUnder(IntrinsicSize.Max, minWidth.coerceAtMost(width), width)

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable? = measurable.intrinsicPlaceable(width, height)

    override fun maximumSize(): Dimension? = measurable.maximumSize()
}

/**
 * This measurable under a width offer from [minWidth], in place of any offer it carries: its height is read at a width,
 * it returns the same results as when measured under an offer from [minWidth], held
 * to that width, up to that width. Its width
 * intrinsic reads are its own.
 *
 * @throws IllegalArgumentException if [minWidth] is negative
 */
public fun IntrinsicMeasurable.withOfferedMinWidth(minWidth: Int): IntrinsicMeasurable {
    require(minWidth >= 0) { "A width offer starts at zero or more, but $minWidth was named." }
    val unoffered = (this as? WidthOfferMeasurable)?.measurable ?: this
    return if (minWidth == 0) unoffered else WidthOfferMeasurable(unoffered, minWidth)
}

/** This measurable under the minimum width declared on a size node. */
internal fun IntrinsicMeasurable.offeredAt(
    declaredMinWidth: Int?,
    declared: Constraints,
): IntrinsicMeasurable = if (declaredMinWidth == null) this else withOfferedMinWidth(declared.minWidth)

/**
 * The scope an intrinsic height read is computed in under a width offer from
 * [minWidth], at most the specified width.
 */
internal fun widthOfferScope(minWidth: Int): IntrinsicMeasureScope =
    if (minWidth == 0) PolicyMeasureScope else WidthOfferScope(minWidth)

private class WidthOfferScope(
    override val offeredMinWidth: Int,
) : IntrinsicMeasureScope
