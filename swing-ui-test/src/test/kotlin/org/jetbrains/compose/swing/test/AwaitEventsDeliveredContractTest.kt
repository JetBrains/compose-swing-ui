package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.AWTEvent
import java.awt.ActiveEvent
import java.awt.Toolkit
import java.awt.event.PaintEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins what [ComposeSwingTest.awaitEventsDelivered] delivers and what it withholds.
 *
 * The gate exists to take apart the two things [ComposeSwingTest.awaitIdle] waits for together: it dispatches
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
        // composition; the tree must read exactly as the last frame left it.
        awaitEventsDelivered()

        onNodeOfType<JLabel>().assertTextEquals("frames: 0")
        frames = 1
        awaitEventsDelivered()
        onNodeOfType<JLabel>().assertTextEquals("frames: 0")
    }

    @TestFactory
    fun anEventOfTheRedrawIdQueuedBeforeTheWaitHasRunWhenItReturns(): List<DynamicTest> =
        listOf<Pair<String, suspend ComposeSwingTest.() -> Unit>>(
            "awaitEventsDelivered" to { awaitEventsDelivered() },
            "awaitIdle" to { awaitIdle() },
        ).map { (gate, wait) ->
            dynamicTest(gate) {
                runComposeSwingTest {
                    setContent { Label(text = "redraw") }
                    var ran = false

                    postRedraw { ran = true }
                    wait()

                    assertTrue(ran, "$gate should dispatch an event of the redraw id queued before it")
                }
            }
        }

    @TestFactory
    fun aQueueThatNeverQuiescesFailsReadably(): List<DynamicTest> = listOf(
        Triple("no redraw queued", false, true),
        Triple("a redraw queued", true, true),
        Triple("a redraw starting the stream", true, false),
    ).map { (name, redrawQueued, streamStartedDirectly) ->
        dynamicTest(name) {
            runComposeSwingTest {
                setContent { Label(text = "spinning") }

                // A runnable that re-queues itself never lets the queue empty. The flag stops it once the gate
                // has given up, so the endless chain does not outlive this test on the shared event queue.
                var reposting = true
                lateinit var repost: Runnable
                repost = Runnable { if (reposting) SwingUtilities.invokeLater(repost) }
                // The event of paint priority sits behind a stream of events of normal priority, which AWT never
                // lets it pass, so the gate must give up on the stream rather than wait for the event. When
                // the redraw starts the stream, none is queued yet at the moment the gate looks.
                if (redrawQueued) postRedraw { if (!streamStartedDirectly) SwingUtilities.invokeLater(repost) }
                if (streamStartedDirectly) SwingUtilities.invokeLater(repost)

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
    }

    @Test
    fun awaitIdleLaysOutOnlyAfterARedrawQueuedByARedrawHasRun() = runComposeSwingTest {
        var redrawn = false
        val redrawnAtLayout = mutableSetOf<Boolean>()
        val panel = JPanel(RecordingLayout(onLayout = { redrawnAtLayout += redrawn }))
        setContent { SwingNode(factory = { panel }) }
        redrawnAtLayout.clear()
        panel.invalidate()

        // The second redraw is queued while the first runs, behind the wait's own task for the queue to empty.
        postRedraw { postRedraw { redrawn = true } }
        awaitIdle()

        assertEquals(setOf(true), redrawnAtLayout, "awaitIdle should lay out, and only after the second redraw ran")
    }

    /** Posts an event of the redraw id whose source is no component, so AWT never merges it, and that runs [task]. */
    private fun postRedraw(task: () -> Unit) = Toolkit.getDefaultToolkit().systemEventQueue.postEvent(TaskEvent(task))
}

private class TaskEvent(
    private val task: () -> Unit,
) : AWTEvent(task, PaintEvent.UPDATE),
    ActiveEvent {
    override fun dispatch() = task()
}
