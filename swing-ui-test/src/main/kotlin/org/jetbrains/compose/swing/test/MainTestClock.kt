package org.jetbrains.compose.swing.test

import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manual control over the frames [ComposeSwingTest] sends its own composition.
 *
 * [ComposeSwingTest.awaitIdle] brings a composition to idle by sending it frames; this clock decides
 * whether those frames happen on their own ([autoAdvance] `true`, the default, leaving every gate's
 * behavior unchanged) or only when a test sends one, which is what makes it possible to observe a
 * frame-driven animation partway through rather than only once it has run to completion.
 *
 * Every call that sends a frame fails the same way [ComposeSwingTest.awaitIdle] does.
 *
 * This clock governs only [ComposeSwingTest]'s own off-screen composition. Content composed under a
 * real [org.jetbrains.compose.swing.window.Window] or [org.jetbrains.compose.swing.window.Dialog]
 * runs on that window's own recomposer, whose frame-driven work is paced by the display that window
 * is on - this clock has no effect on it.
 */
public sealed interface MainTestClock {
    /**
     * Whether [ComposeSwingTest.awaitIdle] sends frames on its own to reach idle. `true` by
     * default.
     *
     * Set to `false` to take over: [ComposeSwingTest.awaitIdle] then drains pending recomposition,
     * snapshot and event-dispatch-thread work without producing a frame, leaving a coroutine parked
     * in `withFrameNanos` - an animation, for instance - suspended until [advanceTimeByFrame] or
     * [advanceTimeBy] sends one.
     *
     * This also governs [ComposeSwingTest.setContent]'s own wait for idle, which depends on it the
     * same way: turning this off before the first [ComposeSwingTest.setContent] call leaves a
     * frame-driven effect started from initial composition parked from the very start, with nothing
     * composed off it until a frame is sent explicitly.
     */
    public var autoAdvance: Boolean

    /** The current composition time. */
    public val currentTime: Duration

    /**
     * The normal frame interval, equal to one frame of a 60Hz display.
     */
    public val frameDuration: Duration

    /**
     * Sends one frame at the normal frame cadence, advancing [currentTime] by [frameDuration], and
     * returns once the frame's effects have propagated as far as they can without another one. Earlier state writes are
     * recomposed and applied by this frame.
     */
    public fun advanceTimeByFrame()

    /**
     * Sends one frame after [duration] has elapsed on the clock. Earlier state writes are recomposed
     * and applied by this frame.
     *
     * @param duration the composition time to add before sending the frame.
     * @throws IllegalArgumentException if [duration] is negative or not finite.
     */
    public fun advanceTimeByFrame(duration: Duration)

    /**
     * Advances [currentTime] by [duration].
     *
     * Advances in [frameDuration] increments by default, rounding [duration] up to a whole frame.
     * When [ignoreFrameDuration] is true, advances by exactly [duration] instead. Frames are sent
     * at the normal frame cadence. Earlier state writes are recomposed and applied by the first frame.
     * Coroutine delays use real time.
     *
     * @param duration the composition time to add.
     * @param ignoreFrameDuration true advances by exactly [duration] instead of rounding up to whole frames;
     * frames still go out at the normal cadence; false by default.
     * @throws IllegalArgumentException if [duration] is negative or not finite.
     */
    public fun advanceTimeBy(
        duration: Duration,
        ignoreFrameDuration: Boolean = false,
    )

    /**
     * Sends frames, one at a time, until [condition] returns `true`. A failure is thrown as itself, never as a
     * [ComposeTimeoutException].
     *
     * @param timeout the composition time this may spend, one second by default.
     * @param condition checked before the first frame and again after each one, on the calling thread.
     * @throws ComposeTimeoutException if [condition] is still not met once [currentTime] has advanced by
     * [timeout].
     */
    public suspend fun advanceTimeUntil(
        timeout: Duration = 1.seconds,
        condition: () -> Boolean,
    )
}

/** Thrown in cases where the test harness can't satisfy a condition in a defined time limit. */
public class ComposeTimeoutException(
    message: String?,
) : Throwable(message)

/**
 * @param currentTimeNanos reads the shared frame-time counter every frame this clock or the harness's
 * own gates advance.
 * @param lastFrameTimeNanos reads the time of the most recently sent frame.
 * @param advanceTime advances the clock without sending a frame.
 * @param runFrame publishes pending snapshot writes, advances the shared frame-time counter by
 * the given number of nanoseconds, sends the result as a frame, and - without suspending - blocks
 * until the frame's effects have propagated as far as they can without another one: recomposed and
 * applied if it revived a pending recomposition, and any effect the frame newly parks (or re-parks)
 * waiting on the next one has registered for it. [advanceTimeByFrame] and [advanceTimeBy] are pinned
 * non-suspending by the public surface, so a caller stepping through several frames in a row - with no
 * suspension point between the calls for the event dispatch thread to otherwise make progress on -
 * still delivers every step to whatever is waiting rather than silently losing the ones a still-parked
 * awaiter cannot yet be dispatched to receive. The `caller` name is the public entry point to attach to
 * a failure if it never becomes idle.
 * @param diagnostics reports the tree and composition state a gate attaches to a composition that never became idle.
 * @param throwFailure throws, once, a failure that ended recomposition or that the library recorded, if one is pending.
 */
internal class MainTestClockImpl(
    private val currentTimeNanos: () -> Long,
    private val lastFrameTimeNanos: () -> Long,
    private val advanceTime: (Long) -> Unit,
    private val runFrame: (deltaNanos: Long, caller: String) -> Unit,
    private val diagnostics: () -> String,
    private val throwFailure: () -> Unit,
) : MainTestClock {
    override var autoAdvance: Boolean = true

    override val currentTime: Duration
        get() = currentTimeNanos().nanoseconds

    override val frameDuration: Duration = FRAME_DURATION

    override fun advanceTimeByFrame() {
        advanceOnFrameCadence(frameDuration.inWholeNanoseconds, "mainClock.advanceTimeByFrame")
    }

    override fun advanceTimeByFrame(duration: Duration) {
        requireValidDuration(duration)
        runFrame(duration.inWholeNanoseconds, "mainClock.advanceTimeByFrame")
    }

    override fun advanceTimeBy(
        duration: Duration,
        ignoreFrameDuration: Boolean,
    ) {
        requireValidDuration(duration)
        val frameNanos = frameDuration.inWholeNanoseconds
        val durationNanos = duration.inWholeNanoseconds
        val roundedNanos =
            if (ignoreFrameDuration || durationNanos % frameNanos == 0L) {
                durationNanos
            } else {
                durationNanos + frameNanos - durationNanos % frameNanos
            }
        advanceOnFrameCadence(roundedNanos, "mainClock.advanceTimeBy")
    }

    private fun advanceOnFrameCadence(
        nanos: Long,
        caller: String,
    ) {
        val frameNanos = frameDuration.inWholeNanoseconds
        var remaining = nanos
        while (remaining > 0) {
            val untilFrame = frameNanos - (currentTimeNanos() - lastFrameTimeNanos())
            if (remaining < untilFrame) {
                advanceTime(remaining)
                return
            }
            runFrame(untilFrame, caller)
            remaining -= untilFrame
        }
    }

    private fun requireValidDuration(duration: Duration) {
        require(duration.isFinite() && !duration.isNegative()) {
            "Cannot advance composition time by a duration that is negative or not finite: $duration."
        }
    }

    override suspend fun advanceTimeUntil(
        timeout: Duration,
        condition: () -> Boolean,
    ) {
        val deadline = currentTime + timeout
        while (!condition()) {
            throwFailure()
            if (currentTime >= deadline) {
                throw ComposeTimeoutException(
                    "Condition still not met after advancing $timeout of composition time. Current tree:\n" +
                        diagnostics(),
                )
            }
            advanceTimeByFrame()
            yield()
        }
    }

    internal companion object {
        /**
         * The default frame interval, equal to one frame of a 60Hz display.
         * Only its monotonic progression matters to frame-driven effects.
         */
        val FRAME_DURATION: Duration = 1.seconds / 60
    }
}
