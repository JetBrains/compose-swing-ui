package org.jetbrains.compose.swing.test

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves [ComposeSwingTest.awaitIdle] awaits chained EDT-deferred work, not just a fixed number of drains.
 *
 * The idle gate recomposes and then drains the event-dispatch queue until it is
 * genuinely empty. A task run by one drain may schedule further `invokeLater` work, and the final
 * link in such a chain may mutate observable state or a composition input. Draining a fixed one or
 * two turns would declare idleness with that scheduled work still queued, so these cases build chains
 * deeper than any fixed count and assert that the final effect has landed by the time `awaitIdle`
 * returns.
 */
class AwaitIdleChainedWorkTest {
    @Test
    fun awaitIdleRunsAChainOfDeferredRunnablesToItsEnd() = runComposeSwingTest {
        setContent { Label(text = "root") }

        // A deep chain of invokeLater hops, each scheduling the next; only the last mutates state.
        // No compose input changes until then, so the composition stays quiescent throughout and the
        // gate can return only once the queue drains empty - which requires every hop to have run.
        val landed = intArrayOf(0)

        fun hop(remaining: Int) {
            if (remaining == 0) {
                landed[0] = CHAIN_DEPTH
            } else {
                SwingUtilities.invokeLater { hop(remaining - 1) }
            }
        }
        SwingUtilities.invokeLater { hop(CHAIN_DEPTH) }

        awaitIdle()

        assertEquals(
            CHAIN_DEPTH,
            landed[0],
            "awaitIdle must run every hop of the deferred chain, not a fixed number of drains",
        )
    }

    @Test
    fun awaitIdleRecomposesWhenTheChainEndsInAStateWrite() = runComposeSwingTest {
        var ready by mutableStateOf(false)
        setContent {
            Label(text = "host")
            if (ready) Label(text = "chained-done")
        }
        onNodeWithText("chained-done").assertDoesNotExist()

        // The final hop flips a composition input. The drain loop runs the chain to completion, the
        // resulting snapshot write revives recomposition, and the outer frame loop recomposes - so the
        // conditionally-added label is present once awaitIdle returns.

        fun hop(remaining: Int) {
            if (remaining == 0) {
                ready = true
            } else {
                SwingUtilities.invokeLater { hop(remaining - 1) }
            }
        }
        SwingUtilities.invokeLater { hop(CHAIN_DEPTH) }

        awaitIdle()

        onNodeWithText("chained-done").assertExists()
    }

    @Test
    fun awaitIdleLaysOutOnlyOnceTheChainHasRun() = runComposeSwingTest {
        val progress = intArrayOf(0)
        val seen = mutableSetOf<Int>()
        val panel = JPanel(RecordingLayout { seen += progress[0] })
        setContent { SwingNode(factory = { panel }) }
        seen.clear()

        startChain(progress, panel)
        awaitIdle()

        assertEquals(setOf(CHAIN_DEPTH), seen, "a layout pass ran with only part of the chain dispatched")
    }

    @Test
    fun aFrameLaysOutOnlyOnceTheChainHasRun() = runComposeSwingTest {
        val progress = intArrayOf(0)
        val seen = mutableSetOf<Int>()
        val panel = JPanel(RecordingLayout { seen += progress[0] })
        setContent { SwingNode(factory = { panel }) }
        seen.clear()

        startChain(progress, panel)
        mainClock.advanceTimeByFrame()

        assertEquals(setOf(CHAIN_DEPTH), seen, "a layout pass ran with only part of the chain dispatched")
    }

    @Test
    fun waitUntilLaysOutOnlyOnceTheChainHasRun() = runComposeSwingTest {
        val progress = intArrayOf(0)
        val seen = mutableSetOf<Int>()
        val panel = JPanel(RecordingLayout { seen += progress[0] })
        setContent { SwingNode(factory = { panel }) }
        seen.clear()

        startChain(progress, panel)
        waitUntil { seen.isNotEmpty() }

        assertEquals(setOf(CHAIN_DEPTH), seen, "a layout pass ran with only part of the chain dispatched")
    }

    @Test
    fun setContentLaysOutOnlyOnceAChainTheCompositionStartedHasRun() = runComposeSwingTest {
        val progress = intArrayOf(0)
        val seen = mutableSetOf<Int>()
        val panel = JPanel(RecordingLayout { seen += progress[0] })

        setContent {
            SwingNode(factory = { panel })
            SideEffect { startChain(progress, panel) }
        }

        assertEquals(setOf(CHAIN_DEPTH), seen, "a layout pass ran with only part of the chain dispatched")
    }

    /**
     * Schedules a chain of [CHAIN_DEPTH] hops, each writing how far the chain has got into [progress] and
     * invalidating [panel], so any layout pass run between two hops lays the panel out.
     */
    private fun startChain(
        progress: IntArray,
        panel: JPanel,
    ) {
        fun hop(remaining: Int) {
            progress[0] = CHAIN_DEPTH - remaining
            panel.invalidate()
            if (remaining > 0) SwingUtilities.invokeLater { hop(remaining - 1) }
        }
        SwingUtilities.invokeLater { hop(CHAIN_DEPTH) }
    }

    /** A layout manager placing nothing, calling [onLayout] on every pass. */
    private class RecordingLayout(
        private val onLayout: () -> Unit,
    ) : FlowLayout() {
        override fun layoutContainer(target: Container) {
            onLayout()
        }
    }

    private companion object {
        // Deeper than any small fixed drain count, so a case that only ran one or two drains would
        // leave the chain unfinished and fail.
        const val CHAIN_DEPTH = 8
    }
}
