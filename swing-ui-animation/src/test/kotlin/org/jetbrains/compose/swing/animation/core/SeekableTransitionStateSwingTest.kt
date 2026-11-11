// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.animation.core.SeekableTransitionStateTest.AnimStates

/** Pins seeking an engine transition that a child transition drives rather than a container. */
class SeekableTransitionStateSwingTest {
    /** The [updatedTransition] shape driven by a child transition rather than a container. */
    @Test
    fun updatedTransitionWithChildTransition() = runComposeSwingTest {
        var childValue = -1f
        var duration = -1L
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)

        setContent {
            LaunchedEffect(seekableTransitionState) {
                seekableTransitionState.seekTo(0f, targetState = AnimStates.To)
            }
            val transition = rememberTransition(seekableTransitionState, label = "Test")
            childValue =
                transition
                    .createChildTransition { it == AnimStates.To }
                    .animateFloat(
                        transitionSpec = { tween(durationMillis = 1000, easing = LinearEasing) }
                    ) { state ->
                        if (state) 1f else 0f
                    }
                    .value
            duration = transition.totalDurationNanos
        }

        awaitIdle()
        assertEquals(1000_000_000L, duration)
        assertEquals(0f, childValue, 0f)

        seekableTransitionState.seekTo(fraction = 0.5f)

        awaitIdle()
        assertEquals(0.5f, childValue, 0.001f)

        seekableTransitionState.seekTo(fraction = 1f)

        awaitIdle()
        assertEquals(1f, childValue, 0f)
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

    /** The [animateAfterSeekToZero] shape driven by a child transition rather than a container. */
    @Test
    fun animateAfterSeekToZeroWithChildTransition() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val seekableTransitionState = SeekableTransitionState(AnimStates.From)
        lateinit var coroutineScope: CoroutineScope
        lateinit var visibleAlpha: State<Float>

        setContent {
            coroutineScope = rememberCoroutineScope()
            val transition = rememberTransition(seekableTransitionState, label = "Test")
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
        assertEquals(1f, visibleAlpha.value)
        val secondAnimate =
            coroutineScope.async { seekableTransitionState.animateTo(AnimStates.To) }
        awaitIdle()
        // This waits for the initial state animation to finish, since we changed the initial state
        // when going from seeking to animating.
        advanceTimeBy(5000)
        awaitIdle()
        assertTrue(secondAnimate.isCompleted)
        assertEquals(1f, visibleAlpha.value)
    }
}
