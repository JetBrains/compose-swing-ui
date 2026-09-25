package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.defaults.DefaultBackground
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A component default changed above a [Window], a [Dialog], a [MenuBar] or a [Layer] reaches the
 * components already composed there, including the layer's glass pane.
 *
 * The peers are composed `visible = false`: sizing to content realizes a peer, which is all the
 * component-tree queries need. Skipped in headless environments where no real peer can be realized.
 */
class WindowComponentDefaultsTest {
    @Test
    fun aChangedDefaultReachesWindowContent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var color by mutableStateOf(Color.MAGENTA)
        setContent {
            ProvideComponentDefaults(DefaultBackground provides color) {
                Window(onCloseRequest = {}, title = "window-defaults", visible = false) { Label("content") }
            }
        }
        val label = onWindowWithTitle("window-defaults").onNodeWithText("content").fetch()
        assertEquals(Color.MAGENTA, label.background)

        color = Color.RED
        awaitIdle()

        assertEquals(Color.RED, label.background, "a changed default reaches the content of a window")
    }

    @Test
    fun aChangedDefaultReachesDialogContent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var color by mutableStateOf(Color.MAGENTA)
        setContent {
            ProvideComponentDefaults(DefaultBackground provides color) {
                Dialog(onCloseRequest = {}, title = "dialog-defaults", visible = false) { Label("content") }
            }
        }
        val label = onWindowWithTitle("dialog-defaults").onNodeWithText("content").fetch()
        assertEquals(Color.MAGENTA, label.background)

        color = Color.RED
        awaitIdle()

        assertEquals(Color.RED, label.background, "a changed default reaches the content of a dialog")
    }

    @Test
    fun aChangedDefaultReachesAMenuBar() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var color by mutableStateOf(Color.MAGENTA)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-defaults", visible = false) {
                ProvideComponentDefaults(DefaultBackground provides color) {
                    MenuBar { MenuItem("Open", onClick = {}) }
                }
            }
        }
        val item = onWindowWithTitle("menu-bar-defaults").fetch<JFrame>().jMenuBar.getComponent(0)
        assertEquals(Color.MAGENTA, item.background)

        color = Color.RED
        awaitIdle()

        assertEquals(Color.RED, item.background, "a changed default reaches the items of a menu bar")
    }

    @Test
    fun aChangedDefaultReachesAGlassPane() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var color by mutableStateOf(Color.MAGENTA)
        setContent {
            Window(onCloseRequest = {}, title = "glass-pane-defaults", visible = false) {
                ProvideComponentDefaults(DefaultBackground provides color) {
                    Layer { GlassPane { Label("overlay") } }
                }
            }
        }
        val label = onWindowWithTitle("glass-pane-defaults").onNodeWithText("overlay").fetch()
        assertEquals(Color.MAGENTA, label.background)

        color = Color.RED
        awaitIdle()

        assertEquals(Color.RED, label.background, "a changed default reaches the content of a glass pane")
    }
}
