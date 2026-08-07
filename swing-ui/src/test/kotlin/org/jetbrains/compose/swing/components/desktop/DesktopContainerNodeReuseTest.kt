package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JInternalFrame
import javax.swing.JLayeredPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A desktop container node is recyclable: a parked [ReusableContentHost] child is reactivated onto the
 * component the node already holds, and the node's factory does not run a second time. Every child a
 * later composition declares therefore has to reach that component - the frames of a [DesktopPane] and
 * the layered children of a [LayeredPane] alike, each of them at the depth it stands on.
 */
class DesktopContainerNodeReuseTest {
    @Test
    fun aReactivatedDesktopPaneHostsTheFramesTheCompositionDeclares() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var showConsole by mutableStateOf(false)
        setContent {
            ReusableContentHost(active = active) {
                DesktopPane {
                    InternalFrame(title = "Editor", bounds = Rectangle(0, 0, 200, 100)) { Label(text = "editor") }
                    if (showConsole) {
                        InternalFrame(title = "Console", bounds = Rectangle(20, 20, 200, 100)) { Label(text = "log") }
                    }
                }
            }
        }
        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeWithText("editor").assertExists()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeWithText("editor").assertExists()

        showConsole = true
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(2)
        onNodeWithText("log").assertExists()
    }

    @Test
    fun aReactivatedLayeredPaneHostsTheChildrenTheCompositionDeclares() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var showOverlay by mutableStateOf(false)
        setContent {
            ReusableContentHost(active = active) {
                LayeredPane {
                    Label(text = "back", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                    Label(text = "plain")
                    if (showOverlay) {
                        Label(text = "front", modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
                    }
                }
            }
        }
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val pane = onNodeOfType<JLayeredPane>().fetch()
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)
        onNodeWithText("back").assertExists()
        assertEquals(
            JLayeredPane.DEFAULT_LAYER,
            pane.getLayer(onNodeWithText("plain").fetch<JComponent>()),
            "a child that declares no depth stands on the pane's default layer",
        )

        showOverlay = true
        awaitIdle()

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(3)
        assertEquals(
            JLayeredPane.PALETTE_LAYER,
            pane.getLayer(onNodeWithText("front").fetch<JComponent>()),
            "the child added after reactivation should sit on its declared layer",
        )
        // A container paints its children from its last index down to its first, so the child of the
        // higher layer is the one holding the lower index.
        assertTrue(
            pane.getIndexOf(onNodeWithText("front").fetch<JComponent>()) <
                pane.getIndexOf(onNodeWithText("plain").fetch<JComponent>()),
            "the child on the higher layer paints above the children of the layer below it",
        )
    }
}
