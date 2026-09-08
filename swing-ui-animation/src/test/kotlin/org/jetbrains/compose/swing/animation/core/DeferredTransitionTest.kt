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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.test.runComposeSwingTest

@OptIn(ExperimentalTransitionApi::class, ExperimentalDeferredTransitionApi::class)
class DeferredTransitionTest {

    private enum class TestStates {
        A,
        B,
        C,
    }

    @Test
    fun deferredTransition_initialStateSetImmediately_updatesOnlyWhenReady() = runComposeSwingTest {
        lateinit var transition: DeferredTransition<TestStates>
        lateinit var state: DeferredTransitionState<TestStates>

        setContent {
            state = remember { DeferredTransitionState(TestStates.A) }
            transition = rememberTransition(state)
        }

        // 1. Verify Initial state (Initial state cannot be deferred)
        awaitIdle()
        assertEquals(TestStates.A, transition.targetState)
        assertNull(transition.pendingTargetState)

        // 2. Update target while content is NOT ready
        awaitIdle()
        state.defer(TestStates.B)

        awaitIdle()
        assertEquals(TestStates.A, transition.currentState)
        assertEquals(TestStates.A, transition.targetState)
        assertEquals(TestStates.B, transition.pendingTargetState)

        // 3. Make content ready and verify update
        awaitIdle()
        state.animateTo(TestStates.B)

        awaitIdle()
        assertEquals(TestStates.B, transition.targetState)
        assertNull(transition.pendingTargetState)
    }

    @Test
    fun deferredTransition_pendingState_isOverriddenByNewUpdates() = runComposeSwingTest {
        lateinit var transition: DeferredTransition<TestStates>
        lateinit var state: DeferredTransitionState<TestStates>

        setContent {
            state = remember { DeferredTransitionState(TestStates.A) }
            transition = rememberTransition(state)
        }

        // Update target to B (Deferred)
        awaitIdle()
        state.defer(TestStates.B)
        awaitIdle()
        assertEquals(TestStates.B, transition.pendingTargetState)

        // Update target to C (Should override pending B)
        awaitIdle()
        state.defer(TestStates.C)

        awaitIdle()
        assertEquals(TestStates.A, transition.currentState)
        assertEquals(TestStates.A, transition.targetState)
        assertEquals(TestStates.C, transition.pendingTargetState)
    }

    @Test
    fun deferredTransition_interruption_continuesCurrentAnimationUntilReady() =
        runComposeSwingTest {
            lateinit var transition: DeferredTransition<TestStates>
            lateinit var state: DeferredTransitionState<TestStates>
            var animatedValue by mutableIntStateOf(-1)

            setContent {
                state = remember { DeferredTransitionState(TestStates.A) }
                transition = rememberTransition(state)
                animatedValue =
                    transition
                        .animateInt(
                            label = "val",
                            transitionSpec = { tween(1000, easing = LinearEasing) },
                        ) { s ->
                            if (s == TestStates.A) 0 else 1000
                        }
                        .value
            }

            mainClock.autoAdvance = false
            awaitIdle()

            // 1. Start animating A -> B
            awaitIdle()
            state.animateTo(TestStates.B)
            advanceTimeByFrame()
            advanceTimeBy(500) // Advance to midpoint
            awaitIdle()
            assertEquals(500f, animatedValue.toFloat(), 20f)

            // 2. Interrupt back to A, but defer it
            awaitIdle()
            state.defer(TestStates.A)
            advanceTimeByFrame()
            awaitIdle()

            // Verify animation still targets B because A isn't ready
            assertEquals(TestStates.A, transition.pendingTargetState)
            assertEquals(TestStates.B, transition.targetState)

            advanceTimeBy(200)
            awaitIdle()
            assertEquals(700f, animatedValue.toFloat(), 20f) // Continues toward 1000

            // 3. Release A
            awaitIdle()
            state.animateTo(TestStates.A)
            advanceTimeByFrame()
            awaitIdle()

            // 4. Verify redirection toward A
            assertEquals(TestStates.A, transition.targetState)
            advanceTimeBy(2000)
            awaitIdle()
            assertEquals(0, animatedValue)
        }

    @Test
    fun deferredTransition_childTransition_defersSimultaneouslyWithParent() = runComposeSwingTest {
        lateinit var transition: DeferredTransition<TestStates>
        lateinit var state: DeferredTransitionState<TestStates>
        lateinit var childTransition: Transition<Boolean>

        setContent {
            state = remember { DeferredTransitionState(TestStates.A) }
            transition = rememberTransition(state)
            childTransition = transition.createChildTransition { it == TestStates.B }
        }

        // Defer target change
        awaitIdle()
        state.defer(TestStates.B)

        awaitIdle()
        assertEquals(TestStates.B, transition.pendingTargetState)
        assertEquals(TestStates.A, transition.targetState)
        assertFalse(childTransition.targetState, "Child should not have updated yet")

        // Release
        awaitIdle()
        state.animateTo(TestStates.B)

        awaitIdle()
        assertEquals(TestStates.B, transition.targetState)
        assertTrue(childTransition.targetState, "Child should update to B (true)")
    }

    @Test
    fun deferredTransition_animateFloat_respectsTransitionSpecAfterRelease() = runComposeSwingTest {
        lateinit var state: DeferredTransitionState<TestStates>
        var value by mutableStateOf(0f)

        setContent {
            state = remember { DeferredTransitionState(TestStates.A) }
            val transition = rememberTransition(state)
            value =
                transition
                    .animateFloat(
                        transitionSpec = {
                            if (TestStates.A isTransitioningTo TestStates.B) {
                                tween(100, easing = LinearEasing)
                            } else {
                                snap()
                            }
                        }
                    ) {
                        if (it == TestStates.B) 1f else 0f
                    }
                    .value
        }

        mainClock.autoAdvance = false
        awaitIdle()

        // Defer change to B
        awaitIdle()
        state.defer(TestStates.B)
        advanceTimeByFrame()
        awaitIdle()
        assertEquals(0f, value)

        // Release and check if tween(100) is used
        awaitIdle()
        state.animateTo(TestStates.B)
        advanceTimeByFrame()
        awaitIdle()

        advanceTimeBy(50)
        assertTrue(value > 0f && value < 1f, "Value should be mid-animation")

        advanceTimeBy(100)
        assertEquals(1f, value)
    }

    @Test
    fun deferredTransition_childTransition_inheritsPendingState() = runComposeSwingTest {
        lateinit var transition: DeferredTransition<TestStates>
        lateinit var state: DeferredTransitionState<TestStates>
        lateinit var childTransition: Transition<Boolean>

        setContent {
            state = remember { DeferredTransitionState(TestStates.A) }
            transition = rememberTransition(state)
            childTransition =
                transition.createChildTransition(label = "ChildTransition") { it == TestStates.B }
        }

        // 1. Initial state (No pending state)
        awaitIdle()
        assertNull(transition.pendingTargetState)
        assertNull(childTransition.pendingTargetState)

        // 2. Set parent to pending state
        awaitIdle()
        state.defer(TestStates.B)

        awaitIdle()
        assertEquals(TestStates.B, transition.pendingTargetState)
        // The child's pendingTargetState should now be its mapped targetState (true)
        // since the parent is pending B. child target state should remain false.
        assertEquals(true, childTransition.pendingTargetState)
        assertEquals(false, childTransition.targetState)

        // 4. Release parent
        awaitIdle()
        state.animateTo(TestStates.B)

        awaitIdle()
        assertNull(transition.pendingTargetState)
        assertNull(childTransition.pendingTargetState)
        assertTrue(childTransition.targetState)
    }

    @Test
    fun deferredTransition_animateToThirdState_overridesPendingState() = runComposeSwingTest {
        lateinit var transition: DeferredTransition<TestStates>
        lateinit var state: DeferredTransitionState<TestStates>

        setContent {
            state = remember { DeferredTransitionState(TestStates.A) }
            transition = rememberTransition(state)
        }

        // 1. Defer A -> B
        awaitIdle()
        state.defer(TestStates.B)
        awaitIdle()
        assertEquals(TestStates.B, transition.pendingTargetState)

        // 2. animateTo(C) - bypassing B
        awaitIdle()
        state.animateTo(TestStates.C)

        // 3. Verify targetState is C and pendingTargetState is null
        awaitIdle()
        assertEquals(TestStates.C, transition.targetState)
        assertNull(transition.pendingTargetState)
    }

    @Test
    fun deferredTransition_deferNewStateWhileAnimating() = runComposeSwingTest {
        lateinit var transition: DeferredTransition<TestStates>
        lateinit var state: DeferredTransitionState<TestStates>
        var animatedValue by mutableIntStateOf(-1)

        setContent {
            state = remember { DeferredTransitionState(TestStates.A) }
            transition = rememberTransition(state)
            animatedValue =
                transition
                    .animateInt(
                        label = "val",
                        transitionSpec = { tween(1000, easing = LinearEasing) },
                    ) { s ->
                        if (s == TestStates.A) 0 else 1000
                    }
                    .value
        }

        mainClock.autoAdvance = false
        awaitIdle()

        // 1. Start animating A -> B
        awaitIdle()
        state.animateTo(TestStates.B)
        advanceTimeBy(500)
        awaitIdle()
        assertEquals(500f, animatedValue.toFloat(), 20f)

        // 2. Call defer(C) mid-animation
        awaitIdle()
        state.defer(TestStates.C)
        advanceTimeByFrame()
        awaitIdle()

        // 3. Verify targetState remains B (animation continues) and pendingTargetState is C
        assertEquals(TestStates.B, transition.targetState)
        assertEquals(TestStates.C, transition.pendingTargetState)

        advanceTimeBy(100)
        awaitIdle()
        assertEquals(600f, animatedValue.toFloat(), 20f) // Still moving toward B
    }
}
