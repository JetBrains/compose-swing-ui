package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent

/** Test-only drawing leaf used where tests need a raw Swing view; [onDraw] draws inside the border. */
@Composable
internal fun Canvas(
    modifier: SwingModifier = SwingModifier,
    onDraw: (Graphics2D, Int, Int) -> Unit,
) {
    SwingNode(
        factory = {
            object : JComponent() {
                override fun paintComponent(g: Graphics) {
                    val insets = insets
                    val drawWidth = (width - insets.left - insets.right).coerceAtLeast(0)
                    val drawHeight = (height - insets.top - insets.bottom).coerceAtLeast(0)
                    val surface = g.create(insets.left, insets.top, drawWidth, drawHeight) as Graphics2D
                    try {
                        onDraw(surface, drawWidth, drawHeight)
                    } finally {
                        surface.dispose()
                    }
                }
            }
        },
        modifier = modifier,
    )
}
