// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.compose.swing.animation.core.Animatable
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins [ComposeSwingTest.mainClock] - manual control over the frames a composition is sent.
 *
 * With [MainTestClock.autoAdvance] off, a coroutine parked in `withFrameNanos` and a composition
 * merely holding an unapplied recomposition both have to read as idle to [ComposeSwingTest.awaitIdle]
 * - an idle wait that instead chased either one with a frame of its own would spin or hang the moment
 * autoAdvance is turned off - while the explicit-advance API is what is left to move either forward.
 */
class MainTestClockTest {
    @ParameterizedTest
    @MethodSource("frameAdvances")
    fun earlierWritesReachTheFrame(case: FrameAdvance) = runComposeSwingTest {
        var text by mutableStateOf("before")
        val frames = mutableListOf<Long>()
        mainClock.autoAdvance = false
        setContent {
            Label(text = text)
            LaunchedEffect(Unit) {
                while (true) withFrameNanos { frames.add(it) }
            }
        }
        val start = mainClock.currentTime
        val duration = case.step(mainClock)

        for (value in listOf("first", "second")) {
            text = value
            case.advance(mainClock, duration)
            onNodeWithText(value).assertExists()
            assertEquals(mainClock.currentTime.inWholeNanoseconds, frames.last())
        }
        assertEquals(
            listOf((start + duration).inWholeNanoseconds, (start + duration * 2).inWholeNanoseconds),
            frames,
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cadenceAdvances")
    fun advancesKeepFrameCadence(
        ignoreFrameDuration: Boolean,
        framesAfterExactAdvance: Int,
    ) = runComposeSwingTest {
        val frames = mutableListOf<Long>()
        mainClock.autoAdvance = false
        setContent {
            LaunchedEffect(Unit) {
                while (true) withFrameNanos { frames.add(it) }
            }
        }
        val start = mainClock.currentTime
        mainClock.advanceTimeBy(mainClock.frameDuration * FRAME_STEPS, ignoreFrameDuration)
        assertEquals(
            (1..FRAME_STEPS).map { (start + mainClock.frameDuration * it).inWholeNanoseconds },
            frames,
        )
        assertEquals(start + mainClock.frameDuration * FRAME_STEPS, mainClock.currentTime)

        mainClock.advanceTimeBy(1.milliseconds, ignoreFrameDuration = true)
        assertEquals(FRAME_STEPS, frames.size)
        assertEquals(start + mainClock.frameDuration * FRAME_STEPS + 1.milliseconds, mainClock.currentTime)

        mainClock.advanceTimeBy(mainClock.frameDuration * framesAfterExactAdvance, ignoreFrameDuration)
        assertEquals(
            (1..(FRAME_STEPS + framesAfterExactAdvance)).map {
                (start + mainClock.frameDuration * it).inWholeNanoseconds
            },
            frames,
        )
        assertEquals(
            start + mainClock.frameDuration * (FRAME_STEPS + framesAfterExactAdvance) + 1.milliseconds,
            mainClock.currentTime,
        )
    }

    @ParameterizedTest
    @MethodSource("oneFrameAdvances")
    fun aOneFrameAdvanceAfterAnExactPartialAdvanceKeepsTheFrameCadence(advance: (MainTestClock) -> Unit) =
        runComposeSwingTest {
            val frames = mutableListOf<Long>()
            mainClock.autoAdvance = false
            setContent {
                LaunchedEffect(Unit) {
                    while (true) withFrameNanos { frames.add(it) }
                }
            }
            val start = mainClock.currentTime
            mainClock.advanceTimeBy(1.milliseconds, ignoreFrameDuration = true)

            advance(mainClock)

            assertEquals(listOf((start + mainClock.frameDuration).inWholeNanoseconds), frames)
            assertEquals(start + mainClock.frameDuration + 1.milliseconds, mainClock.currentTime)
        }

    @Test
    fun aDefaultNonIntegralAdvanceRoundsUpToOneFrame() = runComposeSwingTest {
        val frames = mutableListOf<Long>()
        mainClock.autoAdvance = false
        setContent {
            LaunchedEffect(Unit) {
                while (true) withFrameNanos { frames.add(it) }
            }
        }
        val start = mainClock.currentTime

        mainClock.advanceTimeBy(1.milliseconds)

        assertEquals(listOf((start + mainClock.frameDuration).inWholeNanoseconds), frames)
        assertEquals(start + mainClock.frameDuration, mainClock.currentTime)
    }

    @ParameterizedTest
    @MethodSource("invalidAdvances")
    fun advancesRejectInvalidDurations(advance: (MainTestClock) -> Unit) = runComposeSwingTest {
        setContent { Label(text = "content") }
        mainClock.autoAdvance = false
        val start = mainClock.currentTime

        assertFailsWith<IllegalArgumentException> { advance(mainClock) }
        assertEquals(start, mainClock.currentTime, "a rejected advance must leave composition time where it was")
    }

    @Test
    fun mainClockDefaultsToAutoAdvanceOnAndToOneFrameOfASixtyHertzDisplay() = runComposeSwingTest {
        setContent { Label(text = "content") }

        assertTrue(mainClock.autoAdvance, "autoAdvance must default to true, matching every gate's existing behavior")
        assertEquals(
            1.seconds / 60,
            mainClock.frameDuration,
            "frameDuration must be one frame of a 60Hz display, not the host display's refresh rate",
        )
    }

    @Test
    fun everyGateAdvancesCurrentTimeByTheSamePublishedFrameDuration() = runComposeSwingTest {
        setContent { Label(text = "content") }
        // The initial wait for idle runs with autoAdvance on, so its frames come from awaitIdle's own send;
        // an explicit advance must land on the same grid rather than on a second frame length.
        val start = mainClock.currentTime
        assertEquals(
            0L,
            start.inWholeNanoseconds % mainClock.frameDuration.inWholeNanoseconds,
            "the frames the harness sends itself must step by the published frameDuration: $start",
        )

        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()

        assertEquals(start + mainClock.frameDuration, mainClock.currentTime)
    }

    @Test
    fun autoAdvanceOffHoldsAnEffectAtEachFrameUntilAdvanced() = runComposeSwingTest {
        var framesConsumed = 0
        mainClock.autoAdvance = false
        setContent {
            LaunchedEffect(Unit) {
                repeat(FRAME_STEPS) {
                    withFrameNanos { }
                    framesConsumed++
                }
            }
        }
        assertEquals(
            0,
            framesConsumed,
            "setContent's initial wait must not advance an effect parked on a frame when autoAdvance is off",
        )

        repeat(FRAME_STEPS) { step ->
            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertEquals(step + 1, framesConsumed, "advanceTimeByFrame must release exactly one parked frame at a time")
        }
    }

    @Test
    fun autoAdvanceOffLeavesAPendingRecompositionUnappliedUntilAFrameIsSent() = runComposeSwingTest {
        var revealed by mutableStateOf(false)
        setContent {
            Label(text = "host")
            if (revealed) Label(text = "revealed")
        }
        mainClock.autoAdvance = false

        // A plain state write is frame-scoped work exactly like an animation: the recomposer only
        // recomposes and applies from inside a frame, so this stays unapplied until one is sent.
        revealed = true
        awaitIdle()
        onNodeWithText("revealed").assertDoesNotExist()

        mainClock.advanceTimeByFrame()
        awaitIdle()
        onNodeWithText("revealed").assertExists()
    }

    @Test
    fun advanceTimeUntilDrivesFramesUntilTheConditionIsMet() = runComposeSwingTest {
        var framesConsumed = 0
        mainClock.autoAdvance = false
        setContent {
            LaunchedEffect(Unit) {
                repeat(FRAME_STEPS) {
                    withFrameNanos { }
                    framesConsumed++
                }
            }
        }

        mainClock.advanceTimeUntil { framesConsumed == FRAME_STEPS }

        assertEquals(FRAME_STEPS, framesConsumed)
    }

    @Test
    fun advanceTimeUntilFailsOnceItsTimeoutOfCompositionTimeIsExhausted() = runComposeSwingTest {
        setContent { Label(text = "static") }
        mainClock.autoAdvance = false

        val failure =
            assertFailsWith<ComposeTimeoutException> {
                mainClock.advanceTimeUntil(timeout = mainClock.frameDuration * 2) { false }
            }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("Condition still not met"),
            "the failure should say the condition was never met: $message",
        )
        assertTrue(
            message.contains("static"),
            "the failure should carry a dump of the tree it advanced: $message",
        )
    }

    @Test
    fun autoAdvanceDefaultDrivesARealAnimatableAnimationToItsTargetValueWithinOneAwaitIdle() = runComposeSwingTest {
        val animatable = Animatable(ANIMATION_START_VALUE)
        var start by mutableStateOf(false)
        setContent {
            LaunchedEffect(start) {
                if (start) {
                    animatable.animateTo(
                        ANIMATION_TARGET_VALUE,
                        tween(durationMillis = ANIMATION_DURATION_MILLIS, easing = LinearEasing),
                    )
                }
            }
        }

        // A plain state write is frame-scoped work exactly like an animation: the recomposer only
        // recomposes and applies from inside a frame, so the effect above has not even started yet.
        start = true
        awaitIdle()

        assertEquals(
            ANIMATION_TARGET_VALUE,
            animatable.value,
            "with autoAdvance left at its default, a single awaitIdle call must drive a real " +
                "animation's frames through to its target value",
        )
    }

    @Test
    fun autoAdvanceDefaultCancelsAnAnimationThatNeverEndsInsteadOfWaitingOnIt() = runComposeSwingTest {
        lateinit var animated: State<Float>
        setContent {
            animated =
                rememberInfiniteTransition().animateFloat(
                    initialValue = ANIMATION_START_VALUE,
                    targetValue = ANIMATION_TARGET_VALUE,
                    animationSpec =
                        infiniteRepeatable(tween(durationMillis = ANIMATION_DURATION_MILLIS, easing = LinearEasing)),
                )
        }

        awaitIdle()

        assertEquals(
            ANIMATION_START_VALUE,
            animated.value,
            "an animation that never ends must be cancelled at its first frame while frames run on " +
                "their own, leaving the value where the animation started",
        )
    }

    @Test
    fun autoAdvanceOffDrivesAnAnimationThatNeverEndsFrameByFrame() = runComposeSwingTest {
        lateinit var animated: State<Float>
        mainClock.autoAdvance = false
        setContent {
            animated =
                rememberInfiniteTransition().animateFloat(
                    initialValue = ANIMATION_START_VALUE,
                    targetValue = ANIMATION_TARGET_VALUE,
                    animationSpec =
                        infiniteRepeatable(tween(durationMillis = ANIMATION_DURATION_MILLIS, easing = LinearEasing)),
                )
        }

        repeat(2) { mainClock.advanceTimeByFrame() }
        awaitIdle()

        val midFlightValue = animated.value
        assertTrue(
            midFlightValue > ANIMATION_START_VALUE && midFlightValue < ANIMATION_TARGET_VALUE,
            "under manual frame control the animation is left alone, so two frames in it sits strictly " +
                "between its start and target: $midFlightValue",
        )
    }

    @Test
    fun autoAdvanceOffLetsATweenAnimationBeObservedStrictlyMidFlightBeforeReachingItsTarget() = runComposeSwingTest {
        val animatable = Animatable(ANIMATION_START_VALUE)
        mainClock.autoAdvance = false
        setContent {
            LaunchedEffect(Unit) {
                animatable.animateTo(
                    ANIMATION_TARGET_VALUE,
                    tween(durationMillis = ANIMATION_DURATION_MILLIS, easing = LinearEasing),
                )
            }
        }

        repeat(2) { mainClock.advanceTimeByFrame() }
        awaitIdle()

        val midFlightValue = animatable.value
        assertTrue(
            midFlightValue > ANIMATION_START_VALUE && midFlightValue < ANIMATION_TARGET_VALUE,
            "two frames into a tween animation that has not yet finished, the value must sit strictly " +
                "between its start and target, not merely differ from the target: $midFlightValue",
        )

        mainClock.advanceTimeBy(mainClock.frameDuration * ANIMATION_COMPLETION_FRAME_MARGIN)
        awaitIdle()

        assertEquals(
            ANIMATION_TARGET_VALUE,
            animatable.value,
            "advancing the clock through the remainder of the animation's duration must let it reach " +
                "its target value",
        )
    }

    @Test
    fun awaitIdleReturnsWithoutSendingAFrameWhenAutoAdvanceIsOffAndNothingDrivesOne() = runComposeSwingTest {
        var framesConsumed = 0
        mainClock.autoAdvance = false
        setContent {
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { }
                    framesConsumed++
                }
            }
        }
        val timeAfterSetContent = mainClock.currentTime

        // Nothing ever calls advanceTimeByFrame/advanceTimeBy here. If awaitIdle chased composed()
        // (the autoAdvance-on gate) instead of the parked-at-a-frame state, a composition permanently
        // parked in withFrameNanos would never read as idle, and each call below would spin toward
        // its frame cap and fail loudly rather than return.
        repeat(3) { awaitIdle() }

        assertEquals(
            0,
            framesConsumed,
            "awaitIdle must not advance an effect parked on a frame while autoAdvance is off and no " +
                "frame was explicitly sent",
        )
        assertEquals(
            timeAfterSetContent,
            mainClock.currentTime,
            "awaitIdle must not send a frame of its own while autoAdvance is off, so currentTime must " +
                "stay exactly where the initial wait left it",
        )
    }

    class FrameAdvance(
        val step: (MainTestClock) -> Duration,
        val advance: (MainTestClock, Duration) -> Unit,
    )

    companion object {
        @JvmStatic
        fun frameAdvances(): List<Named<FrameAdvance>> = listOf(
            Named.of("frame", FrameAdvance({ it.frameDuration }, { clock, _ -> clock.advanceTimeByFrame() })),
            Named.of("rounded", FrameAdvance({ it.frameDuration }, { clock, step -> clock.advanceTimeBy(step) })),
            Named.of(
                "delayed frame",
                FrameAdvance({ 100.milliseconds }, { clock, step -> clock.advanceTimeByFrame(step) }),
            ),
            Named.of(
                "zero-delay frame",
                FrameAdvance({ Duration.ZERO }, { clock, step -> clock.advanceTimeByFrame(step) }),
            ),
        )

        @JvmStatic
        fun oneFrameAdvances(): List<Named<(MainTestClock) -> Unit>> = listOf(
            Named.of("frame", { clock -> clock.advanceTimeByFrame() }),
            Named.of("rounded", { clock -> clock.advanceTimeBy(clock.frameDuration) }),
        )

        @JvmStatic
        fun cadenceAdvances(): List<Arguments> = listOf(
            Arguments.of(Named.of("rounded", false), 1),
            Arguments.of(Named.of("exact", true), 2),
        )

        @JvmStatic
        fun invalidAdvances(): List<Named<(MainTestClock) -> Unit>> =
            listOf((-1).milliseconds, Duration.INFINITE).flatMap { duration ->
                listOf<Named<(MainTestClock) -> Unit>>(
                    Named.of("rounded, $duration", { clock -> clock.advanceTimeBy(duration) }),
                    Named.of(
                        "exact, $duration",
                        { clock -> clock.advanceTimeBy(duration, ignoreFrameDuration = true) },
                    ),
                    Named.of("delayed frame, $duration", { clock -> clock.advanceTimeByFrame(duration) }),
                )
            }

        // More than one, so a case that only advanced a single frame would still fail.
        private const val FRAME_STEPS = 3

        private const val ANIMATION_START_VALUE = 0f
        private const val ANIMATION_TARGET_VALUE = 100f

        // Long enough that two frames land well short of it, leaving a clear mid-flight value to assert on.
        private const val ANIMATION_DURATION_MILLIS = 80

        // More frames than the animation's own duration could possibly need, so this reaches
        // completion regardless of how many frames were already spent driving it mid-flight.
        private const val ANIMATION_COMPLETION_FRAME_MARGIN = 10
    }
}
