package org.jetbrains.compose.swing.foundation

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

class CanvasScrollableTest {
    @Test
    fun canvasFulfillsScrollableContract() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE),
                    onDraw = { _, _, _ -> },
                )
            }

            val component = onNodeWithTag(CANVAS).fetch()
            val scrollable = assertIs<Scrollable>(component)

            assertEquals(SIZE, scrollable.preferredScrollableViewportSize)
            assertEquals(
                component.getFontMetrics(component.font).height,
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
            val panel = JPanel()
            panel.add(component)
            assertFalse(scrollable.scrollableTracksViewportWidth)
            assertFalse(scrollable.scrollableTracksViewportHeight)

            // Inside JViewport larger than preferredSize
            val viewport = JViewport()
            viewport.view = component
            viewport.size = Dimension(150, 120)
            assertTrue(scrollable.scrollableTracksViewportWidth)
            assertTrue(scrollable.scrollableTracksViewportHeight)

            // Inside JViewport smaller than preferredSize
            viewport.size = Dimension(50, 40)
            assertFalse(scrollable.scrollableTracksViewportWidth)
            assertFalse(scrollable.scrollableTracksViewportHeight)
        }

    private companion object {
        const val CANVAS = "canvas-under-test"
        val SIZE = Dimension(100, 80)
    }
}
