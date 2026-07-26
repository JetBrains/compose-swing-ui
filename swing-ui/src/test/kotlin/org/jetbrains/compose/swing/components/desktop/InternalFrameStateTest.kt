package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.AWTEvent
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JInternalFrame.JDesktopIcon
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An internal frame's geometry is two-way when the frame is declared with an [InternalFrameState]:
 * assigning to the state moves and resizes the realized frame, and a user dragging it or pulling its
 * border writes the new geometry back into the state. A frame declared with plain bounds keeps its
 * declaration-only geometry.
 */
class InternalFrameStateTest {
    /** Resizes the frame to [bounds] the way the frame border's resize handler does. */
    private fun JDesktopPane.resizeFrameTo(
        frame: JInternalFrame,
        bounds: Rectangle,
    ) {
        desktopManager.beginResizingFrame(frame, SwingConstants.SOUTH_EAST)
        desktopManager.resizeFrame(frame, bounds.x, bounds.y, bounds.width, bounds.height)
        desktopManager.endResizingFrame(frame)
    }

    @Test
    fun aFrameOpensOnTheBoundsItsStateHolds() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 300, 200))
        setContent {
            DesktopPane {
                internalFrame(title = "Editor", state = state) { Label(text = "body") }
            }
        }

        assertEquals(
            Rectangle(10, 20, 300, 200),
            onNodeOfType<JInternalFrame>().fetch().bounds,
            "the frame should open on the declared bounds",
        )
    }

    @Test
    fun assigningBoundsMovesAndResizesTheFrame() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 100, 100))
        setContent {
            DesktopPane {
                internalFrame(title = "Editor", state = state) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        state.bounds = Rectangle(40, 50, 250, 150)
        awaitIdle()
        assertEquals(Rectangle(40, 50, 250, 150), frame.bounds, "assigning bounds should move and resize the frame")

        state.x = 5
        state.height = 90
        awaitIdle()
        assertEquals(Rectangle(5, 50, 250, 90), frame.bounds, "each coordinate should drive the frame on its own")
    }

    @Test
    fun draggingTheFrameWritesItsPositionBack() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 100, 100))
        setContent {
            DesktopPane {
                internalFrame(title = "Editor", state = state) { Label(text = "body") }
            }
        }

        val desktop = onNodeOfType<JDesktopPane>().fetch()
        desktop.dragFrameTo(onNodeOfType<JInternalFrame>().fetch(), 60, 70)
        awaitIdle()
        assertEquals(Rectangle(60, 70, 100, 100), state.bounds, "the drag should reach the state driving the frame")
    }

    @Test
    fun resizingTheFrameWritesItsSizeBack() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 100, 100))
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(resizable = true),
                ) { Label(text = "body") }
            }
        }

        val desktop = onNodeOfType<JDesktopPane>().fetch()
        desktop.resizeFrameTo(onNodeOfType<JInternalFrame>().fetch(), Rectangle(0, 0, 260, 180))
        awaitIdle()
        assertEquals(260, state.width, "the resize should reach the state driving the frame")
        assertEquals(180, state.height, "the resize should reach the state driving the frame")
    }

    @Test
    fun aDragBackToWhereTheFrameWasPlacedReachesTheState() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 100, 100))
        setContent {
            DesktopPane {
                internalFrame(title = "Editor", state = state) { Label(text = "body") }
            }
        }

        // A drag reports each position it passes through, and it can end on the very geometry the
        // composition placed the frame on. The state has to record that ending position like any other:
        // the intermediate one it recorded on the way is not where the user left the frame, and the next
        // recomposition would take the frame back to it.
        val desktop = onNodeOfType<JDesktopPane>().fetch()
        val frame = onNodeOfType<JInternalFrame>().fetch()
        desktop.dragFrameTo(frame, 60, 70)
        // Let the frame report the position it passed through while the drag is still going: no frame is
        // produced, so the composition does not observe the position in between.
        yield()
        desktop.dragFrameTo(frame, 0, 0)
        awaitIdle()
        assertEquals(Rectangle(0, 0, 100, 100), state.bounds, "the position the drag ended on should reach the state")
        assertEquals(Rectangle(0, 0, 100, 100), frame.bounds, "the frame should stay where the drag left it")
    }

    @Test
    fun aDraggedFrameStaysWhereTheUserLeftIt() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 400, 300))
        var tip by mutableStateOf("Edits the document")
        setContent {
            DesktopPane {
                internalFrame(title = "Editor", state = state, modifier = SwingModifier.toolTip(tip)) {
                    Label(text = "body")
                }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(frame, 60, 70)
        awaitIdle()

        // A recomposition re-applies the geometry the state holds, which is now the one the drag put
        // there: the frame does not snap back to where the composition first placed it.
        tip = "Holds the open document"
        awaitIdle()
        assertEquals("Holds the open document", frame.toolTipText, "the recomposition should have reached the frame")
        assertEquals(Rectangle(60, 70, 400, 300), frame.bounds, "the frame should stay where the drag left it")
    }

    // A frame reports its moves and resizes asynchronously, so between the pass that places it and the
    // notification that pass provokes, the frame's geometry is older than the state. The two tests below
    // declare a move from a side effect, which runs in exactly that stretch: the declaration is newer
    // than everything the frame has left to report, so it has to be what the frame ends up on.

    @Test
    fun aMoveDeclaredWhileTheFrameIsBeingPlacedStands() = runComposeSwingTest {
        // A component reports a move or a resize only while something is listening for component events,
        // and a frame is placed before it carries any listener of its own - so its very first placement
        // reaches a listener only in an application that observes component events across the toolkit,
        // as this one does.
        val observer = AWTEventListener { }
        Toolkit.getDefaultToolkit().addAWTEventListener(observer, AWTEvent.COMPONENT_EVENT_MASK)
        try {
            val state = InternalFrameState(Rectangle(10, 20, 100, 100))
            val declared = BooleanArray(1)
            setContent {
                SideEffect {
                    if (!declared[0]) {
                        declared[0] = true
                        state.bounds = Rectangle(80, 90, 100, 100)
                    }
                }
                DesktopPane {
                    internalFrame(title = "Editor", state = state) { Label(text = "body") }
                }
            }

            assertEquals(Rectangle(80, 90, 100, 100), state.bounds, "the move should stand over the placement")
            assertEquals(
                Rectangle(80, 90, 100, 100),
                onNodeOfType<JInternalFrame>().fetch().bounds,
                "the frame should end up where the move puts it",
            )
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(observer)
        }
    }

    @Test
    fun aMoveDeclaredBeforeTheFrameHasReportedTheLastOneStands() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 100, 100))
        var declareSecondMove by mutableStateOf(false)
        setContent {
            val second = declareSecondMove
            SideEffect { if (second) state.bounds = Rectangle(80, 90, 100, 100) }
            DesktopPane {
                internalFrame(title = "Editor", state = state) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        state.bounds = Rectangle(40, 50, 100, 100)
        declareSecondMove = true
        awaitIdle()
        assertEquals(Rectangle(80, 90, 100, 100), state.bounds, "the newer declaration should stand")
        assertEquals(Rectangle(80, 90, 100, 100), frame.bounds, "the frame should end up where the newer move puts it")
    }

    @Test
    fun aFrameDeclaredWithBoundsKeepsThemAndIsNotSnappedBack() = runComposeSwingTest {
        var tip by mutableStateOf("Edits the document")
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    bounds = Rectangle(0, 0, 400, 300),
                    modifier = SwingModifier.toolTip(tip),
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals(Rectangle(0, 0, 400, 300), frame.bounds, "the frame should open on the declared bounds")

        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(frame, 60, 70)
        awaitIdle()
        tip = "Holds the open document"
        awaitIdle()
        assertEquals("Holds the open document", frame.toolTipText, "the recomposition should have reached the frame")
        assertEquals(
            Rectangle(60, 70, 400, 300),
            frame.bounds,
            "a bounds-declared frame is placed by its declaration, not re-placed on every recomposition",
        )
    }

    @Test
    fun aFrameKeepsTheWindowStateItReachedAndTheGeometryItsStateDeclares() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 400, 300))
        var tip by mutableStateOf("Edits the document")
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(iconifiable = true),
                    modifier = SwingModifier.toolTip(tip),
                ) { Label(text = "body") }
            }
        }

        // Iconifying takes the frame off the desktop and puts its icon there instead; the frame keeps the
        // geometry it is to reappear on, and a recomposition leaves the frame as the user left it. The
        // icon standing in its place is how the frame itself is reached while it is iconified.
        onNodeOfType<JInternalFrame>().fetch().isIcon = true
        awaitIdle()
        val icon = onNodeOfType<JDesktopIcon>().fetch()
        tip = "Holds the open document"
        awaitIdle()
        assertEquals(
            "Holds the open document",
            icon.internalFrame.toolTipText,
            "the recomposition should have reached the frame",
        )
        assertTrue(icon.internalFrame.isIcon, "an iconified frame is not restored by a recomposition")
        assertEquals(Rectangle(10, 20, 400, 300), state.bounds, "the declared geometry should stand throughout")
    }

    @Test
    fun droppingAnIconifiedFrameTakesItsDesktopIconWithIt() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 100, 100))
        var show by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (show) {
                    internalFrame(
                        title = "Editor",
                        state = state,
                        controls = InternalFrameControls(iconifiable = true),
                    ) { Label(text = "body") }
                }
            }
        }

        onNodeOfType<JInternalFrame>().fetch().isIcon = true
        awaitIdle()
        onNodeOfType<JDesktopIcon>().assertExists()

        show = false
        awaitIdle()
        // The icon standing in for the frame goes with it, so nothing of the frame is left on the desktop.
        onNodeOfType<JDesktopIcon>().assertDoesNotExist()
        onNodeOfType<JInternalFrame>().assertDoesNotExist()
    }

    @Test
    fun theStateFollowsTheFrameItIsHandedOnRecomposition() = runComposeSwingTest {
        val first = InternalFrameState(Rectangle(0, 0, 100, 100))
        val second = InternalFrameState(Rectangle(0, 0, 100, 100))
        var useSecond by mutableStateOf(false)
        setContent {
            DesktopPane {
                internalFrame(title = "Editor", state = if (useSecond) second else first) { Label(text = "body") }
            }
        }

        useSecond = true
        awaitIdle()

        val desktop = onNodeOfType<JDesktopPane>().fetch()
        desktop.dragFrameTo(onNodeOfType<JInternalFrame>().fetch(), 60, 70)
        awaitIdle()
        assertEquals(Rectangle(60, 70, 100, 100), second.bounds, "the newly declared state should receive the drag")
        assertEquals(
            Rectangle(0, 0, 100, 100),
            first.bounds,
            "the state that left the declaration should be left alone",
        )
    }
}
