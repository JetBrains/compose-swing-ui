package org.jetbrains.compose.swing.test

import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manual control over the frames [ComposeSwingTest] sends its own composition.
 *
 * [ComposeSwingTest.awaitIdle] settles a composition by sending it frames; this clock decides
 * whether those frames happen on their own ([autoAdvance] `true`, the default, leaving every gate's
 * behavior unchanged) or only when a test sends one, which is what makes it possible to observe a
 * frame-driven animation partway through rather than only once it has run to completion.
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
     * This also governs [ComposeSwingTest.setContent]'s own initial settle, which depends on it the
     * same way: turning this off before the first [ComposeSwingTest.setContent] call leaves a
     * frame-driven effect started from initial composition parked from the very start, with nothing
     * composed off it until a frame is sent explicitly.
     */
    public var autoAdvance: Boolean

    /** The composition time reached by the frames sent so far, whichever call sent them. */
    public val currentTime: Duration

    /**
     * The duration a single frame advances [currentTime] by: one frame of a 60Hz display, whichever
     * gate sent it - not the host display's refresh rate.
     */
    public val frameDuration: Duration

    /**
     * Sends one frame, advancing [currentTime] by [frameDuration], and returns once the frame's
     * effects have propagated as far as they can without another one.
     */
    public fun advanceTimeByFrame()

    /**
     * Advances [currentTime] by [duration].
     *
     * Steps in [frameDuration] increments, sending one frame per step, until [currentTime] has
     * advanced by at least [duration] - so the final step can land past the target by less than a
     * frame. Set [ignoreFrameDuration] to deliver the whole [duration] in a single frame instead.
     *
     * @param duration the composition time to add; nothing waits in real time for it to pass.
     * @param ignoreFrameDuration `true` delivers [duration] as a single frame, which lands
     *   [currentTime] exactly on the target rather than up to a frame past it; `false` by default.
     * @throws IllegalArgumentException if [duration] is negative or not finite. Composition time only
     * moves forward by a duration frames can actually step through: the frames a composition is sent
     * carry a monotonically rising time, and [Duration.INFINITE] (or a `NaN` duration) has no frame
     * count that reaches it.
     */
    public fun advanceTimeBy(
        duration: Duration,
        ignoreFrameDuration: Boolean = false,
    )

    /**
     * Sends frames, one at a time, until [condition] returns `true`.
     *
     * @param timeout the composition time this may spend, one second by default.
     * @param condition checked before the first frame and again after each one, on the calling thread.
     * @throws AssertionError if [condition] is still not met once [currentTime] has advanced by
     * [timeout].
     */
    public suspend fun advanceTimeUntil(
        timeout: Duration = 1.seconds,
        condition: () -> Boolean,
    )
}

/**
 * @param currentTimeNanos reads the shared frame-time counter every frame this clock or the harness's
 * own gates advance.
 * @param advanceAndSettle publishes pending snapshot writes, advances the shared frame-time counter by
 * the given number of nanoseconds, sends the result as a frame, and - without suspending - blocks
 * until the frame's effects have propagated as far as they can without another one: recomposed and
 * applied if it revived a pending recomposition, and any effect the frame newly parks (or re-parks)
 * waiting on the next one has registered for it. [advanceTimeByFrame] and [advanceTimeBy] are pinned
 * non-suspending by the public surface, so a caller stepping through several frames in a row - with no
 * suspension point between the calls for the event dispatch thread to otherwise make progress on -
 * still delivers every step to whatever is waiting rather than silently losing the ones a still-parked
 * awaiter cannot yet be dispatched to receive. The `caller` name is the public entry point to attach to
 * a failure if it never settles.
 * @param diagnostics reports the tree and composition state a gate attaches to what it could not settle.
 */
internal class MainTestClockImpl(
    private val currentTimeNanos: () -> Long,
    private val advanceAndSettle: (deltaNanos: Long, caller: String) -> Unit,
    private val diagnostics: () -> String,
) : MainTestClock {
    override var autoAdvance: Boolean = true

    override val currentTime: Duration
        get() = currentTimeNanos().nanoseconds

    override val frameDuration: Duration = FRAME_DURATION

    override fun advanceTimeByFrame() {
        advanceAndSettle(frameDuration.inWholeNanoseconds, "mainClock.advanceTimeByFrame")
    }

    override fun advanceTimeBy(
        duration: Duration,
        ignoreFrameDuration: Boolean,
    ) {
        require(duration.isFinite() && !duration.isNegative()) {
            "Cannot advance composition time by a duration that is negative or not finite: $duration."
        }
        if (ignoreFrameDuration) {
            advanceAndSettle(duration.inWholeNanoseconds, "mainClock.advanceTimeBy")
        } else {
            val target = currentTime + duration
            while (currentTime < target) advanceTimeByFrame()
        }
    }

    override suspend fun advanceTimeUntil(
        timeout: Duration,
        condition: () -> Boolean,
    ) {
        val deadline = currentTime + timeout
        while (!condition()) {
            if (currentTime >= deadline) {
                throw AssertionError(
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
         * One frame of a 60Hz display, the step every frame the harness sends advances composition
         * time by. Only its monotonic progression matters to frame-driven effects.
         */
        val FRAME_DURATION: Duration = 1.seconds / 60
    }
}
