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
 * Adapted from androidx.compose.foundation.layout.PaddingNode in AndroidX's foundation-layout;
 * see this module's META-INF/NOTICE for the synced version. The measure steps, which shrink the
 * constraints by the padding and constrain the child's size grown by it, and the rtlAware placement
 * branch are upstream's.
 */

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Reserves [all] along every edge of the child. A decoration declared before a padding paints over the space it
 * reserves as well, as paint outsets: `background(brush).padding(8)` fills the child and the space around it.
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.padding(all: Int): SwingModifier = padding(all, all, all, all)

/**
 * Reserves [start] before the child and [end] after it along the reading order, and [top] and
 * [bottom] above and below it, each edge left unreserved by default. [start] and [end] swap edges
 * under a right-to-left reading order; see [absolutePadding] for a padding that never does.
 *
 * A padding reserves space, so none of the four is ever below zero; declare [offset] to move a
 * child outward from where its container places it.
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.padding(
    start: Int = 0,
    top: Int = 0,
    end: Int = 0,
    bottom: Int = 0,
): SwingModifier = with(scope) { layout(PaddingElement(start, top, end, bottom, rtlAware = true)) }

/**
 * Reserves [horizontal] before and after the child along the reading order, and [vertical] above
 * and below it, either pair left unreserved by default.
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.padding(
    horizontal: Int = 0,
    vertical: Int = 0,
): SwingModifier = padding(horizontal, vertical, horizontal, vertical)

/**
 * Reserves [left], [top], [right] and [bottom] along the child's edges, each left unreserved by
 * default, the same under a right-to-left reading order as under a left-to-right one; see
 * [padding] for a padding that follows the reading order instead.
 *
 * None of the four is ever below zero, the same as for [padding].
 *
 * @return this modifier with the padding declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.absolutePadding(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): SwingModifier = with(scope) { layout(PaddingElement(left, top, right, bottom, rtlAware = false)) }

/**
 * The space `padding` and `absolutePadding` reserve along the child's edges. Under [rtlAware],
 * [start] leads and [end] trails the child along the reading order, swapping places under a
 * right-to-left one; otherwise [start] is the left edge and [end] the right one under either
 * reading order.
 */
private data class PaddingElement(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
    val rtlAware: Boolean,
) : LayoutModifierNodeElement<PaddingNode>() {
    init {
        // Narrowing subtracts the space reserved, so an edge below zero would hand the child a maximum
        // larger than its parent offered and then place it outside the parent.
        require(start >= 0 && top >= 0 && end >= 0 && bottom >= 0) {
            val sides =
                if (rtlAware) {
                    "start $start, top $top, end $end, bottom $bottom"
                } else {
                    "left $start, top $top, right $end, bottom $bottom"
                }
            "A padding reserves space along the edges it names, and there is no space for an edge below zero, " +
                "but this one declares $sides. To move a child outward from where its container places it, " +
                "declare offset() instead."
        }
    }

    override val name: String get() = if (rtlAware) "padding" else "absolutePadding"

    override val declaredValues: Map<String, Any?>
        get() =
            if (rtlAware) {
                mapOf("start" to start, "top" to top, "end" to end, "bottom" to bottom)
            } else {
                mapOf("left" to start, "top" to top, "right" to end, "bottom" to bottom)
            }

    override fun create(): PaddingNode = PaddingNode(start, top, end, bottom, rtlAware)

    override fun update(node: PaddingNode) {
        node.start = start
        node.top = top
        node.end = end
        node.bottom = bottom
        node.rtlAware = rtlAware
    }
}

/** Measures the child inside the space a [PaddingElement] reserves, and places it past the leading space. */
private class PaddingNode(
    var start: Int,
    var top: Int,
    var end: Int,
    var bottom: Int,
    var rtlAware: Boolean,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val horizontalPadding = start.grownBy(end)
        val verticalPadding = top.grownBy(bottom)
        val placeable = measurable.measure(constraints.offset(-horizontalPadding, -verticalPadding))
        return layout(
            constraints.constrainWidth(placeable.width.grownBy(horizontalPadding)),
            constraints.constrainHeight(placeable.height.grownBy(verticalPadding)),
        ) {
            if (rtlAware) placeable.placeRelative(start, top) else placeable.place(start, top)
        }
    }
}
