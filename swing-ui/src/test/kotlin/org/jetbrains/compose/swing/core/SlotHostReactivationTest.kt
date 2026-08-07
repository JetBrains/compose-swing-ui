package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * A container that hosts its children through a [SlotAttachment] - a dedicated Swing setter rather
 * than the generic `Container.add` - keeps them across parking. Content inside a deactivated
 * [ReusableContentHost] (equally: content parked by `movableContent`) leaves the composition but stays
 * in the Swing tree, and reactivation drives the very same components again: the applier, the sole
 * authority on attachment, records no change for either step.
 *
 * Each test parks and reactivates one such host and reads its children back off the live component.
 */
class SlotHostReactivationTest {
    /**
     * Composes [content] inside a host, parks that host, and brings it back.
     *
     * @param beforePark runs once the first composition has settled and before the host is parked, so a
     *   test can hold on to the components composed then and compare them with what it finds afterwards.
     */
    private suspend fun ComposeSwingTest.parkAndReactivate(
        beforePark: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                content()
            }
        }
        beforePark()
        active = false
        awaitIdle()
        active = true
        awaitIdle()
    }

    @Test
    fun aReactivatedTabbedPaneStillHostsItsTabs() = runComposeSwingTest {
        parkAndReactivate {
            TabbedPane(selectedIndex = 0) {
                Label("g", SwingModifier.tab("General"))
                Label("a", SwingModifier.tab("Advanced"))
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
        lateinit var paneBefore: JScrollPane
        lateinit var bodyBefore: Component
        parkAndReactivate(
            beforePark = {
                paneBefore = onNodeOfType<JScrollPane>().fetch()
                bodyBefore = onNodeWithText("body").fetch()
            },
        ) {
            ScrollPane {
                Label("body", SwingModifier.viewport())
                Label("rows", SwingModifier.rowHeader())
                Label("columns", SwingModifier.columnHeader())
            }
        }

        // Each region is a viewport the pane addresses by its own accessor, not by tree position.
        val pane = onNodeOfType<JScrollPane>().fetch()
        assertNotNull(pane.viewport.view, "the viewport should still hold the declared view")
        assertNotNull(pane.rowHeader?.view, "the row header should still hold the declared view")
        assertNotNull(pane.columnHeader?.view, "the column header should still hold the declared view")

        // Parking leaves a component where it is, so reactivation drives the very ones composed before it.
        assertSame(paneBefore, pane, "reactivation should drive the same pane")
        assertSame(bodyBefore, onNodeWithText("body").fetch(), "reactivation should drive the same content")
        assertSame(bodyBefore, pane.viewport.view, "the viewport should still hold the component it was given")
    }

    @Test
    fun aReactivatedSplitPaneStillHostsBothSides() = runComposeSwingTest {
        parkAndReactivate {
            SplitPane {
                Label("left", SwingModifier.first())
                Label("right", SwingModifier.second())
            }
        }

        // Which side a child sits on is the pane's own state, not a layout constraint.
        val pane = onNodeOfType<JSplitPane>().fetch()
        assertNotNull(pane.leftComponent, "the leading side should still hold the declared component")
        assertNotNull(pane.rightComponent, "the trailing side should still hold the declared component")
    }

    @Test
    fun aReactivatedDesktopPaneStillHostsItsFrames() = runComposeSwingTest {
        parkAndReactivate {
            DesktopPane {
                InternalFrame(title = "Editor", bounds = Rectangle(0, 0, FRAME_SIDE, FRAME_SIDE)) { Label("editor") }
                InternalFrame(
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
