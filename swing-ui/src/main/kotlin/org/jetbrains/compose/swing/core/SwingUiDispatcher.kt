package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.swing.SwingUtilities
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * The [CoroutineDispatcher] a composition's recomposer runs on: work is queued behind the event being
 * dispatched, as [SwingUtilities.invokeLater] queues it, and run from a single event that drains the
 * whole queue.
 *
 * Draining is what a plain dispatcher cannot offer. Resuming the recomposer costs an event of its own
 * there, which lands behind the repaint Swing queued while the widget was changing, so the composition's
 * answer to a change is painted a frame after the change itself. Holding the queue here lets that answer
 * be drained from inside the event that made it - see [SwingFrameClock.settleInPlace], which is the
 * one caller that drains out of turn.
 *
 * The drain is greedy: a task queued by a task runs in the same drain, and the queue is re-checked under
 * the lock before the drain is declared over, so nothing is left stranded with no event scheduled to
 * come back for it. Tasks may be queued from any thread - a state write applied on a background thread
 * resumes the recomposer from there - so the queue is guarded, while the tasks themselves run outside
 * the guard, on the event dispatch thread.
 *
 * The clock a composition on this dispatcher recomposes on is built with it and belongs to it: a frame
 * drains this queue. The recomposer they serve is constructed over this dispatcher, so it is named on the
 * clock afterwards - see [SwingFrameClock.pace].
 */
internal class SwingUiDispatcher : CoroutineDispatcher() {
    /** Guards [queued] and [drainScheduled]. */
    private val lock = Any()

    private val queued = ArrayDeque<Runnable>()

    /** Whether an event that will drain [queued] is already on the event queue. */
    private var drainScheduled = false

    private val drainCallback = Runnable { drain() }

    /**
     * The clock the composition on this dispatcher recomposes on, and the one a component under it takes
     * its frame out of turn on.
     *
     * Declared last: it is handed this dispatcher as it is built, so everything it could read is in place
     * before it has it.
     */
    val frameClock: SwingFrameClock = SwingFrameClock(this)

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        synchronized(lock) {
            queued.addLast(block)
            if (!drainScheduled) {
                drainScheduled = true
                SwingUtilities.invokeLater(drainCallback)
            }
        }
    }

    /**
     * Runs everything queued, including what running it queues, and returns once the queue is empty.
     *
     * Safe to call when nothing is queued, which is what a drain out of turn leaves behind: the event
     * that was scheduled to drain still arrives, and finds the work already done.
     *
     * Runs on the event dispatch thread.
     */
    fun drain() {
        do {
            var task = next()
            while (task != null) {
                task.run()
                task = next()
            }
            // The queue is re-checked under the lock before the drain is declared over: a task queued
            // from another thread between the last check and this one would otherwise be left with
            // drainScheduled still set and no event coming to serve it.
        } while (moreQueued())
    }

    /** Whether anything is still queued; clears [drainScheduled] when nothing is. */
    private fun moreQueued(): Boolean =
        synchronized(lock) {
            if (queued.isEmpty()) {
                drainScheduled = false
                false
            } else {
                true
            }
        }

    private fun next(): Runnable? = synchronized(lock) { queued.removeFirstOrNull() }
}

/**
 * The [SwingFrameClock] a composition mounted under this context recomposes on, or `null` where its
 * recomposer is not this library's and no frame can be taken out of turn.
 *
 * A composition's effects run in a context stating its dispatcher, and a nested composition inherits that
 * context, so this reaches the clock of a content composition, of a window's shared composition, and of
 * an application's windows alike, without walking the component tree to find one. Each call reads the
 * context again rather than holding what it found, since several compositions share one recomposer.
 */
internal fun CompositionContext.swingFrameClock(): SwingFrameClock? =
    (effectCoroutineContext[ContinuationInterceptor] as? SwingUiDispatcher)?.frameClock
