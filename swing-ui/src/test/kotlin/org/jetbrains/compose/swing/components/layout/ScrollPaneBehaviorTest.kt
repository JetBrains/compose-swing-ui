package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
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
 * The central guarantee: content, headers, and corners are hosted in the JScrollPane's pre-wired
 * viewport, header viewports, and corner hosts, never added directly to the JScrollPane by the
 * applier. These tests assert against the real AWT tree, on the EDT.
 */
class ScrollPaneBehaviorTest {
    @Test
    fun contentIsHostedInTheViewportViewNotAddedToTheScrollPane() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Label(text = "Body", modifier = SwingModifier.viewport())
            }
        }

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
                if (flag) {
                    Label(text = "First", modifier = SwingModifier.viewport())
                } else {
                    Label(text = "Second", modifier = SwingModifier.viewport())
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
                Label(text = "Body", modifier = SwingModifier.viewport())
                Label(text = "RowHead", modifier = SwingModifier.rowHeader())
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
                Label(text = "Body", modifier = SwingModifier.viewport())
                Label(text = "ColHead", modifier = SwingModifier.columnHeader())
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
                Label(text = "Body", modifier = SwingModifier.viewport())
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
        // The corner is declared only after the pane flips right-to-left: setCorner resolves the
        // leading/trailing key against orientation at install time, so it must resolve to the right edge.
        var declareCorner by mutableStateOf(false)
        setContent {
            ScrollPane {
                Label(text = "Body", modifier = SwingModifier.viewport())
                if (declareCorner) {
                    Label(text = "CornerView", modifier = SwingModifier.corner(JScrollPane.UPPER_LEADING_CORNER))
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
                Label(text = "Body", modifier = SwingModifier.viewport())
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
                    Label(text = "Body", modifier = SwingModifier.viewport())
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
