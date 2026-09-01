@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.FlowAlignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.FlowLayout

/**
 * A `JPanel` under a `FlowLayout`: the children are laid out in a row, each at the size it prefers, and
 * a child that no longer fits the panel's width starts a new row.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param alignment the horizontal alignment of components within each row (a [FlowAlignment]
 *   `FlowLayout` value); the default `CENTER` centers each row across the panel's width
 * @param hgap the horizontal gap held between two adjacent components and at the panel's left and right
 *   edges; `5`, `FlowLayout`'s own default
 * @param vgap the vertical gap held between two rows and at the panel's top and bottom edges; `5`,
 *   `FlowLayout`'s own default
 * @param content the composable content of the panel; empty by default
 * @see java.awt.FlowLayout
 */
@Composable
public fun FlowPanel(
    modifier: SwingModifier = SwingModifier,
    @FlowAlignment alignment: Int = FlowLayout.CENTER,
    hgap: Int = 5,
    vgap: Int = 5,
    content: @Composable () -> Unit = {},
) {
    SwingNode(
        factory = { ScrollablePanel(FlowLayout(alignment, hgap, vgap)) },
        update = {
            updateLayout<FlowLayout, _>(alignment) { this.alignment = it }
            updateLayout<FlowLayout, _>(hgap) { this.hgap = it }
            updateLayout<FlowLayout, _>(vgap) { this.vgap = it }
            applyModifier(modifier)
        },
        content = content,
    )
}
