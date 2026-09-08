/*
 * Copyright 2023 The Android Open Source Project
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

import org.jetbrains.compose.swing.foundation.layout.Box

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import java.awt.Color
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.animatedContainer
import org.jetbrains.compose.swing.animation.paintedAlpha
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.togetherWith
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest

// Seeking an engine transition, and what the Swing containers do while it is seeked: several cases
// here drive AnimatedVisibility, AnimatedContent and the panel they build on, so they pin container
// behavior as much as the engine's.
class SeekableTransitionStateTest {

    private enum class AnimStates {
        From,
        To,
        Other,
    }

    @Test
    fun seekFraction() = runComposeSwingTest {
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue by mutableIntStateOf(-1)

        setContent {
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = { tween(easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
        }
        awaitIdle()
        assertEquals(0, animatedValue)
        seekableTransitionState.seekTo(fraction = 0.5f)
        assertEquals(0.5f, seekableTransitionState.fraction)
        awaitIdle()
        assertEquals(500, animatedValue)
        seekableTransitionState.seekTo(fraction = 1f)
        assertEquals(1f, seekableTransitionState.fraction)
        awaitIdle()
        assertEquals(1000, animatedValue)
        seekableTransitionState.seekTo(fraction = 0.5f)
        assertEquals(0.5f, seekableTransitionState.fraction)
        awaitIdle()
        assertEquals(500, animatedValue)
        seekableTransitionState.seekTo(fraction = 0f)
        assertEquals(0f, seekableTransitionState.fraction)
        awaitIdle()
        assertEquals(0, animatedValue)
    }

    @Test
    fun animateToTarget() = runComposeSwingTest {
        var animatedValue by mutableIntStateOf(-1)
        var duration by mutableLongStateOf(0)
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope

        mainClock.autoAdvance = false

        setContent {
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.To)
            }
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = { tween(easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
            duration = transition.totalDurationNanos
        }

        advanceTimeByFrame() // wait for composition after seekTo()
        val deferred1 = coroutineScope.async { seekableTransitionState.animateTo() }
        advanceTimeByFrame() // one frame to set the start time
        advanceTimeByFrame()

        var progressFraction = 0f
        awaitIdle()
        assertTrue(seekableTransitionState.fraction > 0f)
        progressFraction = seekableTransitionState.fraction

        advanceTimeByFrame()
        awaitIdle()
        assertTrue(seekableTransitionState.fraction > progressFraction)
        progressFraction = seekableTransitionState.fraction

        // interrupt the progress

        seekableTransitionState.seekTo(fraction = 0.5f)
        advanceTimeByFrame()

        awaitIdle()
        assertTrue(deferred1.isCancelled)
        // We've stopped animating after seeking
        assertEquals(0.5f, seekableTransitionState.fraction)
        assertEquals(500, animatedValue)

        // continue from the same place
        val deferred2 = coroutineScope.async { seekableTransitionState.animateTo() }
        awaitIdle() // wait for coroutine to run
        advanceTimeByFrame() // one frame to set the start time
        advanceTimeByFrame()

        awaitIdle()
        // We've stopped animating after seeking
        assertTrue(seekableTransitionState.fraction > 0.5f)
        assertTrue(seekableTransitionState.fraction < 1f)

        advanceTimeBy(5000)

        awaitIdle()
        assertTrue(deferred2.isCompleted)
        assertEquals(0f, seekableTransitionState.fraction, 0f)
        assertEquals(1000, animatedValue)
    }

    @Test
    fun updatedTransition() = runComposeSwingTest {
        var animatedValue by mutableIntStateOf(-1)
        var duration = -1L
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)

        setContent {
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = { tween(durationMillis = 200, easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
            transition.AnimatedContent(
                transitionSpec = {
                    fadeIn(tween(durationMillis = 1000, easing = LinearEasing)) togetherWith
                        fadeOut(tween(durationMillis = 1000, easing = LinearEasing))
                }
            ) { state ->
                if (state == AnimStates.To) {
                    Label(text = "content")
                }
            }
            duration = transition.totalDurationNanos
        }

        awaitIdle()
        assertEquals(1000_000_000L, duration)
        assertEquals(0f, seekableTransitionState.fraction, 0f)

        // Go to the middle
        seekableTransitionState.seekTo(fraction = 0.5f)

        awaitIdle()
        assertEquals(1000, animatedValue)
        assertEquals(0.5f, seekableTransitionState.fraction)

        // Go to the end
        seekableTransitionState.seekTo(fraction = 1f)

        awaitIdle()
        assertEquals(1000, animatedValue)
        assertEquals(1f, seekableTransitionState.fraction)

        // Go back to part way through the animatedValue
        seekableTransitionState.seekTo(fraction = 0.1f)

        awaitIdle()
        assertEquals(500, animatedValue)
        assertEquals(0.1f, seekableTransitionState.fraction)
    }

    /** The [updatedTransition] shape driven by a child transition rather than a container. */
    @Test
    fun updatedTransitionWithChildTransition() = runComposeSwingTest {
        var animatedValue by mutableIntStateOf(-1)
        var duration = -1L
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)

        setContent {
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = { tween(durationMillis = 200, easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
            transition
                .createChildTransition { it == AnimStates.To }
                .animateFloat(
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) }
                ) { state ->
                    if (state) 1f else 0f
                }
            duration = transition.totalDurationNanos
        }

        awaitIdle()
        assertEquals(1000_000_000L, duration)
        assertEquals(0f, seekableTransitionState.fraction, 0f)

        // Go to the middle
        seekableTransitionState.seekTo(fraction = 0.5f)

        awaitIdle()
        assertEquals(1000, animatedValue)
        assertEquals(0.5f, seekableTransitionState.fraction)

        // Go to the end
        seekableTransitionState.seekTo(fraction = 1f)

        awaitIdle()
        assertEquals(1000, animatedValue)
        assertEquals(1f, seekableTransitionState.fraction)

        // Go back to part way through the animatedValue
        seekableTransitionState.seekTo(fraction = 0.1f)

        awaitIdle()
        assertEquals(500, animatedValue)
        assertEquals(0.1f, seekableTransitionState.fraction)
    }

    @Test
    fun repeatAnimate() = runComposeSwingTest {
        var animatedValue by mutableIntStateOf(-1)
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope

        mainClock.autoAdvance = false

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = { tween(easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
        }

        val deferred1 = coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeByFrame() // one frame to set the start time
        advanceTimeByFrame()

        // Running the same animation again should cancel the existing one
        val deferred2 = coroutineScope.async { seekableTransitionState.animateTo() }

        awaitIdle() // wait for coroutine to run
        advanceTimeByFrame()

        assertTrue(deferred1.isCancelled)
        assertFalse(deferred2.isCancelled)

        // seeking should cancel the animation
        val deferred3 = coroutineScope.async { seekableTransitionState.seekTo(fraction = 0.25f) }

        awaitIdle() // wait for coroutine to run
        advanceTimeByFrame()

        assertTrue(deferred2.isCancelled)
        assertFalse(deferred3.isCancelled)
        assertTrue(deferred3.isCompleted)

        // start the animation again
        val deferred4 = coroutineScope.async { seekableTransitionState.animateTo() }

        awaitIdle() // wait for coroutine to run
        advanceTimeByFrame()

        assertFalse(deferred4.isCancelled)
    }

    @Test
    fun segmentInitialized() = runComposeSwingTest {
        var animatedValue by mutableIntStateOf(-1)
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var segment: Transition.Segment<AnimStates>

        setContent {
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = {
                            if (initialState == targetState) {
                                snap()
                            } else {
                                tween(easing = LinearEasing)
                            }
                        },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
            segment = transition.segment
        }

        awaitIdle()
        assertEquals(AnimStates.From, segment.initialState)
        assertEquals(AnimStates.To, segment.targetState)
    }

    // In the middle of seeking from From to To, seek to Other
    @Test
    fun seekThirdState() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        var animatedValue3 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            val val2 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.Other -> 1000
                        else -> 0
                    }
                }
            val val3 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.To -> 1000
                        AnimStates.Other -> 2000
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
            animatedValue3 = val3.value
        }
        advanceTimeByFrame() // let seekTo() run
        awaitIdle()
        // Check initial values
        assertEquals(0, animatedValue1)
        assertEquals(0, animatedValue2)
        assertEquals(0, animatedValue3)
        // Seek half way
        seekableTransitionState.seekTo(fraction = 0.5f)
        assertEquals(0.5f, seekableTransitionState.fraction)
        advanceTimeByFrame()
        awaitIdle()
        // Check half way values
        assertEquals(500, animatedValue1)
        assertEquals(0, animatedValue2)
        assertEquals(500, animatedValue3)
        // Start seek to new state. It won't complete until the initial state is
        // animated to "To"
        val seekTo =
            coroutineScope.async {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.Other)
            }
        advanceTimeByFrame() // must recompose to Other
        awaitIdle()
        assertEquals(AnimStates.Other, seekableTransitionState.targetState)
        // First frame, nothing has changed. We've only gathered the first frame of the
        // animation since it was not previously animating
        assertEquals(500, animatedValue1)
        assertEquals(0, animatedValue2)
        assertEquals(500, animatedValue3)

        // Continue the initial value animation. It should use a linear animation.
        advanceTimeBy(80) // 4 frames of animation
        awaitIdle()
        assertEquals(500 + (500f * 80f / 150f), animatedValue1.toFloat(), 1f)
        assertEquals(0, animatedValue2)
        assertEquals(500 + (500f * 80f / 150f), animatedValue3.toFloat(), 1f)
        val seekToFraction =
            coroutineScope.async {
                seekableTransitionState.seekTo(fraction = 0.5f)
                assertEquals(0.5f, seekableTransitionState.fraction)
            }
        advanceTimeByFrame()
        awaitIdle()
        val expected1Value = 500 + (500f * 96f / 150f)
        assertEquals(expected1Value, animatedValue1.toFloat(), 1f)
        assertEquals(500, animatedValue2)
        assertEquals(expected1Value + 0.5f * (2000 - expected1Value), animatedValue3.toFloat(), 1f)

        // Advance to the end of the seekTo() animation
        advanceTimeBy(5_000)
        seekToFraction.await()
        assertTrue(seekTo.isCancelled)
        awaitIdle()
        // The initial values should be 1000/0/1000
        // Target values should be 1000, 1000, 2000
        // The seek is 0.5
        assertEquals(1000, animatedValue1)
        assertEquals(500, animatedValue2)
        assertEquals(1500, animatedValue3)
        seekableTransitionState.seekTo(fraction = 1f)
        advanceTimeByFrame()
        awaitIdle()
        // Should be at the target values now
        assertEquals(1000, animatedValue1)
        assertEquals(1000, animatedValue2)
        assertEquals(2000, animatedValue3)
    }

    // In the middle of animating from From to To, seek to Other
    @Test
    fun interruptAnimationWithSeekThirdState() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        var animatedValue3 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.animateTo(AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            val val2 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.Other -> 1000
                        else -> 0
                    }
                }
            val val3 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.To -> 1000
                        AnimStates.Other -> 2000
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
            animatedValue3 = val3.value
        }
        advanceTimeByFrame() // lock in the animation start time
        awaitIdle()
        assertEquals(0f, seekableTransitionState.fraction, 0.01f)
        // Check initial values
        assertEquals(0, animatedValue1)
        assertEquals(0, animatedValue2)
        assertEquals(0, animatedValue3)
        // Advance around half way through the animation
        advanceTimeBy(160)
        awaitIdle()
        // should be 160/300 = 0.5333f
        assertEquals(0.53f, seekableTransitionState.fraction, 0.01f)

        // Check values at that fraction
        assertEquals(533f, animatedValue1.toFloat(), 1f)
        assertEquals(0, animatedValue2)
        assertEquals(533f, animatedValue3.toFloat(), 1f)

        val seekTo =
            coroutineScope.async {
                // seek to Other. This won't finish until the animation finishes
                seekableTransitionState.seekTo(0f, targetState = AnimStates.Other)
            }

        awaitIdle()
        // Nothing will have changed yet. The initial value should continue to animate
        // after this
        assertEquals(533f, animatedValue1.toFloat(), 1f)
        assertEquals(0, animatedValue2)
        assertEquals(533f, animatedValue3.toFloat(), 1f)

        // Advance time by two more frames
        advanceTimeBy(32)
        awaitIdle()
        // should be 192/300 = 0.64 through animation
        assertEquals(640f, animatedValue1.toFloat(), 1f)
        assertEquals(0, animatedValue2)
        assertEquals(640f, animatedValue3.toFloat(), 1f)
        val seekToHalf =
            coroutineScope.async {
                seekableTransitionState.seekTo(fraction = 0.5f)
                assertEquals(0.5f, seekableTransitionState.fraction)
            }
        advanceTimeByFrame()
        assertEquals(500, animatedValue2)

        // Advance to the end of the seekTo() animation
        advanceTimeBy(5_000)
        assertTrue(seekToHalf.isCompleted)
        assertTrue(seekTo.isCancelled)
        awaitIdle()
        // The initial values should be 1000/0/1000
        // Target values should be 1000, 1000, 2000
        // The seek is 0.5
        assertEquals(1000, animatedValue1)
        assertEquals(500, animatedValue2)
        assertEquals(1500, animatedValue3)
        coroutineScope.launch {
            seekableTransitionState.seekTo(fraction = 1f)
            assertEquals(1f, seekableTransitionState.fraction, 0f)
        }
        advanceTimeByFrame()
        awaitIdle()
        // Should be at the target values now
        assertEquals(1000, animatedValue1)
        assertEquals(1000, animatedValue2)
        assertEquals(2000, animatedValue3)
    }

    // In the middle of animating from From to To, seek to Other
    @Test
    fun interruptAnimationWithAnimateToThirdState() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        var animatedValue3 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.animateTo(AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            val val2 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.Other -> 1000
                        else -> 0
                    }
                }
            val val3 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.To -> 1000
                        AnimStates.Other -> 2000
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
            animatedValue3 = val3.value
        }
        advanceTimeByFrame() // lock in the animation start time
        awaitIdle()
        assertEquals(0f, seekableTransitionState.fraction, 0.01f)
        // Check initial values
        assertEquals(0, animatedValue1)
        assertEquals(0, animatedValue2)
        assertEquals(0, animatedValue3)
        // Advance around half way through the animation
        advanceTimeBy(160)
        awaitIdle()
        // should be 160/300 = 0.5333f
        assertEquals(0.53f, seekableTransitionState.fraction, 0.01f)

        // Check values at that fraction
        assertEquals(533f, animatedValue1.toFloat(), 1f)
        assertEquals(0, animatedValue2)
        assertEquals(533f, animatedValue3.toFloat(), 1f)
        val animateToOther =
            coroutineScope.async { seekableTransitionState.animateTo(AnimStates.Other) }

        advanceTimeBy(16) // composition after animateTo()

        awaitIdle()
        // initial should be 176/300 = 0.587 through animation
        assertEquals(586.7f, animatedValue1.toFloat(), 1f)
        assertEquals(0, animatedValue2)
        assertEquals(586.7f, animatedValue3.toFloat(), 1f)

        // Lock in the animation for the animation to Other, but advance animation to To
        advanceTimeBy(16)
        awaitIdle()
        // initial should be 192/300 = 0.640 through animation
        // target should be 16/300 = 0.053
        assertEquals(640f, animatedValue1.toFloat(), 1f)
        assertEquals(53.3f, animatedValue2.toFloat(), 1f)
        assertEquals(640f + ((2000f - 640f) * 0.053f), animatedValue3.toFloat(), 1f)

        // Advance time by two more frames
        advanceTimeBy(32)
        awaitIdle()
        // initial should be 224/300 = 0.746.7 through animation
        // other should be 48/300 = 0.160 through the animation
        assertEquals(746.7f, animatedValue1.toFloat(), 1f)
        assertEquals(160f, animatedValue2.toFloat(), 1f)
        assertEquals(746.7f + ((2000f - 746.7f) * 0.160f), animatedValue3.toFloat(), 2f)

        // Advance to the end of the animation
        advanceTimeBy(5_000)
        assertTrue(animateToOther.isCompleted)
        awaitIdle()
        assertEquals(1000, animatedValue1)
        assertEquals(1000, animatedValue2)
        assertEquals(2000, animatedValue3)
    }

    // In the middle of animating from From to To, seek to Other
    @Test
    fun cancelAnimationWithAnimateToThirdStateW() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        var animatedValue3 by mutableIntStateOf(-1)
        var targetState by mutableStateOf(AnimStates.To)

        setContent {
            LaunchedEffect(seekableTransitionState, targetState) {
                seekableTransitionState.animateTo(targetState)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            val val2 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.Other -> 1000
                        else -> 0
                    }
                }
            val val3 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.To -> 1000
                        AnimStates.Other -> 2000
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
            animatedValue3 = val3.value
        }
        advanceTimeByFrame() // lock in the animation start time
        awaitIdle()
        assertEquals(0f, seekableTransitionState.fraction, 0.01f)
        // Check initial values
        assertEquals(0, animatedValue1)
        assertEquals(0, animatedValue2)
        assertEquals(0, animatedValue3)
        // Advance around half way through the animation
        advanceTimeBy(160)
        awaitIdle()
        // should be 160/300 = 0.5333f
        assertEquals(0.53f, seekableTransitionState.fraction, 0.01f)

        // Check values at that fraction
        assertEquals(533f, animatedValue1.toFloat(), 1f)
        assertEquals(0, animatedValue2)
        assertEquals(533f, animatedValue3.toFloat(), 1f)
        targetState = AnimStates.Other

        // Advance the clock so that the LaunchedEffect can run
        advanceTimeBy(16)

        awaitIdle()
        assertEquals(AnimStates.Other, seekableTransitionState.targetState)

        // The time is advanced first, so the values are updated, then the
        // LaunchedEffect cancels the animation
        assertEquals(586, animatedValue1)
        assertEquals(0, animatedValue2)
        assertEquals(586, animatedValue3)

        // Compose the change
        advanceTimeBy(16)

        awaitIdle()
        // The previous animation's start time can be used, so continue the animation
        assertEquals(640, animatedValue1)
        assertEquals(0, animatedValue2) // animation hasn't started yet
        assertEquals(640, animatedValue3) // animation hasn't started yet

        // Advance one frame
        advanceTimeBy(16)
        awaitIdle()
        assertEquals(693, animatedValue1)
        assertEquals(53, animatedValue2)
        assertEquals(693 + ((2000 - 693) * 16 / 300), animatedValue3)

        // Advance to the end of the animation
        advanceTimeBy(5_000)
        awaitIdle()
        assertEquals(1000, animatedValue1)
        assertEquals(1000, animatedValue2)
        assertEquals(2000, animatedValue3)
    }

    @Test
    fun interruptInterruption() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        var animatedValue3 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            val val2 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.Other -> 1000
                        else -> 0
                    }
                }
            val val3 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.To -> 1000
                        AnimStates.Other -> 2000
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
            animatedValue3 = val3.value
        }
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.seekTo(0f, targetState = AnimStates.To) }
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.seekTo(fraction = 0.5f) }
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.seekTo(0f, targetState = AnimStates.Other) }
        awaitIdle()
        advanceTimeByFrame() // lock in the initial value animation start time
        coroutineScope.launch { seekableTransitionState.seekTo(fraction = 0.5f) }
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.seekTo(0f, targetState = AnimStates.From) }
        awaitIdle()

        // Now we have two initial value animations running. One is for animating
        // from From -> To, one from To -> Other
        // The From -> To animation should affect animatedValue1 and animatedValue2
        // The To -> Other animation should affect animatedValue3

        // Holding the value here, the animations should move the values to 1000, 1000, 2000
        advanceTimeBy(5_000)
        awaitIdle()
        assertEquals(1000, animatedValue1)
        assertEquals(1000, animatedValue2)
        assertEquals(2000, animatedValue3)
    }

    @OptIn(InternalAnimationApi::class)
    @Test
    fun delayedTransition() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope
        lateinit var transition: Transition<AnimStates>

        setContent {
                Box {
            coroutineScope = rememberCoroutineScope()
            transition = rememberTransition(seekableTransitionState, label = "Test")
            transition.AnimatedVisibility(
                visible = { it != AnimStates.To },
                enter = fadeIn(tween(300, 0, LinearEasing)),
                exit = fadeOut(tween(300, 0, LinearEasing)),
            ) {
                Label(text = "content")
            }

                }}
        awaitIdle()
        val seekTo = coroutineScope.async { seekableTransitionState.seekTo(0.5f, AnimStates.To) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        assertEquals(150L * MillisToNanos, transition.playTimeNanos)
    }

    /** The [delayedTransition] shape driven by a child transition rather than a container. */
    @Test
    fun delayedTransitionWithChildTransition() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope
        lateinit var transition: Transition<AnimStates>

        setContent {
            coroutineScope = rememberCoroutineScope()
            transition = rememberTransition(seekableTransitionState, label = "Test")
            transition
                .createChildTransition { it != AnimStates.To }
                .animateFloat(transitionSpec = { tween(300, 0, LinearEasing) }) { visible ->
                    if (visible) 1f else 0f
                }
        }
        awaitIdle()
        val seekTo = coroutineScope.async { seekableTransitionState.seekTo(0.5f, AnimStates.To) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        assertEquals(150L * MillisToNanos, transition.playTimeNanos)
    }

    @Test
    fun seekAfterAnimating() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val deferred = coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeBy(10_000) // complete the animation
        awaitIdle()
        assertTrue(deferred.isCompleted)
        assertEquals(1000, animatedValue1)

        // seeking after the animation has completed should not change any value
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.seekTo(fraction = 0.5f) }
        awaitIdle()
        advanceTimeByFrame()
        assertEquals(1000, animatedValue1)
    }

    @Test
    fun animateToWithSpec() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.seekTo(0.5f, AnimStates.To) }
        awaitIdle()
        advanceTimeByFrame()
        val deferred =
            coroutineScope.async {
                seekableTransitionState.animateTo(animationSpec = tween(1000, 0, LinearEasing))
            }
        advanceTimeByFrame() // lock in the start time
        advanceTimeBy(64)
        awaitIdle()
        // should be 500 + 500 * 64/1000 = 532
        assertEquals(532, animatedValue1)
        advanceTimeBy(192)
        awaitIdle()
        // should be 500 + 500 * 256/1000 = 628
        assertEquals(628, animatedValue1)
        advanceTimeBy(256)
        awaitIdle()
        // should be 500 + 500 * 512/1000 = 756
        assertEquals(756, animatedValue1)
        advanceTimeBy(512)
        awaitIdle()
        assertTrue(deferred.isCompleted)
        assertEquals(1000, animatedValue1)
    }

    @Test
    fun seekToFollowedByAnimation() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        coroutineScope.launch {
            seekableTransitionState.seekTo(1f, AnimStates.To)
            seekableTransitionState.animateTo(AnimStates.From)
        }
        advanceTimeByFrame() // let the composition happen after seekTo
        awaitIdle() // seekTo() should run now, setting the animated value
        assertEquals(1000, animatedValue1)
        advanceTimeByFrame() // lock in the animation clock
        awaitIdle()
        assertEquals(1000, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(984, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(968, animatedValue1)
        advanceTimeBy(1000)
        awaitIdle()
        assertEquals(0, animatedValue1)
    }

    @Test
    fun conflictingSeekTo() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val defer1 =
            coroutineScope.async {
                seekableTransitionState.seekTo(1f, AnimStates.To)
                seekableTransitionState.animateTo(AnimStates.From)
            }
        val defer2 =
            coroutineScope.async {
                seekableTransitionState.seekTo(1f, AnimStates.Other)
                seekableTransitionState.animateTo(AnimStates.From)
            }
        advanceTimeByFrame() // let the composition happen after seekTo
        awaitIdle()
        assertTrue(defer1.isCancelled)
        assertFalse(defer2.isCancelled)
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame() // lock in the animation clock
        awaitIdle()
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(1968, animatedValue1)
        advanceTimeBy(1000)
        awaitIdle()
        assertEquals(0, animatedValue1)
        assertTrue(defer2.isCompleted)
    }

    @Test
    fun conflictingSnapTo() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val defer1 =
            coroutineScope.async {
                seekableTransitionState.snapTo(AnimStates.To)
                seekableTransitionState.animateTo(AnimStates.From)
            }
        val defer2 =
            coroutineScope.async {
                seekableTransitionState.snapTo(AnimStates.Other)
                seekableTransitionState.animateTo(AnimStates.From)
            }
        advanceTimeByFrame() // let the composition happen after seekTo
        awaitIdle()
        assertTrue(defer1.isCancelled)
        assertFalse(defer2.isCancelled)
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame() // lock in the animation clock
        awaitIdle()
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(1968, animatedValue1)
        advanceTimeBy(1000)
        awaitIdle()
        assertEquals(0, animatedValue1)
        assertTrue(defer2.isCompleted)
    }

    /**
     * Here, the first seekTo() doesn't do anything since the target is the same as the current
     * value. It only changes the fraction.
     */
    @Test
    fun conflictingSeekTo2() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        coroutineScope.launch {
            seekableTransitionState.seekTo(1f, AnimStates.From)
            seekableTransitionState.animateTo(AnimStates.To)
        }
        coroutineScope.launch {
            seekableTransitionState.seekTo(1f, AnimStates.Other)
            seekableTransitionState.animateTo(AnimStates.From)
        }
        advanceTimeByFrame() // let the composition happen after seekTo
        awaitIdle()
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame() // lock in the animation clock
        awaitIdle()
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(1968, animatedValue1)
        advanceTimeBy(1000)
        awaitIdle()
        assertEquals(0, animatedValue1)
    }

    /**
     * Here, the first seekTo() doesn't do anything since the target is the same as the current
     * value. It only changes the fraction.
     */
    @Test
    fun conflictingSnapTo2() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        coroutineScope.launch {
            seekableTransitionState.snapTo(AnimStates.From)
            seekableTransitionState.animateTo(AnimStates.To)
        }
        coroutineScope.launch {
            seekableTransitionState.snapTo(AnimStates.Other)
            seekableTransitionState.animateTo(AnimStates.From)
        }
        advanceTimeByFrame() // let the composition happen after snapTo
        awaitIdle()
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame() // lock in the animation clock
        awaitIdle()
        assertEquals(2000, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(1968, animatedValue1)
        advanceTimeBy(1000)
        awaitIdle()
        assertEquals(0, animatedValue1)
    }

    @Test
    fun snapToStopsAllAnimations() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.seekTo(1f, AnimStates.To) }
        advanceTimeByFrame()
        awaitIdle()
        val animation = coroutineScope.async { seekableTransitionState.animateTo(AnimStates.Other) }
        advanceTimeByFrame()
        awaitIdle()
        val snapTo = coroutineScope.async { seekableTransitionState.snapTo(AnimStates.From) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(animation.isCancelled)
        assertTrue(snapTo.isCompleted)
        assertEquals(0, animatedValue1)
    }

    @Test
    fun snapToSameTargetState() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo = coroutineScope.async { seekableTransitionState.seekTo(0.5f, AnimStates.To) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        val snapTo = coroutineScope.async { seekableTransitionState.snapTo(AnimStates.To) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(snapTo.isCompleted)
        assertEquals(1000, animatedValue1)
    }

    @Test
    fun snapToSameCurrentState() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo = coroutineScope.async { seekableTransitionState.seekTo(0.5f, AnimStates.To) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        val snapTo = coroutineScope.async { seekableTransitionState.snapTo(AnimStates.From) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(snapTo.isCompleted)
        assertEquals(0, animatedValue1)
    }

    @Test
    fun snapToExistingState() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val snapTo = coroutineScope.async { seekableTransitionState.snapTo(AnimStates.From) }
        advanceTimeByFrame()
        awaitIdle()
        assertTrue(snapTo.isCompleted)
        assertEquals(0, animatedValue1)
        val seekAndSnap =
            coroutineScope.async {
                seekableTransitionState.seekTo(0.5f, AnimStates.To)
                seekableTransitionState.snapTo(AnimStates.From)
                seekableTransitionState.snapTo(AnimStates.From)
            }
        advanceTimeByFrame() // seekTo
        advanceTimeByFrame() // snapTo
        awaitIdle()
        assertTrue(seekAndSnap.isCompleted)
        assertEquals(0, animatedValue1)
    }

    @Test
    fun animateAndContinueAnimation() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo =
            coroutineScope.async {
                seekableTransitionState.seekTo(fraction = 0f, targetState = AnimStates.To)
            }
        advanceTimeByFrame() // wait for composition after seekTo
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        val animateTo = coroutineScope.async { seekableTransitionState.animateTo() }
        advanceTimeByFrame() // lock animation clock
        advanceTimeBy(160)
        awaitIdle()
        assertEquals(160, animatedValue1)

        val animateTo2 = coroutineScope.async { seekableTransitionState.animateTo() }

        awaitIdle()
        assertTrue(animateTo.isCancelled)

        advanceTimeByFrame() // continue the animation

        awaitIdle()
        assertEquals(176, animatedValue1)

        advanceTimeBy(900)

        awaitIdle()
        assertTrue(animateTo2.isCompleted)
        assertEquals(1000, animatedValue1)
    }

    @Test
    fun continueAnimationWithNewSpec() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo =
            coroutineScope.async {
                seekableTransitionState.seekTo(fraction = 0f, targetState = AnimStates.To)
            }
        advanceTimeByFrame() // wait for composition after seekTo
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        val animateTo = coroutineScope.async { seekableTransitionState.animateTo() }
        advanceTimeByFrame() // lock animation clock
        advanceTimeBy(160)
        awaitIdle()
        assertEquals(160, animatedValue1)

        val animateTo2 =
            coroutineScope.async {
                seekableTransitionState.animateTo(
                    animationSpec = tween(durationMillis = 200, easing = LinearEasing)
                )
            }

        awaitIdle()
        assertTrue(animateTo.isCancelled)

        advanceTimeByFrame() // continue the animation

        awaitIdle()
        // 160 + (840 * 16/200) = 227.2
        assertEquals(227.2f, animatedValue1.toFloat(), 1f)

        advanceTimeBy(200)

        awaitIdle()
        assertTrue(animateTo2.isCompleted)
        assertEquals(1000, animatedValue1)
    }

    @Test
    fun continueAnimationUsesInitialVelocity() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo =
            coroutineScope.async {
                seekableTransitionState.seekTo(fraction = 0f, targetState = AnimStates.To)
            }
        advanceTimeByFrame() // wait for composition after seekTo
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        val animateTo = coroutineScope.async { seekableTransitionState.animateTo() }
        advanceTimeByFrame() // lock animation clock
        advanceTimeBy(800) // half way
        awaitIdle()
        assertEquals(500, animatedValue1)

        coroutineScope.launch {
            seekableTransitionState.animateTo(
                animationSpec =
                    spring(visibilityThreshold = 0.01f, stiffness = Spring.StiffnessVeryLow)
            )
        }

        awaitIdle()
        assertTrue(animateTo.isCancelled)

        advanceTimeByFrame() // continue the animation

        awaitIdle()
        // The velocity should be similar to what it was before after only one frame
        // 500 / 800 = 0.625 pixels per ms * 16 = 10 pixels
        assertEquals(510f, animatedValue1.toFloat(), 2f)
    }

    @Test
    fun continueAnimationNewSpecUsesInitialVelocity() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo =
            coroutineScope.async {
                seekableTransitionState.seekTo(fraction = 0f, targetState = AnimStates.To)
            }
        advanceTimeByFrame() // wait for composition after seekTo
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        val springSpec = spring<Float>(dampingRatio = 2f)
        val vecSpringSpec = springSpec.vectorize(Float.VectorConverter)
        val animateTo =
            coroutineScope.async { seekableTransitionState.animateTo(animationSpec = springSpec) }
        advanceTimeByFrame() // lock animation clock

        // find how long it takes to get to about half way:
        var halfDuration = 16L
        val zeroVector = AnimationVector1D(0f)
        val oneVector = AnimationVector1D(1f)
        while (
            vecSpringSpec
                .getValueFromMillis(
                    playTimeMillis = halfDuration,
                    start = zeroVector,
                    end = oneVector,
                    startVelocity = zeroVector,
                )[0] < 0.5f
        ) {
            halfDuration += 16L
        }
        advanceTimeBy(halfDuration.toInt()) // ~half way
        val halfValue =
            vecSpringSpec
                .getValueFromMillis(
                    playTimeMillis = halfDuration,
                    start = zeroVector,
                    end = oneVector,
                    startVelocity = zeroVector,
                )[0] * 1000
        awaitIdle()
        assertEquals(halfValue, animatedValue1.toFloat(), 1f)

        val velocityAtHalfWay =
            vecSpringSpec
                .getVelocityFromNanos(
                    playTimeNanos = halfDuration * MillisToNanos,
                    initialValue = zeroVector,
                    targetValue = oneVector,
                    initialVelocity = zeroVector,
                )[0]

        coroutineScope.launch {
            seekableTransitionState.animateTo(
                animationSpec =
                    spring(
                        visibilityThreshold = 0.01f,
                        stiffness = Spring.StiffnessVeryLow,
                        dampingRatio = Spring.DampingRatioHighBouncy,
                    )
            )
        }

        awaitIdle()
        assertTrue(animateTo.isCancelled)

        advanceTimeByFrame() // continue the animation

        awaitIdle()
        // The velocity should be similar to what it was before after only one frame
        assertEquals(halfValue + (velocityAtHalfWay * 16f), animatedValue1.toFloat(), 2f)
    }

    @Test
    fun animationCompletionHasNoInitialValueAnimation() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    val target =
                        when (state) {
                            AnimStates.From -> 0
                            AnimStates.Other -> 2000
                            else -> 1000
                        }
                    target
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        coroutineScope.launch { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeBy(1700)
        coroutineScope.launch { seekableTransitionState.animateTo(AnimStates.From) }
        advanceTimeByFrame() // lock in the clock
        awaitIdle()
        assertEquals(1000, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(990, animatedValue1)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(980, animatedValue1)
    }

    @Test
    fun animationDurationWorksOnInitialStateChange() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.Other -> 2000
                        else -> 1000
                    }
                }
            val val2 =
                transition.animateInt(
                    label = "Value2",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
        }
        awaitIdle()
        coroutineScope.launch {
            seekableTransitionState.animateTo(
                AnimStates.To,
                animationSpec = tween(durationMillis = 160, easing = LinearEasing),
            )
        }
        advanceTimeByFrame() // lock in the clock
        coroutineScope.launch { seekableTransitionState.animateTo(AnimStates.Other) }
        advanceTimeByFrame() // advance one frame toward To and compose to Other
        awaitIdle()
        assertEquals(100, animatedValue1)
        assertEquals(100, animatedValue2)
        advanceTimeByFrame()
        awaitIdle()
        // 200 + (1800 * 16/1600) = 218
        assertEquals(218, animatedValue1)
        // continue the animatedValue2 animation
        assertEquals(200, animatedValue2)
        advanceTimeBy(128)
        awaitIdle()
        // 1000 + (1000 * 144/1600) = 1090
        assertEquals(1090, animatedValue1)
        assertEquals(1000, animatedValue2)
        advanceTimeBy(1600)
        awaitIdle()
        assertEquals(2000, animatedValue1)
        assertEquals(1000, animatedValue2)
    }

    @Test
    fun animationAlreadyAtTarget() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo = coroutineScope.async { seekableTransitionState.seekTo(1f, AnimStates.To) }
        advanceTimeByFrame() // wait for composition
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        assertEquals(1000, animatedValue1)
        val anim = coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeByFrame() // compose to current state = target state
        awaitIdle()
        assertTrue(anim.isCompleted)
        assertEquals(AnimStates.To, seekableTransitionState.currentState)
        assertEquals(AnimStates.To, seekableTransitionState.targetState)
    }

    @Test
    fun seekCurrentEqualsTarget() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            animatedValue1 = val1.value
        }
        awaitIdle()
        val seekTo = coroutineScope.async { seekableTransitionState.seekTo(0.5f) }
        awaitIdle()
        assertTrue(seekTo.isCompleted)
        assertEquals(0, animatedValue1)
    }

    @Test
    fun animateToAtEndCurrentInitialValueAnimations() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.To -> 1000
                        AnimStates.Other -> 2000
                    }
                }
            val val2 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
        }
        awaitIdle()
        val seekTo = coroutineScope.async { seekableTransitionState.seekTo(0f, AnimStates.To) }
        advanceTimeByFrame() // compose to To
        assertTrue(seekTo.isCompleted)
        val seekOther =
            coroutineScope.async { seekableTransitionState.seekTo(1f, AnimStates.Other) }
        advanceTimeByFrame() // compose to Other
        assertFalse(seekOther.isCompleted) // should be animating animatedValue2
        val animateOther =
            coroutineScope.async {
                // already at the end (1f), but it should continue the animatedValue2 animation
                seekableTransitionState.animateTo(AnimStates.Other)
            }
        awaitIdle() // animateOther can cancel the seekOther
        assertTrue(seekOther.isCancelled)
        assertTrue(animateOther.isActive)
        advanceTimeByFrame() // advance the animation
        awaitIdle()
        assertTrue(animateOther.isActive)
        assertEquals(2000, animatedValue1)
        assertEquals(1000 * 16 / 1600, animatedValue2)
    }

    @Test
    fun changingDuration() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }
            val val2 =
                if (val1.value < 500) {
                    remember { mutableFloatStateOf(0f) }
                } else {
                    transition.animateFloat(
                        label = "Value2",
                        transitionSpec = { tween(durationMillis = 3200, easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0f
                            else -> 1000f
                        }
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value.roundToInt()
        }
        awaitIdle()
        val anim = coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeByFrame() // wait for composition
        advanceTimeBy(800) // half way through
        awaitIdle()
        assertEquals(500, animatedValue1)
        assertEquals(0, animatedValue2)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(510, animatedValue1)
        assertEquals(5, animatedValue2)
        advanceTimeBy(784)
        awaitIdle()
        assertEquals(1000, animatedValue1)
        assertEquals(250, animatedValue2)
        assertFalse(anim.isCompleted)
        advanceTimeBy(2400)
        awaitIdle()
        assertEquals(1000, animatedValue1)
        assertEquals(1000, animatedValue2)
        advanceTimeByFrame() // wait for composition
        assertTrue(anim.isCompleted)
    }

    @Test
    fun changingAnimationWithAnimateToThirdState() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        var animatedValue2 by mutableFloatStateOf(-1f)
        lateinit var coroutineScope: CoroutineScope

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        AnimStates.To -> 1000
                        else -> 2000
                    }
                }
            val val2 =
                if (val1.value < 500) {
                    remember { mutableFloatStateOf(0f) }
                } else {
                    transition.animateFloat(
                        label = "Value2",
                        transitionSpec = { tween(durationMillis = 3200, easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0f
                            AnimStates.To -> 1000f
                            else -> 2000f
                        }
                    }
                }
            animatedValue1 = val1.value
            animatedValue2 = val2.value
        }
        awaitIdle()
        val animateTo = coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeByFrame() // wait for composition
        advanceTimeBy(800) // half way through

        awaitIdle()
        // Won't have advanced the value to Other, but will continue advance to To
        assertEquals(1000 * 800 / 1600, animatedValue1)
        assertEquals(1000 * 0 / 3200, animatedValue2.roundToInt())
        advanceTimeByFrame() // one frame past recomposition, so animation is running

        awaitIdle()
        // Won't have advanced the value to Other, but will continue advance to To
        assertEquals(1000 * 816 / 1600, animatedValue1)
        assertEquals(1000 * 16 / 3200, animatedValue2.roundToInt())

        // now seek to third state
        val animateOther =
            coroutineScope.async { seekableTransitionState.animateTo(AnimStates.Other) }
        awaitIdle() // animateOther can cancel the animateTo
        assertTrue(animateTo.isCancelled)
        advanceTimeByFrame() // wait for composition
        awaitIdle()
        // Won't have advanced the value to Other, but will continue advance to To
        assertEquals(1000 * 832 / 1600, animatedValue1)
        assertEquals(1000 * 32 / 3200, animatedValue2.roundToInt())
        advanceTimeByFrame()
        awaitIdle()
        // Continues the advance to Other
        val anim1Value1 = 1000 * 848 / 1600
        val anim1Value2 = 1000 * 48 / 3200
        assertEquals(anim1Value1 + ((2000 - anim1Value1) * 16 / 1600), animatedValue1)
        assertEquals(anim1Value2 + ((2000f - anim1Value2) * 16 / 3200), animatedValue2)

        advanceTimeBy(752)
        awaitIdle()
        val anim2Value1 = 1000
        val anim2Value2 = 1000 * 800 / 3200
        assertEquals(anim2Value1 + ((2000 - anim2Value1) * 768 / 1600), animatedValue1)
        assertEquals(anim2Value2 + ((2000f - anim2Value2) * 768 / 3200), animatedValue2)
        assertFalse(animateOther.isCompleted)

        advanceTimeBy(832)
        awaitIdle()
        val anim3Value2 = 1000 * 1632 / 3200
        assertEquals(2000, animatedValue1)
        assertEquals(anim3Value2 + ((2000f - anim3Value2) * 1600 / 3200), animatedValue2)
        assertFalse(animateOther.isCompleted)

        advanceTimeBy(1600)
        awaitIdle()
        assertEquals(2000, animatedValue1)
        assertEquals(2000f, animatedValue2)
        assertFalse(animateOther.isCompleted)

        advanceTimeByFrame() // composition after the current value changes
        awaitIdle()
        assertTrue(animateOther.isCompleted)
    }

    @Test
    fun animateAfterSeekToZero() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope

        setContent {
                Box {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }

            animatedValue1 = val1.value
            transition.AnimatedVisibility({ it == AnimStates.To }) {
                Label(text = "content", modifier = SwingModifier.preferredSize(40, 20).opaque(true).background(Color.BLUE))
            }

                }}
        awaitIdle()
        val initialAnimateAndSeek =
            coroutineScope.async {
                seekableTransitionState.animateTo(AnimStates.To)
                seekableTransitionState.seekTo(0.5f, targetState = AnimStates.From)
                seekableTransitionState.seekTo(0f, targetState = AnimStates.From)
            }
        advanceTimeBy(5000)
        awaitIdle()
        assertTrue(initialAnimateAndSeek.isCompleted)
        assertEquals(1000, animatedValue1)
        assertContentFullyEntered()
        val secondAnimate =
            coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        awaitIdle()
        // This waits for the initial state animation to finish, since we changed the initial state
        // when going from seeking to animating.
        advanceTimeBy(5000)
        awaitIdle()
        assertTrue(secondAnimate.isCompleted)
        assertEquals(1000, animatedValue1)
        assertContentFullyEntered()
    }

    /** Asserts that the single animated container in the tree holds its content at full opacity. */
    private fun ComposeSwingTest.assertContentFullyEntered() {
        onNodeWithText("content").assertExists()
        assertEquals(1f, animatedContainer().paintedAlpha())
    }

    /** The [animateAfterSeekToZero] shape driven by a child transition rather than a container. */
    @Test
    fun animateAfterSeekToZeroWithChildTransition() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        var animatedValue1 by mutableIntStateOf(-1)
        lateinit var coroutineScope: CoroutineScope
        lateinit var visibleAlpha: State<Float>

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            val val1 =
                transition.animateInt(
                    label = "Value",
                    transitionSpec = { tween(durationMillis = 1600, easing = LinearEasing) },
                ) { state ->
                    when (state) {
                        AnimStates.From -> 0
                        else -> 1000
                    }
                }

            animatedValue1 = val1.value
            visibleAlpha =
                transition
                    .createChildTransition { it == AnimStates.To }
                    .animateFloat { visible -> if (visible) 1f else 0f }
        }
        awaitIdle()
        val initialAnimateAndSeek =
            coroutineScope.async {
                seekableTransitionState.animateTo(AnimStates.To)
                seekableTransitionState.seekTo(0.5f, targetState = AnimStates.From)
                seekableTransitionState.seekTo(0f, targetState = AnimStates.From)
            }
        advanceTimeBy(5000)
        awaitIdle()
        assertTrue(initialAnimateAndSeek.isCompleted)
        assertEquals(1000, animatedValue1)
        assertEquals(1f, visibleAlpha.value)
        val secondAnimate =
            coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        awaitIdle()
        // This waits for the initial state animation to finish, since we changed the initial state
        // when going from seeking to animating.
        advanceTimeBy(5000)
        awaitIdle()
        assertTrue(secondAnimate.isCompleted)
        assertEquals(1000, animatedValue1)
        assertEquals(1f, visibleAlpha.value)
    }

    @Test
    fun isRunningDuringAnimateTo() = runComposeSwingTest {
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var transition: Transition<AnimStates>
        var animatedValue by mutableIntStateOf(-1)

        mainClock.autoAdvance = false

        setContent {
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.animateTo(AnimStates.To)
            }
            transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = { tween(easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
        }
        awaitIdle()
        assertEquals(0, animatedValue)
        assertFalse(transition.isRunning)
        advanceTimeByFrame() // wait for composition after animateTo()
        advanceTimeByFrame() // one frame to set the start time
        awaitIdle()
        assertTrue(animatedValue > 0)
        assertTrue(transition.isRunning)
        advanceTimeBy(5000)
        awaitIdle()
        assertEquals(1000, animatedValue)
        assertFalse(transition.isRunning)
    }

    @Test
    fun isRunningFalseAfterSnapTo() = runComposeSwingTest {
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var transition: Transition<AnimStates>
        var animatedValue by mutableIntStateOf(-1)

        mainClock.autoAdvance = false

        setContent {
            LaunchedEffect(seekableTransitionState) {
                // Not sure why this is needed. Animated val doesn't change without it.
                withFrameNanos {}
                seekableTransitionState.snapTo(AnimStates.To)
            }
            transition = rememberTransition(seekableTransitionState, label = "Test")
            animatedValue =
                transition
                    .animateInt(
                        label = "Value",
                        transitionSpec = { tween(easing = LinearEasing) },
                    ) { state ->
                        when (state) {
                            AnimStates.From -> 0
                            else -> 1000
                        }
                    }
                    .value
        }
        awaitIdle()
        assertEquals(0, animatedValue)
        assertFalse(transition.isRunning)
        advanceTimeByFrame() // wait for composition after animateTo()
        advanceTimeByFrame() // one frame to snap
        advanceTimeByFrame() // one frame for LaunchedEffect's awaitFrame()
        awaitIdle()
        assertEquals(1000, animatedValue)
        assertFalse(transition.isRunning)
    }

    @Test
    fun isRunningFalseAfterChildAnimatedVisibilityTransition() = runComposeSwingTest {
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope
        lateinit var transition: Transition<AnimStates>
        var animatedVisibilityTransition: Transition<*>? = null

        mainClock.autoAdvance = false

        setContent {
                Box {
            coroutineScope = rememberCoroutineScope()
            transition = rememberTransition(seekableTransitionState, label = "Test")
            transition.AnimatedVisibility(visible = { it == AnimStates.To }) {
                animatedVisibilityTransition = this.transition
                Label(text = "content")
            }

                }}
        awaitIdle()
        assertFalse(transition.isRunning)
        assertNull(animatedVisibilityTransition)

        coroutineScope.launch { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeBy(50)
        awaitIdle()
        assertTrue(transition.isRunning)
        assertTrue(animatedVisibilityTransition!!.isRunning)

        advanceTimeBy(5000)
        awaitIdle()
        assertFalse(transition.isRunning)
        assertFalse(animatedVisibilityTransition!!.isRunning)
    }

    @Test
    fun isRunningFalseAfterRemovingAnimationWhileAnimatingToPreviousState() = runComposeSwingTest {
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope
        lateinit var transition: Transition<AnimStates>
        var floatAnim: State<Float>? = null
        var conditionalAnim: State<Float>? = null
        var addConditionalAnim by mutableStateOf(true)
        setContent {
            coroutineScope = rememberCoroutineScope()
            transition = rememberTransition(seekableTransitionState)
            floatAnim =
                transition.animateFloat(transitionSpec = { tween(500) }) {
                    if (it == AnimStates.From) 0f else 1000f
                }
            conditionalAnim =
                if (addConditionalAnim) {
                    // Longer duration than floatAnim so we can check if it keeps the transition
                    // running
                    transition.animateFloat(transitionSpec = { tween(10000000) }) {
                        if (it == AnimStates.From) 0f else 1000f
                    }
                } else {
                    null
                }
        }
        awaitIdle()

        // Check initial values
        assertEquals(0f, floatAnim?.value)
        assertEquals(0f, conditionalAnim?.value)

        mainClock.autoAdvance = false

        // Animate
        coroutineScope.launch { seekableTransitionState.animateTo(AnimStates.To) }
        advanceTimeByFrame()

        // Finish floatAnim but not conditionalAnim
        advanceTimeBy(750)

        assertEquals(1000f, floatAnim?.value)
        assertTrue(conditionalAnim!!.value < 1000f)
        assertTrue(transition.isRunning)

        // Animate back
        coroutineScope.launch { seekableTransitionState.animateTo(AnimStates.From) }
        advanceTimeByFrame()

        // Finish floatAnim but not conditionalAnim
        advanceTimeBy(500)

        assertEquals(0f, floatAnim?.value)
        assertTrue(conditionalAnim!!.value > 0f)
        assertTrue(transition.isRunning)

        // Remove conditionalAnim
        addConditionalAnim = false
        advanceTimeByFrame()
        advanceTimeByFrame()

        assertTrue(conditionalAnim == null)
        assertFalse(transition.isRunning)
    }

    @Test
    fun testCleanupAfterDispose() = runComposeSwingTest {
        val seekableState: SeekableTransitionState<*> = SeekableTransitionState(true)
        var disposed by mutableStateOf(false)

        fun isObserving(): Boolean {
            var active = false
            seekableState.snapshotStateObserver?.clearIf {
                active = true
                false
            }
            return active
        }

        setContent {
            if (!disposed) {
                rememberTransition(transitionState = seekableState)
            }
        }
        awaitIdle()
        assertTrue(isObserving())

        disposed = true
        awaitIdle()
        assertFalse(isObserving())
    }

    @Test
    fun quickAddAndRemove() = runComposeSwingTest {
        @Stable
        class ScreenState(val label: String, removing: Boolean = false) {
            var removing by mutableStateOf(removing)
        }

        var labelIndex = 1
        val screenStates = mutableStateListOf(ScreenState("1"))
        val seekableScreenTransitionState = SeekableTransitionState(screenStates.toList())

        setContent {
                Box {
            val screenTransition = rememberTransition(seekableScreenTransitionState)
            LaunchedEffect(Unit) {
                snapshotFlow { screenStates.toList().filter { !it.removing } }
                    .collectLatest { capturedScreenStates ->
                        seekableScreenTransitionState.animateTo(capturedScreenStates)
                        // Done animating
                        screenStates.reversed().forEach {
                            if (it.removing) {
                                screenStates.remove(it)
                            }
                        }
                    }
            }

            Column {
                screenStates.forEach { screenState ->
                    key(screenState) {
                        val visibleTransition =
                            screenTransition.createChildTransition {
                                screenState === it.lastOrNull() && !screenState.removing
                            }
                        visibleTransition.AnimatedVisibility(visible = { it }) {
                            Label(
                                text = "Hello ${screenState.label}",
                                modifier = SwingModifier.testTag(screenState.label),
                            )
                        }
                    }
                }
            }

                }}
        suspend fun removeState() {
            screenStates.last { !it.removing }.removing = true
            awaitIdle()
        }
        suspend fun addState() {
            screenStates += ScreenState(label = "${++labelIndex}")
            awaitIdle()
        }

        awaitIdle()
        mainClock.autoAdvance = false
        addState()
        advanceTimeBy(50)
        removeState()
        advanceTimeBy(50)
        addState()
        advanceTimeBy(50)
        removeState()
        mainClock.autoAdvance = true
        awaitIdle()

        onNodeWithTag("1").assertIsDisplayed()
        onNodeWithTag("2").assertDoesNotExist()
        onNodeWithTag("3").assertDoesNotExist()
    }

    @Test
    fun exitingContentKillTransition() = runComposeSwingTest {
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope
        lateinit var transition: Transition<AnimStates>

        setContent {
                Box {
            transition = rememberTransition(seekableTransitionState, label = "Test")
            transition.AnimatedVisibility(visible = { it == AnimStates.From }) {
                coroutineScope = rememberCoroutineScope()
                Label(text = "content")
            }

                }}

        awaitIdle()
        coroutineScope.launch { seekableTransitionState.animateTo(AnimStates.To) }

        // The transition comes to rest even though the content that launched the animation leaves
        // during it.
        awaitIdle()
        assertEquals(0L, transition.playTimeNanos)
    }
}
