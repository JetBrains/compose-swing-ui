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

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import kotlin.math.roundToInt

/**
 * Sizes the child to [ratio] width per unit height, taking the size from the greatest width its
 * incoming constraints allow, then the greatest height, then the least width and the least
 * height, and stopping at the first of those that satisfies both the constraints and the ratio.
 * Where none of them does, the constraints are not respected: the child takes the size the first
 * of those extents that names a size at all implies at the ratio, and only where none of them
 * names one is the child measured under the incoming constraints unchanged.
 *
 * @param ratio the desired width to height ratio, finite and greater than zero
 * @param matchHeightConstraintsFirst takes the size from the greatest height, then the greatest
 *   width, then the least height and the least width, for a child whose height is the extent
 *   that should decide the other; `false` by default
 * @return this modifier with the aspect ratio declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.aspectRatio(
    @FloatRange(from = 0.0, fromInclusive = false) ratio: Float,
    matchHeightConstraintsFirst: Boolean = false,
): SwingModifier = with(scope) { layout(AspectRatioElement(ratio, matchHeightConstraintsFirst)) }

/** The `aspectRatio` the child is sized under. */
private data class AspectRatioElement(
    val ratio: Float,
    val matchHeightConstraintsFirst: Boolean,
) : LayoutModifierNodeElement<AspectRatioNode>() {
    init {
        require(ratio.isFinite() && ratio > 0) { "aspectRatio $ratio must be finite and greater than zero" }
    }

    override val name: String get() = "aspectRatio"

    override val declaredValues: Map<String, Any?>
        get() = mapOf("ratio" to ratio, "matchHeightConstraintsFirst" to matchHeightConstraintsFirst)

    override fun create(): AspectRatioNode = AspectRatioNode(ratio, matchHeightConstraintsFirst)

    override fun update(node: AspectRatioNode) {
        node.ratio = ratio
        node.matchHeightConstraintsFirst = matchHeightConstraintsFirst
    }
}

/**
 * Sizes the child to [ratio] width per unit height, taken from the extents its incoming constraints name in the
 * order [findSizeAt] tries them. Where none of those names a size at all, the child is measured under the
 * constraints unchanged.
 */
private class AspectRatioNode(
    var ratio: Float,
    var matchHeightConstraintsFirst: Boolean,
) : LayoutModifierNode() {
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

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ) = if (height != Constraints.Infinity) {
        (height * ratio).roundToInt()
    } else {
        measurable.minIntrinsicWidth(height)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ) = if (height != Constraints.Infinity) {
        (height * ratio).roundToInt()
    } else {
        measurable.maxIntrinsicWidth(height)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ) = if (width != Constraints.Infinity) {
        (width / ratio).roundToInt()
    } else {
        measurable.minIntrinsicHeight(width)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ) = if (width != Constraints.Infinity) {
        (width / ratio).roundToInt()
    } else {
        measurable.maxIntrinsicHeight(width)
    }
}

/**
 * The size satisfying [ratio]: each extent tried in turn while the size it yields must satisfy the
 * constraints, then the same round again with that requirement dropped. `null` where none yields a
 * size at all, which leaves the child measured under the constraints it was offered.
 */
private fun Constraints.findSizeAt(
    ratio: Float,
    matchHeightConstraintsFirst: Boolean,
): Dimension? =
    findSizeRound(ratio, matchHeightConstraintsFirst, enforce = true)
        ?: findSizeRound(ratio, matchHeightConstraintsFirst, enforce = false)

/** The first size one round of [findSizeAt] yields, held to the constraints where [enforce]. */
private fun Constraints.findSizeRound(
    ratio: Float,
    matchHeightConstraintsFirst: Boolean,
    enforce: Boolean,
): Dimension? =
    if (matchHeightConstraintsFirst) {
        (if (hasBoundedHeight) sizeAtHeight(maxHeight, ratio, enforce) else null)
            ?: (if (hasBoundedWidth) sizeAtWidth(maxWidth, ratio, enforce) else null)
            ?: sizeAtHeight(minHeight, ratio, enforce)
            ?: sizeAtWidth(minWidth, ratio, enforce)
    } else {
        (if (hasBoundedWidth) sizeAtWidth(maxWidth, ratio, enforce) else null)
            ?: (if (hasBoundedHeight) sizeAtHeight(maxHeight, ratio, enforce) else null)
            ?: sizeAtWidth(minWidth, ratio, enforce)
            ?: sizeAtHeight(minHeight, ratio, enforce)
    }

/** The size at [ratio] for a child this wide, or `null` where [enforce] and it falls outside these constraints. */
private fun Constraints.sizeAtWidth(
    width: Int,
    ratio: Float,
    enforce: Boolean,
): Dimension? {
    val height = (width / ratio).roundToInt()
    return if (height > 0 && (!enforce || isSatisfiedBy(width, height))) Dimension(width, height) else null
}

/** The size at [ratio] for a child this tall, or `null` where [enforce] and it falls outside these constraints. */
private fun Constraints.sizeAtHeight(
    height: Int,
    ratio: Float,
    enforce: Boolean,
): Dimension? {
    val width = (height * ratio).roundToInt()
    return if (width > 0 && (!enforce || isSatisfiedBy(width, height))) Dimension(width, height) else null
}

/** Whether a size this wide and this tall satisfies the constraints, without allocating a [Dimension] for it. */
private fun Constraints.isSatisfiedBy(
    width: Int,
    height: Int,
): Boolean = width in minWidth..maxWidth && height in minHeight..maxHeight
