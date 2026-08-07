package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests asserting that the closing gesture on a realized [Window] or [Dialog] peer runs the
 * `onCloseRequest` the latest recomposition declared, exactly once per gesture.
 *
 * The peers are composed `visible = false`: sizing to content realizes a peer, which is all a closing
 * gesture needs. Skipped in headless environments where no real peer can be realized.
 */
class WindowCloseRequestTest {
    @Test
    fun theWindowClosingGestureRunsTheCallbackTheLatestRecompositionDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var declared by mutableIntStateOf(1)
        var handled = 0
        setContent {
            val generation = declared
            Window(
                onCloseRequest = { handled = generation },
                title = "window-close-request-generation-test",
                visible = false,
            ) {}
        }
        val frame = onWindow().fetch<JFrame>()

        declared = 2
        awaitIdle()
        frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
        assertEquals(
            2,
            handled,
            "the closing gesture must run the onCloseRequest of the latest recomposition, not a captured earlier one",
        )
    }

    @Test
    fun aWindowClosingGestureRunsTheCallbackOnce() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("window-close-request-once-test")
        var closeRequests = 0
        setContent {
            Window(onCloseRequest = { closeRequests++ }, title = title, visible = false) {}
        }
        val frame = onWindow().fetch<JFrame>()

        title = "window-close-request-once-test-updated"
        awaitIdle()
        title = "window-close-request-once-test-final"
        awaitIdle()

        frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
        assertEquals(
            1,
            closeRequests,
            "recompositions must not stack closing listeners: one gesture must reach onCloseRequest once",
        )
    }

    @Test
    fun theDialogClosingGestureRunsTheCallbackTheLatestRecompositionDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var declared by mutableIntStateOf(1)
        var handled = 0
        setContent {
            val generation = declared
            Dialog(
                onCloseRequest = { handled = generation },
                title = "dialog-close-request-generation-test",
                visible = false,
            ) {}
        }
        val dialog = onWindow().fetch<JDialog>()

        declared = 2
        awaitIdle()
        dialog.dispatchEvent(WindowEvent(dialog, WindowEvent.WINDOW_CLOSING))
        assertEquals(
            2,
            handled,
            "the closing gesture must run the onCloseRequest of the latest recomposition, not a captured earlier one",
        )
    }

    @Test
    fun aDialogClosingGestureRunsTheCallbackOnce() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("dialog-close-request-once-test")
        var closeRequests = 0
        setContent {
            Dialog(onCloseRequest = { closeRequests++ }, title = title, visible = false) {}
        }
        val dialog = onWindow().fetch<JDialog>()

        title = "dialog-close-request-once-test-updated"
        awaitIdle()
        title = "dialog-close-request-once-test-final"
        awaitIdle()

        dialog.dispatchEvent(WindowEvent(dialog, WindowEvent.WINDOW_CLOSING))
        assertEquals(
            1,
            closeRequests,
            "recompositions must not stack closing listeners: one gesture must reach onCloseRequest once",
        )
    }
}
