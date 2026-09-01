package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests asserting how the geometry a window is declared with reaches its realized peer.
 *
 * A window system reports a drag or a resize once per frame of the gesture, and every report is
 * written back into the very state the window is declared with. The declared geometry therefore
 * reaches the peer by observing that state rather than by recomposing what declared the window: a
 * change reaches the peer with no frame of the composition's, and a report of the user's own gesture
 * costs the composition no recomposition at all.
 *
 * Every window here declares its size and is never shown, which keeps its peer unrealized: the window
 * system then performs no placement and no resize of its own, so every report the composition answers
 * is the one this test made.
 *
 * Skipped in headless environments, where a window cannot be constructed at all.
 */
class WindowGeometryObservationTest {
    @Test
    fun aDeclaredWindowSizeIsAppliedWithNoCompositionFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "geometry-observed-window-size", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }

        mainClock.autoAdvance = false
        state.size = REDECLARED_SIZE
        waitUntil { frame.size == REDECLARED_SIZE }

        assertEquals(
            REDECLARED_SIZE,
            frame.size,
            "a declared size must reach the window without a frame of the composition",
        )
    }

    @Test
    fun aDeclaredWindowPositionIsAppliedWithNoCompositionFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(140, 90), size = DECLARED_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "geometry-observed-window-position", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }

        mainClock.autoAdvance = false
        state.position = WindowPosition.Absolute(REDECLARED_PLACEMENT.x, REDECLARED_PLACEMENT.y)
        waitUntil { frame.location == REDECLARED_PLACEMENT }

        assertEquals(
            REDECLARED_PLACEMENT,
            frame.location,
            "a declared position must reach the window without a frame of the composition",
        )
    }

    @Test
    fun aDeclaredWindowPositionIsAppliedInsideTheNotificationOfItsWrite() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(140, 90), size = DECLARED_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "geometry-observed-window-inline", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }

        // The test body runs on the event dispatch thread, so the write, the notification and the
        // assertion below share one turn of the event queue: nothing suspends between them.
        state.position = WindowPosition.Absolute(REDECLARED_PLACEMENT.x, REDECLARED_PLACEMENT.y)
        Snapshot.sendApplyNotifications()

        assertEquals(
            REDECLARED_PLACEMENT,
            frame.location,
            "a declared position must reach the window inside the notification of its write",
        )
    }

    @Test
    fun aDeclaredDialogSizeIsAppliedWithNoCompositionFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = DECLARED_SIZE)
        lateinit var dialog: JDialog
        setContent {
            Dialog(onCloseRequest = {}, state = state, title = "geometry-observed-dialog-size", visible = false) {
                dialog = LocalWindow.current as JDialog
                FlowPanel()
            }
        }

        mainClock.autoAdvance = false
        state.size = REDECLARED_SIZE
        waitUntil { dialog.size == REDECLARED_SIZE }

        assertEquals(
            REDECLARED_SIZE,
            dialog.size,
            "a declared size must reach the dialog without a frame of the composition",
        )
    }

    @Test
    fun aWindowResizeTheWindowSystemReportsRecomposesNothing() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "geometry-observed-window-resize", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }
        awaitIdle()

        val applied = appliedCompositionChanges()
        frame.resizeTo(REDECLARED_SIZE)
        assertEquals(REDECLARED_SIZE, state.size, "the reported resize must reach the state, or nothing is measured")
        awaitIdle()

        assertEquals(
            applied,
            appliedCompositionChanges(),
            "a resize the window system reports must not recompose what declared the window",
        )
    }

    @Test
    fun aWindowMoveTheWindowSystemReportsRecomposesNothing() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(140, 90), size = DECLARED_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "geometry-observed-window-move", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }
        // The window system reports the declared placement performed, and then has the window on
        // screen, where a move of the user's is possible at all.
        frame.notifyPlacement()
        frame.notifyOnScreen()
        awaitIdle()

        val applied = appliedCompositionChanges()
        frame.placeAt(REDECLARED_PLACEMENT)
        assertEquals(
            WindowPosition.Absolute(REDECLARED_PLACEMENT.x, REDECLARED_PLACEMENT.y),
            state.position,
            "the reported move must reach the state, or nothing is measured",
        )
        awaitIdle()

        assertEquals(
            applied,
            appliedCompositionChanges(),
            "a move the window system reports must not recompose what declared the window",
        )
    }

    @Test
    fun aDialogResizeTheWindowSystemReportsRecomposesNothing() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = DECLARED_SIZE)
        lateinit var dialog: JDialog
        setContent {
            Dialog(onCloseRequest = {}, state = state, title = "geometry-observed-dialog-resize", visible = false) {
                dialog = LocalWindow.current as JDialog
                FlowPanel()
            }
        }
        awaitIdle()

        val applied = appliedCompositionChanges()
        dialog.resizeTo(REDECLARED_SIZE)
        assertEquals(REDECLARED_SIZE, state.size, "the reported resize must reach the state, or nothing is measured")
        awaitIdle()

        assertEquals(
            applied,
            appliedCompositionChanges(),
            "a resize the window system reports must not recompose what declared the dialog",
        )
    }

    @Test
    fun aReportedResizeStandsAndASizeDeclaredAfterItStillApplies() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "geometry-observed-window-settles", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }

        // The report reaches the state, and the apply it sets off runs after it: settling here is what
        // gives that apply its turn, and it must find the window already standing as the state says
        // rather than put back the size that was declared before the report.
        frame.resizeTo(RESIZED_BY_THE_USER)
        awaitIdle()
        assertEquals(RESIZED_BY_THE_USER, frame.size, "the size the window system reported must stand")
        assertEquals(RESIZED_BY_THE_USER, state.size, "the size the window system reported must reach the state")

        state.size = REDECLARED_SIZE
        awaitIdle()
        assertEquals(REDECLARED_SIZE, frame.size, "a size declared after the user's resize must still reach the window")
        assertEquals(REDECLARED_SIZE, state.size, "a size declared after the user's resize must stand in the state")
    }
}

/**
 * The number of times the compositions running in this process have applied changes, which is what a
 * recomposition that reaches anything costs. Read either side of a report of the window system's to
 * pin what that report costs the composition.
 */
private fun appliedCompositionChanges(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

private val DECLARED_SIZE = Dimension(321, 211)

/** A size the composition declares once the window already stands at another. */
private val REDECLARED_SIZE = Dimension(480, 360)

/** A size no composition asked for, standing in for one a user's drag leaves the window at. */
private val RESIZED_BY_THE_USER = Dimension(640, 480)

/** A placement the composition declares once the window already stands somewhere else. */
private val REDECLARED_PLACEMENT = Point(300, 200)
