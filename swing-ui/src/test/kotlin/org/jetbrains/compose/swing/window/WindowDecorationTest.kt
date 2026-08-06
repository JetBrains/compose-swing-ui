package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioural tests covering the platform decorations of a [Window] and a [Dialog]: a peer declared
 * undecorated realizes without its title bar and border, a peer that leaves the argument alone keeps
 * them, and a change of the declaration reaches the realized peer.
 *
 * AWT only accepts decorations on a window that is not yet realized, so honouring a change means
 * replacing the peer. The tests assert on the peer realized after the change: that the one it replaced
 * was released, that the content, the geometry and the extended state moved across, and that the
 * replacement is wired like a peer built in the first place - the closing gesture reaches
 * `onCloseRequest`, and a user-driven resize or maximize is written back into the hoisted state.
 * Skipped in headless environments where no real peer can be realized.
 */
class WindowDecorationTest {
    @Test
    fun undecoratedRemovesTheDecorationsADefaultWindowKeeps() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "decorated-window-test", visible = false) {}
            Window(onCloseRequest = {}, title = "undecorated-window-test", visible = false, undecorated = true) {}
        }
        assertFalse(
            onWindowWithTitle("decorated-window-test").fetch<JFrame>().isUndecorated,
            "a window that does not declare undecorated must realize with its platform decorations",
        )
        assertTrue(
            onWindowWithTitle("undecorated-window-test").fetch<JFrame>().isUndecorated,
            "a window declared undecorated must realize without its platform decorations",
        )
    }

    @Test
    fun undecoratedRemovesTheDecorationsADefaultDialogKeeps() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Dialog(onCloseRequest = {}, title = "decorated-dialog-test", visible = false) {}
            Dialog(onCloseRequest = {}, title = "undecorated-dialog-test", visible = false, undecorated = true) {}
        }
        assertFalse(
            onWindowWithTitle("decorated-dialog-test").fetch<JDialog>().isUndecorated,
            "a dialog that does not declare undecorated must realize with its platform decorations",
        )
        assertTrue(
            onWindowWithTitle("undecorated-dialog-test").fetch<JDialog>().isUndecorated,
            "a dialog declared undecorated must realize without its platform decorations",
        )
    }

    @Test
    fun undecoratedReactsToRecompositionOnAWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        setContent {
            Window(
                onCloseRequest = {},
                state = state,
                title = "undecorated-reactive-window-test",
                undecorated = undecorated,
            ) {
                Label(text = "window-content")
            }
        }
        val window = onWindowWithTitle("undecorated-reactive-window-test")
        val realized = window.fetch<JFrame>()
        assertTrue(realized.isUndecorated, "the window must realize undecorated while undecorated is declared true")

        undecorated = false
        awaitIdle()

        val decorated = window.fetch<JFrame>()
        assertFalse(
            decorated.isUndecorated,
            "the realized window must carry its platform decorations once undecorated recomposes to false",
        )
        assertFalse(realized.isDisplayable, "the window the change replaced must be released")
        assertEquals(
            Dimension(320, 240),
            decorated.size,
            "the size held in the state must be applied to the window that replaces the released one",
        )
        window.assertIsVisible()
        window.onNodeWithText("window-content").assertExists()

        undecorated = true
        awaitIdle()

        assertTrue(
            window.fetch<JFrame>().isUndecorated,
            "the realized window must drop its decorations again once undecorated recomposes back to true",
        )
        assertFalse(decorated.isDisplayable, "the window the change back replaced must be released")
    }

    @Test
    fun undecoratedReactsToRecompositionOnADialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        setContent {
            Dialog(
                onCloseRequest = {},
                state = state,
                title = "undecorated-reactive-dialog-test",
                undecorated = undecorated,
            ) {
                Label(text = "dialog-content")
            }
        }
        val dialog = onWindowWithTitle("undecorated-reactive-dialog-test")
        val realized = dialog.fetch<JDialog>()
        assertTrue(realized.isUndecorated, "the dialog must realize undecorated while undecorated is declared true")

        undecorated = false
        awaitIdle()

        val decorated = dialog.fetch<JDialog>()
        assertFalse(
            decorated.isUndecorated,
            "the realized dialog must carry its platform decorations once undecorated recomposes to false",
        )
        assertFalse(realized.isDisplayable, "the dialog the change replaced must be released")
        assertEquals(
            Dimension(320, 240),
            decorated.size,
            "the size held in the state must be applied to the dialog that replaces the released one",
        )
        dialog.assertIsVisible()
        dialog.onNodeWithText("dialog-content").assertExists()

        undecorated = true
        awaitIdle()

        assertTrue(
            dialog.fetch<JDialog>().isUndecorated,
            "the realized dialog must drop its decorations again once undecorated recomposes back to true",
        )
        assertFalse(decorated.isDisplayable, "the dialog the change back replaced must be released")
    }

    @Test
    fun aReplacementWindowIsPlacedWhereTheStateSaysItIs() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(140, 90), size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        setContent {
            Window(
                onCloseRequest = {},
                state = state,
                title = "position-replacement-test",
                undecorated = undecorated,
            ) {}
        }
        val window = onWindowWithTitle("position-replacement-test")
        val realized = window.fetch<JFrame>()
        assertEquals(Point(140, 90), realized.location, "the window must realize at the position the state holds")

        undecorated = false
        awaitIdle()

        val replacement = window.fetch<JFrame>()
        assertNotSame(realized, replacement, "a decoration change must realize a replacement window")
        assertEquals(
            Point(140, 90),
            replacement.location,
            "the position held in the state must be applied to the window that replaces the released one",
        )
    }

    @Test
    fun aReplacementDialogIsPlacedWhereTheStateSaysItIs() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(position = WindowPosition.Absolute(160, 110), size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        setContent {
            Dialog(
                onCloseRequest = {},
                state = state,
                title = "dialog-position-replacement-test",
                undecorated = undecorated,
            ) {}
        }
        val dialog = onWindowWithTitle("dialog-position-replacement-test")
        val realized = dialog.fetch<JDialog>()
        assertEquals(Point(160, 110), realized.location, "the dialog must realize at the position the state holds")

        undecorated = false
        awaitIdle()

        val replacement = dialog.fetch<JDialog>()
        assertNotSame(realized, replacement, "a decoration change must realize a replacement dialog")
        assertEquals(
            Point(160, 110),
            replacement.location,
            "the position held in the state must be applied to the dialog that replaces the released one",
        )
    }

    @Test
    fun aReplacementWindowIsMaximizedWhenTheStateSaysItIs() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(
            Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH),
            "Maximizing a frame requires toolkit support for MAXIMIZED_BOTH",
        )
        val state = WindowState(size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        setContent {
            Window(
                onCloseRequest = {},
                state = state,
                title = "extended-state-replacement-test",
                undecorated = undecorated,
            ) {}
        }
        val window = onWindowWithTitle("extended-state-replacement-test")
        val realized = window.fetch<JFrame>()
        state.extendedState = Frame.MAXIMIZED_BOTH
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { realized.extendedState == Frame.MAXIMIZED_BOTH }

        undecorated = false
        awaitIdle()

        val replacement = window.fetch<JFrame>()
        assertNotSame(realized, replacement, "a decoration change must realize a replacement window")
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { replacement.extendedState == Frame.MAXIMIZED_BOTH }
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            replacement.extendedState,
            "the extended state held in the state must be applied to the window that replaces the released one",
        )
    }

    @Test
    fun theClosingGestureReachesAReplacementWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        var closeRequests = 0
        setContent {
            Window(
                onCloseRequest = { closeRequests++ },
                state = state,
                title = "close-request-replacement-test",
                undecorated = undecorated,
            ) {}
        }
        val window = onWindowWithTitle("close-request-replacement-test")
        val realized = window.fetch<JFrame>()

        undecorated = false
        awaitIdle()

        val replacement = window.fetch<JFrame>()
        assertNotSame(realized, replacement, "a decoration change must realize a replacement window")
        assertFalse(realized.isDisplayable, "the window the change replaced must be released")
        replacement.dispatchEvent(WindowEvent(replacement, WindowEvent.WINDOW_CLOSING))
        assertEquals(
            1,
            closeRequests,
            "the closing gesture on the replacement window must reach onCloseRequest",
        )
    }

    @Test
    fun aUserResizeOfAReplacementWindowIsWrittenBackIntoState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        setContent {
            Window(
                onCloseRequest = {},
                state = state,
                title = "resize-write-back-replacement-test",
                undecorated = undecorated,
            ) {}
        }
        val window = onWindowWithTitle("resize-write-back-replacement-test")
        val realized = window.fetch<JFrame>()

        undecorated = false
        awaitIdle()

        val replacement = window.fetch<JFrame>()
        assertNotSame(realized, replacement, "a decoration change must realize a replacement window")
        replacement.size = Dimension(640, 480)
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.size == Dimension(640, 480) }
        assertEquals(
            Dimension(640, 480),
            state.size,
            "a resize of the replacement window must be written back into the state",
        )
    }

    @Test
    fun aUserMaximizeOfAReplacementWindowIsWrittenBackIntoState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(
            Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH),
            "Maximizing a frame requires toolkit support for MAXIMIZED_BOTH",
        )
        val state = WindowState(size = Dimension(320, 240))
        var undecorated by mutableStateOf(true)
        setContent {
            Window(
                onCloseRequest = {},
                state = state,
                title = "maximize-write-back-replacement-test",
                undecorated = undecorated,
            ) {}
        }
        val window = onWindowWithTitle("maximize-write-back-replacement-test")
        val realized = window.fetch<JFrame>()

        undecorated = false
        awaitIdle()

        val replacement = window.fetch<JFrame>()
        assertNotSame(realized, replacement, "a decoration change must realize a replacement window")
        replacement.extendedState = Frame.MAXIMIZED_BOTH
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.extendedState == Frame.MAXIMIZED_BOTH }
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            state.extendedState,
            "a maximize of the replacement window must be written back into the state",
        )
    }
}

/**
 * Wall-clock deadline for conditions gated on native window-system notifications (moves, resizes,
 * maximize transitions), which arrive with real latency - including window-manager animations.
 */
private val NATIVE_EVENT_TIMEOUT = 10.seconds
