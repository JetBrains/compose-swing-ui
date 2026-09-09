package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.fillsViewport
import org.jetbrains.compose.swing.components.scrollableLine
import org.jetbrains.compose.swing.components.scrollablePage
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.Scrollable

/** Test-only raw Swing view for the scrollable-container comparison. */
@Composable
internal fun Canvas(
    modifier: SwingModifier = SwingModifier,
    onDraw: (Graphics, Int, Int) -> Unit,
) {
    SwingNode(
        factory = {
            object : JComponent(), Scrollable {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    onDraw(g, width, height)
                }

                override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

                override fun getScrollableUnitIncrement(
                    visibleRect: Rectangle,
                    orientation: Int,
                    direction: Int,
                ): Int = scrollableLine()

                override fun getScrollableBlockIncrement(
                    visibleRect: Rectangle,
                    orientation: Int,
                    direction: Int,
                ): Int = scrollablePage(visibleRect, orientation)

                override fun getScrollableTracksViewportWidth(): Boolean = fillsViewport { it.width }

                override fun getScrollableTracksViewportHeight(): Boolean = fillsViewport { it.height }
            }
        },
        modifier = modifier,
    )
}
