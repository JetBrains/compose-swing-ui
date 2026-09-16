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
 * Adapted from androidx.compose.foundation.layout.OffsetNode in AndroidX's foundation-layout;
 * see this module's META-INF/NOTICE for the synced version. update compares the offset as upstream does,
 * and assigns it before calling invalidatePlacement, which places the child at once. The rtlAware placement
 * branch is upstream's.
 */

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Moves the child by ([x], [y]) from where it would otherwise sit, neither axis moved by default,
 * without changing the space it measures into. A positive [x] moves the child toward the trailing
 * edge: right under a left-to-right reading order and left under a right-to-left one. See
 * [absoluteOffset] for an offset that always moves it toward the right.
 *
 * @return this modifier with the offset declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.offset(
    x: Int = 0,
    y: Int = 0,
): SwingModifier = with(scope) { layout(OffsetElement(x, y, rtlAware = true)) }

/**
 * Moves the child by ([x], [y]) from where it would otherwise sit, neither axis moved by default,
 * the same under a right-to-left reading order as under a left-to-right one; see [offset] for an
 * offset that follows the reading order instead.
 *
 * @return this modifier with the offset declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.absoluteOffset(
    x: Int = 0,
    y: Int = 0,
): SwingModifier = with(scope) { layout(OffsetElement(x, y, rtlAware = false)) }

/**
 * The move `offset` and `absoluteOffset` apply to the child's placement, without changing the space
 * it measures into. Under [rtlAware], [x] moves it toward the trailing edge; otherwise toward the
 * right under either reading order.
 */
private data class OffsetElement(
    val x: Int,
    val y: Int,
    val rtlAware: Boolean,
) : LayoutModifierNodeElement<OffsetNode>() {
    override val name: String get() = if (rtlAware) "offset" else "absoluteOffset"

    override val declaredValues: Map<String, Any?> get() = mapOf("x" to x, "y" to y)

    override fun create(): OffsetNode = OffsetNode(x, y, rtlAware)

    override fun update(node: OffsetNode): Unit = node.update(x, y, rtlAware)
}

/**
 * Places the child moved by the offset an [OffsetElement] declares. A new offset places the child again without
 * measuring it, since the offset leaves every size as it was.
 */
private class OffsetNode(
    var x: Int,
    var y: Int,
    var rtlAware: Boolean,
) : LayoutModifierNode() {
    override val shouldAutoInvalidate: Boolean get() = false

    fun update(
        x: Int,
        y: Int,
        rtlAware: Boolean,
    ) {
        val changed = this.x != x || this.y != y || this.rtlAware != rtlAware
        this.x = x
        this.y = y
        this.rtlAware = rtlAware
        if (changed) invalidatePlacement()
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (rtlAware) placeable.placeRelative(x, y) else placeable.place(x, y)
        }
    }
}
