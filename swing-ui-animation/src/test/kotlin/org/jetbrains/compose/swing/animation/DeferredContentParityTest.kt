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

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The preparation and interruption behavior specific to deferred animated content. */
@OptIn(ExperimentalDeferredTransitionApi::class)
class DeferredContentParityTest {
    @Test
    fun `preparation gives each mutable content transform its full size`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var enteringSize = Dimension()
            var leavingSize = Dimension()
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    mutableTransformSpec = {
                        MutableContentTransform {
                            targetContentTransform { fullSize -> enteringSize = Dimension(fullSize) }
                            initialContentTransform { fullSize -> leavingSize = Dimension(fullSize) }
                        }
                    },
                ) { key -> Body(key, if (key == "a") 40 else 80) }
            }

            state.defer("b")
            awaitIdle()

            assertTrue(enteringSize.width > 0, "the announced content's full size was not captured: $enteringSize")
            assertTrue(leavingSize.width > 0, "the content on screen's full size was not captured: $leavingSize")
        }

    @Test
    fun `content stays held until readiness and then enters and exits`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        fadeIn(tween(160, easing = LinearEasing)) togetherWith
                            fadeOut(tween(160, easing = LinearEasing))
                    },
                ) { key -> Body(key, 40) }
            }
            val animated = container
            assertEquals(listOf("a"), animated.contentTexts())

            mainClock.autoAdvance = false
            state.defer("b")
            driveOneFrame()
            assertEquals(listOf("b", "a"), animated.contentTexts())
            assertEquals(0f, animated.contentAlpha(0), "the pending content entered before readiness")
            assertEquals(1f, animated.contentAlpha(1), "the outgoing content exited before readiness")

            state.animateTo("b")
            repeat(4) { driveOneFrame() }
            assertTrue(animated.contentAlpha(0) > 0f, "the content did not enter when the gate opened")
            assertTrue(animated.contentAlpha(1) < 1f, "the content did not start exiting when the gate opened")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf("b"), animated.contentTexts())
        }

    @Test
    fun `an immediate target change enters without a preparation phase`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        fadeIn(tween(160, easing = LinearEasing)) togetherWith
                            fadeOut(tween(160, easing = LinearEasing))
                    },
                ) { key -> Body(key, 40) }
            }
            val animated = container
            assertEquals(listOf("a"), animated.contentTexts())

            mainClock.autoAdvance = false
            state.animateTo("b")
            driveOneFrame()

            assertEquals(listOf("b", "a"), animated.contentTexts())

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf("b"), animated.contentTexts())
        }

    @Test
    fun `content size stays held until the deferred phase ends`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).DeferredAnimatedContent { key -> Body(key, if (key == "a") 40 else 80) }
            }
            val animated = container
            assertEquals(40, animated.width)

            state.defer("b")
            awaitIdle()
            assertEquals(40, animated.width, "the pending target resized the content container")

            state.animateTo("b")
            awaitIdle()
            assertEquals(80, animated.width, "the container did not resize after the phase ended")
        }

    @Test
    fun `a gate reopened during a running content transition does not pause it`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        (EnterTransition.None togetherWith ExitTransition.None) using
                            SizeTransform { _, _ -> tween(durationMillis = 320) }
                    },
                ) { key -> Body(key, if (key == "a") 40 else 160) }
            }
            val animated = container
            mainClock.autoAdvance = false

            state.animateTo("b")
            repeat(4) { driveOneFrame() }
            val beforeFlap = animated.width
            assertTrue(beforeFlap in 41 until 160, "precondition: size had not started travelling: $beforeFlap")

            state.defer("b")
            driveOneFrame()
            val heldAtFlap = animated.width
            driveOneFrame()

            assertTrue(
                animated.width > heldAtFlap,
                "reopening the gate paused the size animation at $heldAtFlap (now ${animated.width})",
            )
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(160, animated.width)
        }

    @Test
    fun `the transition stays at its current state until deferred content is ready`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            lateinit var transition: Transition<String>
            setContent {
                transition = rememberTransition(state)
                transition.DeferredAnimatedContent { key -> Body(key, 40) }
            }
            awaitIdle()
            assertEquals("a", transition.currentState)
            assertEquals("a", transition.targetState)

            state.defer("b")
            awaitIdle()

            assertEquals("a", transition.currentState, "preparation advanced the current state")
            assertEquals("a", transition.targetState, "preparation advanced the transition target")
            assertEquals("b", state.pendingTargetState)

            state.animateTo("b")
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals("b", transition.currentState)
        }

    @Test
    fun `an unready interruption lets the running size finish its segment before interrupting`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        (EnterTransition.None togetherWith ExitTransition.None) using
                            SizeTransform { _, _ -> tween(durationMillis = 320) }
                    },
                ) { key -> Body(key, if (key == "b") 160 else 40) }
            }
            val animated = container
            mainClock.autoAdvance = false

            state.animateTo("b")
            repeat(4) { driveOneFrame() }
            val beforePreparation = animated.width
            assertTrue(beforePreparation > 40, "precondition: A to B had not started: $beforePreparation")

            state.defer("c")
            driveOneFrame()
            val afterPreparation = animated.width
            driveOneFrame()
            assertTrue(
                animated.width > afterPreparation,
                "the unready C interrupted the running A to B size animation: $afterPreparation -> ${animated.width}",
            )

            state.animateTo("c")
            driveOneFrame()
            val beforeReadyInterruption = animated.width
            repeat(2) { driveOneFrame() }
            assertTrue(
                animated.width < beforeReadyInterruption,
                "the ready C did not interrupt the size animation towards B: " +
                    "$beforeReadyInterruption -> ${animated.width}",
            )
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(40, animated.width)
            assertEquals("c", container.contentText(0))
        }

    @Test
    fun `an unready interruption composes the new content and keeps both running contents`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) { key -> Body(key, 40) }
            }
            val animated = container
            mainClock.autoAdvance = false

            state.animateTo("b")
            repeat(4) { driveOneFrame() }
            assertTrue(animated.contentTexts().containsAll(setOf("a", "b")))

            state.defer("c")
            driveOneFrame()

            assertTrue(animated.contentTexts().containsAll(setOf("a", "b", "c")), "C was not composed for preparation")
            state.animateTo("c")
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf("c"), animated.contentTexts())
        }

    @Test
    fun `preview scope receives its announced states`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val seen = mutableListOf<Pair<String, String>>()
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    mutableTransformSpec = {
                        seen += initialState to targetState
                        null
                    },
                ) { key -> Body(key, 40) }
            }

            state.defer("b")
            awaitIdle()

            assertEquals(listOf("a" to "b"), seen)
        }

    @Test
    fun `preview scope receives running segment states when preparation interrupts it`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val seen = mutableListOf<Pair<String, String>>()
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                    mutableTransformSpec = {
                        seen += initialState to targetState
                        null
                    },
                ) { key -> Body(key, 40) }
            }
            mainClock.autoAdvance = false

            state.animateTo("b")
            driveOneFrame()
            assertEquals(emptyList(), seen)
            state.defer("a")
            driveOneFrame()

            assertEquals(listOf("b" to "a"), seen)
        }

    @Test
    fun `interrupting a preview phase preserves its original scope and does not reevaluate it`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val seen = mutableListOf<Pair<String, String>>()
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    mutableTransformSpec = {
                        seen += initialState to targetState
                        null
                    },
                ) { key -> Body(key, 40) }
            }

            state.defer("b")
            awaitIdle()
            assertEquals(listOf("a" to "b"), seen)

            state.defer("a")
            awaitIdle()
            assertEquals(listOf("a" to "b"), seen, "the interrupted preview was evaluated again")
        }

    @Test
    fun `preview offsets hand off into both content transitions`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var previewing by mutableIntStateOf(0)
            var previewOffset by mutableIntStateOf(0)
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        slideInHorizontally(tween(160)) { 200 } togetherWith
                            slideOutHorizontally(tween(160)) { -200 }
                    },
                    mutableTransformSpec = {
                        if (previewing != 0 && targetState != "a") {
                            MutableContentTransform {
                                targetContentTransform { offset = Point(previewOffset, 100) }
                                initialContentTransform { offset = Point(-previewOffset, 100) }
                            }
                        } else {
                            null
                        }
                    },
                ) { key -> Body(key, 100) }
            }
            val animated = container
            val initialContent = animated.contentPanel(0)
            assertEquals(0, initialContent.x)

            mainClock.autoAdvance = false
            previewing = 1
            state.defer("b")
            repeat(2) { driveOneFrame() }
            previewOffset = 50
            repeat(2) { driveOneFrame() }

            val entering = animated.contentPanel(0)
            val leaving = animated.contentPanel(1)
            assertEquals(250, entering.x, "enter did not combine its slide and preview offsets")
            assertEquals(-50, leaving.x, "exit did not combine its slide and preview offsets")
            assertEquals(100, entering.y)
            assertEquals(100, leaving.y)

            previewing = 0
            state.animateTo("b")
            driveOneFrame()
            mainClock.advanceTimeBy(80.milliseconds)
            driveOneFrame()

            assertTrue(entering.x in 1 until 250, "enter did not travel from preview offset 250: ${entering.x}")
            assertTrue(leaving.x < -50 && leaving.x > -200, "exit did not travel from preview offset -50: ${leaving.x}")
            assertTrue(entering.y in 1 until 100, "enter y did not return from preview offset 100: ${entering.y}")
            assertTrue(leaving.y in 1 until 100, "exit y did not return from preview offset 100: ${leaving.y}")

            mainClock.advanceTimeBy(1000.milliseconds)
            driveOneFrame()
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, entering.x)
            assertEquals(listOf("b"), animated.contentTexts())
        }

    @Test
    fun `returning to the original state during a preview scale is seamless`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var previewScale by mutableIntStateOf(100)
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f) togetherWith
                            scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f)
                    },
                    mutableTransformSpec = {
                        if (targetState != "a") {
                            MutableContentTransform {
                                targetContentTransform { scale = previewScale / 100f }
                                initialContentTransform { scale = previewScale / 100f }
                            }
                        } else {
                            null
                        }
                    },
                ) { key -> Body(key, 100) }
            }
            val animated = container
            val fullWidth = animated.contentPaintedWidth(animated.contentIndex("a"))
            assertEquals(100, fullWidth)

            mainClock.autoAdvance = false
            state.defer("b")
            previewScale = 80
            driveOneFrame()
            awaitIdle()
            val widthDuringPreview = animated.contentPaintedWidth(animated.contentIndex("a"))
            assertEquals(80.0, widthDuringPreview.toDouble(), 1.0)

            state.animateTo("a")
            driveOneFrame()
            val widthAfterInterrupt = animated.contentPaintedWidth(animated.contentIndex("a"))
            assertEquals(
                80.0,
                widthAfterInterrupt.toDouble(),
                1.0,
                "the original content jumped when returning to its state",
            )
            mainClock.advanceTimeBy(100.milliseconds)
            awaitIdle()
            val widthAfterAnimation = animated.contentPaintedWidth(animated.contentIndex("a"))
            assertTrue(
                widthAfterAnimation > widthAfterInterrupt,
                "the original content did not grow back toward full width: " +
                    "$widthAfterInterrupt -> $widthAfterAnimation",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                fullWidth.toDouble(),
                animated.contentPaintedWidth(animated.contentIndex("a")).toDouble(),
                1.0,
            )
        }

    @Test
    fun `the deferred interruption resolves the exit under its new transition spec`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val exits = mutableMapOf<Pair<String, String>, ExitTransition>()
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        val exit =
                            when (initialState to targetState) {
                                "a" to "b" -> fadeOut(tween(100))
                                "a" to "c" -> fadeOut(tween(500))
                                "b" to "a" -> fadeOut(tween(800))
                                else -> fadeOut()
                            }
                        exits[initialState to targetState] = exit
                        fadeIn() togetherWith exit
                    },
                ) { key -> Body(key, 40) }
            }

            state.defer("b")
            awaitIdle()
            assertEquals(fadeOut(tween(100)), exits["a" to "b"])

            state.animateTo("c")
            awaitIdle()

            assertEquals(fadeOut(tween(500)), exits["a" to "c"])
            assertEquals(fadeOut(tween(800)), exits["b" to "a"])
            assertEquals(listOf("c"), container.contentTexts())
        }
}

@Composable
private fun Body(
    text: String,
    width: Int,
) = Label(text = text, modifier = SwingModifier.preferredSize(width, 20).opaque(true))

private fun Container.contentTexts(): List<String> =
    (0 until componentCount).mapNotNull { index ->
        val panel = getComponent(index) as? Container ?: return@mapNotNull null
        (0 until panel.componentCount)
            .mapNotNull { child -> (panel.getComponent(child) as? JLabel)?.text }
            .singleOrNull()
    }

private fun Container.contentIndex(text: String): Int =
    (0 until componentCount).first { index ->
        val panel = getComponent(index) as? Container ?: return@first false
        (0 until panel.componentCount).any { child -> (panel.getComponent(child) as? JLabel)?.text == text }
    }
