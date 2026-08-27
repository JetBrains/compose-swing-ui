package org.jetbrains.compose.swing.core

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import java.awt.Component
import java.awt.DisplayMode
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.time.Duration.Companion.seconds

/**
 * The [MonotonicFrameClock] a [Recomposer] recomposes on, holding recomposition and frame-driven work
 * to two separate cadences.
 *
 * Recomposition follows the event queue by default: the recomposer's frame is dispatched as an event of
 * its own, so a declared state write reaches its widget a handful of event-dispatch cycles after the
 * event that made it, rather than at the next tick of a frame cadence. It is one pass per event and not
 * one per write: the frame is dispatched only once the Swing event that made the writes has returned, so
 * a listener writing ten properties costs a single pass. A discrete interaction takes its frame inside
 * the event that made it, through [settleInPlace].
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
    private val dispatcher: SwingUiDispatcher,
    framesPerSecond: Int = DEFAULT_FRAMES_PER_SECOND,
) : MonotonicFrameClock {
    /** The [Recomposer] this clock paces, named by [pace]. */
    private lateinit var recomposer: Recomposer
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
    private lateinit var compositionClock: BroadcastFrameClock

    private var dispatchScheduled = false

    /** Whether a frame is running, which is what keeps [settleInPlace] from starting one inside one. */
    private var inFrame = false

    /**
     * Whether the event being dispatched still owes a settlement, which [EventDispatchHook] sets when it
     * queues one. A component reporting a change inside that event settles it in place and clears this,
     * so the queued settlement finds the work already done and stands down.
     *
     * Reporting is what settles a change ahead of a repaint an earlier event left pending: that repaint
     * stands where it was queued, which is ahead of the settlement this event queued behind it.
     */
    internal var settlementOwedForEvent: Boolean = false

    /**
     * Whether the recomposer's own clock is paused, tracked here because [Recomposer] exposes no accessor
     * for it and this class is the only thing that pauses or resumes it.
     */
    private var compositionFramePaused = false

    private var disposed = false

    private val broadcastClock = BroadcastFrameClock(onNewAwaiters = ::scheduleDispatch)

    init {
        timer.addActionListener {
            // Resuming requests a frame of its own when frame-driven work is pending, which is what
            // carries that work forward by exactly one step: the dispatch that serves it pauses again.
            resumeCompositionFrames()
            if (!compositionClock.hasAwaiters) timer.stop()
        }
    }

    /**
     * Names the [Recomposer] this clock paces: the one whose broadcast a frame withholds, and whose
     * awaiters say whether frame-driven work is pending. Call it once, from whoever builds the pair.
     *
     * A clock cannot take its recomposer as it is built: the recomposer is constructed over the
     * dispatcher that owns this clock, so the clock exists first.
     */
    fun pace(recomposer: Recomposer) {
        check(!this::recomposer.isInitialized) { "This clock already paces a recomposer" }
        // Read before either field is written, so a recomposer this clock cannot pace leaves it unpaced
        // rather than half-paced, and the failure names what is wrong instead of the retry.
        val broadcast =
            checkNotNull(recomposer.effectCoroutineContext[MonotonicFrameClock] as? BroadcastFrameClock) {
                "A Recomposer publishes its BroadcastFrameClock as the frame clock of its effect context"
            }
        this.recomposer = recomposer
        compositionClock = broadcast
    }

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        broadcastClock.withFrameNanos(onFrame)

    /** Withholds the recomposer's broadcast, so a frame recomposes without advancing frame-driven work. */
    private fun pauseCompositionFrames() {
        if (compositionFramePaused) return
        compositionFramePaused = true
        recomposer.pauseCompositionFrameClock()
    }

    /** Restores the broadcast, so the next frame carries frame-driven work forward again. */
    private fun resumeCompositionFrames() {
        if (!compositionFramePaused) return
        compositionFramePaused = false
        recomposer.resumeCompositionFrameClock()
    }

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
     * Stops the underlying timer, withdraws the clock from [EventDispatchHook], and retires it: nothing
     * further is scheduled, and a frame dispatch already on the event queue neither broadcasts a frame
     * nor restarts the timer. Safe to call multiple times.
     *
     * Withdrawing here is what keeps a subscription from outliving the clock it settles: whoever
     * subscribed the clock need not be the one to dispose it.
     */
    fun dispose() {
        disposed = true
        timer.stop()
        EventDispatchHook.unsubscribe(this)
    }

    /**
     * Queues the frame the recomposer just asked for behind the event being dispatched, so every write
     * that event still has to make joins it. [BroadcastFrameClock] calls this (under its lock) when the
     * first awaiter appears, which for this clock is the recomposer and nothing else.
     */
    private fun scheduleDispatch() {
        if (disposed || dispatchScheduled) return
        dispatchScheduled = true
        SwingUtilities.invokeLater(::dispatchQueuedFrame)
    }

    /**
     * Runs the frame the pending state writes are owed now, inside the event that made them, rather than
     * from an event of its own.
     *
     * This is what puts a declaration back onto a widget before the user sees the change it answers.
     * Swing queues the repaint a change provokes while the widget is still handling it, ahead of anything
     * the report of that change can schedule, so a frame that costs an event of its own is always a frame
     * too late: the change is painted, and the declaration arrives for the paint after it. Running the
     * frame here leaves the widget settled before that first repaint is served.
     *
     * Call it from a widget listener, once the caller has been told of the change and has had its chance
     * to adopt it - a frame run before that would settle the widget against a declaration the caller is
     * about to change.
     *
     * This runs the frame inside the call that asks for it; [dispatchQueuedFrame] runs one from an event of its
     * own.
     *
     * The frame runs whatever is already queued rather than deferring to it. A settlement queued as an
     * event of its own is served in its turn, behind whatever the queue already held - a repaint among
     * them, since Swing merges a later dirty region into a repaint already pending rather than queueing
     * a second.
     *
     * The recomposer's broadcast is withheld for the duration, so the frame recomposes and applies
     * without carrying frame-driven work forward by a step it has not earned. The clock is left as it
     * was found, so a pause the cadence is holding survives this.
     *
     * No frame is run while one is already running - a report reached from inside a frame leaves the
     * settlement owed, for the one the event queued to make. A disposed clock runs nothing: its
     * recomposer is cancelled, and the writes are owed to a composition that is gone.
     *
     * A frame the recomposer has already asked for stays queued and runs afterwards, finding its work
     * done.
     *
     * Runs on the event dispatch thread.
     */
    fun settleInPlace() {
        if (disposed || inFrame) return
        // This frame is the settlement the event owed, so the one queued for it stands down.
        settlementOwedForEvent = false
        val heldPausedElsewhere = compositionFramePaused
        pauseCompositionFrames()
        try {
            trace("frame") { runFrame() }
        } finally {
            if (!heldPausedElsewhere) resumeCompositionFrames()
        }
    }

    /**
     * The frame itself, whoever asked for it: publish the writes made since the last one, resume the
     * recomposer they invalidated, and give it the frame it then asks for. The recomposer performs the
     * whole recomposition and applies its changes inside that frame, so this returns with the widgets
     * already updated.
     *
     * Publishing is part of the frame rather than of the caller that asks for one. A frame queued when
     * the recomposer asked for it runs after whatever writes the events in between made, and a frame
     * that broadcast without publishing those would recompose against state already superseded - which
     * leaves a widget holding a change the composition has answered, for the next paint to show.
     *
     * Holds [inFrame] for as long as it runs, so a widget listener reached from the changes applied here
     * asks for no frame of its own - see [settleInPlace].
     */
    private fun runFrame() {
        inFrame = true
        try {
            Snapshot.sendApplyNotifications()
            dispatcher.drain()
            broadcastClock.sendFrame(System.nanoTime())
        } finally {
            inFrame = false
        }
    }

    /**
     * The frame the recomposer asked for, run from an event of its own.
     *
     * Sections the whole frame: recomposition and the changes it applies both run inside it, so what a
     * declared change costs is the length of this section. The frame runs as a call rather than as this
     * function's body, so the section brackets it from outside and the frame itself stays one method the
     * runtime compiles on its own.
     */
    private fun dispatchQueuedFrame() {
        // A dispatch queued before disposal still runs afterwards; it must do nothing.
        if (disposed) return
        trace("frame") { runQueuedFrame() }
    }

    /**
     * The frame, plus the pacing frame-driven work needs: a frame that carried any leaves the recomposer's
     * broadcast paused behind it and the timer running, so the next step comes on the cadence rather than
     * from the next state write.
     */
    private fun runQueuedFrame() {
        // Cleared first: a frame the recomposer asks for again while this one runs must re-arm.
        dispatchScheduled = false
        // Read before the frame, because an awaiter this frame resumes re-registers from a later
        // dispatch cycle - after which the pause below is already in force.
        val carriesFrameDrivenWork = compositionClock.hasAwaiters
        runFrame()
        if (carriesFrameDrivenWork) {
            pauseCompositionFrames()
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
