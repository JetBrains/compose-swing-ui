@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.constants.FlowAlignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with FlowLayout.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param alignment the horizontal alignment of components within each row (a [FlowAlignment]
 *   `FlowLayout` value)
 * @param hgap the horizontal gap between components
 * @param vgap the vertical gap between components
 * @param content the composable content of the panel
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
        factory = { JPanel(FlowLayout(alignment, hgap, vgap)) },
        update = {
            updateLayout<FlowLayout, _>(alignment) { this.alignment = it }
            updateLayout<FlowLayout, _>(hgap) { this.hgap = it }
            updateLayout<FlowLayout, _>(vgap) { this.vgap = it }
            applyModifier(modifier)
        },
        content = content,
    )
}
