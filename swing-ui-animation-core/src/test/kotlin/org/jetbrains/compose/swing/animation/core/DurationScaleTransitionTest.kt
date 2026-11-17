/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.core.MotionDurationScale
import org.jetbrains.compose.swing.test.runComposeSwingTest

class DurationScaleTransitionTest {
    @Test
    fun childTransitionWithDurationScale() {
        val motionDurationScale =
            object : MotionDurationScale {
                override val scaleFactor: Float
                    get() = 4f
            }
        runComposeSwingTest(effectContext = motionDurationScale) {
            mainClock.autoAdvance = false
            val state = MutableTransitionState(0)
            var value1 = -1f
            var value2 = -1f
            var withChild by mutableStateOf(false)
            setContent {
                val transition = rememberTransition(transitionState = state)
                val animatedValue1 by
                    transition.animateFloat({ tween(160, easing = LinearEasing) }) {
                        if (it == 0) 0f else 1000f
                    }
                value1 = animatedValue1
                if (withChild) {
                    val child = transition.createChildTransition { it }
                    val animatedValue2 by
                        child.animateFloat({ tween(160, easing = LinearEasing) }) {
                            if (it == 0) 0f else 1000f
                        }
                    value2 = animatedValue2
                }
            }
            advanceTimeByFrame() // let everything settle
            state.targetState = 1
            advanceTimeByFrame() // recompose
            advanceTimeByFrame() // lock in animation clock
            assertEquals(0f, value1)
            assertEquals(-1f, value2) // not set until withChild = true

            advanceTimeBy(320) // half way through transition
            assertEquals(500f, value1, 0.1f)
            assertEquals(-1f, value2) // not set until withChild = true

            withChild = true
            advanceTimeByFrame() // recomposed after withChild changed
            assertEquals(0f, value2)

            // Now the transition will progress from here with value2 taking 1000ms more
            advanceTimeBy(320)
            assertEquals(1000f, value1)
            assertEquals(500f, value2, 0.1f)
            advanceTimeBy(320)
            assertEquals(1000f, value2)
        }
    }

    @Test
    fun childTransitionWithDurationScaleSeekableTransition() {
        val motionDurationScale =
            object : MotionDurationScale {
                override val scaleFactor: Float
                    get() = 4f
            }
        runComposeSwingTest(effectContext = motionDurationScale) {
            mainClock.autoAdvance = false
            val state = SeekableTransitionState(0)
            var value1 = -1f
            var value2 = -1f
            var withChild by mutableStateOf(false)
            lateinit var coroutineScope: CoroutineScope
            setContent {
                coroutineScope = rememberCoroutineScope()
                val transition = rememberTransition(transitionState = state)
                val animatedValue1 by
                    transition.animateFloat({ tween(160, easing = LinearEasing) }) {
                        if (it == 0) 0f else 1000f
                    }
                value1 = animatedValue1
                if (withChild) {
                    val child = transition.createChildTransition { it }
                    val animatedValue2 by
                        child.animateFloat({ tween(160, easing = LinearEasing) }) {
                            if (it == 0) 0f else 1000f
                        }
                    value2 = animatedValue2
                }
            }
            advanceTimeByFrame() // let everything settle
            val seekTo = coroutineScope.async { state.seekTo(fraction = 0f, targetState = 1) }
            advanceTimeByFrame() // recompose
            assertTrue(seekTo.isCompleted)

            assertEquals(0f, value1)
            assertEquals(-1f, value2) // not set until withChild = true

            coroutineScope.launch { state.animateTo(targetState = 1) }
            advanceTimeByFrame() // lock in the animation clock
            advanceTimeBy(320) // half way through transition
            assertEquals(500f, value1, 0.1f)
            assertEquals(-1f, value2) // not set until withChild = true

            withChild = true
            advanceTimeByFrame() // recomposed after withChild changed
            assertEquals(0f, value2)

            // Now the transition will progress from here with value2 taking 1000ms more
            advanceTimeBy(320)
            assertEquals(1000f, value1)
            assertEquals(500f, value2, 0.1f)
            advanceTimeBy(320)
            assertEquals(1000f, value2)
        }
    }

    @Test
    fun childTransitionWithDurationScaleSeekTransition() {
        val motionDurationScale =
            object : MotionDurationScale {
                override val scaleFactor: Float
                    get() = 4f
            }
        runComposeSwingTest(effectContext = motionDurationScale) {
            mainClock.autoAdvance = false
            val state = SeekableTransitionState(0)
            var value1 = -1f
            var value2 = -1f
            var withChild by mutableStateOf(false)
            lateinit var coroutineScope: CoroutineScope
            setContent {
                coroutineScope = rememberCoroutineScope()
                val transition = rememberTransition(transitionState = state)
                val animatedValue1 by
                    transition.animateFloat({ tween(160, easing = LinearEasing) }) {
                        if (it == 0) 0f else 1000f
                    }
                value1 = animatedValue1
                if (withChild) {
                    val child = transition.createChildTransition { it }
                    val animatedValue2 by
                        child.animateFloat({ tween(160, easing = LinearEasing) }) {
                            if (it == 0) 0f else 1000f
                        }
                    value2 = animatedValue2
                }
            }
            advanceTimeByFrame() // let everything settle
            val seekTo = coroutineScope.async { state.seekTo(fraction = 0.5f, targetState = 1) }
            advanceTimeByFrame() // recompose
            assertTrue(seekTo.isCompleted)

            assertEquals(500f, value1)
            assertEquals(-1f, value2) // not set until withChild = true

            withChild = true
            advanceTimeByFrame() // recomposed after withChild changed
            advanceTimeByFrame() // allow seekToFrame() to run after total duration change

            // Now we're 50% of the way to 1500ms = 750ms
            assertEquals(750f, value1, 0.1f)
            assertEquals(250f, value2, 0.1f)

            coroutineScope.launch {
                state.seekTo(fraction = 0.75f) // 1125ms
            }
            advanceTimeByFrame()

            assertEquals(1000f, value1)
            assertEquals(625f, value2, 0.1f)

            coroutineScope.launch { state.seekTo(fraction = 1f) }
            advanceTimeByFrame()

            assertEquals(1000f, value1)
            assertEquals(1000f, value2)
        }
    }
}
