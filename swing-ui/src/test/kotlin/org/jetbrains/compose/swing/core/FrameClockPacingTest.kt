package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for the two cadences a composition runs on: recomposition, which follows the event
 * queue, and frame-driven work, which follows the clock.
 *
 * These run on a real [SwingRecomposer] with its real timer, since what they pin is timing the test
 * harness's controllable clock deliberately takes out of the picture. They measure in event-dispatch
 * cycles rather than milliseconds wherever they can, so a loaded machine slows a case down without
 * changing what it asserts.
 */
class FrameClockPacingTest {
    @Test
    fun everyWriteMadeWhileHandlingOneEventCostsASinglePass() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var counter by mutableStateOf(0)
        var passes = 0
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    Label(text = "n=$counter")
                    SideEffect { passes++ }
                }
            awaitUntil("the content mounts") { labelTextOrNull(composition) == "n=0" }
            val passesBefore = passes

            // No suspension point between the writes, so all of them are made while the event
            // dispatch thread is handling a single event, exactly as a Swing listener would.
            repeat(WRITES_PER_EVENT) { counter = it + 1 }

            awaitUntil("the writes reach the widget") { labelTextOrNull(composition) == "n=$WRITES_PER_EVENT" }
            delay(QUIET_PERIOD)
            assertEquals(
                1,
                passes - passesBefore,
                "$WRITES_PER_EVENT writes made under one event must cost one pass, not one pass each",
            )
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aWriteReachesItsWidgetWithoutWaitingOutAFrame() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var text by mutableStateOf("v0")
        var content: DisposableHandle? = null
        try {
            content = composition.setContent(parent = recomposer.compositionContext) { Label(text = text) }
            awaitUntil("the content mounts") { labelTextOrNull(composition) == "v0" }

            repeat(WRITES_TIMED) { round ->
                val expected = "v${round + 1}"
                text = expected
                val cycles =
                    cyclesUntil("write $expected reaches the widget") { labelTextOrNull(composition) == expected }
                assertTrue(
                    cycles <= MAX_CYCLES_TO_APPLY,
                    "a write must apply on the cycles right after the event that made it, " +
                        "not a frame interval later; it took $cycles event-dispatch cycles",
                )
            }
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun anAnimationAdvancesAtTheClockCadenceRatherThanAsFastAsTheEventQueueTurns() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        val cadenceFrames = ANIMATION_MILLIS / recomposer.clock.frameDelayMillis
        var target by mutableStateOf(0f)
        val steps = mutableListOf<Float>()
        val frameNanos = mutableListOf<Long>()
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    val fraction = linearRamp(target, cadenceFrames) { frameNanos += it }
                    Label(text = "f=$fraction")
                    SideEffect { if (steps.lastOrNull() != fraction) steps += fraction }
                }
            awaitUntil("the content mounts") { steps.isNotEmpty() }

            target = 1f
            awaitUntil("the animation reaches its target") { steps.last() == 1f }

            assertTrue(
                steps.size > 1,
                "the animation must advance in steps rather than snap to its target; it went $steps",
            )
            assertFramesSpanTheirCadence(recomposer.clock.frameDelayMillis, frameNanos)
            assertEveryFrameAdvancedOneStep(steps, cadenceFrames)
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aWriteLandingMidAnimationAppliesAtOnceAndTheAnimationNeitherSkipsNorJumps() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        val cadenceFrames = ANIMATION_MILLIS / recomposer.clock.frameDelayMillis
        var target by mutableStateOf(0f)
        var counter by mutableStateOf(0)
        val steps = mutableListOf<Float>()
        val frameNanos = mutableListOf<Long>()
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    val fraction = linearRamp(target, cadenceFrames) { frameNanos += it }
                    Label(text = "n=$counter f=$fraction")
                    SideEffect { if (steps.lastOrNull() != fraction) steps += fraction }
                }
            awaitUntil("the content mounts") { steps.isNotEmpty() }

            target = 1f
            // Write on every cycle for as long as the animation runs: each write lands between two
            // animation frames, which is the case the paused clock has to serve.
            var writes = 0
            var worstCycles = 0
            while (steps.last() != 1f) {
                counter = ++writes
                worstCycles =
                    maxOf(
                        worstCycles,
                        cyclesUntil("write $writes reaches the widget") {
                            labelTextOrNull(composition)?.startsWith("n=$writes ") == true
                        },
                    )
            }

            assertTrue(writes > 1, "the case needs writes made while the animation was still running")
            assertTrue(
                worstCycles <= MAX_CYCLES_TO_APPLY,
                "a write made mid-animation must not wait for the animation's next frame; " +
                    "the slowest took $worstCycles event-dispatch cycles",
            )
            assertFramesSpanTheirCadence(recomposer.clock.frameDelayMillis, frameNanos)
            assertEveryFrameAdvancedOneStep(steps, cadenceFrames)
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aWriteBetweenFramesAppliesAtOnceWhileFrameAwaitersWaitOutTheCadence() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        val clock = recomposer.clock
        clock.setFramesPerSecond(1)
        var counter by mutableStateOf(0)
        val frameNanos = mutableListOf<Long>()
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    Label(text = "n=$counter")
                    LaunchedEffect(Unit) {
                        while (true) {
                            withFrameNanos { frameNanos += it }
                        }
                    }
                }
            awaitUntil("the content mounts") { labelTextOrNull(composition) == "n=0" }
            awaitUntil("the frame-driven work takes its first frame") { frameNanos.isNotEmpty() }

            var worstCycles = 0
            repeat(WRITES_TIMED) { round ->
                counter = round + 1
                worstCycles =
                    maxOf(
                        worstCycles,
                        cyclesUntil("write ${round + 1} reaches the widget") {
                            labelTextOrNull(composition) == "n=${round + 1}"
                        },
                    )
            }

            assertTrue(
                worstCycles <= MAX_CYCLES_TO_APPLY,
                "a write made between frames must not wait for the next one; " +
                    "the slowest took $worstCycles event-dispatch cycles",
            )

            awaitUntil("the cadence releases the frame awaiters") { frameNanos.size > 1 }
            val wait = (frameNanos[1] - frameNanos[0]).nanoseconds
            // The timer starts after the first frame is stamped and cannot fire before its delay, so
            // a second frame that arrives sooner was not sent by the cadence.
            val cadence = clock.frameDelayMillis.milliseconds
            assertTrue(
                wait >= cadence,
                "frame-driven work must be sent no second frame before the cadence elapses, however many " +
                    "writes are recomposed in between; the second came $wait after the first, " +
                    "on a cadence of $cadence",
            )
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun workThatStartsAwaitingFramesLongAfterTheLastOneStillGetsThem() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var framesSeen by mutableStateOf(0)
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    Label(text = "frames=$framesSeen")
                    LaunchedEffect(Unit) {
                        // The composition is fully settled and asking for nothing by the time this
                        // starts awaiting frames.
                        delay(LATE_START)
                        repeat(LATE_FRAMES) {
                            withFrameNanos { framesSeen++ }
                        }
                    }
                }
            awaitUntil("the content mounts") { labelTextOrNull(composition) == "frames=0" }
            // Read partway into the gap, while the effect is still waiting it out: a read taken at the
            // end of the gap races the first frame the effect then asks for, which a composition that
            // settles inside the event that woke it can win.
            delay(LATE_START / 2)
            assertEquals(0, framesSeen, "the case needs the composition idle when the frame awaiting starts")
            assertFalse(
                recomposer.clock.isPacingFrameDrivenWork,
                "the case needs nothing awaiting a frame when the frame awaiting starts",
            )

            awaitUntil("frame-driven work started from an idle composition runs") { framesSeen == LATE_FRAMES }
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun anIdleCompositionKeepsNoTimerRunning() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var target by mutableStateOf(0f)
        val steps = mutableListOf<Float>()
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    val fraction = linearRamp(target, ANIMATION_MILLIS / recomposer.clock.frameDelayMillis) {}
                    Label(text = "f=$fraction")
                    SideEffect { if (steps.lastOrNull() != fraction) steps += fraction }
                }
            awaitUntil("the content mounts") { steps.isNotEmpty() }
            assertTrue(
                !recomposer.clock.isPacingFrameDrivenWork,
                "a composition that has only ever recomposed must run no timer",
            )

            target = 1f
            awaitUntil("the animation is running") { steps.size > 1 }
            assertTrue(recomposer.clock.isPacingFrameDrivenWork, "an animation must be paced by the timer")

            awaitUntil("the animation ends and the timer stops") {
                steps.last() == 1f && !recomposer.clock.isPacingFrameDrivenWork
            }
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    /**
     * A frame-driven stand-in for an animation, so these tests pin the clock rather than an animation
     * engine: each time [target] moves, it walks from the value it holds to [target] in [stepCount]
     * equal steps, one per frame the clock sends, and reports the time of each frame to [onFrame].
     * It lands on [target] exactly and then awaits no further frame, so a settled ramp asks the clock
     * for nothing.
     *
     * A step counts frames and does not read the time, so a frame that arrives late makes the run
     * longer and no step bigger: load can slow this ramp down but never change what it did.
     */
    @Composable
    private fun linearRamp(
        target: Float,
        stepCount: Int,
        onFrame: (frameNanos: Long) -> Unit,
    ): Float {
        var value by remember { mutableStateOf(target) }
        LaunchedEffect(target) {
            val start = value
            if (start == target) return@LaunchedEffect
            onFrame(withFrameNanos { it })
            repeat(stepCount) { step ->
                onFrame(withFrameNanos { it })
                value = if (step == stepCount - 1) target else start + (target - start) * (step + 1) / stepCount
            }
        }
        return value
    }

    /**
     * Asserts that the [frameNanos] the clock sent span at least a [CADENCE_HEADROOM]th of the time that
     * many frames take on a cadence of [cadenceMillis].
     *
     * This is what tells a clock-paced run from one that ran as fast as the event queue turned: the
     * latter sends the same frames in a handful of milliseconds. It bounds the span from below only,
     * because load stretches a run and never shortens it. It reads the span rather than the gap between
     * two frames, since a frame sent late leaves the next one close behind it, however evenly the timer
     * ticks.
     */
    private fun assertFramesSpanTheirCadence(
        cadenceMillis: Int,
        frameNanos: List<Long>,
    ) {
        val frameCount = frameNanos.size - 1
        val spanMillis = (frameNanos.last() - frameNanos.first()).nanoseconds.inWholeMilliseconds
        assertTrue(frameCount > 1, "the animation must have been sent several frames; it got $frameNanos")
        assertTrue(
            spanMillis * CADENCE_HEADROOM >= frameCount * cadenceMillis,
            "$frameCount frames on a ${cadenceMillis}ms cadence take on the order of " +
                "${frameCount * cadenceMillis}ms; they were sent within ${spanMillis}ms, which means the work " +
                "ran as fast as the event queue turned rather than on the clock",
        )
    }

    /**
     * Asserts that each of [steps] after the first moved the ramp by one of its [stepCount] equal steps:
     * no frame was skipped and none was served twice.
     */
    private fun assertEveryFrameAdvancedOneStep(
        steps: List<Float>,
        stepCount: Int,
    ) {
        assertEquals(stepCount + 1, steps.size, "every frame's value must reach the composed widget")
        assertTrue(
            steps.zipWithNext().all { (previous, next) -> abs(next - previous - 1f / stepCount) <= 1e-4f },
            "each step must be exactly one of $stepCount equal steps; it went $steps",
        )
    }

    /**
     * Suspends on the EDT until [condition] holds, yielding the EDT back between checks so the
     * composition can make progress, and answers how many cycles that took. A condition that never
     * becomes true fails the test at the deadline, naming [description], instead of hanging.
     */
    private suspend fun cyclesUntil(
        description: String,
        condition: () -> Boolean,
    ): Int {
        var cycles = 0
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!condition()) {
                    cycles++
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
        return cycles
    }

    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        cyclesUntil(description, condition)
    }

    /** The single [JLabel]'s text in [container]'s subtree, or `null` while none has mounted yet. */
    private fun labelTextOrNull(container: Container): String? {
        val labels = mutableListOf<JLabel>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return labels.singleOrNull()?.text
    }

    private companion object {
        const val WRITES_PER_EVENT: Int = 10
        const val WRITES_TIMED: Int = 20

        /**
         * The event-dispatch cycles a write may take to reach its widget. A write travels through the
         * snapshot apply notification, the recomposer waking, and the frame that follows the event -
         * a handful of cycles - while a write held back to a frame interval spends thousands here.
         */
        const val MAX_CYCLES_TO_APPLY: Int = 50
        const val ANIMATION_MILLIS: Int = 400

        /**
         * The measured frame span must be at least one quarter of its nominal cadence span.
         * Event queue scheduling can bunch adjacent frames together under load.
         */
        const val CADENCE_HEADROOM: Int = 4
        const val LATE_FRAMES: Int = 3
        val LATE_START = 300.milliseconds
        val QUIET_PERIOD = 200.milliseconds
        val SETTLE_TIMEOUT = 10.seconds
    }
}
