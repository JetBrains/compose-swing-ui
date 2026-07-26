package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * End-to-end tests for [ScrollPane]'s region installation. They drive the real composable through a
 * [SwingApplier], then assert against the resulting [JScrollPane]'s actual structure: each region must
 * reach its dedicated slot (`viewport.view`, `rowHeader`/`columnHeader`, `getCorner`), redeclaring a
 * region must replace its content, and removing a region must clear the corresponding JScrollPane slot
 * with no leftover.
 */
class ScrollPaneRegionTest {
    private fun labelTextOf(component: Component?): String? = (component as? JLabel)?.text

    @Test
    fun contentReachesTheCentralViewport() = runComposeSwingTest {
        setContent {
            ScrollPane {
                content { Label(text = "body") }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
        assertEquals("body", (pane.viewport.view as? JLabel)?.text)
    }

    @Test
    fun headersAndCornerReachTheirDedicatedSlots() = runComposeSwingTest {
        setContent {
            ScrollPane {
                content { Label(text = "body") }
                rowHeader { Label(text = "rows") }
                columnHeader { Label(text = "cols") }
                corner(JScrollPane.UPPER_TRAILING_CORNER) { Label(text = "corner") }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
        assertEquals("body", labelTextOf(pane.viewport.view), "content should reach the central viewport")
        assertEquals("rows", labelTextOf(pane.rowHeader?.view), "rowHeader should reach the row header slot")
        assertEquals("cols", labelTextOf(pane.columnHeader?.view), "columnHeader should reach the column header slot")
        assertEquals(
            "corner",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER)),
            "corner should reach the upper-trailing corner slot",
        )
    }

    @Test
    fun redeclaringContentReplacesTheView() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            ScrollPane {
                content { Label(text = label) }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
        assertEquals("first", labelTextOf(pane.viewport.view), "the viewport should start with the first content")

        label = "second"
        awaitIdle()

        // The single viewport view now reflects the new content; the viewport itself is reused.
        assertEquals("second", labelTextOf(pane.viewport.view), "redeclaring content should replace the viewport view")
    }

    @Test
    fun removingARegionClearsTheJScrollPaneSlot() = runComposeSwingTest {
        var showHeaders by mutableStateOf(true)
        setContent {
            ScrollPane {
                content { Label(text = "body") }
                if (showHeaders) {
                    rowHeader { Label(text = "rows") }
                    columnHeader { Label(text = "cols") }
                    corner(JScrollPane.UPPER_TRAILING_CORNER) { Label(text = "corner") }
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
        assertTrue(pane.rowHeader != null, "row header should be installed before removal")
        assertTrue(pane.columnHeader != null, "column header should be installed before removal")
        assertTrue(
            pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER) != null,
            "corner should be installed before removal",
        )

        showHeaders = false
        awaitIdle()

        // Uninstall must release each host slot entirely (not leave an empty header viewport / corner).
        assertNull(pane.rowHeader, "row header slot leaked")
        assertNull(pane.columnHeader, "column header slot leaked")
        assertNull(pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER), "corner slot leaked")
        // Content is untouched.
        assertEquals("body", labelTextOf(pane.viewport.view), "content should survive removing the headers")
    }

    @Test
    fun removingContentClearsTheViewportView() = runComposeSwingTest {
        var showContent by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (showContent) {
                    content { Label(text = "body") }
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
        val viewportBefore = pane.viewport
        assertEquals("body", labelTextOf(pane.viewport.view), "the viewport should start holding the content")

        showContent = false
        awaitIdle()

        // The constructor-wired viewport stays (Swing owns it) but holds nothing.
        assertSame(viewportBefore, pane.viewport, "viewport instance must be reused")
        assertNull(pane.viewport.view, "viewport view leaked after content removal")
    }
}
