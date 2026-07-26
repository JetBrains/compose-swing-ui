package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins what [ComposeSwingTest.awaitEventsDelivered] delivers and what it withholds.
 *
 * The gate exists to take apart the two things [ComposeSwingTest.awaitIdle] settles together: it dispatches
 * the notifications already queued on the event dispatch thread, and it produces no composition frame,
 * so a value observed after it came from a widget rather than from a recomposition.
 */
class AwaitEventsDeliveredContractTest {
    @Test
    fun queuedNotificationsAreDeliveredAndTheCompositionDoesNotAdvance() = runComposeSwingTest {
        var count by mutableIntStateOf(0)
        setContent { Label(text = "count: $count") }

        SwingUtilities.invokeLater { count++ }
        awaitEventsDelivered()

        assertEquals(1, count, "the queued notification should have been dispatched")
        onNodeOfType<JLabel>().assertTextEquals("count: 0")

        awaitIdle()
        onNodeOfType<JLabel>().assertTextEquals("count: 1")
    }

    @Test
    fun aNotificationQueuedByAnotherIsDeliveredToo() = runComposeSwingTest {
        setContent { Label(text = "chained") }
        val order = mutableListOf<String>()

        SwingUtilities.invokeLater {
            order += "first"
            SwingUtilities.invokeLater { order += "second" }
        }
        awaitEventsDelivered()

        assertEquals(listOf("first", "second"), order, "work queued while draining should be drained too")
    }

    @Test
    fun anEmptyQueueReturnsWithoutAFrame() = runComposeSwingTest {
        var frames by mutableIntStateOf(0)
        setContent { Label(text = "frames: $frames") }

        // Nothing is queued, so the gate has nothing to dispatch and no reason to advance the
        // composition; the tree must read exactly as the last settled frame left it.
        awaitEventsDelivered()

        onNodeOfType<JLabel>().assertTextEquals("frames: 0")
        frames = 1
        awaitEventsDelivered()
        onNodeOfType<JLabel>().assertTextEquals("frames: 0")
    }

    @Test
    fun aQueueThatNeverQuiescesFailsReadably() = runComposeSwingTest {
        setContent { Label(text = "spinning") }

        // A runnable that re-queues itself never lets the queue empty. The flag stops it once the gate
        // has given up, so the endless chain does not outlive this test on the shared event queue.
        var reposting = true
        lateinit var repost: Runnable
        repost = Runnable { if (reposting) SwingUtilities.invokeLater(repost) }
        SwingUtilities.invokeLater(repost)

        val failure =
            try {
                assertFailsWith<AssertionError> { awaitEventsDelivered() }
            } finally {
                reposting = false
            }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("awaitEventsDelivered"), "the failure should name the gate: $message")
        assertTrue(message.contains("JLabel"), "the failure should carry a tree dump: $message")
    }
}
