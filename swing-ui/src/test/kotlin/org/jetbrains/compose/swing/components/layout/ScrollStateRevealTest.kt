package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A [ScrollState] brings a region of the pane's content into view where [ScrollState.revealRect] is
 * called, and reports where that leaves the pane.
 *
 * The harness lays the tree out synchronously off-screen, so the pane's viewport has real metrics and a
 * real position: a region below the visible ones is genuinely out of view until the state is asked for it.
 */
class ScrollStateRevealTest {
    /** The region of the content far enough down that no pane sized here can be showing it to begin with. */
    private val distantRegion = Rectangle(0, 350, 10, 10)

    private fun ComposeSwingTest.viewport(): JViewport = onNodeOfType<JScrollPane>().fetch().viewport

    /**
     * Composes a pane smaller than its content, so there is room to scroll on both axes, and returns the
     * [ScrollState] it declares.
     */
    private fun ComposeSwingTest.paneWithRoomToScroll(): ScrollState {
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        return declared ?: error("the scroll pane did not compose")
    }

    @Test
    fun revealingARegionScrollsThePaneToIt() = runComposeSwingTest {
        val state = paneWithRoomToScroll()
        assertFalse(viewport().viewRect.contains(distantRegion), "the region starts out of view")

        assertTrue(state.revealRect(distantRegion), "the pane rendering the state reveals the region")
        assertTrue(viewport().viewRect.contains(distantRegion), "which scrolls the pane to it")
    }

    @Test
    fun whereARevealLandsIsReportedBackAsThePosition() = runComposeSwingTest {
        val state = paneWithRoomToScroll()

        state.revealRect(distantRegion)

        assertEquals(viewport().viewPosition.y, state.y, "the position the reveal reached must be reported")
        assertEquals(viewport().viewPosition.x, state.x, "on both axes")
    }

    @Test
    fun aRegionAlreadyInViewLeavesThePaneWhereItStands() = runComposeSwingTest {
        val state = paneWithRoomToScroll()
        state.y = 100
        val position = viewport().viewPosition

        assertTrue(state.revealRect(Rectangle(0, 110, 10, 10)), "a region already in view is reached")
        assertEquals(position, viewport().viewPosition, "and the pane is left where it stands")
    }

    @Test
    fun aRegionOfContentThatArrivedAfterTheStateIsRevealed() = runComposeSwingTest {
        var filled by mutableStateOf(false)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState(y = 200)
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport { if (filled) Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")

        filled = true
        awaitIdle()

        assertTrue(state.revealRect(distantRegion), "the content that arrived has the region to reveal")
        assertTrue(viewport().viewRect.contains(distantRegion), "and the pane is scrolled to it")
    }

    @Test
    fun aRevealIssuedBeforeThePaneIsLaidOutStands() = runComposeSwingTest {
        var revealed: Boolean? = null
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            DisposableEffect(Unit) {
                revealed = state.revealRect(distantRegion)
                onDispose {}
            }
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        awaitIdle()

        assertEquals(true, revealed, "the pane that renders the state takes the reveal")
        assertTrue(
            viewport().viewRect.contains(distantRegion),
            "so the pane must show the revealed region, but stands at ${viewport().viewPosition}",
        )
        assertEquals(viewport().viewPosition.y, state.y, "and the state must report where the reveal landed")
    }

    @Test
    fun aRevealIssuedOnceTheViewArrivesAndBeforeLayoutStands() = runComposeSwingTest {
        var declared: ScrollState? = null
        lateinit var view: JLabel
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport {
                    SwingNode(
                        factory = { JLabel("body").also { view = it } },
                        modifier = SwingModifier.preferredSize(300, 400),
                    )
                    DisposableEffect(Unit) {
                        val viewport = assertIs<JViewport>(view.parent, "the view must be attached before revealing it")
                        assertEquals(Dimension(0, 0), viewport.extentSize, "the viewport must not have been laid out")
                        assertTrue(state.revealRect(distantRegion), "the attached content has a region to reveal")
                        onDispose {}
                    }
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")

        awaitIdle()

        assertTrue(
            viewport().viewRect.contains(distantRegion),
            "the pane must be left showing the revealed region, but stands at ${viewport().viewPosition}",
        )
        assertEquals(viewport().viewPosition.y, state.y, "and the state must report where the reveal landed")
    }

    @Test
    fun aRevealIssuedOnceTheViewArrivesReplacesOneStillWaiting() = runComposeSwingTest {
        val second = Rectangle(0, 200, 10, 10)
        setContent {
            val state = rememberScrollState()
            DisposableEffect(Unit) {
                state.revealRect(distantRegion)
                onDispose {}
            }
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport {
                    Label("body", modifier = SwingModifier.preferredSize(300, 400))
                    DisposableEffect(Unit) {
                        state.revealRect(second)
                        onDispose {}
                    }
                }
            }
        }
        awaitIdle()

        assertTrue(viewport().viewRect.contains(second), "the later region is the one shown")
        assertFalse(viewport().viewRect.contains(distantRegion), "and the replaced one is not")
    }

    @Test
    fun aRevealWaitingForContentIsDeliveredWhenItArrivesOnALaterPass() = runComposeSwingTest {
        var filled by mutableStateOf(false)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport { if (filled) Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")

        assertTrue(state.revealRect(distantRegion), "a bound pane takes the reveal even with nothing to show yet")
        awaitIdle()
        filled = true
        awaitIdle()

        assertTrue(
            viewport().viewRect.contains(distantRegion),
            "content arriving on a later pass shows the kept region, but the pane stands at ${viewport().viewPosition}",
        )
    }

    @Test
    fun aRevealKeptByAStateWhosePaneLeftIsNotDeliveredToAPaneRenderingItLater() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        var filled by mutableStateOf(false)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            if (shown) {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                    Viewport { if (filled) Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        awaitIdle()
        assertTrue(state.revealRect(distantRegion), "the pane without content keeps the reveal")

        shown = false
        awaitIdle()
        filled = true
        shown = true
        awaitIdle()

        assertEquals(Point(0, 0), viewport().viewPosition, "a pane rendering the state later is not scrolled")
    }

    @Test
    fun aDeliveredRevealDoesNotScrollThePaneAgainWhenContentIsReplaced() = runComposeSwingTest {
        var filled by mutableStateOf(false)
        var generation by mutableStateOf(0)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport {
                    if (filled) {
                        key(generation) {
                            Label("body", modifier = SwingModifier.preferredSize(300, 400))
                        }
                    }
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        state.revealRect(distantRegion)
        filled = true
        awaitIdle()
        assertTrue(viewport().viewRect.contains(distantRegion), "the reveal is delivered with the content")

        state.y = 0
        generation++
        awaitIdle()

        assertEquals(0, viewport().viewPosition.y, "a delivered reveal is not delivered again")
    }

    @Test
    fun aLaterRevealReplacesOneStillWaiting() = runComposeSwingTest {
        var filled by mutableStateOf(false)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport { if (filled) Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        val second = Rectangle(0, 200, 10, 10)
        state.revealRect(distantRegion)
        state.revealRect(second)

        filled = true
        awaitIdle()

        assertTrue(viewport().viewRect.contains(second), "the later region is the one shown")
        assertFalse(viewport().viewRect.contains(distantRegion), "and the replaced one is not")
    }

    @Test
    fun aLaterPositionWriteReplacesARevealStillWaiting() = runComposeSwingTest {
        var filled by mutableStateOf(false)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport { if (filled) Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        state.revealRect(distantRegion)
        state.y = 0

        filled = true
        awaitIdle()

        assertEquals(0, viewport().viewPosition.y, "the position written after the reveal is the one shown")
    }

    @Test
    fun aScrollBlockMovingThePositionReplacesARevealStillWaiting() = runComposeSwingTest {
        var filled by mutableStateOf(false)
        var declared: ScrollState? = null
        var scope: CoroutineScope? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            scope = rememberCoroutineScope()
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Viewport { if (filled) Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        awaitIdle()
        state.revealRect(distantRegion)
        (scope ?: error("the scroll pane did not compose")).launch { state.scroll { scrollTo(0, 0) } }
        awaitIdle()

        filled = true
        awaitIdle()

        assertEquals(0, viewport().viewPosition.y, "the block's move after the reveal is the one shown")
    }

    @Test
    fun aRevealWaitingForAViewThatKeptItsSizeIsDeliveredWhenItArrives() = runComposeSwingTest {
        var inPane by mutableStateOf(false)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            val body = remember { movableContentOf { Label("body", modifier = SwingModifier.preferredSize(300, 400)) } }
            Panel {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                    Viewport { if (inPane) body() }
                }
                if (!inPane) body()
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        awaitIdle()
        val view = onNodeOfType<JLabel>().fetch()
        assertEquals(Dimension(300, 400), view.size, "the view is laid out at its size before it moves")
        assertTrue(state.revealRect(distantRegion), "the viewport without a view keeps the reveal")

        inPane = true
        awaitIdle()

        assertEquals(Dimension(300, 400), view.size, "the view arrives at the size it already had")
        assertTrue(viewport().viewRect.contains(distantRegion), "and the reveal is shown")
    }

    @Test
    fun aRevealWaitingForAPaneThatAnotherPaneTookOverFromIsNotDeliveredToTheOther() = runComposeSwingTest {
        var swapped by mutableStateOf(false)
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) { Viewport {} }
            if (swapped) {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                    Viewport { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
                }
            }
        }
        val state = declared ?: error("the scroll pane did not compose")
        awaitIdle()
        assertTrue(state.revealRect(distantRegion), "the pane without content keeps the reveal")

        swapped = true
        awaitIdle()

        val takenOver = onAllNodesOfType<JScrollPane>().fetchAll().last()
        assertEquals(0, takenOver.viewport.viewPosition.y, "the pane that took over is not scrolled by it")
    }

    @Test
    fun aStateNoPaneRendersRevealsNothing() = runComposeSwingTest {
        var declared: ScrollState? = null
        setContent { declared = rememberScrollState() }
        val state = declared ?: error("the state was not remembered")

        assertFalse(state.revealRect(distantRegion), "a state no pane renders reveals nothing")
    }
}
