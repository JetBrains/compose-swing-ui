package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests asserting that the `state` argument of a [Window] or [Dialog] is reactive in both
 * directions: a recomposition that hands over a different state holder drives the realized peer from
 * the new holder, and the peer's user-driven geometry and extended-state changes are written back into
 * that same holder rather than into the one it replaced.
 *
 * Skipped in headless environments where no real peer can be realized.
 */
class WindowStateHoistingTest {
    @Test
    fun aWindowFollowsTheGeometryOfTheStateTheLatestRecompositionDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = WindowState(size = Dimension(320, 240))
        val second = WindowState(size = Dimension(480, 360))
        var state by mutableStateOf(first)
        setContent { Window(onCloseRequest = {}, state = state, title = "window-state-swap-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertReaches(
            Dimension(320, 240),
            "the frame must realize with the geometry of the declared state",
        ) { frame.size }

        state = second
        assertReaches(
            Dimension(480, 360),
            "the frame must take the geometry of the state the recomposition declared",
        ) { frame.size }

        state = first
        assertReaches(
            Dimension(320, 240),
            "the frame must take the geometry of the state a further recomposition declares back",
        ) { frame.size }
    }

    @Test
    fun aWindowResizeIsWrittenBackIntoTheStateTheLatestRecompositionDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = WindowState(size = Dimension(320, 240))
        val second = WindowState(size = Dimension(480, 360))
        var state by mutableStateOf(first)
        setContent { Window(onCloseRequest = {}, state = state, title = "window-state-swap-write-back-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertReaches(Dimension(320, 240)) { frame.size }

        state = second
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.size == Dimension(480, 360) }

        frame.size = Dimension(640, 480)
        assertReaches(
            Dimension(640, 480),
            "a resize must be written back into the state the window currently reads",
        ) { second.size }
        assertEquals(
            Dimension(320, 240),
            first.size,
            "the state the recomposition replaced must stop receiving the window's geometry",
        )
    }

    @Test
    fun aWindowMaximizeIsWrittenBackIntoTheStateTheLatestRecompositionDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(
            Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH),
            "Maximizing a frame requires toolkit support for MAXIMIZED_BOTH",
        )
        val first = WindowState(size = Dimension(320, 240))
        val second = WindowState(size = Dimension(480, 360))
        var state by mutableStateOf(first)
        setContent { Window(onCloseRequest = {}, state = state, title = "window-state-swap-maximize-test") {} }
        val frame = onWindow().fetch<JFrame>()

        state = second
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.size == Dimension(480, 360) }

        frame.extendedState = Frame.MAXIMIZED_BOTH
        assertReaches(
            Frame.MAXIMIZED_BOTH,
            "a maximize must be written back into the state the window currently reads",
        ) { second.extendedState }
        assertEquals(
            Frame.NORMAL,
            first.extendedState,
            "the state the recomposition replaced must stop receiving the window's extended state",
        )
    }

    @Test
    fun aDialogFollowsTheGeometryOfTheStateTheLatestRecompositionDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = DialogState(size = Dimension(360, 260))
        val second = DialogState(size = Dimension(520, 420))
        var state by mutableStateOf(first)
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-state-swap-test") {} }
        val dialog = onWindow().fetch<JDialog>()
        assertReaches(
            Dimension(360, 260),
            "the dialog must realize with the geometry of the declared state",
        ) { dialog.size }

        state = second
        assertReaches(
            Dimension(520, 420),
            "the dialog must take the geometry of the state the recomposition declared",
        ) { dialog.size }

        state = first
        assertReaches(
            Dimension(360, 260),
            "the dialog must take the geometry of the state a further recomposition declares back",
        ) { dialog.size }
    }

    @Test
    fun aDialogResizeIsWrittenBackIntoTheStateTheLatestRecompositionDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = DialogState(size = Dimension(360, 260))
        val second = DialogState(size = Dimension(520, 420))
        var state by mutableStateOf(first)
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-state-swap-write-back-test") {} }
        val dialog = onWindow().fetch<JDialog>()
        assertReaches(Dimension(360, 260)) { dialog.size }

        state = second
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { dialog.size == Dimension(520, 420) }

        dialog.size = Dimension(640, 480)
        assertReaches(
            Dimension(640, 480),
            "a resize must be written back into the state the dialog currently reads",
        ) { second.size }
        assertEquals(
            Dimension(360, 260),
            first.size,
            "the state the recomposition replaced must stop receiving the dialog's geometry",
        )
    }
}
