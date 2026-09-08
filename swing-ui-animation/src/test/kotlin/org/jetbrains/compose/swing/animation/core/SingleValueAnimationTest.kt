/*
 * Copyright 2020 The Android Open Source Project
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.swing.test.runComposeSwingTest

class SingleValueAnimationTest {

    @Test
    fun animate1DTest() = runComposeSwingTest {
        fun <T> myTween(): TweenSpec<T> =
            TweenSpec(easing = FastOutSlowInEasing, durationMillis = 100)

        var enabled by mutableStateOf(false)
        var expected by mutableStateOf(250)
        mainClock.autoAdvance = false
        setContent {
            val animationValue by animateIntAsState(if (enabled) 50 else 250, myTween())
            if (enabled) {
                LaunchedEffect(Unit) {
                    val startTime = withFrameNanos { it }
                    var frameTime = startTime
                    do {
                        withFrameNanos {
                            frameTime = it
                            val playTime = ((frameTime - startTime) / 1_000_000L).coerceIn(0, 100)
                            val fraction = FastOutSlowInEasing.transform(playTime / 100f)
                            expected = lerp(250f, 50f, fraction).toInt()
                        }
                    } while (frameTime - startTime <= 100_000_000L)
                    // Animation is finished at this point
                    expected = 50
                }
                assertEquals(expected, animationValue)
            } else {
                assertEquals(250, animationValue)
            }
        }
        assertEquals(250, expected)
        awaitIdle()
        enabled = true
        advanceTimeBy(100 + 2 * UpstreamFrameMillis)
        awaitIdle()
        assertEquals(50, expected)
    }

    @Test
    fun animate1DOnCoroutineTest() = runComposeSwingTest {
        var enabled by mutableStateOf(false)
        var expected by mutableStateOf(250f)
        mainClock.autoAdvance = false
        setContent {
            // Animate from 250f to 50f when enable flips to true
            val animationValue by
                animateFloatAsState(
                    if (enabled) 50f else 250f,
                    tween(200, easing = FastOutLinearInEasing),
                )
            if (enabled) {
                LaunchedEffect(Unit) {
                    assertEquals(250f, animationValue)
                    val startTime = withFrameNanos { it }
                    var frameTime = startTime
                    do {
                        withFrameNanos {
                            frameTime = it
                            val playTime = ((frameTime - startTime) / 1_000_000L).coerceIn(0, 200)
                            val fraction = FastOutLinearInEasing.transform(playTime / 200f)
                            expected = lerp(250f, 50f, fraction)
                        }
                    } while (frameTime - startTime <= 200_000_000L)
                    expected = 50f
                }
            }
            assertEquals(expected, animationValue)
        }
        awaitIdle()
        enabled = true
        advanceTimeBy(200 + 2 * UpstreamFrameMillis)
        awaitIdle()
        // Animation is finished at this point
        assertEquals(50f, expected)
    }

    @Test
    fun animate2DTest() = runComposeSwingTest {
        val startVal = AnimationVector(120f, 56f)
        val endVal = AnimationVector(0f, 77f)
        var expected by mutableStateOf(startVal)

        fun <V> tween(): TweenSpec<V> = TweenSpec(easing = LinearEasing, durationMillis = 100)

        var enabled by mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent {
            val sizeValue by
                animateValueAsState(
                    if (enabled) Point2DVectorConverter.convertFromVector(endVal)
                    else Point2DVectorConverter.convertFromVector(startVal),
                    Point2DVectorConverter,
                    tween(),
                )

            val pxPositionValue by
                animateValueAsState(
                    if (enabled) Point2DVectorConverter.convertFromVector(endVal)
                    else Point2DVectorConverter.convertFromVector(startVal),
                    Point2DVectorConverter,
                    tween(),
                )

            if (enabled) {
                LaunchedEffect(Unit) {
                    val startTime = withFrameNanos { it }
                    var frameTime = startTime
                    do {
                        withFrameNanos {
                            frameTime = it
                            val playTime = ((frameTime - startTime) / 1_000_000L).coerceIn(0, 100)
                            expected =
                                AnimationVector(
                                    lerp(startVal.v1, endVal.v1, playTime / 100f),
                                    lerp(startVal.v2, endVal.v2, playTime / 100f),
                                )
                        }
                    } while (frameTime - startTime <= 100_000_000L)
                    expected = endVal
                }
            }

            assertEquals(Point2DVectorConverter.convertFromVector(expected), sizeValue)
            assertEquals(Point2DVectorConverter.convertFromVector(expected), pxPositionValue)
        }

        awaitIdle()
        enabled = true
        advanceTimeBy(100 + 2 * UpstreamFrameMillis)
        awaitIdle()
        assertEquals(endVal, expected)
    }

    @Test
    fun frameByFrameInterruptionTest() = runComposeSwingTest {
        var enabled by mutableStateOf(false)
        var currentValue by mutableStateOf(Point2D(-300f, -300f))
        setContent {
            var destination: Point2D by remember { mutableStateOf(Point2D(600f, 600f)) }
            val offsetValue =
                animateValueAsState(
                    if (enabled) destination else Point2D(0f, 0f),
                    Point2DVectorConverter,
                )
            if (enabled) {
                LaunchedEffect(enabled) {
                    var startTime = -1L
                    while (true) {
                        val current = withFrameMillis {
                            if (startTime < 0) startTime = it
                            // Fuzzy test by fine adjusting the target on every frame, and
                            // verify there's a reasonable amount of test. This is to make sure
                            // the animation does not stay "frozen" when there's continuous
                            // target changes.
                            if (destination.x >= 600) {
                                destination = Point2D(599f, 599f)
                            } else {
                                destination = Point2D(601f, 601f)
                            }
                            it
                        }
                        currentValue = offsetValue.value
                        if (current - startTime > 1000) {
                            break
                        }
                    }
                }
            }
        }
        awaitIdle()
        enabled = true
        assertEquals(Point2D(-300f, -300f), currentValue)
        waitUntil(1300.milliseconds) { currentValue.x > 300f && currentValue.y > 300f }
    }

    @Test
    fun visibilityThresholdTest() = runComposeSwingTest {
        val specForFloat = FloatSpringSpec(visibilityThreshold = 0.01f)
        val specForOffset = FloatSpringSpec(visibilityThreshold = 0.5f)

        val animationSpecForOffset = spring<Point2D>(visibilityThreshold = Point2D(0.5f, 0.5f))

        var expectedFloat by mutableStateOf(0f)
        var expectedOffset by mutableStateOf(Point2D(0f, 0f))
        var enabled by mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent {
            val offsetValue by
                animateValueAsState(
                    if (enabled) Point2D(100f, 100f) else Point2D(0f, 0f),
                    Point2DVectorConverter,
                    animationSpecForOffset,
                )

            val floatValue by animateFloatAsState(if (enabled) 100f else 0f, specForFloat)

            val durationForFloat = specForFloat.getDurationNanos(0f, 100f, 0f)
            val durationForOffset = specForOffset.getDurationNanos(0f, 100f, 0f)

            if (enabled) {
                LaunchedEffect(Unit) {
                    val startTime = withFrameNanos { it }
                    var frameTime = startTime
                    do {
                        withFrameNanos {
                            frameTime = it
                            val playTime = frameTime - startTime
                            expectedFloat =
                                if (playTime < durationForFloat) {
                                    specForFloat.getValueFromNanos(playTime, 0f, 100f, 0f)
                                } else {
                                    100f
                                }

                            expectedOffset =
                                if (playTime < durationForOffset) {
                                    val offset =
                                        specForOffset.getValueFromNanos(playTime, 0f, 100f, 0f)
                                    Point2D(offset, offset)
                                } else {
                                    Point2D(100f, 100f)
                                }
                        }
                    } while (frameTime - startTime <= durationForFloat)
                    expectedFloat = 100f
                }
            }

            assertEquals(expectedOffset, offsetValue)
            assertEquals(expectedFloat, floatValue)
        }

        awaitIdle()
        enabled = true
        advanceTimeBy(
            specForFloat.getDurationMillis(0f, 100f, 0f).toInt() + 2 * UpstreamFrameMillis
        )
        awaitIdle()
    }

    @Test
    fun defaultVisibilityThresholdTest() = runComposeSwingTest {
        // The specs the defaulted animations below are expected to run: a spring carrying the
        // threshold each type's converter is registered with.
        val floatThreshold = VisibilityThresholdMap.getValue(Float.VectorConverter)
        val intThreshold = Int.VisibilityThreshold.toFloat()
        val specForFloat = FloatSpringSpec(visibilityThreshold = floatThreshold)
        val specForInt = FloatSpringSpec(visibilityThreshold = intThreshold)

        var expectedFloat by mutableStateOf(0f)
        var expectedInt by mutableStateOf(0)
        var enabled by mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent {
            val intValue by animateIntAsState(if (enabled) 100 else 0)
            val floatValue by animateFloatAsState(if (enabled) 100f else 0f)

            val durationForFloat = specForFloat.getDurationNanos(0f, 100f, 0f)
            val durationForInt = specForInt.getDurationNanos(0f, 100f, 0f)

            if (enabled) {
                LaunchedEffect(Unit) {
                    val startTime = withFrameNanos { it }
                    var frameTime = startTime
                    do {
                        withFrameNanos {
                            frameTime = it
                            val playTime = frameTime - startTime
                            expectedFloat =
                                if (playTime < durationForFloat) {
                                    specForFloat.getValueFromNanos(playTime, 0f, 100f, 0f)
                                } else {
                                    100f
                                }

                            expectedInt =
                                if (playTime < durationForInt) {
                                    specForInt.getValueFromNanos(playTime, 0f, 100f, 0f).toInt()
                                } else {
                                    100
                                }
                        }
                    } while (frameTime - startTime <= durationForFloat)
                    expectedFloat = 100f
                    expectedInt = 100
                }
            }

            // The expected values and actual values should have a delta no larger than
            // the visibility threshold
            assertEquals(expectedFloat, floatValue, floatThreshold)
            assertEquals(expectedInt.toFloat(), intValue.toFloat(), intThreshold)
        }

        awaitIdle()
        enabled = true
        advanceTimeBy(
            specForFloat.getDurationMillis(0f, 100f, 0f).toInt() + 2 * UpstreamFrameMillis
        )
        awaitIdle()
        assertEquals(100f, expectedFloat)
        assertEquals(100, expectedInt)
    }

    @Test
    fun customSpringSpecVisibilityThresholdTest() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val threshold = 0.1f
        // Use a very low stiffness to make the animation slow and easy to track
        val customSpec =
            spring<Float>(stiffness = Spring.StiffnessVeryLow, visibilityThreshold = threshold)
        var enabled by mutableStateOf(false)
        var latestValue = 0f
        var isFinished = false
        setContent {
            val floatValue by
                animateFloatAsState(
                    if (enabled) 1f else 0f,
                    customSpec,
                    finishedListener = { isFinished = true },
                )
            latestValue = floatValue
        }

        awaitIdle()
        enabled = true

        // Advance the clock frame by frame and record values
        val values = mutableListOf<Float>()
        while (!isFinished) {
            values.add(latestValue)
            advanceTimeByFrame()
            awaitIdle()
        }
        values.add(latestValue)

        assertTrue(isFinished, "Animation should be finished")
        // Ensure it reached 1f
        assertEquals(1f, values.last())

        // Target value is 1f. Threshold is 0.1f.
        // Once the value is within threshold of target (i.e. > 0.9f), it should snap to 1f.
        // So no value should be in the range (0.9, 1.0)
        for (v in values) {
            if (v != 1f) {
                assertTrue(
                    v <= 1f - threshold,
                    "Value $v should not be in the range (0.9, 1.0) given threshold $threshold",
                )
            }
        }

        // Ensure we actually animated and didn't just snap immediately from 0 to 1
        assertTrue(values.size > 2, "Should have multiple values")
    }

    @Test
    fun customSpringSpecLargeVisibilityThresholdTest() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val threshold = 5f
        val startValue = 200f
        val targetValue = 1000f
        // Use a very low stiffness to make the animation slow and easy to track
        val customSpec =
            spring<Float>(stiffness = Spring.StiffnessVeryLow, visibilityThreshold = threshold)
        var target by mutableStateOf(startValue)
        var latestValue = startValue
        var isFinished = false
        setContent {
            val floatValue by
                animateFloatAsState(target, customSpec, finishedListener = { isFinished = true })
            latestValue = floatValue
        }

        awaitIdle()
        target = targetValue

        // Advance the clock frame by frame and record values
        val values = mutableListOf<Float>()
        while (!isFinished) {
            values.add(latestValue)
            advanceTimeByFrame()
            awaitIdle()
            // Safety break to avoid infinite loop
            if (values.size > 2000) break
        }
        values.add(latestValue)

        assertTrue(isFinished, "Animation should be finished")
        // Ensure it reached 1000f
        assertEquals(targetValue, values.last())

        // Target value is 1000f. Threshold is 5f.
        // Once the value is within threshold of target (i.e. > 995f), it should snap to 1000f.
        // So no value should be in the range (995, 1000)
        for (v in values) {
            if (v != targetValue) {
                assertTrue(
                    v <= targetValue - threshold,
                    "Error: Value = $v, when values in the range (995, 1000) should be snapped to 1000 given threshold $threshold",
                )
            }
        }

        // Ensure we actually animated
        assertTrue(values.size > 2, "Should have multiple values")
    }

    @Test
    fun updateAnimationSpecTest() = runComposeSwingTest {
        var duration by mutableStateOf(100)
        var firstRun by mutableStateOf(true)
        fun <T> myTween(): TweenSpec<T> =
            TweenSpec(easing = FastOutSlowInEasing, durationMillis = duration)

        var enabled by mutableStateOf(false)
        var expected by mutableStateOf(250f)
        mainClock.autoAdvance = false
        setContent {
            val animationValue by animateFloatAsState(if (enabled) 50f else 250f, myTween())
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
                        }
                    } while (frameTime - startTime <= duration * 1_000_000L)
                    expected = if (enabled) 50f else 250f
                }
            }
        }
        awaitIdle()
        enabled = true
        firstRun = false
        advanceTimeBy(duration + 2 * UpstreamFrameMillis)
        awaitIdle()
        // Animation is finished at this point
        assertEquals(50f, expected)

        awaitIdle()
        enabled = false
        duration = 200
        advanceTimeBy(duration + 2 * UpstreamFrameMillis)
        awaitIdle()
        // Animation is finished at this point
        assertEquals(250f, expected)
    }
}
