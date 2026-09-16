/*
 * Copyright 2020 The Android Open Source Project
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
 * Adapted from androidx.compose.ui.ZIndexNode in AndroidX's ui; see this module's
 * META-INF/NOTICE for the synced version.
 */

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Places the child at [zIndex] in its container's paint and hit-testing order: a child with a larger
 * value paints over, and receives a mouse event before, every sibling with a smaller one. Siblings
 * with the same value keep the order they are declared in, the last on top, whatever order their
 * container places them in. A child declaring none is at `0f`.
 *
 * Multiple declarations add their values, and add to the z-index the container places the child with.
 *
 * @param zIndex where the child sits among its siblings, the largest on top
 * @return this modifier with the child's z-index declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.zIndex(zIndex: Float): SwingModifier = with(scope) { layout(ZIndexElement(zIndex)) }

/** The `zIndex` declaration: places the child where it is, at [zIndex] among its siblings. */
private data class ZIndexElement(
    val zIndex: Float,
) : LayoutModifierNodeElement<ZIndexNode>() {
    override val name: String get() = "zIndex"

    override val declaredValues: Map<String, Any?> get() = mapOf("zIndex" to zIndex)

    override fun create(): ZIndexNode = ZIndexNode(zIndex)

    override fun update(node: ZIndexNode) {
        node.zIndex = zIndex
    }
}

/** Places the child where it is, at [zIndex] among its siblings. */
private class ZIndexNode(
    var zIndex: Float,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0, zIndex = zIndex) }
    }
}
