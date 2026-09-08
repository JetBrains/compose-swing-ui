/*
 * Copyright 2026 The Android Open Source Project
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.swing.test.runComposeSwingTest

class UpdateVisibilityThresholdTest {

    @Test
    fun updateVisibilityThresholdTest() = runComposeSwingTest {
        var duration by mutableStateOf(100)
        var firstRun by mutableStateOf(true)
        var visibilityThreshold by mutableStateOf(0f)
        var enabled by mutableStateOf(false)
        var expected by mutableStateOf(250f)

        var finished = false
        var midpoint = false

        mainClock.autoAdvance = false
        setContent {
            val animationValue by
                animateValueAsState(
                    if (enabled) 50f else 250f,
                    Float.VectorConverter,
                    visibilityThreshold = visibilityThreshold,
                    animationSpec = TweenSpec(duration, easing = FastOutSlowInEasing),
                    finishedListener = { finished = true },
                )
            assertEquals(expected, animationValue)
            if (!firstRun) {
                LaunchedEffect(enabled) {
                    if (enabled) {
                        assertEquals(100, duration)
                    } else {
                        assertEquals(200, duration)
                    }
                    val startTime = withFrameNanos { it }
                    var frameTime = startTime
                    do {
                        withFrameNanos {
                            frameTime = it
                            val playTime =
                                ((frameTime - startTime) / 1_000_000L).coerceIn(
                                    0,
                                    duration.toLong(),
                                )
                            val fraction =
                                FastOutSlowInEasing.transform(playTime / duration.toFloat())
                            expected =
                                if (enabled) {
                                    lerp(250f, 50f, fraction)
                                } else {
                                    lerp(50f, 250f, fraction)
                                }
                            if (fraction > .5f) {
                                midpoint = true
                            }
                        }
                    } while (frameTime - startTime <= duration * 1_000_000L)
                    expected = if (enabled) 50f else 250f
                }
            }
        }
        finished = false
        enabled = true
        firstRun = false
        while (!midpoint) advanceTimeByFrame()
        visibilityThreshold = 10f
        while (!finished) advanceTimeByFrame()
        // Animation is finished at this point
        assertEquals(50f, expected)
    }
}
