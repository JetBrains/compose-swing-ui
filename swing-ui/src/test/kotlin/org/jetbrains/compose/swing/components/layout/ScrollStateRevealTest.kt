package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
                Label("body", modifier = SwingModifier.preferredSize(300, 400).viewport())
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
                if (filled) Label("body", modifier = SwingModifier.preferredSize(300, 400).viewport())
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
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            // Runs as soon as the pane's content is installed, which is before the pane has ever been
            // laid out: the region is revealed on a viewport whose metrics are still to come.
            DisposableEffect(Unit) {
                state.revealRect(distantRegion)
                onDispose {}
            }
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {
                Label("body", modifier = SwingModifier.preferredSize(300, 400).viewport())
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
    fun aPaneWithNothingToShowHasNoRegionToReveal() = runComposeSwingTest {
        var declared: ScrollState? = null
        setContent {
            val state = rememberScrollState()
            declared = state
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = state) {}
        }
        val state = declared ?: error("the scroll pane did not compose")

        assertFalse(state.revealRect(distantRegion), "a pane holding no content reveals nothing")
    }

    @Test
    fun aStateNoPaneRendersRevealsNothing() = runComposeSwingTest {
        var declared: ScrollState? = null
        setContent { declared = rememberScrollState() }
        val state = declared ?: error("the state was not remembered")

        assertFalse(state.revealRect(distantRegion), "a state no pane renders reveals nothing")
    }
}
