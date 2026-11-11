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
import androidx.compose.runtime.setValue
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest

/**
 * Pins that a child transition's animated alpha reaches the widget it drives, read back through the
 * `:swing-ui-test` finders, mid-flight and at its end value.
 */
class TransitionHarnessTest {

    private enum class AnimStates {
        From,
        To,
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
