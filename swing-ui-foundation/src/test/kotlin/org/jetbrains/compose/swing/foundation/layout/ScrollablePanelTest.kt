package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScrollablePanelTest {
    @Test
    fun constrainedPanelFulfillsScrollableContract() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER).preferredSize(SIZE)) {}
            }

            val component = onNodeWithTag(CONTAINER).fetch()
            val panel = assertIs<ScrollablePanel>(component)
            val scrollable = assertIs<Scrollable>(panel)

            assertFalse(panel.isOptimizedDrawingEnabled)
            assertEquals(SIZE, scrollable.preferredScrollableViewportSize)
            assertEquals(
                panel.getFontMetrics(panel.font).height,
                scrollable.getScrollableUnitIncrement(Rectangle(0, 0, 10, 10), SwingConstants.VERTICAL, 1),
            )
            assertEquals(
                25,
                scrollable.getScrollableBlockIncrement(Rectangle(0, 0, 50, 25), SwingConstants.VERTICAL, 1),
            )
            assertEquals(
                50,
                scrollable.getScrollableBlockIncrement(Rectangle(0, 0, 50, 25), SwingConstants.HORIZONTAL, 1),
            )

            // Outside JViewport
            assertFalse(scrollable.scrollableTracksViewportWidth)
            assertFalse(scrollable.scrollableTracksViewportHeight)

            // Parent is JPanel (not JViewport)
            val parentPanel = JPanel()
            parentPanel.add(panel)
            assertFalse(scrollable.scrollableTracksViewportWidth)
            assertFalse(scrollable.scrollableTracksViewportHeight)

            // Inside JViewport larger than preferredSize
            val viewport = JViewport()
            viewport.view = panel
            viewport.size = Dimension(150, 120)
            assertTrue(scrollable.scrollableTracksViewportWidth)
            assertTrue(scrollable.scrollableTracksViewportHeight)

            // Inside JViewport smaller than preferredSize
            viewport.size = Dimension(50, 40)
            assertFalse(scrollable.scrollableTracksViewportWidth)
            assertFalse(scrollable.scrollableTracksViewportHeight)
        }

    private companion object {
        const val CONTAINER = "container-under-test"
        val SIZE = Dimension(100, 80)
    }
}
