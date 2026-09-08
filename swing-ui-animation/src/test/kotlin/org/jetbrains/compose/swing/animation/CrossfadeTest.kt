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
import org.jetbrains.compose.swing.animation.core.AnimationConstants.DefaultDurationMillis
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The box a [Crossfade] stands on, which the harness mounts as the root's one child. */
private val ComposeSwingTest.crossfade: Container
    get() = root.getComponent(0) as Container

/** What every content on screen reads, in no particular order: upstream replaces a content in place. */
private fun Container.texts(): Set<String> =
    components.mapTo(mutableSetOf()) { ((it as Container).getComponent(0) as JLabel).text }

/** The faded box holding the content that reads [text]. */
private fun Container.panelOf(text: String): Container =
    components.map { it as Container }.firstOrNull { (it.getComponent(0) as JLabel).text == text }
        ?: error("no content on screen reads \"$text\", the container held ${texts()}")

/** How opaque this box paints its opaque content at a pixel inside it, from `0f` to `1f`. */
private fun Component.paintedAlpha(): Float = Color(captureToImage().getRGB(2, 2), true).alpha / 255f

/** A content of a known size, so the container's own size can be asserted in pixels. */
@Composable
private fun Body(
    text: String,
    width: Int = 40,
) = Label(text = text, modifier = SwingModifier.preferredSize(width, height = 20).opaque(true))

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

            driveOneFrame()
            mainClock.advanceTimeBy(duration.milliseconds)
            showFirst = false
            mainClock.advanceTimeBy(duration.milliseconds)
            driveOneFrame()
            driveOneFrame()

            showFirst = true
            mainClock.advanceTimeBy(duration.milliseconds)
            driveOneFrame()
            driveOneFrame()

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
                    Body(text = state.toString())
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

            driveOneFrame()
            driveOneFrame()
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
    fun `a settled container holds the content of one state`() =
        runComposeSwingTest {
            setContent {
                Crossfade(targetState = "a") { Body(text = it) }
            }
            assertEquals(setOf("a"), crossfade.texts(), "a settled container held content for more than one state")
        }

    @Test
    fun `both contents fade while the crossfade runs, and the one being left is gone once it settles`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(320)) { Body(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(3) { driveOneFrame() }
            assertEquals(
                setOf("a", "b"),
                crossfade.texts(),
                "the content being left was dropped before its fade out ran",
            )
            assertTrue(
                crossfade.panelOf("a").paintedAlpha() < 1f,
                "the content being left was not fading out: ${crossfade.panelOf("a").paintedAlpha()}",
            )
            assertTrue(
                crossfade.panelOf("b").paintedAlpha() < 1f,
                "the arriving content was not fading in: ${crossfade.panelOf("b").paintedAlpha()}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(setOf("b"), crossfade.texts(), "the content that finished fading out was left in the tree")
            assertEquals(
                1f,
                crossfade.panelOf("b").paintedAlpha(),
                "the arriving content did not settle at full opacity",
            )
        }

    @Test
    fun `a state change while a crossfade runs retargets it rather than starting it over`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(320)) { Body(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(3) { driveOneFrame() }
            val interrupted = crossfade.panelOf("b")
            val alphaAtInterruption = interrupted.paintedAlpha()
            assertTrue(alphaAtInterruption < 0.5f, "the crossfade had already settled, alpha was $alphaAtInterruption")

            // An interrupted transition takes the state it was heading to as the one it now leaves, so the
            // content of "b" fades out from the opacity it had reached rather than from a full fade in.
            state = "c"
            val alphas =
                List(4) {
                    driveOneFrame()
                    crossfade.panelOf("b").paintedAlpha()
                }
            assertTrue(
                crossfade.texts().containsAll(listOf("b", "c")),
                "the retargeted crossfade dropped a content: ${crossfade.texts()}",
            )
            assertSame(interrupted, crossfade.panelOf("b"), "the content the crossfade was heading to was rebuilt")
            assertTrue(
                alphas.all { it < 0.5f },
                "the fade started over from a fully faded-in \"b\" at $alphaAtInterruption: $alphas",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(setOf("c"), crossfade.texts(), "the crossfade did not settle on the state it now targets")
        }

    @Test
    fun `the container does not animate its size, and takes the room both contents need at once`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(320)) {
                    Body(text = it, width = if (it == "a") 40 else 160)
                }
            }
            assertEquals(40, crossfade.preferredSize.width, "the settled container did not take the room it needs")

            mainClock.autoAdvance = false
            state = "b"
            val widths =
                List(8) {
                    driveOneFrame()
                    crossfade.preferredSize.width
                }
            assertTrue(
                widths.none { it in 41..159 },
                "the container animated its size instead of taking the room both contents need: $widths",
            )
            // The arriving content is measured one pass after it is mounted, so the room it needs is taken
            // from the second frame on.
            assertEquals(160, widths.last(), "the container did not take the room the arriving content needs")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(160, crossfade.preferredSize.width, "the container did not settle at the arriving size")
        }

    @Test
    fun `the fade runs under the spec the container is handed`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(2000)) { Body(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            awaitIdle()
            // Long past the default fade, which would have settled and dropped the content being left.
            mainClock.advanceTimeBy(800.milliseconds)
            awaitIdle()
            assertEquals(
                setOf("a", "b"),
                crossfade.texts(),
                "the fade had already settled, so it ran under the default spec rather than the one declared",
            )
        }

    @Test
    fun `the default fade runs for the default duration`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state) { Body(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            awaitIdle()
            mainClock.advanceTimeBy((DefaultDurationMillis - 50).milliseconds)
            awaitIdle()
            assertEquals(
                setOf("a", "b"),
                crossfade.texts(),
                "the content being left was already gone short of ${DefaultDurationMillis}ms, so the default " +
                    "spec is quicker than the default tween",
            )

            mainClock.advanceTimeBy(150.milliseconds)
            awaitIdle()
            assertEquals(
                setOf("b"),
                crossfade.texts(),
                "the content being left was still there past ${DefaultDurationMillis}ms, so the default spec " +
                    "is slower than the default tween",
            )
        }

    @Test
    fun `the container is laid out under the modifier it is handed`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, modifier = SwingModifier.preferredSize(200, 100)) {
                    Body(text = it, width = if (it == "a") 40 else 320)
                }
            }
            assertEquals(Dimension(200, 100), crossfade.preferredSize, "the declared size did not reach the container")

            state = "b"
            awaitIdle()
            assertEquals(
                Dimension(200, 100),
                crossfade.preferredSize,
                "the arriving content, wider than the declared size, took the container's size back",
            )
        }

    @Test
    fun `two states that share a content key share one content`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                updateTransition(state).Crossfade(contentKey = { it.length }) { Body(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            assertEquals(1, crossfade.componentCount, "two states with one content key were composed twice")
            assertEquals(setOf("b"), crossfade.texts(), "the content did not follow the state it shares a key with")
        }

    @Test
    fun `a null state is a state like any other`() =
        runComposeSwingTest {
            var state by mutableStateOf<String?>(null)
            setContent {
                Crossfade(targetState = state) { Body(text = it ?: "none") }
            }
            assertEquals(setOf("none"), crossfade.texts(), "the content of a null state was not shown")

            state = "other"
            awaitIdle()
            assertEquals(setOf("other"), crossfade.texts(), "the container did not fade away from a null state")
        }
}
