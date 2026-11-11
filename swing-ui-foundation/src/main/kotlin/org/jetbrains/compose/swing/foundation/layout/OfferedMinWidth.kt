// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

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
    maySizeStockChildren: Boolean = false,
): Int =
    when (this) {
        is PreferencesMeasurable -> intrinsicHeightUnder(intrinsicSize, minWidth, maxWidth, maySizeStockChildren)
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
    if (this is OfferedMinWidthMeasurable) {
        return if (minWidth == this.minWidth) {
            this
        } else {
            OfferedMinWidthMeasurable(source, maySizeStockChildren, minWidth)
        }
    }
    return if (minWidth == 0) this else OfferedMinWidthMeasurable(this, false, minWidth)
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
