package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Point
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests for [ScrollState], the two-way scroll position of a [ScrollPane]. The harness lays
 * the tree out synchronously off-screen, so the pane's viewport has real metrics and a real position:
 * each test drives either the state or the viewport and asserts what the other one then reports.
 */
class ScrollStateTest {
    private fun ComposeSwingTest.viewport(): JViewport = onNodeOfType<JScrollPane>().fetch().viewport

    /** The change listeners the library put on this viewport, as opposed to the pane's own. */
    private fun JViewport.libraryChangeListeners(): List<ChangeListener> =
        changeListeners.filter { it.javaClass.name.startsWith("org.jetbrains.compose.swing") }

    /**
     * Composes a pane smaller than its content, so there is room to scroll on both axes, and returns the
     * [ScrollState] it declares.
     */
    private fun ComposeSwingTest.paneWithRoomToScroll(
        x: Int = 0,
        y: Int = 0,
    ): ScrollState {
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState(x = x, y = y)
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                content { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        return declared ?: error("the scroll pane did not compose")
    }

    @Test
    fun theStateReportsTheViewportsMetrics() = runComposeSwingTest {
        val state = paneWithRoomToScroll()

        assertEquals(400, state.viewHeight, "the content's full height must be reported")
        assertEquals(300, state.viewWidth, "the content's full width must be reported")
        assertTrue(
            state.extentHeight in 1 until 400,
            "the visible height must be a real part of the content, but was ${state.extentHeight}",
        )
        assertTrue(
            state.extentWidth in 1 until 300,
            "the visible width must be a real part of the content, but was ${state.extentWidth}",
        )
        assertEquals(
            state.viewHeight - state.extentHeight,
            state.maxY,
            "the largest useful position is what the content leaves beyond the visible part",
        )
        assertEquals(state.viewWidth - state.extentWidth, state.maxX, "the same holds across the pane")
    }

    @Test
    fun assigningThePositionScrollsThePane() = runComposeSwingTest {
        val state = paneWithRoomToScroll()

        state.y = 40
        state.x = 25
        assertEquals(
            Point(25, 40),
            viewport().viewPosition,
            "the viewport must show the position the state was assigned",
        )
    }

    @Test
    fun scrollingThePaneWritesThePositionBack() = runComposeSwingTest {
        val state = paneWithRoomToScroll()

        // Moving the viewport is what the wheel, the scrollbars and the keyboard all end up doing.
        viewport().viewPosition = Point(10, 30)
        assertEquals(10 to 30, state.x to state.y, "the state must follow the pane the user scrolled")
    }

    @Test
    fun theEndOfTheContentIsReachableThroughTheLargestPosition() = runComposeSwingTest {
        val state = paneWithRoomToScroll()

        state.y = state.maxY
        val bottom = state.maxY
        awaitIdle()
        assertEquals(
            bottom,
            viewport().viewPosition.y,
            "assigning the largest position must land the viewport at the end of the content",
        )
        assertEquals(bottom, state.y, "and the pane must stay there across a layout pass")
    }

    @Test
    fun aPositionBeyondTheContentIsCorrectedByTheLayoutAndReportedBack() = runComposeSwingTest {
        val state = paneWithRoomToScroll()

        // The pane takes an out-of-range position verbatim, as the widget does, and its next layout pass
        // corrects it - which is what the state then reports.
        state.y = 10_000
        awaitIdle()
        assertEquals(state.maxY, state.y, "the corrected position must be reported back into the state")
        assertEquals(
            state.maxY,
            viewport().viewPosition.y,
            "the pane must end up at the furthest position its content allows",
        )
    }

    @Test
    fun aPositionDeclaredBeforeThePaneExistsIsAppliedWhenItBinds() = runComposeSwingTest {
        val state = paneWithRoomToScroll(y = 70)

        assertEquals(
            70,
            viewport().viewPosition.y,
            "the position the state starts at must reach the pane that binds to it",
        )
        assertEquals(70, state.y, "and stay the position the state reports")
    }

    @Test
    fun aPaneLeavingTheCompositionStopsDrivingTheState() = runComposeSwingTest {
        var declared: ScrollState? = null
        var visible by mutableStateOf(true)
        setContent {
            val state = rememberScrollState()
            declared = state
            if (visible) {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                    content { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        val orphaned = viewport()
        assertEquals(
            1,
            orphaned.libraryChangeListeners().size,
            "the state observes the pane it drives through exactly one listener",
        )

        assertTrue(
            state.extentWidth > 0 && state.extentHeight > 0 && state.viewWidth > 0 && state.viewHeight > 0,
            "the metrics of the pane the state drives are there to lose",
        )

        visible = false
        awaitIdle()
        orphaned.viewPosition = Point(0, 35)
        assertEquals(0, state.y, "a pane out of the composition must no longer write into the state")
        assertEquals(
            listOf(0, 0, 0, 0),
            listOf(state.extentWidth, state.extentHeight, state.viewWidth, state.viewHeight),
            "nor must the metrics of a pane no longer rendered be reported",
        )
        assertEquals(
            emptyList(),
            orphaned.libraryChangeListeners(),
            "and the state must leave nothing of its own behind on it",
        )
    }

    @Test
    fun newContentIsScrolledToWhereTheStateStands() = runComposeSwingTest {
        var declared: ScrollState? = null
        var second by mutableStateOf(false)
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                content {
                    Label(if (second) "second" else "first", modifier = SwingModifier.preferredSize(300, 400))
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        state.y = 55
        awaitIdle()

        second = true
        awaitIdle()

        assertEquals(55, state.y, "replacing the content must not move the position the state holds")
        assertEquals(
            55,
            viewport().viewPosition.y,
            "the content that replaced it must be shown from that same position",
        )
    }

    @Test
    fun contentThatComesBackIsShownFromWhereTheStateStands() = runComposeSwingTest {
        var declared: ScrollState? = null
        var hasContent by mutableStateOf(true)
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                if (hasContent) {
                    content { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        state.y = 55
        awaitIdle()

        hasContent = false
        awaitIdle()
        assertEquals(55, state.y, "an emptied pane has no position of its own to report")

        hasContent = true
        awaitIdle()
        assertEquals(
            55,
            viewport().viewPosition.y,
            "the content that came back must be shown from the position the state kept",
        )
    }

    @Test
    fun twoPanesSwappingTheirStatesEachEndUpDrivenByTheOneTheyNowDeclare() = runComposeSwingTest {
        var declaredOnFirst: ScrollState? = null
        var declaredOnSecond: ScrollState? = null
        var swapped by mutableStateOf(false)
        setContent {
            val fromFirst = rememberScrollState()
            val fromSecond = rememberScrollState()
            declaredOnFirst = fromFirst
            declaredOnSecond = fromSecond
            Column {
                ScrollPane(
                    modifier = SwingModifier.preferredSize(100, 50),
                    state = if (swapped) fromSecond else fromFirst,
                ) {
                    content { Label("first", modifier = SwingModifier.preferredSize(300, 400)) }
                }
                ScrollPane(
                    modifier = SwingModifier.preferredSize(100, 50),
                    state = if (swapped) fromFirst else fromSecond,
                ) {
                    content { Label("second", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
        }
        val fromFirst = declaredOnFirst ?: error("the scroll panes did not compose")
        val fromSecond = declaredOnSecond ?: error("the scroll panes did not compose")
        fromFirst.y = 60
        fromSecond.y = 40
        awaitIdle()

        swapped = true
        awaitIdle()

        val (first, second) = onAllNodesOfType<JScrollPane>().fetchAll()
        assertEquals(
            60 to 40,
            second.viewport.viewPosition.y to first.viewport.viewPosition.y,
            "each pane must be scrolled to the position of the state that moved onto it",
        )
        assertEquals(60 to 40, fromFirst.y to fromSecond.y, "and both states must keep reporting theirs")
        assertEquals(
            400,
            fromSecond.viewHeight,
            "a state that moved to another pane reports that pane's metrics",
        )

        // Both bindings must be live in both directions, not just the one established last.
        fromSecond.y = 12
        assertEquals(
            12,
            first.viewport.viewPosition.y,
            "the state a pane now declares must drive that pane",
        )
        first.viewport.viewPosition = Point(0, 11)
        assertEquals(11, fromSecond.y, "and must follow the user scrolling it")
        assertEquals(60, fromFirst.y, "while the state that left that pane must no longer follow it")
    }

    @Test
    fun aPaneLeavingLeavesTheStateDrivingThePaneThatStillRendersIt() = runComposeSwingTest {
        assertTheStateDrivesThePaneThatStays(leavingDeclaredLast = false)
    }

    @Test
    fun aPaneLeavingHandsTheStateBackToThePaneDeclaredBeforeIt() = runComposeSwingTest {
        assertTheStateDrivesThePaneThatStays(leavingDeclaredLast = true)
    }

    /**
     * Two panes declare one state and one of them leaves; whichever of the two declarations the leaving
     * pane was, the state must end up driving the pane that stays. [leavingDeclaredLast] puts the leaving
     * pane's declaration after the staying pane's, which makes the leaving pane the one the state drove
     * while both were there.
     */
    private suspend fun ComposeSwingTest.assertTheStateDrivesThePaneThatStays(leavingDeclaredLast: Boolean) {
        var declared: ScrollState? = null
        var bothPanes by mutableStateOf(true)
        setContent {
            val state = rememberScrollState()
            declared = state
            val declareLeaving: @Composable () -> Unit = {
                if (bothPanes) {
                    ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                        content { Label("leaving", modifier = SwingModifier.preferredSize(300, 400)) }
                    }
                }
            }
            val declareStaying: @Composable () -> Unit = {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                    content { Label("staying", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
            Column {
                if (leavingDeclaredLast) {
                    declareStaying()
                    declareLeaving()
                } else {
                    declareLeaving()
                    declareStaying()
                }
            }
        }
        val state = declared ?: error("the scroll panes did not compose")

        bothPanes = false
        awaitIdle()

        // Only the staying pane is left, so it is the single scroll pane in the tree.
        val staying = onNodeOfType<JScrollPane>().fetch()
        state.y = 33
        assertEquals(
            33,
            staying.viewport.viewPosition.y,
            "the pane that renders the state must still be driven by it",
        )
        staying.viewport.viewPosition = Point(0, 21)
        assertEquals(21, state.y, "and the state must still follow the user scrolling that pane")
        assertEquals(400, state.viewHeight, "the metrics of the pane it renders must still be reported")
    }

    @Test
    fun aPaneTakingAnotherStateHandsTheOldOneBackToThePaneThatStillDeclaresIt() = runComposeSwingTest {
        var declaredOnBoth: ScrollState? = null
        var secondPaneHasItsOwn by mutableStateOf(false)
        setContent {
            val shared = rememberScrollState()
            val own = rememberScrollState()
            declaredOnBoth = shared
            Column {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = shared) {
                    content { Label("first", modifier = SwingModifier.preferredSize(300, 400)) }
                }
                ScrollPane(
                    modifier = SwingModifier.preferredSize(100, 50),
                    state = if (secondPaneHasItsOwn) own else shared,
                ) {
                    content { Label("second", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
        }
        val shared = declaredOnBoth ?: error("the scroll panes did not compose")
        val first = onAllNodesOfType<JScrollPane>().onFirst().fetch()

        // The second pane declared the shared state last, so it is the pane that state drives.
        secondPaneHasItsOwn = true
        awaitIdle()

        shared.y = 28
        assertEquals(
            28,
            first.viewport.viewPosition.y,
            "the state must drive the pane whose declaration of it is left",
        )
        first.viewport.viewPosition = Point(0, 17)
        assertEquals(17, shared.y, "and must follow the user scrolling that pane")
        assertEquals(400, shared.viewHeight, "reporting the metrics of the pane it renders")
    }

    @Test
    fun aStateTakingOverAPaneScrollsItToItsOwnPosition() = runComposeSwingTest {
        var second: ScrollState? = null
        var useSecond by mutableStateOf(false)
        setContent {
            val one = rememberScrollState()
            val two = rememberScrollState(y = 15)
            second = two
            ScrollPane(
                modifier = SwingModifier.preferredSize(100, 50),
                state = if (useSecond) two else one,
            ) {
                content { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        viewport().viewPosition = Point(0, 60)

        useSecond = true
        awaitIdle()

        assertEquals(
            15,
            viewport().viewPosition.y,
            "the state the composition now declares owns the position, so the pane moves to it",
        )
        assertEquals(15, second?.y, "and the state keeps reporting the position it imposed")
    }

    @Test
    fun aPaneReturningToTheCompositionIsScrolledBackToWhereTheStateIs() = runComposeSwingTest {
        var declared: ScrollState? = null
        var visible by mutableStateOf(true)
        setContent {
            val state = rememberScrollState()
            declared = state
            if (visible) {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                    content { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        viewport().viewPosition = Point(0, 60)

        visible = false
        awaitIdle()
        visible = true
        awaitIdle()

        assertEquals(60, state.y, "the state keeps the position across the pane's absence")
        assertEquals(
            60,
            viewport().viewPosition.y,
            "and the pane it binds to again must be scrolled back to it",
        )
    }

    @Test
    fun swappingTheStateLeavesThePreviousOneInert() = runComposeSwingTest {
        var first: ScrollState? = null
        var second: ScrollState? = null
        var useSecond by mutableStateOf(false)
        setContent {
            val one = rememberScrollState()
            val two = rememberScrollState()
            first = one
            second = two
            ScrollPane(
                modifier = SwingModifier.preferredSize(100, 50),
                state = if (useSecond) two else one,
            ) {
                content { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        val dropped = first ?: error("the scroll pane did not compose")
        val adopted = second ?: error("the scroll pane did not compose")

        useSecond = true
        awaitIdle()
        viewport().viewPosition = Point(0, 20)
        assertEquals(20, adopted.y, "the state now declared must follow the pane")
        assertEquals(0, dropped.y, "the state no longer declared must stop following it")

        dropped.y = 45
        assertEquals(
            20,
            viewport().viewPosition.y,
            "the state no longer declared must stop driving the pane as well",
        )
        assertEquals(400, adopted.viewHeight, "the state now declared reports the pane's metrics")
        assertEquals(
            listOf(0, 0, 0, 0),
            listOf(dropped.extentWidth, dropped.extentHeight, dropped.viewWidth, dropped.viewHeight),
            "and the state no longer declared reports none, though the pane it drove is still there",
        )
    }

    @Test
    fun aPaneWithNothingToShowHasNowhereToScroll() = runComposeSwingTest {
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {}
        }
        val state = declared ?: error("the scroll pane did not compose")

        assertEquals(0, state.maxY, "a pane showing nothing has nowhere to scroll")
        state.y = 25
        assertEquals(
            Point(0, 0),
            viewport().viewPosition,
            "a viewport with nothing to move stays where it is",
        )
    }
}
