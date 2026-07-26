package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLayeredPane
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A container that hosts its children through a [SlotAttachment] - a dedicated Swing setter rather
 * than the generic `Container.add` - keeps them across parking. Content inside a deactivated
 * [ReusableContentHost] (equally: content parked by `movableContent`) leaves the composition but stays
 * in the Swing tree, and reactivation drives the very same components again: the applier, the sole
 * authority on attachment, records no change for either step.
 *
 * Each test parks and reactivates one slot host and reads its children back off the live component.
 */
class SlotHostReactivationTest {
    /** Composes [content] inside a host, parks that host, and brings it back. */
    private suspend fun ComposeSwingTest.parkAndReactivate(content: @Composable () -> Unit) {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                content()
            }
        }
        active = false
        awaitIdle()
        active = true
        awaitIdle()
    }

    @Test
    fun aReactivatedTabbedPaneStillHostsItsTabs() = runComposeSwingTest {
        parkAndReactivate {
            TabbedPane(selectedIndex = 0) {
                tab("General") { Label("g") }
                tab("Advanced") { Label("a") }
            }
        }

        // A tab title is the pane's own state rather than a component, so it is read off the pane.
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(
            listOf("General", "Advanced"),
            (0 until pane.tabCount).map(pane::getTitleAt),
            "the pane should still host its declared tabs",
        )
    }

    @Test
    fun aReactivatedScrollPaneStillHostsItsRegions() = runComposeSwingTest {
        parkAndReactivate {
            ScrollPane {
                content { Label("body") }
                rowHeader { Label("rows") }
                columnHeader { Label("columns") }
            }
        }

        // Each region is a viewport the pane addresses by its own accessor, not by tree position.
        val pane = onNodeOfType<JScrollPane>().fetch()
        assertNotNull(pane.viewport.view, "the viewport should still hold the declared view")
        assertNotNull(pane.rowHeader?.view, "the row header should still hold the declared view")
        assertNotNull(pane.columnHeader?.view, "the column header should still hold the declared view")
    }

    @Test
    fun aReactivatedSplitPaneStillHostsBothSides() = runComposeSwingTest {
        parkAndReactivate {
            SplitPane {
                first { Label("left") }
                second { Label("right") }
            }
        }

        // Which side a child sits on is the pane's own state, not a layout constraint.
        val pane = onNodeOfType<JSplitPane>().fetch()
        assertNotNull(pane.leftComponent, "the leading side should still hold the declared component")
        assertNotNull(pane.rightComponent, "the trailing side should still hold the declared component")
    }

    @Test
    fun aReactivatedLayeredPaneStillHostsItsLayers() = runComposeSwingTest {
        parkAndReactivate {
            LayeredPane {
                layer(JLayeredPane.DEFAULT_LAYER) { Label("back") }
                layer(JLayeredPane.PALETTE_LAYER) { Label("front") }
            }
        }

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)
        onNodeWithText("back").assertExists()
        onNodeWithText("front").assertExists()
    }

    @Test
    fun aReactivatedDesktopPaneStillHostsItsFrames() = runComposeSwingTest {
        parkAndReactivate {
            DesktopPane {
                internalFrame(title = "Editor", bounds = Rectangle(0, 0, FRAME_SIDE, FRAME_SIDE)) { Label("editor") }
                internalFrame(
                    title = "Console",
                    bounds = Rectangle(20, 20, FRAME_SIDE, FRAME_SIDE),
                ) { Label("console") }
            }
        }

        onNodeOfType<JDesktopPane>().onChildren().assertCountEquals(2)
        // An internal frame carries a title of its own, which no matcher reads.
        assertEquals(
            listOf("Editor", "Console"),
            onAllNodesOfType<JInternalFrame>().fetchAll().map { it.title },
            "the desktop should still host its declared frames",
        )
    }

    private companion object {
        const val FRAME_SIDE: Int = 120
    }
}
