package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests asserting that the arguments of [rememberWindowState] and [rememberDialogState] are
 * seeds rather than reactive declarations: they are read once, when the state is created, and a later
 * recomposition that supplies different ones neither re-creates the state nor moves, resizes or
 * maximizes the peer. The remembered state itself stays the one thing that drives the peer.
 *
 * Skipped in headless environments where no real peer can be realized.
 */
class WindowStateSeedTest {
    @Test
    fun aWindowStateKeepsTheGeometryItWasSeededWith() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var seededPosition by mutableStateOf<WindowPosition>(WindowPosition.Absolute(120, 80))
        var seededSize by mutableStateOf(Dimension(320, 240))
        lateinit var state: WindowState
        setContent {
            state = rememberWindowState(position = seededPosition, size = seededSize)
            Window(onCloseRequest = {}, state = state, title = "window-state-seed-test") {}
        }
        val frame = onWindow().fetch<JFrame>()
        assertReaches(Dimension(320, 240), "the frame must realize with the seeded size") { frame.size }
        assertReaches(Point(120, 80), "the frame must realize at the seeded position") { frame.location }

        // The placement the frame settled at, which the seed change must leave alone even where the
        // window manager adjusted it.
        val placement = frame.location
        val placementInState = state.position

        seededPosition = WindowPosition.Absolute(260, 180)
        seededSize = Dimension(500, 400)
        awaitIdle()

        assertEquals(Dimension(320, 240), state.size, "a later seed must not resize the remembered state")
        assertEquals(placementInState, state.position, "a later seed must not move the remembered state")
        assertEquals(Dimension(320, 240), frame.size, "a later seed must not resize the frame")
        assertEquals(placement, frame.location, "a later seed must not move the frame")

        state.size = Dimension(500, 400)
        assertReaches(
            Dimension(500, 400),
            "the remembered state, not the seed, is what resizes the frame",
        ) { frame.size }
    }

    @Test
    fun aWindowStateKeepsTheExtendedStateItWasSeededWith() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(
            Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH),
            "Maximizing a frame requires toolkit support for MAXIMIZED_BOTH",
        )
        var seededExtendedState by mutableIntStateOf(Frame.NORMAL)
        lateinit var state: WindowState
        setContent {
            state = rememberWindowState(size = Dimension(320, 240), extendedState = seededExtendedState)
            Window(onCloseRequest = {}, state = state, title = "window-extended-state-seed-test") {}
        }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Frame.NORMAL, frame.extendedState, "the frame must realize with the seeded extended state")

        seededExtendedState = Frame.MAXIMIZED_BOTH
        awaitIdle()
        assertEquals(
            Frame.NORMAL,
            state.extendedState,
            "a later seed must not maximize the remembered state",
        )
        assertEquals(Frame.NORMAL, frame.extendedState, "a later seed must not maximize the frame")

        state.extendedState = Frame.MAXIMIZED_BOTH
        awaitIdle()
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            frame.extendedState,
            "the remembered state, not the seed, is what maximizes the frame",
        )
    }

    @Test
    fun aDialogStateKeepsTheGeometryItWasSeededWith() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var seededPosition by mutableStateOf<WindowPosition>(WindowPosition.Absolute(140, 90))
        var seededSize by mutableStateOf(Dimension(360, 260))
        lateinit var state: DialogState
        setContent {
            state = rememberDialogState(position = seededPosition, size = seededSize)
            Dialog(onCloseRequest = {}, state = state, title = "dialog-state-seed-test") {}
        }
        val dialog = onWindow().fetch<JDialog>()
        assertReaches(Dimension(360, 260), "the dialog must realize with the seeded size") { dialog.size }
        assertReaches(Point(140, 90), "the dialog must realize at the seeded position") { dialog.location }

        // The placement the dialog settled at, which the seed change must leave alone even where the
        // window manager adjusted it.
        val placement = dialog.location
        val placementInState = state.position

        seededPosition = WindowPosition.Absolute(240, 170)
        seededSize = Dimension(520, 420)
        awaitIdle()

        assertEquals(Dimension(360, 260), state.size, "a later seed must not resize the remembered state")
        assertEquals(placementInState, state.position, "a later seed must not move the remembered state")
        assertEquals(Dimension(360, 260), dialog.size, "a later seed must not resize the dialog")
        assertEquals(placement, dialog.location, "a later seed must not move the dialog")

        state.size = Dimension(520, 420)
        assertReaches(
            Dimension(520, 420),
            "the remembered state, not the seed, is what resizes the dialog",
        ) { dialog.size }
    }
}
