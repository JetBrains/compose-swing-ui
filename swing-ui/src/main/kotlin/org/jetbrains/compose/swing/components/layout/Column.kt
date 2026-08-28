@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode

/**
 * A composable that arranges its [content] vertically, top to bottom.
 *
 * Every child keeps the height it prefers, and the height the column has left over is placed by
 * [verticalArrangement] - above the children, below them, between them, or as a fixed gap through
 * [Arrangement.spacedBy]. Across the column each child keeps the width it prefers and sits where
 * [horizontalAlignment] puts it.
 *
 * A child claims a share of the leftover height with `weight`, or names its own horizontal placement
 * with `align`, through [ColumnScope]:
 *
 * ```
 * Column(verticalArrangement = Arrangement.spacedBy(8), horizontalAlignment = Alignment.CenterHorizontally) {
 *     Label(text = "Title")
 *     FlowPanel(modifier = SwingModifier.weight(1f)) { Body() }
 *     Button(text = "Close", onClick = ::close)
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param verticalArrangement where the children, and the height left over, go along the column
 * @param horizontalAlignment where each child sits across the column
 * @param content the composable content of the column; see [ColumnScope]
 */
@Composable
public fun Column(
    modifier: SwingModifier = SwingModifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Remembered with the column: children write weight/alignment into it for the layout manager to
    // read each pass.
    val scope = remember { ColumnScopeImpl() }
    val axisArrangement = VerticalAxisArrangement(verticalArrangement)
    val axisAlignment = HorizontalAxisAlignment(horizontalAlignment)

    SwingNode(
        factory = {
            ScrollablePanel(LinearLayout(LayoutAxis.Vertical, scope.placements, axisArrangement, axisAlignment))
        },
        update = {
            updateLayout<LinearLayout, _>(scope.placements) { this.placements = it }
            updateLayout<LinearLayout, _>(axisArrangement) { this.arrangement = it }
            updateLayout<LinearLayout, _>(axisAlignment) { this.alignment = it }
            applyModifier(modifier)
        },
        content = { scope.content() },
    )
}
