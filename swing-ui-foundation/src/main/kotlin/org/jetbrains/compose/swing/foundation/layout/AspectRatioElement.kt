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
 * Adapted from androidx.compose.foundation.layout.AspectRatioNode in AndroidX's
 * foundation-layout; see this module's META-INF/NOTICE for the synced version. The two-pass
 * findSize search order, the isSatisfiedBy check and the intrinsic functions are upstream's.
 */

package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension
import kotlin.math.roundToInt

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
