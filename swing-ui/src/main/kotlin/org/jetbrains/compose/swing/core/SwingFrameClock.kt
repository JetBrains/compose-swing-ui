package org.jetbrains.compose.swing.core

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import java.awt.Component
import java.awt.DisplayMode
import javax.swing.Timer

/**
 * A [MonotonicFrameClock] backed by a Swing [Timer] that fires at a fixed nominal frame rate.
 *
 * Frames are dispatched on the Event Dispatch Thread, so consumers of [withFrameNanos] receive frames
 * on the thread where recomposition and applier mutations run.
 *
 * The timer runs only while there are awaiters: it starts on the first frame request and stops once
 * there are none, so an idle composition keeps no timer running. Call [dispose] when the owning
 * composition is torn down to guarantee the timer is stopped.
 *
 * The cadence is a best-effort, nominal wall-clock rate, not vsync or variable-refresh-rate (VRR)
 * synchronization: actual frame delivery jitters with EDT load. [displayRefreshRate] reads a
 * component's display refresh rate to seed that cadence.
 */
internal class SwingFrameClock(
    framesPerSecond: Int = DEFAULT_FRAMES_PER_SECOND,
) : MonotonicFrameClock {
    private val timer: Timer = Timer(delayMillisFor(framesPerSecond), null)

    /** The timer's current cadence, in milliseconds. */
    val frameDelayMillis: Int
        get() = timer.delay

    private val broadcastClock =
        BroadcastFrameClock(onNewAwaiters = {
            // BroadcastFrameClock invokes this (under its lock) when the first awaiter appears.
            if (!timer.isRunning) {
                timer.start()
            }
        })

    init {
        timer.addActionListener {
            broadcastClock.sendFrame(System.nanoTime())
            if (!broadcastClock.hasAwaiters) {
                timer.stop()
            }
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
     * Stops the underlying timer. Safe to call multiple times.
     */
    fun dispose() {
        timer.stop()
    }

    internal companion object {
        const val DEFAULT_FRAMES_PER_SECOND: Int = 60
        private const val MILLIS_PER_SECOND: Int = 1000

        private fun delayMillisFor(framesPerSecond: Int): Int =
            (MILLIS_PER_SECOND / framesPerSecond.coerceAtLeast(1)).coerceAtLeast(1)

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
