package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves [ComposeSwingTest.awaitIdle] awaits chained EDT-deferred work, not just a fixed number of drains.
 *
 * The idle gate settles the composition and then drains the event-dispatch queue until it is
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

    private companion object {
        // Deeper than any small fixed drain count, so a case that only ran one or two drains would
        // leave the chain unfinished and fail.
        const val CHAIN_DEPTH = 8
    }
}
