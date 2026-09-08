/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.jetbrains.compose.swing.test.runComposeSwingTest

class InfiniteTransitionTest {

    @Test
    fun transitionTest() = runComposeSwingTest {
        // Manually advance the clock to prevent the infinite transition from being cancelled
        mainClock.autoAdvance = false

        val colorAnim = TargetBasedAnimation(tween(1000), Float.VectorConverter, 0f, 1f)

        // Animate from 0f to 0f for 1000ms
        val keyframes =
            keyframes<Float> {
                durationMillis = 1000
                0f at 0
                200f at 400
                1000f at 1000
            }

        val keyframesAnim = TargetBasedAnimation(keyframes, Float.VectorConverter, 0f, 0f)

        val runAnimation = mutableStateOf(true)
        setContent {
            val transition = rememberInfiniteTransition()
            if (runAnimation.value) {
                val animFloat =
                    transition.animateFloat(
                        0f,
                        0f,
                        infiniteRepeatable(keyframes, repeatMode = RepeatMode.Reverse),
                    )

                val animColor = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000)))

                LaunchedEffect(Unit) {
                    val startTime = withFrameNanos { it }
                    var playTime = 0L
                    while (playTime < 2100L) {
                        playTime = withFrameNanos { it } - startTime
                        var iterationTime = playTime % (2000 * MillisToNanos)
                        if (iterationTime > 1000 * MillisToNanos) {
                            iterationTime = 2000L * MillisToNanos - iterationTime
                        }
                        val expectedFloat = keyframesAnim.getValueFromNanos(iterationTime)
                        val expectedColor =
                            colorAnim.getValueFromNanos(playTime % (1000 * MillisToNanos))
                        assertEquals(expectedFloat, animFloat.value, 0.01f)
                        assertEquals(expectedColor, animColor.value)
                    }
                    runAnimation.value = false
                }
            }
        }
        // Manually advance the clock
        while (runAnimation.value) {
            advanceTimeByFrame()
            awaitIdle()
        }
        assertFalse(runAnimation.value)
    }
}
