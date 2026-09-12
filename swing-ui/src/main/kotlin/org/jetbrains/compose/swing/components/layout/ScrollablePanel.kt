package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.components.fillsViewport
import org.jetbrains.compose.swing.components.scrollableLine
import org.jetbrains.compose.swing.components.scrollablePage
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.Scrollable

/** The panel behind every composable container: it answers a scroll pane for itself, see [scrollableLine]. */
internal open class ScrollablePanel(
    layout: LayoutManager? = null,
) : JPanel(layout),
    Scrollable {
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
