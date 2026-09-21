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
 * Adapted from androidx.compose.foundation.layout.IntrinsicWidthNode in AndroidX's
 * foundation-layout; see this module's META-INF/NOTICE for the synced version. The element/node
 * structure and the enforceIncoming measure logic, for both width and height, are upstream's.
 */

package org.jetbrains.compose.swing.foundation.layout

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
