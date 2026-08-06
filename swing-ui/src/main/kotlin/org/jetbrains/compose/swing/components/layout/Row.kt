@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JPanel

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
 *     FlowPanel(modifier = SwingModifier.weight(1f)) { Details() }
 *     Button(text = "Close", onClick = ::close)
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param horizontalArrangement where the children, and the width left over, go along the row
 * @param verticalAlignment where each child sits across the row
 * @param content the composable content of the row; see [RowScope]
 */
@Composable
public fun Row(
    modifier: SwingModifier = SwingModifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    // Remembered with the row: children write weight/alignment into it for the layout manager to read
    // each pass.
    val scope = remember { RowScopeImpl() }
    val axisArrangement = HorizontalAxisArrangement(horizontalArrangement)
    val axisAlignment = VerticalAxisAlignment(verticalAlignment)

    SwingNode(
        factory = { JPanel(LinearLayout(LayoutAxis.Horizontal, scope.placements, axisArrangement, axisAlignment)) },
        update = {
            updateLayout<LinearLayout, _>(scope.placements) { this.placements = it }
            updateLayout<LinearLayout, _>(axisArrangement) { this.arrangement = it }
            updateLayout<LinearLayout, _>(axisAlignment) { this.alignment = it }
            applyModifier(modifier)
        },
        content = { scope.content() },
    )
}
