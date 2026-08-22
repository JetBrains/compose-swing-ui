package org.jetbrains.compose.swing.passcost.harness

import androidx.compose.runtime.snapshots.Snapshot
import java.awt.EventQueue
import java.awt.Toolkit
import javax.swing.SwingUtilities

/**
 * Drives one composition pass and answers what it cost.
 *
 * The protocol is the same for every arm, the one that changes nothing included, so the arms differ
 * only in what [change] writes:
 *
 *  1. on the event dispatch thread: run [change], then publish it with `Snapshot.sendApplyNotifications`;
 *  2. drain the event queue, which is what lets the recomposer become a waiter for a frame - it is woken
 *     by a posted event, so a frame sent before it has run reaches nobody;
 *  3. send a frame and drain again, until no waiter is left.
 *
 * How many frames step 3 took is part of what the pass cost, and is answered alongside the figures.
 *
 * Time and allocation are read on the event dispatch thread at the head of the first step and the head
 * of a last one of its own, so the figures cover the thread the composition runs on and not this
 * thread's waiting. Every pass checks that both reads were taken on the same thread: AWT retires an
 * idle event dispatch thread and starts another, and a counter read across that boundary is nonsense.
 *
 * Call off the event dispatch thread.
 */
internal fun drivePass(change: () -> Unit): PassMeasurement {
    var startThread = 0L
    var startNanos = 0L
    var startBytes = 0L
    onEventDispatchThread {
        startThread = Thread.currentThread().threadId()
        startNanos = System.nanoTime()
        startBytes = currentThreadAllocatedBytes()
        change()
        Snapshot.sendApplyNotifications()
    }
    drainEventQueue()

    var frames = 0
    do {
        onEventDispatchThread { Frames.sendFrame() }
        frames++
        drainEventQueue()
        check(frames <= MAX_FRAMES_PER_PASS) {
            "a pass still wanted a frame after $MAX_FRAMES_PER_PASS of them: this is not one pass"
        }
    } while (Frames.hasFrameAwaiter)

    var endThread = 0L
    var endNanos = 0L
    var endBytes = 0L
    onEventDispatchThread {
        endThread = Thread.currentThread().threadId()
        endNanos = System.nanoTime()
        endBytes = currentThreadAllocatedBytes()
    }
    check(startThread == endThread) {
        "the pass began on thread $startThread and ended on thread $endThread, so its counters do not " +
            "describe one thread"
    }
    return PassMeasurement(endNanos - startNanos, endBytes - startBytes, frames)
}

/** Runs [block] on the event dispatch thread and waits for it. Call off that thread. */
internal fun onEventDispatchThread(block: () -> Unit): Unit = SwingUtilities.invokeAndWait(block)

/**
 * Blocks until the event queue is empty.
 *
 * A runnable posted to the queue reports, as it is dispatched, whether anything was left behind it. That
 * is enough here because nothing is ever shown: with no realized widget there is no paint or repaint
 * event, so the one queue this reads is the only one in play.
 */
private fun drainEventQueue() {
    val queue: EventQueue = Toolkit.getDefaultToolkit().systemEventQueue
    var empty = false
    var rounds = 0
    while (!empty) {
        onEventDispatchThread { empty = queue.peekEvent() == null }
        rounds++
        check(rounds <= MAX_DRAIN_ROUNDS) {
            "the event queue has not been empty after $MAX_DRAIN_ROUNDS rounds: something keeps posting work"
        }
    }
}

/** How many frames one pass may want before it is not one pass any more. */
private const val MAX_FRAMES_PER_PASS = 8

/** How many rounds the queue is given to fall empty. */
private const val MAX_DRAIN_ROUNDS = 64
