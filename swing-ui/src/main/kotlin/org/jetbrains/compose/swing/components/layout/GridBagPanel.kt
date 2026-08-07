@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.GridBagLayout
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with GridBagLayout, placing each child in the grid cell that child's
 * own constraints describe.
 *
 * A child names its cell with `item`, through [GridBagPanelScope]:
 *
 * ```
 * GridBagPanel {
 *     Label(text = "Name", modifier = SwingModifier.item(gridx = 0, gridy = 0))
 *     Button(
 *         text = "Pick",
 *         modifier = SwingModifier.item(gridx = 1, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL),
 *     )
 * }
 * ```
 *
 * A child that declares no `item` is laid out under `GridBagConstraints`' own defaults.
 *
 * Constraints are reactive: changing a child's arguments re-places it, and dropping the child (e.g.
 * behind an `if`) removes it.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param content the composable content of the panel; see [GridBagPanelScope]
 * @see java.awt.GridBagLayout
 */
@Composable
public fun GridBagPanel(
    modifier: SwingModifier = SwingModifier,
    content: @Composable GridBagPanelScope.() -> Unit,
) {
    SwingNode(
        factory = { JPanel(GridBagLayout()) },
        update = {
            applyModifier(modifier)
        },
        content = { GridBagPanelScopeImpl.content() },
    )
}
