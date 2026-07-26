package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ScrollPane].
 *
 * The central guarantee: content and headers/corners are hosted in the JScrollPane's pre-wired
 * viewport / installed header viewports / corner hosts - never added directly to the JScrollPane by
 * the applier. These tests assert against the real AWT tree (viewport view, header viewports, corner
 * components) on the EDT.
 */
class ScrollPaneBehaviorTest {
    @Test
    fun contentIsHostedInTheViewportViewNotAddedToTheScrollPane() = runComposeSwingTest {
        setContent {
            ScrollPane {
                content { Label(text = "Body") }
            }
        }

        // The content is reached through the viewport rather than as a child of the pane itself.
        val body = onNodeWithText("Body")
        body.assert(SwingMatcher.hasAnyAncestor(SwingMatcher.isOfType<JViewport>()))
        body.assert(!SwingMatcher.hasParent(SwingMatcher.isOfType<JScrollPane>()))

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertSame(
            body.fetch(),
            pane.viewport.view,
            "the content should be the view the pre-wired viewport holds",
        )
        // The JScrollPane's own direct children are the Swing-owned regions - the viewport, the
        // scrollbars - so the content host is among them only if it was added to the pane directly.
        val directChildren = pane.components.toList()
        assertTrue(
            directChildren.none { it === pane.viewport.view },
            "content host was added directly to the JScrollPane instead of into the viewport",
        )
        assertTrue(directChildren.contains(pane.viewport), "viewport is not a direct child of the JScrollPane")
    }

    @Test
    fun swappingContentUpdatesTheViewportViewInPlace() = runComposeSwingTest {
        var flag by mutableStateOf(true)
        setContent {
            ScrollPane {
                content {
                    if (flag) Label(text = "First") else Label(text = "Second")
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        // The viewport host is the same instance before and after the swap; only its view changes.
        val viewportBefore = pane.viewport
        onNodeWithText("First").assertExists()

        flag = false
        awaitIdle()

        onNodeWithText("First").assertDoesNotExist()
        assertSame(viewportBefore, pane.viewport, "viewport instance changed across content swap")
        assertSame(onNodeWithText("Second").fetch(), pane.viewport.view, "new content not in viewport")
    }

    @Test
    fun rowHeaderPresentInstallsTheHeaderViewportAndItsView() = runComposeSwingTest {
        setContent {
            ScrollPane {
                content { Label(text = "Body") }
                rowHeader { Label(text = "RowHead") }
            }
        }

        val rowHeader =
            assertNotNull(
                onNodeOfType<JScrollPane>().fetch().rowHeader,
                "row header viewport was not installed",
            )
        assertSame(
            onNodeWithText("RowHead").fetch(),
            rowHeader.view,
            "row header content not hosted in the row header viewport",
        )
    }

    @Test
    fun columnHeaderPresentInstallsTheHeaderViewportAndItsView() = runComposeSwingTest {
        setContent {
            ScrollPane {
                content { Label(text = "Body") }
                columnHeader { Label(text = "ColHead") }
            }
        }

        val columnHeader =
            assertNotNull(
                onNodeOfType<JScrollPane>().fetch().columnHeader,
                "column header viewport was not installed",
            )
        assertSame(
            onNodeWithText("ColHead").fetch(),
            columnHeader.view,
            "column header content not hosted in the column header viewport",
        )
    }

    @Test
    fun anAbsentHeaderInstallsNothing() = runComposeSwingTest {
        setContent {
            ScrollPane {
                content { Label(text = "Body") }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertNull(pane.rowHeader, "row header viewport installed though no rowHeader was declared")
        assertNull(pane.columnHeader, "column header viewport installed though no columnHeader was declared")
        assertNull(pane.getCorner(JScrollPane.UPPER_LEFT_CORNER), "a corner was installed though none declared")
        assertNull(
            pane.getCorner(JScrollPane.UPPER_RIGHT_CORNER),
            "a corner was installed though none declared",
        )
    }

    @Test
    fun componentOrientationResolvesLeadingCornerUnderRightToLeft() = runComposeSwingTest {
        // Declare the corner only after the pane is flipped to right-to-left, so it is installed
        // (setCorner resolves the leading/trailing key against the orientation at install time)
        // while the pane is RTL: the leading corner must then resolve to the right edge.
        var declareCorner by mutableStateOf(false)
        setContent {
            ScrollPane {
                content { Label(text = "Body") }
                if (declareCorner) {
                    corner(JScrollPane.UPPER_LEADING_CORNER) { Label(text = "CornerView") }
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        pane.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        declareCorner = true
        awaitIdle()

        assertSame(
            onNodeWithText("CornerView").fetch(),
            pane.getCorner(JScrollPane.UPPER_RIGHT_CORNER),
            "UpperLeading corner did not resolve to UPPER_RIGHT under RTL",
        )
        assertNull(
            pane.getCorner(JScrollPane.UPPER_LEFT_CORNER),
            "UpperLeading corner should not occupy the left edge under RTL",
        )
    }

    @Test
    fun scrollbarPolicyMapsThrough() = runComposeSwingTest {
        setContent {
            ScrollPane(verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS) {
                content { Label(text = "Body") }
            }
        }

        assertEquals(
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            onNodeOfType<JScrollPane>().fetch().verticalScrollBarPolicy,
            "vertical scrollbar policy did not map through",
        )
    }

    @Test
    fun disposingTheScrollPaneTearsItDown() = runComposeSwingTest {
        var show by mutableStateOf(true)
        setContent {
            if (show) {
                ScrollPane {
                    content { Label(text = "Body") }
                }
            }
        }

        onNodeOfType<JScrollPane>().assertExists()
        onNodeWithText("Body").assertExists()

        show = false
        awaitIdle()

        onNodeOfType<JScrollPane>().assertDoesNotExist()
        onNodeWithText("Body").assertDoesNotExist()
    }
}
