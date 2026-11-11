// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.AWTEvent
import java.awt.ActiveEvent
import java.awt.Toolkit
import java.awt.event.PaintEvent
import java.util.stream.Stream
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("waits")
    fun anEventOfTheRedrawIdQueuedBeforeTheWaitHasRunWhenItReturns(
        gate: String,
        wait: suspend ComposeSwingTest.() -> Unit,
    ) = runComposeSwingTest {
        setContent { Label(text = "redraw") }
        var ran = false

        postRedraw { ran = true }
        wait()

        assertTrue(ran, "$gate should dispatch an event of the redraw id queued before it")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("neverQuiescingQueues")
    fun aQueueThatNeverQuiescesFailsReadably(
        redrawQueued: Boolean,
        streamStartedDirectly: Boolean,
    ) = runComposeSwingTest {
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

        try {
            assertFailsWith<AssertionError> { awaitEventsDelivered() }
        } finally {
            reposting = false
        }
        onNodeOfType<JLabel>().assertTextEquals("spinning")
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

    companion object {
        @JvmStatic
        fun waits(): Stream<Arguments> {
            val events: suspend ComposeSwingTest.() -> Unit = { awaitEventsDelivered() }
            val idle: suspend ComposeSwingTest.() -> Unit = { awaitIdle() }
            return Stream.of(Arguments.of("awaitEventsDelivered", events), Arguments.of("awaitIdle", idle))
        }

        @JvmStatic
        fun neverQuiescingQueues(): Stream<Arguments> = Stream.of(
            Arguments.of(Named.of("no redraw queued", false), true),
            Arguments.of(Named.of("a redraw queued", true), true),
            Arguments.of(Named.of("a redraw starting the stream", true), false),
        )
    }
}

private class TaskEvent(
    private val task: () -> Unit,
) : AWTEvent(task, PaintEvent.UPDATE),
    ActiveEvent {
    override fun dispatch() = task()
}
