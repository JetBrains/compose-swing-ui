package org.jetbrains.compose.swing.window

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
 * Behavioral tests asserting which placements of a window reach the driving state.
 *
 * Nobody can drag a window that is not on screen, so a move it takes there is the window system
 * placing it: it resolves a position that names no coordinates, and it leaves declared coordinates
 * standing. A window on screen can be dragged, and there a report of the coordinates a declared
 * reposition superseded is the window system still performing the placement it was busy with rather
 * than a move of the user's, so it leaves the declared coordinates standing too.
 *
 * Every window here declares its size and is never shown, which is what keeps its peer unrealized: a
 * window left to size itself to its content is packed, and packing realizes the peer. Every placement
 * and every notification of one is then the test's own. A realized peer hands its placements to the
 * window system, which performs them and reports back on a schedule of its own, so a report the
 * subject here is meant to answer would be racing reports nobody asked for. A test whose subject
 * needs the window on screen reports it there itself.
 *
 * Skipped in headless environments, where a window cannot be constructed at all.
 */
class WindowPlacementWriteBackTest {
    @Test
    fun aMoveOffScreenLeavesADeclaredWindowPositionStanding() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(140, 90), size = WINDOW_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "placement-declared", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }

        frame.placeAt(IMPOSED_PLACEMENT)
        awaitIdle()

        assertEquals(
            WindowPosition.Absolute(140, 90),
            state.position,
            "a placement the window takes off screen must leave the declared position standing",
        )
    }

    @Test
    fun aMoveOffScreenResolvesAWindowPositionThatNamesNoCoordinates() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = WINDOW_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "placement-platform-default", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }

        frame.placeAt(IMPOSED_PLACEMENT)
        awaitIdle()

        assertEquals(
            WindowPosition.Absolute(IMPOSED_PLACEMENT.x, IMPOSED_PLACEMENT.y),
            state.position,
            "a placement the window takes off screen must resolve a position that names no coordinates",
        )
    }

    @Test
    fun aRepositionAfterAResolvedPositionStandsAgainstAReportOfTheCoordinatesItSuperseded() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = WINDOW_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "placement-after-resolution", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }
        // The window system settles the position that names no coordinates on its own placement, and
        // then has the window on screen, where a move of the user's is possible at all.
        frame.placeAt(IMPOSED_PLACEMENT)
        frame.notifyOnScreen()
        awaitIdle()
        assertEquals(
            WindowPosition.Absolute(IMPOSED_PLACEMENT.x, IMPOSED_PLACEMENT.y),
            state.position,
            "the placement must resolve the position, or the reposition supersedes nothing",
        )

        // The window system is still busy with the placement that put the window on screen, so the
        // reposition asked of it now goes unperformed and the window stands where it was.
        state.position = WindowPosition.Absolute(REPOSITIONED_PLACEMENT.x, REPOSITIONED_PLACEMENT.y)
        settleAPlacementTheWindowSystemHasNotPerformed(frame)
        frame.notifyPlacement()
        awaitIdle()

        assertEquals(
            WindowPosition.Absolute(REPOSITIONED_PLACEMENT.x, REPOSITIONED_PLACEMENT.y),
            state.position,
            "a report of the coordinates the reposition superseded must not reach the state as a move",
        )
        assertEquals(
            REPOSITIONED_PLACEMENT,
            frame.location,
            "the reposition must be asked of the window again over the coordinates it superseded",
        )
    }

    @Test
    fun aRepositionAfterAUserMoveStandsAgainstAReportOfTheCoordinatesItSuperseded() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(140, 90), size = WINDOW_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "placement-after-user-move", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }
        // The window system reports the declared placement performed, and then has the window on
        // screen, where the user takes it somewhere of their own.
        frame.notifyPlacement()
        frame.notifyOnScreen()
        awaitIdle()
        frame.placeAt(IMPOSED_PLACEMENT)
        awaitIdle()
        assertEquals(
            WindowPosition.Absolute(IMPOSED_PLACEMENT.x, IMPOSED_PLACEMENT.y),
            state.position,
            "a move of the user's must reach the state, or the reposition supersedes nothing",
        )

        // The window system is still busy with the user's move, so the reposition asked of it now goes
        // unperformed and the window stands where the user left it.
        state.position = WindowPosition.Absolute(REPOSITIONED_PLACEMENT.x, REPOSITIONED_PLACEMENT.y)
        settleAPlacementTheWindowSystemHasNotPerformed(frame)
        frame.notifyPlacement()
        awaitIdle()

        assertEquals(
            WindowPosition.Absolute(REPOSITIONED_PLACEMENT.x, REPOSITIONED_PLACEMENT.y),
            state.position,
            "a report of the coordinates the reposition superseded must not reach the state as a move",
        )
        assertEquals(
            REPOSITIONED_PLACEMENT,
            frame.location,
            "the reposition must be asked of the window again over the coordinates it superseded",
        )
    }

    @Test
    fun aRepositionAfterAUserMoveOverAnUnperformedPlacementSupersedesThatPlacement() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(140, 90), size = WINDOW_SIZE)
        lateinit var frame: JFrame
        setContent {
            Window(onCloseRequest = {}, state = state, title = "placement-superseded-by-user", visible = false) {
                frame = LocalWindow.current as JFrame
                FlowPanel()
            }
        }
        // The window system reports the declared placement performed, and then has the window on
        // screen, where a move of the user's is possible at all.
        frame.notifyPlacement()
        frame.notifyOnScreen()
        awaitIdle()

        // A reposition the window system never performs, so it stays outstanding and the window goes
        // on standing where it was - and the user takes it somewhere of their own instead.
        state.position = WindowPosition.Absolute(UNPERFORMED_PLACEMENT.x, UNPERFORMED_PLACEMENT.y)
        settleAPlacementTheWindowSystemHasNotPerformed(frame)
        frame.placeAt(IMPOSED_PLACEMENT)
        awaitIdle()
        assertEquals(
            WindowPosition.Absolute(IMPOSED_PLACEMENT.x, IMPOSED_PLACEMENT.y),
            state.position,
            "a move of the user's must reach the state, or the next reposition supersedes nothing",
        )

        // The window system is still busy with the user's move, so the reposition asked of it now goes
        // unperformed too and the window stands where the user left it. What that report supersedes is
        // where the user left the window, not the placement their move already overtook.
        state.position = WindowPosition.Absolute(REPOSITIONED_PLACEMENT.x, REPOSITIONED_PLACEMENT.y)
        settleAPlacementTheWindowSystemHasNotPerformed(frame)
        frame.notifyPlacement()
        awaitIdle()

        assertEquals(
            WindowPosition.Absolute(REPOSITIONED_PLACEMENT.x, REPOSITIONED_PLACEMENT.y),
            state.position,
            "a report of the coordinates the reposition superseded must not reach the state as a move",
        )
        assertEquals(
            REPOSITIONED_PLACEMENT,
            frame.location,
            "the reposition must be asked of the window again over the coordinates it superseded",
        )
    }

    @Test
    fun aMoveOffScreenLeavesADeclaredDialogPositionStanding() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(position = WindowPosition.Absolute(140, 90), size = WINDOW_SIZE)
        lateinit var dialog: JDialog
        setContent {
            Dialog(onCloseRequest = {}, state = state, title = "placement-declared-dialog", visible = false) {
                dialog = LocalWindow.current as JDialog
                FlowPanel()
            }
        }

        dialog.placeAt(IMPOSED_PLACEMENT)
        awaitIdle()

        assertEquals(
            WindowPosition.Absolute(140, 90),
            state.position,
            "a placement the dialog takes off screen must leave the declared position standing",
        )
    }
}

/** The size every window here is declared with. */
private val WINDOW_SIZE = Dimension(321, 211)

/** A placement no composition asked for, standing in for one the window system imposes. */
private val IMPOSED_PLACEMENT = Point(300, 200)

/** A placement the composition asks for once the window already stands somewhere else. */
private val REPOSITIONED_PLACEMENT = Point(420, 260)

/** A placement the composition asks for that the window system never performs. */
private val UNPERFORMED_PLACEMENT = Point(260, 150)
