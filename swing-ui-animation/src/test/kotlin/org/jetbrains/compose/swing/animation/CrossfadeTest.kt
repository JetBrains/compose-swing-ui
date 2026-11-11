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
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The box a [Crossfade] stands on, which the harness mounts as the root's one child. */
internal val ComposeSwingTest.crossfade: Container
    get() = root.getComponent(0) as Container

/** What every content on screen reads, in no particular order: upstream replaces a content in place. */
internal fun Container.texts(): Set<String> =
    components.mapTo(mutableSetOf()) { ((it as Container).getComponent(0) as JLabel).text }

/** A content of a known size, so the container's own size can be asserted in pixels. */
@Composable
internal fun CrossfadeBody(
    text: String,
    width: Int = 40,
) = Label(text = text, style = {
    preferredSize(width, height = 20)
    opaque(true)
})

class CrossfadeTest {
    @Test
    fun crossfadeTest_rememberSaveableIsNotRecreatedForScreens() =
        runComposeSwingTest {
            mainClock.autoAdvance = false

            val duration = 100
            var showFirst by mutableStateOf(true)
            var counter = 1
            var counter1 = 0
            var counter2 = 0
            setContent {
                val saveableStateHolder = rememberSaveableStateHolder()
                Crossfade(showFirst, animationSpec = tween(duration)) { state ->
                    saveableStateHolder.SaveableStateProvider(state) {
                        if (state) {
                            counter1 = rememberSaveable { counter++ }
                        } else {
                            counter2 = rememberSaveable { counter++ }
                        }
                    }
                }
            }

            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(duration.milliseconds)
            showFirst = false
            mainClock.advanceTimeBy(duration.milliseconds)
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()

            showFirst = true
            mainClock.advanceTimeBy(duration.milliseconds)
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()

            assertEquals(1, counter1)
            assertEquals(2, counter2)
        }

    @Test
    fun crossfadeTest_contentKey() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            var targetState by mutableStateOf(1)
            val list = mutableListOf<Int>()
            var isRunning = false
            var currentState = 0
            var observedTargetState = 0
            setContent {
                val transition = updateTransition(targetState)
                isRunning = transition.isRunning
                currentState = transition.currentState
                observedTargetState = transition.targetState
                val holder = rememberSaveableStateHolder()
                transition.Crossfade(contentKey = { it > 0 }) { state ->
                    if (state > 0) {
                        holder.SaveableStateProvider(true) {
                            var count by rememberSaveable { mutableStateOf(0) }
                            LaunchedEffect(Unit) { list.add(++count) }
                        }
                    }
                    CrossfadeBody(text = state.toString())
                }
                LaunchedEffect(Unit) {
                    assertFalse(transition.isRunning)
                    targetState = 2
                    withFrameMillis {
                        assertFalse(transition.isRunning)
                        assertEquals(transition.currentState, transition.targetState)
                        targetState = -1
                    }
                    withFrameMillis { assertTrue(transition.isRunning) }
                }
            }

            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            assertTrue(isRunning, "the different content key did not start a transition")
            assertEquals(1, currentState)
            assertEquals(-1, observedTargetState)

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf(1), list)
            targetState = 1
            awaitIdle()
            assertEquals(listOf(1, 2), list)
        }

    @Test
    fun crossfadeTest_showsContent() =
        runComposeSwingTest {
            setContent {
                Crossfade(targetState = "a") { CrossfadeBody(text = it) }
            }
            assertEquals(setOf("a"), crossfade.texts(), "a settled container held content for more than one state")
        }

    @Test
    fun nullInitialValue() =
        runComposeSwingTest {
            var state by mutableStateOf<String?>(null)
            setContent {
                Crossfade(targetState = state) { CrossfadeBody(text = it ?: "none") }
            }
            assertEquals(setOf("none"), crossfade.texts(), "the content of a null state was not shown")

            state = "other"
            awaitIdle()
            assertEquals(setOf("other"), crossfade.texts(), "the container did not fade away from a null state")
        }
}
