@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.BorderLayout

/**
 * A composable wrapper for JPanel with BorderLayout, placing each child in the region that child names.
 *
 * A child names its region on its own modifier, through [BorderPanelScope]:
 * ```
 * BorderPanel {
 *     Toolbar(modifier = SwingModifier.north())
 *     Editor()
 *     StatusBar(modifier = SwingModifier.south())
 * }
 * ```
 * A child that names no region occupies the center, so the panel's main content is written plainly.
 *
 * A region hosts one child: dropping a child (e.g. behind an `if`) empties the region it occupied, and
 * an edge no child names holds nothing.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param hgap the horizontal gap between regions
 * @param vgap the vertical gap between regions
 * @param content the composable content of the panel; see [BorderPanelScope]
 * @see java.awt.BorderLayout
 */
@Composable
public fun BorderPanel(
    modifier: SwingModifier = SwingModifier,
    hgap: Int = 0,
    vgap: Int = 0,
    content: @Composable BorderPanelScope.() -> Unit,
) {
    SwingNode(
        factory = { ScrollablePanel(BorderLayout(hgap, vgap)) },
        update = {
            updateLayout<BorderLayout, _>(hgap) { this.hgap = it }
            updateLayout<BorderLayout, _>(vgap) { this.vgap = it }
            applyModifier(modifier)
        },
        content = { BorderPanelScopeImpl.content() },
    )
}
