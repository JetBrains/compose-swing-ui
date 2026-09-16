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

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Prefers the child's [intrinsicSize] width, while still allowing the constraints the parent offers
 * to override it.
 *
 * @return this modifier with the preferred intrinsic width declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.width(intrinsicSize: IntrinsicSize): SwingModifier =
    with(scope) { layout(IntrinsicWidthElement(intrinsicSize, enforceIncoming = true)) }

/**
 * Prefers the child's [intrinsicSize] height, while still allowing the constraints the parent offers
 * to override it.
 *
 * @return this modifier with the preferred intrinsic height declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.height(intrinsicSize: IntrinsicSize): SwingModifier =
    with(scope) { layout(IntrinsicHeightElement(intrinsicSize, enforceIncoming = true)) }

/**
 * Requires the child's [intrinsicSize] width, even where it is outside the constraints the parent offers.
 *
 * @return this modifier with the required intrinsic width declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredWidth(intrinsicSize: IntrinsicSize): SwingModifier =
    with(scope) { layout(IntrinsicWidthElement(intrinsicSize, enforceIncoming = false)) }

/**
 * Requires the child's [intrinsicSize] height, even where it is outside the constraints the parent offers.
 *
 * @return this modifier with the required intrinsic height declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.requiredHeight(intrinsicSize: IntrinsicSize): SwingModifier =
    with(scope) { layout(IntrinsicHeightElement(intrinsicSize, enforceIncoming = false)) }

/** Which of a child's two intrinsic answers an intrinsic size modifier uses. */
public enum class IntrinsicSize {
    /** The least extent that lets the child paint correctly. */
    Min,

    /** The extent beyond which growing the child brings no further benefit. */
    Max,
}

/** An intrinsic width declaration, optionally letting the parent override its exact result. */
private data class IntrinsicWidthElement(
    val intrinsicSize: IntrinsicSize,
    val enforceIncoming: Boolean,
) : LayoutModifierNodeElement<IntrinsicWidthNode>() {
    override val name: String get() = if (enforceIncoming) "width" else "requiredWidth"

    override val declaredValues: Map<String, Any?> get() = mapOf("intrinsicSize" to intrinsicSize)

    override fun create(): IntrinsicWidthNode = IntrinsicWidthNode(intrinsicSize, enforceIncoming)

    override fun update(node: IntrinsicWidthNode) {
        node.intrinsicSize = intrinsicSize
        node.enforceIncoming = enforceIncoming
    }
}

/** Sizes the child to its [intrinsicSize] width, held to the incoming constraints where [enforceIncoming]. */
private class IntrinsicWidthNode(
    var intrinsicSize: IntrinsicSize,
    var enforceIncoming: Boolean,
) : LayoutModifierNode() {
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

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(width)
}

/** An intrinsic height declaration, optionally letting the parent override its exact result. */
private data class IntrinsicHeightElement(
    val intrinsicSize: IntrinsicSize,
    val enforceIncoming: Boolean,
) : LayoutModifierNodeElement<IntrinsicHeightNode>() {
    override val name: String get() = if (enforceIncoming) "height" else "requiredHeight"

    override val declaredValues: Map<String, Any?> get() = mapOf("intrinsicSize" to intrinsicSize)

    override fun create(): IntrinsicHeightNode = IntrinsicHeightNode(intrinsicSize, enforceIncoming)

    override fun update(node: IntrinsicHeightNode) {
        node.intrinsicSize = intrinsicSize
        node.enforceIncoming = enforceIncoming
    }
}

/** Sizes the child to its [intrinsicSize] height, held to the incoming constraints where [enforceIncoming]. */
private class IntrinsicHeightNode(
    var intrinsicSize: IntrinsicSize,
    var enforceIncoming: Boolean,
) : LayoutModifierNode() {
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

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height)
}

internal fun IntrinsicMeasurable.intrinsicWidth(
    intrinsicSize: IntrinsicSize,
    height: Int,
): Int = if (intrinsicSize == IntrinsicSize.Min) minIntrinsicWidth(height) else maxIntrinsicWidth(height)

internal fun IntrinsicMeasurable.intrinsicHeight(
    intrinsicSize: IntrinsicSize,
    width: Int,
): Int = if (intrinsicSize == IntrinsicSize.Min) minIntrinsicHeight(width) else maxIntrinsicHeight(width)
