package org.jetbrains.compose.swing.swingmark.harness

import java.awt.ActiveEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.PaintEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

/*
 * SwingMark's own way of waiting for a change to finish, ported as it stands. Timing a change against a
 * different wait would report a different number.
 */

private val bogusComponent: Component = JPanel()

/** The queue every wait here drains, and the one a test posts input events to. */
internal val eventQueue: EventQueue = Toolkit.getDefaultToolkit().systemEventQueue

/**
 * Blocks until every AWT queue is empty.
 *
 * A probe event is posted, and reports on dispatch whether anything was left behind it; the loop ends the
 * first time nothing was. Waiting on one queue would prove nothing, since AWT dispatches repaints, paints
 * and posted runnables from three.
 *
 * Must be called off the event dispatch thread: it waits for that thread to reach the event it posted.
 *
 * Bounded, unlike the original's. A composition that never settles keeps posting work, and an unbounded
 * wait would hang there rather than report it - which is how a wrong test read as a frozen benchmark.
 * Passing the deadline is a failure of the thing being measured, so it is raised.
 */
internal fun rest() {
    Protocol.record(Step.REST)
    drain(null)
}

/**
 * Rests, and answers what [read] said on the event dispatch thread at the moment the queue was empty.
 *
 * The probe that finds the queue empty is dispatched on that thread with nothing behind it, which is
 * where a widget may be read and the only moment worth reading it. So a caller that waits and then asks
 * pays for the wait alone, rather than for a round trip of its own on top of it.
 */
internal fun restReading(read: () -> Boolean): Boolean = drain(read)

/**
 * Drains the queue, answering what [read] said as it fell empty, or false when there is nothing to read.
 *
 * Not a step of its own: it is what [rest] and one declared change are built from, and each of those
 * counts itself.
 */
private fun drain(read: (() -> Boolean)?): Boolean {
    Thread.yield()
    val deadline = System.nanoTime() + REST_TIMEOUT_NANOS
    var probe: NotifyingPaintEvent
    var queueEmpty = false
    do {
        probe = NotifyingPaintEvent(bogusComponent, read)
        eventQueue.postEvent(probe)
        // Bounded here rather than around the loop: an event thread stuck inside one dispatch never comes
        // back to be asked again, so a deadline checked between events is never reached.
        check(probe.awaitDispatch(REST_TIMEOUT_NANOS)) {
            "the event dispatch thread has not come back for " +
                "${REST_TIMEOUT_NANOS / NANOS_PER_SECOND}s. It is stuck here:\n${eventThreadStack()}"
        }
        queueEmpty = probe.queueEmpty
        check(queueEmpty || System.nanoTime() < deadline) {
            "the event queue has not been empty for ${REST_TIMEOUT_NANOS / NANOS_PER_SECOND}s: something " +
                "keeps posting work. The event thread is at:\n${eventThreadStack()}"
        }
    } while (!queueEmpty)
    Toolkit.getDefaultToolkit().sync()
    Watchdog.progress()
    return probe.reading
}

/** Where the event dispatch thread is, for a wait that gave up on it. */
internal fun eventThreadStack(): String =
    Thread
        .getAllStackTraces()
        .entries
        .firstOrNull { it.key.name.startsWith("AWT-EventQueue") }
        ?.value
        ?.joinToString("\n") { "    at $it" }
        ?: "    (no event dispatch thread is running)"

private const val NANOS_PER_SECOND = 1_000_000_000L

/** How long the queue is given to fall idle. */
private const val REST_TIMEOUT_NANOS = 5 * NANOS_PER_SECOND

/** Collects, as SwingMark's own `syncRam` does, so a test is not charged for the garbage before it. */
@Suppress("ExplicitGarbageCollectionCall")
internal fun syncRam() {
    System.gc()
    System.gc()
    Thread.sleep(COLLECTION_SETTLE_MILLIS)
}

private const val COLLECTION_SETTLE_MILLIS = 100L

/**
 * Reports, as it is dispatched, whether the queue behind it was empty, and reads [read] where it was.
 *
 * Each one claims a distinct update rectangle, because AWT coalesces paint events by rectangle and a
 * coalesced probe is never dispatched.
 */
private class NotifyingPaintEvent(
    source: Component,
    private val read: (() -> Boolean)?,
) : PaintEvent(source, UPDATE, null),
    ActiveEvent {
    private val location = nextLocation++
    private val dispatched = CountDownLatch(1)

    /** What [read] said, or false where the queue was not empty and it was never asked. */
    var reading: Boolean = false
        private set

    @Volatile
    var queueEmpty: Boolean = false
        private set

    override fun getUpdateRect(): Rectangle = Rectangle(location, location, 1, 1)

    /** Blocks until the event dispatch thread reaches this event, or [timeoutNanos] passes. */
    fun awaitDispatch(timeoutNanos: Long): Boolean = dispatched.await(timeoutNanos, TimeUnit.NANOSECONDS)

    override fun dispatch() {
        val empty = eventQueue.peekEvent() == null
        if (empty) reading = read?.invoke() ?: false
        queueEmpty = empty
        dispatched.countDown()
    }

    private companion object {
        @Volatile
        private var nextLocation = 0
    }
}
