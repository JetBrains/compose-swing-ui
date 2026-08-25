package org.jetbrains.compose.swing.core

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import java.awt.Component
import java.awt.DisplayMode
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.time.Duration.Companion.seconds

/**
 * The [MonotonicFrameClock] a [Recomposer] recomposes on, holding recomposition and frame-driven work
 * to two separate cadences.
 *
 * Recomposition follows the event queue: the recomposer's frame is dispatched as an event of its own,
 * so a declared state write reaches its widget a handful of event-dispatch cycles after the event that
 * made it, rather than at the next tick of a frame cadence. It is one pass per event and not one per
 * write: the frame is dispatched only once the Swing event that made the writes has returned, so a
 * listener writing ten properties costs a single pass, and the pass that carries those writes is never
 * inside the event that made them.
 *
 * Frame-driven work - [androidx.compose.runtime.withFrameNanos] and the animations built on it -
 * advances at a nominal frame rate instead, paced by a [Timer] that runs only while something is
 * awaiting a frame. Between one such frame and the next the recomposer's own clock is
 * [paused][Recomposer.pauseCompositionFrameClock], which withholds the broadcast without withholding
 * recomposition: a state write landing mid-animation applies on the cycles that follow the event that
 * made it, without waiting for the animation's next frame, while every `withFrameNanos` caller stays
 * exactly where it was, so the animation neither skips a frame nor jumps a step. The clock rests
 * unpaused, so work that starts awaiting frames long after the last one wakes the recomposer the
 * ordinary way.
 *
 * Frames are dispatched on the Event Dispatch Thread, so consumers of [withFrameNanos] receive frames
 * on the thread where recomposition and applier mutations run. Every method here must be called on
 * that thread.
 *
 * A [Recomposer] takes the clock it recomposes on from the coroutine that runs
 * [Recomposer.runRecomposeAndApplyChanges], not from the context it was constructed with. That is
 * what lets this clock be built around the recomposer it paces and then handed to the coroutine that
 * runs it.
 *
 * The cadence is a best-effort, nominal wall-clock rate, not vsync or variable-refresh-rate (VRR)
 * synchronization: actual frame delivery jitters with EDT load. [displayRefreshRate] reads a
 * component's display refresh rate to seed that cadence.
 *
 * Call [dispose] when the owning composition is torn down to guarantee the timer is stopped.
 */
internal class SwingFrameClock(
    private val recomposer: Recomposer,
    framesPerSecond: Int = DEFAULT_FRAMES_PER_SECOND,
) : MonotonicFrameClock {
    private val timer: Timer = Timer(delayMillisFor(framesPerSecond), null)

    /** The timer's current cadence, in milliseconds: the interval frame-driven work advances on. */
    val frameDelayMillis: Int
        get() = timer.delay

    /**
     * Whether the timer that paces frame-driven work is running. It runs only while something is
     * awaiting a frame, so an idle composition keeps no timer of its own.
     */
    val isPacingFrameDrivenWork: Boolean
        get() = timer.isRunning

    /**
     * The clock [recomposer] broadcasts frames on, which is where every `withFrameNanos` caller in its
     * compositions parks. Its awaiter count is the true one - pausing hides those awaiters from the
     * recomposer's own bookkeeping, never from the clock holding them.
     */
    private val compositionClock: BroadcastFrameClock =
        checkNotNull(recomposer.effectCoroutineContext[MonotonicFrameClock] as? BroadcastFrameClock) {
            "A Recomposer publishes its BroadcastFrameClock as the frame clock of its effect context"
        }

    private var dispatchScheduled = false

    private var disposed = false

    private val broadcastClock = BroadcastFrameClock(onNewAwaiters = ::scheduleDispatch)

    init {
        timer.addActionListener {
            // Resuming requests a frame of its own when frame-driven work is pending, which is what
            // carries that work forward by exactly one step: the dispatch that serves it pauses again.
            recomposer.resumeCompositionFrameClock()
            if (!compositionClock.hasAwaiters) timer.stop()
        }
    }

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        broadcastClock.withFrameNanos(onFrame)

    /**
     * Retimes the clock to a new nominal frame rate, recomputing the timer delay. Takes effect on the
     * next timer cycle. Must be called on the EDT. A no-op when the resulting delay is unchanged.
     */
    fun setFramesPerSecond(framesPerSecond: Int) {
        val newDelay = delayMillisFor(framesPerSecond)
        if (newDelay == timer.delay) return
        timer.delay = newDelay
    }

    /**
     * Stops the underlying timer and retires the clock: nothing further is scheduled, and a frame
     * dispatch already on the event queue neither broadcasts a frame nor restarts the timer. Safe to
     * call multiple times.
     */
    fun dispose() {
        disposed = true
        timer.stop()
    }

    /**
     * Queues the frame the recomposer just asked for behind the event being dispatched, so every write
     * that event still has to make joins it. [BroadcastFrameClock] calls this (under its lock) when the
     * first awaiter appears, which for this clock is the recomposer and nothing else.
     */
    private fun scheduleDispatch() {
        if (disposed || dispatchScheduled) return
        dispatchScheduled = true
        SwingUtilities.invokeLater(::dispatchFrame)
    }

    /**
     * Sections the whole frame: recomposition and the changes it applies both run inside it, so what a
     * declared change costs is the length of this section.
     *
     * The frame runs as a call rather than as this function's body, so the section brackets it from outside
     * and the frame itself stays one method the runtime compiles on its own.
     */
    private fun dispatchFrame() {
        // A dispatch queued before disposal still runs afterwards; it must do nothing.
        if (disposed) return
        trace("frame") { runFrame() }
    }

    private fun runFrame() {
        // Cleared first: a frame the recomposer asks for again while this one runs must re-arm.
        dispatchScheduled = false
        // Read before the frame, because an awaiter this frame resumes re-registers from a later
        // dispatch cycle - after which the pause below is already in force.
        val carriesFrameDrivenWork = compositionClock.hasAwaiters
        broadcastClock.sendFrame(System.nanoTime())
        if (carriesFrameDrivenWork) {
            recomposer.pauseCompositionFrameClock()
            if (!timer.isRunning) timer.start()
        }
    }

    internal companion object {
        const val DEFAULT_FRAMES_PER_SECOND: Int = 60

        /**
         * The [Timer] delay one frame of [framesPerSecond] takes, in whole milliseconds and never below
         * one: a zero delay would fire the timer as fast as the event queue drains. A non-positive rate
         * is read as a single frame per second.
         */
        private fun delayMillisFor(framesPerSecond: Int): Int {
            val frame = 1.seconds / framesPerSecond.coerceAtLeast(1)
            return frame.inWholeMilliseconds.coerceAtLeast(1).toInt()
        }

        /**
         * The component's current display refresh rate in frames per second, read from
         * `graphicsConfiguration.device.displayMode.refreshRate` and falling back to
         * [DEFAULT_FRAMES_PER_SECOND] when the display reports an unknown
         * ([DisplayMode.REFRESH_RATE_UNKNOWN]) or non-positive rate, or the component has no
         * [java.awt.GraphicsConfiguration] - which is what a component outside any container reports.
         */
        fun Component.displayRefreshRate(): Int {
            val rate = graphicsConfiguration?.device?.displayMode?.refreshRate ?: DisplayMode.REFRESH_RATE_UNKNOWN
            return if (rate == DisplayMode.REFRESH_RATE_UNKNOWN || rate <= 0) DEFAULT_FRAMES_PER_SECOND else rate
        }
    }
}
