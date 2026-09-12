@file:JvmMultifileClass
@file:JvmName("FoundationLayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.updateLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode

/**
 * A composable that stacks its [content] in one place, one child over another.
 *
 * The box asks for the largest size among the children that do not match its own, plus its insets. Each
 * child keeps the size it prefers, capped at the box's inner extent, and sits where [contentAlignment]
 * puts it. The children stack in declaration order: the last child declared paints over the ones before
 * it, and takes a mouse event at a point they share. A child naming a `zIndex` rises over every sibling
 * declaring a smaller one, wherever the two are declared.
 *
 * A child names its own placement with `align`, takes the box's whole extent with `matchParentSize`, or
 * names where in the stack it sits with `zIndex`, through [BoxScope]:
 *
 * ```
 * Box(contentAlignment = Alignment.Center) {
 *     ProgressBar(value = 40, modifier = SwingModifier.matchParentSize())
 *     Label(text = "Loading")
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param contentAlignment where each child sits in the box; the default [Alignment.TopStart] puts each at
 *   the top of the leading edge of the extent available to it
 * @param content the composable content of the box; see [BoxScope]
 */
@Composable
public fun Box(
    modifier: SwingModifier = SwingModifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    SwingNode(
        factory = { OverlapPanel(OverlapLayout(contentAlignment)) },
        modifier = modifier,
        update = {
            updateLayout<OverlapLayout, _>(contentAlignment) { this.alignment = it }
        },
        content = { BoxScopeImpl.content() },
    )
}
