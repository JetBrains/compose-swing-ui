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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest

/**
 * Pins how the ported `Transition` suite hosts its composition: the shipped `:swing-ui-test`
 * harness, driven with the frame length upstream's asserted values are computed from.
 *
 * Each case below replays a sequence taken from upstream's `TransitionTest`, keeping its assertions
 * and tolerances, so a change in either the harness or the vendored engine that would break the
 * port breaks here first.
 */
class TransitionHarnessTest {

    private enum class AnimStates {
        From,
        To,
    }

    /**
     * The opening of upstream's `addAnimationToCompletedChildTransition`, whose per-frame values
     * are exact to 0.1f and so only come out right on upstream's own 16ms frame - see
     * [UpstreamFrameMillis].
     */
    @Test
    fun frameSteppedTransitionValuesMatchUpstream() = runComposeSwingTest {
        var value1 = 0f
        var value2 = 0f
        var value3 = 0f
        lateinit var coroutineScope: CoroutineScope
        val state = MutableTransitionState(false)

        // Before setContent: the harness settles the initial composition under whatever mode this
        // clock is in, and a settle with frames of its own would run the animation off on its own.
        mainClock.autoAdvance = false
        setContent {
            coroutineScope = rememberCoroutineScope()
            val parent = rememberTransition(state)
            value1 =
                parent
                    .animateFloat({ tween(1600, easing = LinearEasing) }) { if (it) 1000f else 0f }
                    .value

            val child = parent.createChildTransition { it }
            value2 =
                child
                    .animateFloat({ tween(160, easing = LinearEasing) }) { if (it) 1000f else 0f }
                    .value

            value3 =
                if (!parent.targetState) {
                    child
                        .animateFloat({ tween(160, easing = LinearEasing) }) {
                            if (it) 0f else 1000f
                        }
                        .value
                } else {
                    0f
                }
        }
        coroutineScope.launch { state.targetState = true }
        advanceTimeByFrame() // wait for composition
        assertEquals(0f, value1, 0f)
        assertEquals(0f, value2, 0f)
        assertEquals(0f, value3, 0f)

        advanceTimeByFrame() // latch the animation start value
        assertEquals(0f, value1, 0f)
        assertEquals(0f, value2, 0f)
        assertEquals(0f, value3, 0f)

        advanceTimeByFrame() // first frame of animation
        assertEquals(10f, value1, 0.1f)
        assertEquals(100f, value2, 0.1f)
        assertEquals(0f, value3, 0f) // hasn't started yet

        advanceTimeBy(160)
        assertEquals(110f, value1, 0.1f)
        assertEquals(1000f, value2, 0f)
        assertEquals(0f, value3, 0f) // hasn't started yet
    }

    /**
     * The `transitionTest` idiom: frames left to the harness, a target written from the test body,
     * and one idle gate carrying the transition through to its end values.
     */
    @Test
    fun autoAdvanceCarriesATransitionToItsTargetValues() = runComposeSwingTest {
        var target by mutableStateOf(AnimStates.From)
        var animFloat = -1f
        setContent {
            val transition = updateTransition(target)
            animFloat =
                transition
                    .animateFloat(transitionSpec = { tween(durationMillis = 1000) }) {
                        when (it) {
                            AnimStates.From -> 0f
                            AnimStates.To -> 1f
                        }
                    }
                    .value
        }
        assertEquals(0f, animFloat)

        target = AnimStates.To
        awaitIdle()
        assertEquals(1f, animFloat)

        target = AnimStates.From
        awaitIdle()
        assertEquals(0f, animFloat)
    }

    /**
     * A child transition's value read back off the widget it drives, which is the port of
     * upstream's assertion on a pixel an alpha reaching 1f produced: the finders reach the
     * component, and a value mid-flight is a value the tree already shows.
     */
    @Test
    fun aChildTransitionAlphaReachesTheWidgetTheFindersSee() = runComposeSwingTest {
        var target by mutableStateOf(AnimStates.From)

        mainClock.autoAdvance = false
        setContent {
            val transition = updateTransition(target)
            val alpha =
                transition
                    .createChildTransition { it }
                    .animateFloat({ tween(160, easing = LinearEasing) }) {
                        if (it == AnimStates.To) 1f else 0f
                    }
            Label(text = "${alpha.value}", modifier = SwingModifier.testTag("alpha"))
        }
        onNodeWithTag("alpha").assertTextEquals("0.0")

        target = AnimStates.To
        advanceTimeBy(3 * UpstreamFrameMillis)
        val midFlight = onNodeWithTag("alpha").fetch<JLabel>().text.toFloat()
        assertTrue(midFlight > 0f && midFlight < 1f, "expected a mid-flight alpha, got $midFlight")

        advanceTimeBy(160)
        onNodeWithTag("alpha").assertTextEquals("1.0")
    }
}
