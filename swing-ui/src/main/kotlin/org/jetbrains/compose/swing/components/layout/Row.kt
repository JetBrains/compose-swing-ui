@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode

/**
 * A composable that arranges its [content] horizontally, along the panel's reading order.
 *
 * Every child keeps the width it prefers, and the width the row has left over is placed by
 * [horizontalArrangement] - before the children, after them, between them, or as a fixed gap through
 * [Arrangement.spacedBy]. Across the row each child keeps the height it prefers and sits where
 * [verticalAlignment] puts it.
 *
 * A child claims a share of the leftover width with `weight`, or names its own vertical placement with
 * `align`, through [RowScope]:
 *
 * ```
 * Row(horizontalArrangement = Arrangement.spacedBy(8), verticalAlignment = Alignment.CenterVertically) {
 *     Label(text = "Status")
 *     Panel(PanelLayout.Flow(), modifier = SwingModifier.weight(1f)) { Details() }
 *     Button(text = "Close", onClick = ::close)
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param horizontalArrangement where the children, and the width left over, go along the row; the
 *   default [Arrangement.Start] packs them against the leading edge and leaves the rest of the width
 *   after them
 * @param verticalAlignment where each child sits across the row; the default [Alignment.Top] puts each
 *   at the top of the height available to it
 * @param content the composable content of the row; see [RowScope]
 */
@Composable
public fun Row(
    modifier: SwingModifier = SwingModifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    val axisArrangement = HorizontalAxisArrangement(horizontalArrangement)
    val axisAlignment = VerticalAxisAlignment(verticalAlignment)

    SwingNode(
        factory = {
            ScrollablePanel(LinearLayout(LayoutAxis.Horizontal, axisArrangement, axisAlignment))
        },
        modifier = modifier,
        update = {
            updateLayout<LinearLayout, _>(axisArrangement) { this.arrangement = it }
            updateLayout<LinearLayout, _>(axisAlignment) { this.alignment = it }
        },
        content = { RowScopeImpl.content() },
    )
}
