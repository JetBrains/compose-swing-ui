package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import kotlin.test.Test

/**
 * Behavioural tests asserting that the `content` of a realized [Window] or [Dialog] keeps recomposing:
 * a changed child declaration reaches the peer's component tree, and a child that the declaration adds
 * or drops appears in and disappears from it.
 *
 * The peers are composed `visible = false`: sizing to content realizes a peer, which is all the
 * component-tree queries need. Skipped in headless environments where no real peer can be realized.
 */
class WindowContentReactivityTest {
    @Test
    fun windowContentFollowsAChangedChildDeclaration() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var text by mutableStateOf("window-child")
        setContent {
            Window(onCloseRequest = {}, title = "window-content-react-test", visible = false) {
                Label(text = text)
            }
        }
        val window = onWindowWithTitle("window-content-react-test")
        window.onNodeWithText("window-child").assertExists()

        text = "window-child-updated"
        awaitIdle()
        window.onNodeWithText("window-child-updated").assertExists()
        window.onNodeWithText("window-child").assertDoesNotExist()

        text = "window-child"
        awaitIdle()
        window.onNodeWithText("window-child").assertExists()
    }

    @Test
    fun windowContentAddsAndDropsAChildDeclaration() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var withExtra by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "window-content-structure-test", visible = false) {
                Column {
                    Label(text = "window-always")
                    if (withExtra) Label(text = "window-extra")
                }
            }
        }
        val window = onWindowWithTitle("window-content-structure-test")
        window.onNodeWithText("window-extra").assertDoesNotExist()

        withExtra = true
        awaitIdle()
        window.onNodeWithText("window-extra").assertExists()

        withExtra = false
        awaitIdle()
        window.onNodeWithText("window-extra").assertDoesNotExist()
        window.onNodeWithText("window-always").assertExists()
    }

    @Test
    fun dialogContentFollowsAChangedChildDeclaration() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var text by mutableStateOf("dialog-child")
        setContent {
            Dialog(onCloseRequest = {}, title = "dialog-content-react-test", visible = false) {
                Label(text = text)
            }
        }
        val dialog = onWindowWithTitle("dialog-content-react-test")
        dialog.onNodeWithText("dialog-child").assertExists()

        text = "dialog-child-updated"
        awaitIdle()
        dialog.onNodeWithText("dialog-child-updated").assertExists()
        dialog.onNodeWithText("dialog-child").assertDoesNotExist()

        text = "dialog-child"
        awaitIdle()
        dialog.onNodeWithText("dialog-child").assertExists()
    }

    @Test
    fun dialogContentAddsAndDropsAChildDeclaration() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var withExtra by mutableStateOf(false)
        setContent {
            Dialog(onCloseRequest = {}, title = "dialog-content-structure-test", visible = false) {
                Column {
                    Label(text = "dialog-always")
                    if (withExtra) Label(text = "dialog-extra")
                }
            }
        }
        val dialog = onWindowWithTitle("dialog-content-structure-test")
        dialog.onNodeWithText("dialog-extra").assertDoesNotExist()

        withExtra = true
        awaitIdle()
        dialog.onNodeWithText("dialog-extra").assertExists()

        withExtra = false
        awaitIdle()
        dialog.onNodeWithText("dialog-extra").assertDoesNotExist()
        dialog.onNodeWithText("dialog-always").assertExists()
    }
}
