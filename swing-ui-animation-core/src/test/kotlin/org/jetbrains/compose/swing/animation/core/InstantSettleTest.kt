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
 * Degenerate-spec behavior: zero-duration tween/keyframes and an already-settled spring must reach
 * their target on the very first frame instead of animating over time. These are the boundary cases
 * a UI hits when it wires an animation up but configures it to be effectively instant.
 */
class InstantSettleTest {
    @Test
    fun zeroDurationTweenIsAtTargetOnTheFirstFrame() {
        val animation =
            TargetBasedAnimation(
                animationSpec = tween(durationMillis = 0),
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 100f,
            )

        assertEquals(0L, animation.durationNanos, "a zero-duration tween has no runtime")
        assertEquals(
            100f,
            animation.getValueFromNanos(0L),
            "value is the target on the first frame",
        )
        assertTrue(
            animation.isFinishedFromNanos(0L),
            "a zero-duration tween is finished immediately",
        )
    }

    @Test
    fun zeroDurationTweenSettlesAnimatableImmediately() = runTest {
        val clock = BroadcastFrameClock()
        val animatable = Animatable(0f)
        val deferred = async(clock) { animatable.animateTo(100f, tween(durationMillis = 0)) }

        // A single frame is enough for a zero-duration spec to settle on its target.
        yield()
        clock.sendFrame(0L)
        val result = deferred.await()

        assertEquals(100f, animatable.value, "animateTo lands on the target on the first frame")
        assertEquals(AnimationEndReason.Finished, result.endReason)
    }

    @Test
    fun alreadySettledSpringIsAtTargetOnTheFirstFrame() {
        // Start already at the target with no velocity: a spring has nothing to do and settles at
        // once.
        val animation =
            TargetBasedAnimation(
                animationSpec = spring<Float>(),
                typeConverter = Float.VectorConverter,
                initialValue = 100f,
                targetValue = 100f,
            )

        assertEquals(
            0L,
            animation.durationNanos,
            "a spring with zero displacement settles instantly",
        )
        assertEquals(
            100f,
            animation.getValueFromNanos(0L),
            "value is the target on the first frame",
        )
        assertTrue(
            animation.isFinishedFromNanos(0L),
            "an already-settled spring is finished immediately",
        )
    }

    @Test
    fun zeroDurationKeyframesIsAtTargetOnTheFirstFrame() {
        val animation =
            TargetBasedAnimation(
                animationSpec =
                    keyframes {
                        durationMillis = 0
                        100f at 0
                    },
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 100f,
            )

        assertEquals(0L, animation.durationNanos, "a zero-duration keyframes spec has no runtime")
        assertEquals(
            100f,
            animation.getValueFromNanos(0L),
            "value is the target on the first frame",
        )
        assertTrue(animation.isFinishedFromNanos(0L), "a zero-duration keyframes spec is finished")
    }
}
