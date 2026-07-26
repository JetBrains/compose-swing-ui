package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
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

/**
 * A desktop container node is recyclable: a parked [ReusableContentHost] child is reactivated onto the
 * component the node already holds, and the node's factory does not run a second time. Every child a
 * later composition declares therefore has to reach that component - the frames of a [DesktopPane] and
 * the layered children of a [LayeredPane] alike.
 */
class DesktopContainerNodeReuseTest {
    @Test
    fun aReactivatedDesktopPaneHostsTheFramesTheCompositionDeclares() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var showConsole by mutableStateOf(false)
        setContent {
            ReusableContentHost(active = active) {
                DesktopPane {
                    internalFrame(title = "Editor", bounds = Rectangle(0, 0, 200, 100)) { Label(text = "editor") }
                    if (showConsole) {
                        internalFrame(title = "Console", bounds = Rectangle(20, 20, 200, 100)) { Label(text = "log") }
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
                    layer(JLayeredPane.DEFAULT_LAYER) { Label(text = "back") }
                    if (showOverlay) {
                        layer(JLayeredPane.PALETTE_LAYER) { Label(text = "front") }
                    }
                }
            }
        }
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(1)

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(1)
        onNodeWithText("back").assertExists()

        showOverlay = true
        awaitIdle()

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)
        assertEquals(
            JLayeredPane.PALETTE_LAYER,
            JLayeredPane.getLayer(onNodeWithText("front").fetch<JComponent>()),
            "the child added after reactivation should sit on its declared layer",
        )
    }
}
