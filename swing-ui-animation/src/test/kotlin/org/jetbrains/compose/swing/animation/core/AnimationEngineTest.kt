/*
 * Copyright 2024 The compose-swing-ui authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.swing.animation.core

import androidx.compose.runtime.BroadcastFrameClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/**
 * Headless, deterministic tests for the vendored animation engine. Animations are driven by an
 * explicit [BroadcastFrameClock]: each [pumpFrames] call delivers synthetic, monotonically
 * increasing frame timestamps, so every assertion is instant and reproducible with no wall-clock
 * waiting.
 */
class AnimationEngineTest {
    /**
     * Drive [count] frames spaced [stepNanos] apart, starting at [startNanos], yielding between
     * each.
     */
    private suspend fun BroadcastFrameClock.pumpFrames(
        count: Int,
        stepNanos: Long = 16_000_000L,
        startNanos: Long = 0L,
    ) {
        var t = startNanos
        repeat(count) {
            // Let the animation coroutine register its awaiter before sending the frame.
            yield()
            sendFrame(t)
            yield()
            t += stepNanos
        }
    }

    @Test
    fun animatableReachesTargetThroughFrames() = runTest {
        val clock = BroadcastFrameClock()
        val animatable = Animatable(0f)
        val deferred = async(clock) { animatable.animateTo(100f, tween(durationMillis = 100)) }

        clock.pumpFrames(count = 12, stepNanos = 16_000_000L)
        val result = deferred.await()

        assertEquals(100f, animatable.value, "animateTo should settle on its target")
        assertEquals(AnimationEndReason.Finished, result.endReason)
    }

    @Test
    fun animatableValueIsMonotonicMidFlightForTween() = runTest {
        val clock = BroadcastFrameClock()
        val animatable = Animatable(0f)
        val samples = mutableListOf<Float>()
        val deferred =
            async(clock) {
                animatable.animateTo(100f, tween(durationMillis = 200, easing = LinearEasing)) {
                    samples += value
                }
            }

        clock.pumpFrames(count = 16, stepNanos = 16_000_000L)
        deferred.await()

        // Mid-flight values are strictly inside (0, 100) and never decrease for a linear tween.
        val midFlight = samples.dropLast(1)
        assertTrue(midFlight.isNotEmpty(), "expected at least one mid-flight sample")
        assertTrue(midFlight.all { it in 0f..100f }, "values stayed within the start..target range")
        assertTrue(
            samples.zipWithNext().all { (a, b) -> b >= a },
            "a linear tween value should be monotonically non-decreasing",
        )
        assertEquals(100f, animatable.value)
    }

    @Test
    fun cancellingTheAnimationFreezesTheValue() = runTest {
        val clock = BroadcastFrameClock()
        val animatable = Animatable(0f)
        val job =
            async(clock) {
                animatable.animateTo(100f, tween(durationMillis = 1_000, easing = LinearEasing))
            }

        clock.pumpFrames(count = 3, stepNanos = 16_000_000L)
        val frozen = animatable.value
        job.cancel()
        yield()
        // No further frames are delivered after cancellation, so the value must not advance.
        clock.pumpFrames(count = 5, stepNanos = 16_000_000L)

        assertTrue(frozen > 0f && frozen < 100f, "cancelled mid-flight, expected a partial value")
        assertEquals(frozen, animatable.value, "value should be frozen after cancellation")
    }

    @Test
    fun springSettlesOnTargetAndBouncyOvershoots() = runTest {
        val clock = BroadcastFrameClock()
        val animatable = Animatable(0f)
        val samples = mutableListOf<Float>()
        val deferred =
            async(clock) {
                animatable.animateTo(
                    targetValue = 100f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                ) {
                    samples += value
                }
            }

        clock.pumpFrames(count = 240, stepNanos = 16_000_000L)
        deferred.await()

        assertEquals(100f, animatable.value, "spring should settle on its target")
        assertTrue(
            samples.any { it > 100f },
            "a high-bounce spring should overshoot the target at least once",
        )
    }

    @Test
    fun targetBasedAnimationInterpolatesBetweenStartAndTarget() {
        val animation =
            TargetBasedAnimation(
                animationSpec = tween(durationMillis = 100, easing = LinearEasing),
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 10f,
            )

        assertEquals(0f, animation.getValueFromNanos(0L))
        assertEquals(5f, animation.getValueFromNanos(50_000_000L), 1e-3f)
        assertEquals(10f, animation.getValueFromNanos(100_000_000L))
        assertTrue(animation.isFinishedFromNanos(100_000_000L))
    }
}
