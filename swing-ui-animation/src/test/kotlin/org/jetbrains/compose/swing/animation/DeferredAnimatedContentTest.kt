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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.rememberDeferredTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The preparation and interruption behavior specific to deferred animated content. LargeClass and PreferImportsOverFqn
 * are suppressed to keep the upstream suite as ported, for direct source comparison.
 */
@Suppress("LargeClass", "PreferImportsOverFqn")
class DeferredAnimatedContentTest {
    /** Also covers upstream immediateExitTest and verifyExitAnimationAfterGateOpens. */
    @Test
    fun waitToEnterTest() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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
            mainClock.advanceTimeByFrame()
            assertEquals(listOf("b", "a"), animated.contentTexts())
            assertEquals(0f, animated.contentAlpha(0), "the pending content entered before readiness")
            assertEquals(1f, animated.contentAlpha(1), "the outgoing content exited before readiness")

            state.animateTo("b")
            repeat(4) { mainClock.advanceTimeByFrame() }
            assertTrue(animated.contentAlpha(0) > 0f, "the content did not enter when the gate opened")
            assertTrue(animated.contentAlpha(1) < 1f, "the content did not start exiting when the gate opened")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf("b"), animated.contentTexts())
        }

    @Test
    fun immediateEnterTest() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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
            mainClock.advanceTimeByFrame()

            assertEquals(listOf("b", "a"), animated.contentTexts())

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf("b"), animated.contentTexts())
        }

    @Test
    fun sizeChangeDelayedUntilReady() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent { key ->
                    Body(key, if (key == "a") 40 else 80)
                }
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
    fun flappingTest() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        (EnterTransition.None togetherWith ExitTransition.None) using
                            SizeTransform { _, _ -> tween(durationMillis = 320) }
                    },
                ) { key -> Body(key, if (key == "a") 40 else 160) }
            }
            val animated = container
            mainClock.autoAdvance = false

            state.animateTo("b")
            repeat(4) { mainClock.advanceTimeByFrame() }
            val beforeFlap = animated.width
            assertTrue(beforeFlap in 41 until 160, "precondition: size had not started travelling: $beforeFlap")

            state.defer("b")
            mainClock.advanceTimeByFrame()
            val heldAtFlap = animated.width
            mainClock.advanceTimeByFrame()

            assertTrue(
                animated.width > heldAtFlap,
                "reopening the gate paused the size animation at $heldAtFlap (now ${animated.width})",
            )
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(160, animated.width)
        }

    @Test
    fun verifyCurrentStateRemainsDuringPreparation() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            lateinit var transition: Transition<String>
            setContent {
                transition = rememberDeferredTransition(state)
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
    fun interruptionRespectedOnlyWhenReady() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        (EnterTransition.None togetherWith ExitTransition.None) using
                            SizeTransform { _, _ -> tween(durationMillis = 320) }
                    },
                ) { key -> Body(key, if (key == "b") 160 else 40) }
            }
            val animated = container
            mainClock.autoAdvance = false

            state.animateTo("b")
            repeat(4) { mainClock.advanceTimeByFrame() }
            val beforePreparation = animated.width
            assertTrue(beforePreparation > 40, "precondition: A to B had not started: $beforePreparation")

            state.defer("c")
            mainClock.advanceTimeByFrame()
            val afterPreparation = animated.width
            mainClock.advanceTimeByFrame()
            assertTrue(
                animated.width > afterPreparation,
                "the unready C interrupted the running A to B size animation: $afterPreparation -> ${animated.width}",
            )

            state.animateTo("c")
            mainClock.advanceTimeByFrame()
            val beforeReadyInterruption = animated.width
            repeat(2) { mainClock.advanceTimeByFrame() }
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
    fun interruptionRespectedOnlyWhenReady_verifyComposition() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) { key -> Body(key, 40) }
            }
            val animated = container
            mainClock.autoAdvance = false

            state.animateTo("b")
            repeat(4) { mainClock.advanceTimeByFrame() }
            assertTrue(animated.contentTexts().containsAll(setOf("a", "b")))

            state.defer("c")
            mainClock.advanceTimeByFrame()

            assertTrue(animated.contentTexts().containsAll(setOf("a", "b", "c")), "C was not composed for preparation")
            state.animateTo("c")
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf("c"), animated.contentTexts())
        }

    @Test
    fun predictiveBackMutableTransformDataTest() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var enteringSize = Dimension()
            var leavingSize = Dimension()
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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
    fun animatedContent_previewTransforms_handoffCorrectly() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var previewing by mutableIntStateOf(0)
            var previewOffset by mutableIntStateOf(0)
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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
            repeat(2) { mainClock.advanceTimeByFrame() }
            previewOffset = 50
            repeat(2) { mainClock.advanceTimeByFrame() }

            val entering = animated.contentPanel(0)
            val leaving = animated.contentPanel(1)
            assertEquals(50, entering.x, "enter did not override its slide with the preview offset")
            assertEquals(-50, leaving.x, "exit did not override its slide with the preview offset")
            assertEquals(100, entering.y)
            assertEquals(100, leaving.y)

            previewing = 0
            state.animateTo("b")
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(80.milliseconds)
            mainClock.advanceTimeByFrame()

            assertTrue(entering.x in 1 until 50, "enter did not travel from preview offset 50: ${entering.x}")
            assertTrue(leaving.x < -50 && leaving.x > -200, "exit did not travel from preview offset -50: ${leaving.x}")
            assertTrue(entering.y in 1 until 100, "enter y did not return from preview offset 100: ${entering.y}")
            assertTrue(leaving.y in 1 until 100, "exit y did not return from preview offset 100: ${leaving.y}")

            mainClock.advanceTimeBy(1000.milliseconds)
            mainClock.advanceTimeByFrame()
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, entering.x)
            assertEquals(listOf("b"), animated.contentTexts())
        }

    @Test
    fun previewScope_providesInitialAndTargetState() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val seen = mutableListOf<Pair<String, String>>()
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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
    fun previewScope_providesCorrectStates_onInterruption() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val seen = mutableListOf<Pair<String, String>>()
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                    mutableTransformSpec = {
                        seen += initialState to targetState
                        null
                    },
                ) { key -> Body(key, 40) }
            }
            mainClock.autoAdvance = false

            state.animateTo("b")
            mainClock.advanceTimeByFrame()
            assertEquals(emptyList(), seen)
            state.defer("a")
            mainClock.advanceTimeByFrame()

            assertEquals(listOf("b" to "a"), seen)
        }

    @Test
    fun previewScope_providesCorrectStates_onInterruptionDuringPreviewPhase() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val seen = mutableListOf<Pair<String, String>>()
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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
    fun animatedContent_previewScale_interrupt_deferred_by_original_state_is_seamless() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var previewScale by mutableIntStateOf(100)
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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
            mainClock.advanceTimeByFrame()
            awaitIdle()
            val widthDuringPreview = animated.contentPaintedWidth(animated.contentIndex("a"))
            assertEquals(80.0, widthDuringPreview.toDouble(), 1.0)

            state.animateTo("a")
            mainClock.advanceTimeByFrame()
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
    fun animatedContent_interruption_during_deferred_phase_uses_correct_spec() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val exits = mutableMapOf<Pair<String, String>, ExitTransition>()
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
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

    @Test
    fun animatedContent_previewScaleTransforms_handoffUsesTweenSpec() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var previewing by mutableStateOf(false)
            var previewScale by mutableIntStateOf(100)
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f) togetherWith
                            scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f)
                    },
                    mutableTransformSpec = {
                        if (previewing && targetState != "a") {
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
            awaitIdle()
            mainClock.autoAdvance = false

            previewing = true
            state.defer("b")
            previewScale = 50
            mainClock.advanceTimeByFrame()
            awaitIdle()

            previewing = false
            state.animateTo("b")
            mainClock.advanceTimeByFrame()
            awaitIdle()

            mainClock.advanceTimeBy(500.milliseconds)
            awaitIdle()

            assertEquals(75.0, animated.contentPaintedWidth(animated.contentIndex("b")).toDouble(), 2.0)
        }

    @Test
    fun animatedContent_interruption_during_regular_phase_uses_correct_spec() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val exits = mutableMapOf<Pair<String, String>, ExitTransition>()
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        val exit =
                            when (initialState to targetState) {
                                "a" to "b" -> fadeOut(tween(100))
                                "a" to "c" -> fadeOut(tween(500))
                                "b" to "c" -> fadeOut(tween(800))
                                else -> fadeOut()
                            }
                        exits[initialState to targetState] = exit
                        fadeIn() togetherWith exit
                    },
                ) { key -> Body(key, 40) }
            }
            mainClock.autoAdvance = false

            state.animateTo("b")
            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertEquals(fadeOut(tween(100)), exits["a" to "b"])

            state.animateTo("c")
            mainClock.advanceTimeByFrame()
            awaitIdle()

            assertEquals(setOf("a", "b", "c"), container.contentTexts().toSet())
            assertEquals(fadeOut(tween(100)), exits["a" to "b"], "the exit of a was evaluated again")
            assertEquals(fadeOut(tween(800)), exits["b" to "c"], "the exit of b did not use the b to c spec")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf("c"), container.contentTexts())
        }

    @Test
    fun nonMutatingNodeDoesNotReceiveStaleMutations() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        val enter =
                            fadeIn(tween(1000, easing = LinearEasing)) +
                                slideIn(tween(1000, easing = LinearEasing)) { Point(0, 0) }
                        val exit =
                            fadeOut(tween(1000, easing = LinearEasing)) +
                                slideOut(tween(1000, easing = LinearEasing)) { Point(0, 0) }
                        enter togetherWith exit
                    },
                    mutableTransformSpec = {
                        if (initialState == "a" && targetState == "b") {
                            MutableContentTransform {
                                initialContentTransform { offset = Point(100, 100) }
                            }
                        } else {
                            null
                        }
                    },
                ) { key -> Body(key, 100) }
            }
            val animated = container
            awaitIdle()
            mainClock.autoAdvance = false

            state.defer("b")
            mainClock.advanceTimeByFrame()
            assertEquals(Point(100, 100), animated.contentPanel(animated.contentIndex("a")).location)

            state.animateTo("b")
            mainClock.advanceTimeBy(100.milliseconds)
            awaitIdle()

            val before = animated.contentPanel(animated.contentIndex("a")).x
            assertTrue(before in 80..95, "the offset was not animating back to 0: $before")

            state.defer("c")
            mainClock.advanceTimeByFrame()
            awaitIdle()

            val after = animated.contentPanel(animated.contentIndex("a")).x
            assertTrue(after < 100, "the offset jumped, so stale mutations were applied: $before -> $after")
        }

    @Test
    fun deferAfterExitFinishedRecoversExitStateTest() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        slideInHorizontally(tween(100000, easing = LinearEasing)) { 100 } togetherWith
                            slideOutHorizontally(tween(100, easing = LinearEasing)) { -100 }
                    },
                    mutableTransformSpec = {
                        MutableContentTransform {
                            initialContentTransform {}
                            targetContentTransform {}
                        }
                    },
                ) { key -> Body(key, 100) }
            }
            val animated = container
            awaitIdle()

            mainClock.autoAdvance = false
            state.animateTo("b")
            mainClock.advanceTimeBy(5000.milliseconds)
            awaitIdle()

            assertFalse("a" in animated.contentTexts(), "a's exit finished, so its panel is disposed")

            state.defer("a")
            mainClock.advanceTimeBy(1.milliseconds)
            awaitIdle()

            val afterDefer = animated.contentPanel(animated.contentIndex("a")).x
            assertTrue(afterDefer < -90, "a jumped back from its exit: $afterDefer")
        }

    @Test
    fun testVeilMatchParentSize_isPreservedDuringHandoff() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
                    mutableTransformSpec = {
                        MutableContentTransform(targetVeilMatchParentSize = true) {
                            targetContentTransform {
                                scale = 0.5f
                                veil = Color.RED
                            }
                        }
                    },
                ) { Block(width = 200, height = 200) }
            }
            val animated = container
            awaitIdle()
            mainClock.autoAdvance = false

            state.defer("b")
            mainClock.advanceTimeBy(80.milliseconds)
            awaitIdle()
            val preview = animated.captureToImage()
            assertEquals(Color.RED.rgb, preview.getRGB(0, 0))

            state.animateTo("b")
            mainClock.advanceTimeBy(64.milliseconds)
            awaitIdle()

            val image = animated.captureToImage()
            assertTrue(Color(image.getRGB(0, 0), true).red > 0, "the red veil did not cover the blue corner")
        }

    @Test
    fun animatedContent_manualGesture_snapsWhenIdle_springsWhenAnimating() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            var previewScale by mutableIntStateOf(100)
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f) togetherWith
                            scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f)
                    },
                    mutableTransformSpec = {
                        MutableContentTransform {
                            targetContentTransform { scale = previewScale / 100f }
                            initialContentTransform { scale = previewScale / 100f }
                        }
                    },
                ) { key -> Body(key, 100) }
            }
            val animated = container
            awaitIdle()
            val fullWidth = animated.contentPaintedWidth(animated.contentIndex("a"))
            mainClock.autoAdvance = false

            // An idle content snaps to the gesture value.
            state.defer("b")
            previewScale = 50
            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertEquals(fullWidth * 0.5, animated.contentPaintedWidth(animated.contentIndex("a")).toDouble(), 1.0)

            mainClock.autoAdvance = true
            previewScale = 100
            state.animateTo("a")
            awaitIdle()
            mainClock.autoAdvance = false

            // An animating content springs to the gesture value.
            state.animateTo("b")
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(100.milliseconds)
            state.defer("a")
            previewScale = 50
            mainClock.advanceTimeByFrame()
            awaitIdle()

            val initialCatchUp = animated.contentPaintedWidth(animated.contentIndex("a"))
            assertTrue(
                kotlin.math.abs(initialCatchUp - fullWidth * 0.5) > 5.0,
                "the width snapped to half instead of catching up: $initialCatchUp",
            )

            mainClock.advanceTimeBy(5000.milliseconds)
            awaitIdle()
            assertEquals(fullWidth * 0.5, animated.contentPaintedWidth(animated.contentIndex("a")).toDouble(), 1.0)
        }

    @Test
    fun testUnmutatedPropertiesDoNotJump() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f) togetherWith
                            scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f)
                    },
                    mutableTransformSpec = {
                        MutableContentTransform {
                            // Only the offset is mutated, never the scale.
                            targetContentTransform { offset = Point(100, 100) }
                        }
                    },
                ) { key -> Body(key, 100) }
            }
            val animated = container
            awaitIdle()
            mainClock.autoAdvance = false

            state.animateTo("b")
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(500.milliseconds)
            awaitIdle()
            val halfway = animated.contentPaintedWidth(animated.contentIndex("b"))
            assertTrue(halfway > 10, "the width was not halfway: $halfway")

            state.defer("a")
            mainClock.advanceTimeByFrame()
            awaitIdle()
            mainClock.advanceTimeBy(100.milliseconds)
            awaitIdle()

            val afterGesture = animated.contentPaintedWidth(animated.contentIndex("b"))
            assertTrue(afterGesture < 100, "the width jumped to its full size: $halfway -> $afterGesture")
        }

    @Test
    fun animatedContent_consecutiveGestures_resetsTransformScope() =
        runComposeSwingTest {
            val state = DeferredTransitionState("c")
            var gestureSlide by mutableIntStateOf(0)
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        slideInHorizontally(tween(1000, easing = LinearEasing)) { -it } togetherWith
                            slideOutHorizontally(tween(1000, easing = LinearEasing)) { -it }
                    },
                    mutableTransformSpec = {
                        val slide = Point(gestureSlide, 0)
                        when (targetState) {
                            "b" -> MutableContentTransform { targetContentTransform { offset = slide } }
                            else -> MutableContentTransform {}
                        }
                    },
                ) { key -> Body(key, 100) }
            }
            val animated = container
            awaitIdle()

            gestureSlide = -80
            state.defer("b")
            mainClock.advanceTimeByFrame()
            awaitIdle()

            mainClock.autoAdvance = false
            state.animateTo("b")
            mainClock.advanceTimeByFrame()

            state.defer("a")
            mainClock.advanceTimeByFrame()

            mainClock.autoAdvance = true
            awaitIdle()

            val x = animated.contentPanel(animated.contentIndex("b")).x
            assertTrue(x > -20, "page b was pulled back to the leftover -80 offset: $x")
        }

    @Test
    fun deferredTransition_evaluatesTransitionSpec_withPendingScope() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val evaluated = mutableListOf<Pair<String, String>>()
            setContent {
                rememberDeferredTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        evaluated += initialState to targetState
                        fadeIn(tween(100, easing = LinearEasing)) togetherWith
                            fadeOut(tween(100, easing = LinearEasing))
                    },
                ) { key -> Body(key, 40) }
            }
            awaitIdle()

            state.animateTo("b")
            awaitIdle()
            evaluated.clear()

            state.defer("a")
            awaitIdle()

            assertTrue(evaluated.contains("b" to "a"), "the spec was not evaluated with the pending scope: $evaluated")
            assertTrue(!evaluated.contains("a" to "b"), "the spec evaluated the old root segment: $evaluated")
        }

    @Test
    fun animatedContent_identicalContentKey_gestureAndTransition_isStaticWithoutOffset() =
        runComposeSwingTest {
            val initialPage = Page("c", 1)
            val targetPage = Page("c", 2)
            val state = DeferredTransitionState(initialPage)
            val mutableTransform =
                MutableContentTransform {
                    targetContentTransform { offset = Point(-100, -100) }
                    initialContentTransform { offset = Point(-100, -100) }
                }
            setContent {
                rememberDeferredTransition(state, label = "identicalKeyTest").DeferredAnimatedContent(
                    contentKey = { it.id },
                    transitionSpec = {
                        slideInHorizontally { 500 } + slideInVertically { 500 } togetherWith
                            slideOutHorizontally { -500 } + slideOutVertically { -500 }
                    },
                    mutableTransformSpec = { mutableTransform },
                ) { page -> Body(page.id, 100) }
            }
            val animated = container
            awaitIdle()
            assertEquals(Point(0, 0), animated.contentPanel(0).location)

            state.defer(targetPage)
            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertEquals(Point(0, 0), animated.contentPanel(0).location)

            state.animateTo(targetPage)
            awaitIdle()
            assertEquals(Point(0, 0), animated.contentPanel(0).location)
        }
}

private data class Page(
    val id: String,
    val instance: Int,
)

@Composable
private fun Body(
    text: String,
    width: Int,
    height: Int = 20,
) = Label(text = text, style = {
    preferredSize(width, height)
    opaque(true)
})

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
