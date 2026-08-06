package org.jetbrains.compose.swing.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.compose.swing.animation.core.Animatable
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Pins [ComposeSwingTest.mainClock] - manual control over the frames a composition is sent.
 *
 * With [MainTestClock.autoAdvance] off, a coroutine parked in `withFrameNanos` and a composition
 * merely holding an unapplied recomposition both have to read as idle to [ComposeSwingTest.awaitIdle]
 * - a settle loop that instead chased either one with a frame of its own would spin or hang the moment
 * autoAdvance is turned off - while the explicit-advance API is what is left to move either forward.
 */
class MainTestClockTest {
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
        // The initial settle runs with autoAdvance on, so its frames come from awaitIdle's own send;
        // an explicit advance must land on the same grid rather than on a second frame length.
        val settled = mainClock.currentTime
        assertEquals(
            0L,
            settled.inWholeNanoseconds % mainClock.frameDuration.inWholeNanoseconds,
            "the frames the harness sends itself must step by the published frameDuration: $settled",
        )

        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()

        assertEquals(settled + mainClock.frameDuration, mainClock.currentTime)
    }

    @Test
    fun advanceTimeByRejectsANegativeDuration() = runComposeSwingTest {
        setContent { Label(text = "content") }
        mainClock.autoAdvance = false
        val start = mainClock.currentTime

        // Either form would otherwise drive the frame counter backwards or silently do nothing.
        val stepped = assertFailsWith<IllegalArgumentException> { mainClock.advanceTimeBy(-mainClock.frameDuration) }
        val single =
            assertFailsWith<IllegalArgumentException> {
                mainClock.advanceTimeBy(-mainClock.frameDuration, ignoreFrameDuration = true)
            }

        for (failure in listOf(stepped, single)) {
            assertTrue(
                failure.message.orEmpty().contains((-mainClock.frameDuration).toString()),
                "the failure should name the duration it rejected: ${failure.message}",
            )
        }
        assertEquals(start, mainClock.currentTime, "a rejected advance must leave composition time where it was")
    }

    @Test
    fun advanceTimeByRejectsAnInfiniteDuration() = runComposeSwingTest {
        setContent { Label(text = "content") }
        mainClock.autoAdvance = false
        val start = mainClock.currentTime

        // Duration.INFINITE has no frame count that reaches it - it would overflow the frame counter
        // (stepped) or hang the EDT computing a single infinite-length frame (ignoreFrameDuration).
        val stepped = assertFailsWith<IllegalArgumentException> { mainClock.advanceTimeBy(Duration.INFINITE) }
        val single =
            assertFailsWith<IllegalArgumentException> {
                mainClock.advanceTimeBy(Duration.INFINITE, ignoreFrameDuration = true)
            }

        for (failure in listOf(stepped, single)) {
            assertTrue(
                failure.message.orEmpty().contains(Duration.INFINITE.toString()),
                "the failure should name the duration it rejected: ${failure.message}",
            )
        }
        assertEquals(start, mainClock.currentTime, "a rejected advance must leave composition time where it was")
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
            "setContent's initial settle must not advance an effect parked on a frame when autoAdvance is off",
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
    fun advanceTimeByFrameAdvancesCurrentTimeByExactlyOneFrameDuration() = runComposeSwingTest {
        setContent { Label(text = "content") }
        mainClock.autoAdvance = false
        val start = mainClock.currentTime

        mainClock.advanceTimeByFrame()

        assertEquals(start + mainClock.frameDuration, mainClock.currentTime)
    }

    @Test
    fun advanceTimeByStepsThroughFrameDurationIncrements() = runComposeSwingTest {
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

        mainClock.advanceTimeBy(mainClock.frameDuration * FRAME_STEPS)
        awaitIdle()

        assertEquals(FRAME_STEPS, framesConsumed, "advanceTimeBy must send one frame per frameDuration increment")
    }

    @Test
    fun advanceTimeByWithIgnoreFrameDurationSendsExactlyOneFrame() = runComposeSwingTest {
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

        mainClock.advanceTimeBy(mainClock.frameDuration * FRAME_STEPS, ignoreFrameDuration = true)
        awaitIdle()

        assertEquals(
            1,
            framesConsumed,
            "ignoreFrameDuration must deliver the whole duration in a single frame, not one frame per step",
        )
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
            assertFailsWith<AssertionError> {
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
                "stay exactly where the initial settle left it",
        )
    }

    private companion object {
        // More than one, so a case that only advanced a single frame would still fail.
        const val FRAME_STEPS = 3

        const val ANIMATION_START_VALUE = 0f
        const val ANIMATION_TARGET_VALUE = 100f

        // Long enough that two frames land well short of it, leaving a clear mid-flight value to assert on.
        const val ANIMATION_DURATION_MILLIS = 80

        // More frames than the animation's own duration could possibly need, so this reaches
        // completion regardless of how many frames were already spent driving it mid-flight.
        const val ANIMATION_COMPLETION_FRAME_MARGIN = 10
    }
}
