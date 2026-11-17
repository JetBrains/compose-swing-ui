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

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.advanceTimeBy
import org.jetbrains.compose.swing.animation.core.rememberDeferredTransition
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.layout.layout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Point2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral parity cases ported from Compose Animation 1.13 DeferredAnimatedVisibilityTest. LargeClass is suppressed
 * to keep the upstream suite as ported, for direct source comparison.
 */
@Suppress("LargeClass")
@OptIn(ExperimentalAnimationApi::class)
class DeferredAnimatedVisibilityTest {
    @Test
    fun visibility_enterGateClosed_defersAnimationUntilReady() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var composed = false
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160, easing = LinearEasing)),
                        exit = fadeOut(tween(160, easing = LinearEasing)),
                    ) {
                        Block(width = 100, height = 100)
                        DisposableEffect(Unit) {
                            composed = true
                            onDispose { composed = false }
                        }
                    }
                }
            }
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
            assertFalse(composed)

            state.defer(true)
            awaitIdle()
            assertTrue(composed, "the pending content was not composed for preparation")
            assertEquals(1, onAllNodesWithTag("deferredVisibility").fetchAll().size)
            assertEquals(0f, onRoot().onChild().paintedAlpha(), "the enter ran while its gate was closed")

            mainClock.autoAdvance = false
            state.animateTo(true)
            advanceTimeBy(50)
            assertEquals(1, onAllNodesWithTag("deferredVisibility").fetchAll().size)
            advanceTimeBy(200)
            assertEquals(1f, onRoot().onChild().paintedAlpha())
        }

    @Test
    fun visibility_exitGateClosed_holdsContentInExitStateUntilReady() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var composed = false
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(160)),
                    ) {
                        Block()
                        DisposableEffect(Unit) {
                            composed = true
                            onDispose { composed = false }
                        }
                    }
                }
            }
            awaitIdle()
            assertTrue(composed)

            mainClock.autoAdvance = false
            state.defer(false)
            advanceTimeBy(500)
            awaitIdle()
            assertTrue(composed, "the content left while the exit gate was closed")
            assertEquals(1f, onRoot().onChild().paintedAlpha(), "the exit ran while its gate was closed")

            state.animateTo(false)
            advanceTimeBy(200)
            assertFalse(composed, "the content remained after the exit finished")
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun visibility_enterGateFlapping_cannotBeClosedOnceStarted() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        visible = { it },
                        modifier = SwingModifier.testTag("deferredVisibility"),
                    ) {
                        Block(width = 100, height = 100)
                    }
                }
            }
            state.defer(true)
            awaitIdle()
            mainClock.autoAdvance = false
            state.animateTo(true)
            advanceTimeBy(100)
            state.defer(true)
            advanceTimeBy(1000)
            assertEquals(1f, onRoot().onChild().paintedAlpha())
        }

    @Test
    fun visibility_exitGateFlapping_cannotBeClosedOnceStarted() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(320, easing = LinearEasing)),
                    ) {
                        Block()
                    }
                }
            }
            mainClock.autoAdvance = false
            state.defer(false)
            awaitIdle()
            state.animateTo(false)
            advanceTimeBy(100)
            state.defer(false)
            advanceTimeBy(1000)
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun transition_childState_remainsInPreEnterDuringPreparation() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var childState: EnterExitState? = null
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(1000, easing = LinearEasing)),
                    ) {
                        childState = transition.currentState
                        Block()
                    }
                }
            }
            state.defer(true)
            awaitIdle()
            assertEquals(EnterExitState.PreEnter, childState)

            mainClock.autoAdvance = false
            state.animateTo(true)
            advanceTimeBy(50)
            assertEquals(EnterExitState.PreEnter, childState)
            repeat(70) { mainClock.advanceTimeByFrame() }
            assertEquals(EnterExitState.Visible, childState)
        }

    @Test
    fun layout_size_isHeldAtInitialStateDuringPreparation() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var animatedSize = Dimension(-1, -1)
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        visible = { it },
                        enter = expandIn(tween(100, easing = LinearEasing)) { Dimension(0, 0) },
                        modifier =
                            SwingModifier.testTag("deferredVisibility").layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    animatedSize = Dimension(placeable.width, placeable.height)
                                    placeable.place(0, 0)
                                }
                            },
                    ) {
                        Block(width = 100, height = 100)
                    }
                }
            }
            state.defer(true)
            awaitIdle()
            assertEquals(Dimension(0, 0), animatedSize)

            mainClock.autoAdvance = false
            state.animateTo(true)
            advanceTimeBy(50)
            val expandedSize = animatedSize
            val child = onNodeWithTag("deferredVisibility").onChild().fetch()
            assertTrue(
                expandedSize.width > 0,
                "the expand had not advanced after the gate opened: container=$expandedSize, " +
                    "child=${child.size}, childPreferred=${child.preferredSize}",
            )
            advanceTimeBy(100)
            assertEquals(
                Dimension(100, 100),
                animatedSize,
            )
        }

    @Test
    fun interruption_isDeferred_whenGateIsClosed() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var animatedSize = Dimension(-1, -1)
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        visible = { it },
                        enter = expandIn(tween(320, easing = LinearEasing)) { Dimension(0, 0) },
                        exit = shrinkOut(tween(160, easing = LinearEasing)) { Dimension(0, 0) },
                        modifier =
                            SwingModifier.testTag("deferredVisibility").layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    animatedSize = Dimension(placeable.width, placeable.height)
                                    placeable.place(0, 0)
                                }
                            },
                    ) {
                        Block()
                    }
                }
            }
            mainClock.autoAdvance = false
            state.animateTo(true)
            repeat(5) { mainClock.advanceTimeByFrame() }
            val sizeBeforeDeferredExit = animatedSize.width
            assertTrue(sizeBeforeDeferredExit > 0)

            state.defer(false)
            repeat(3) { mainClock.advanceTimeByFrame() }
            repeat(3) { mainClock.advanceTimeByFrame() }
            val sizeWhileExitIsHeld = animatedSize.width
            assertTrue(
                sizeWhileExitIsHeld > sizeBeforeDeferredExit,
                "the enter reversed while the exit gate was closed: $sizeBeforeDeferredExit -> $sizeWhileExitIsHeld",
            )

            state.animateTo(false)
            repeat(5) { mainClock.advanceTimeByFrame() }
            val sizeBeforeExit = animatedSize.width
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertTrue(
                animatedSize.width < sizeBeforeExit,
                "the size did not decrease after the exit gate opened",
            )
            repeat(70) { mainClock.advanceTimeByFrame() }
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun visibility_previewScaleSustained_whenNoScaleInTransitions() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = onRoot().onChild().paintedWidth()

            state.defer(false)
            awaitIdle()
            previewScale = 0.5f
            awaitIdle()
            assertEquals(fullWidth / 2, onRoot().onChild().paintedWidth())

            mainClock.autoAdvance = false
            state.animateTo(false)
            repeat(6) { mainClock.advanceTimeByFrame() }
            assertEquals(fullWidth / 2, onRoot().onChild().paintedWidth())
        }

    @Test
    fun visibility_previewOffsetSustained_whenNoOffsetInTransitions() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewOffset by mutableStateOf(0)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) offset = Point(previewOffset, 0)
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                        mutableTransform = transform,
                    ) { DeferredBlock() }
                }
            }

            state.defer(false)
            awaitIdle()
            previewOffset = 10
            awaitIdle()
            assertEquals(10, paintedDeferredContentLeft())

            mainClock.autoAdvance = false
            state.animateTo(false)
            repeat(6) { mainClock.advanceTimeByFrame() }
            assertEquals(10, paintedDeferredContentLeft())
        }

    @Test
    fun visibility_previewScale_handoffToExitTransition() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = scaleIn(tween(160)),
                        exit = scaleOut(tween(320), targetScale = 0f),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = onRoot().onChild().paintedWidth()

            mainClock.autoAdvance = false
            state.defer(false)
            repeat(2) { mainClock.advanceTimeByFrame() }
            previewScale = 0.5f
            mainClock.advanceTimeByFrame()
            assertEquals(fullWidth / 2, onRoot().onChild().paintedWidth())

            state.animateTo(false)
            repeat(6) { mainClock.advanceTimeByFrame() }
            val width = onRoot().onChild().paintedWidth()
            assertTrue(width in 1 until fullWidth / 2, "the exit did not carry the preview scale down: $width")
            repeat(70) { mainClock.advanceTimeByFrame() }
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun visibility_previewOffset_handoffToEnterTransition() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var previewOffset by mutableStateOf(0)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) offset = Point(previewOffset, 0)
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = slideInHorizontally(tween(320)) { it },
                        exit = slideOutHorizontally(tween(320)) { it },
                        mutableTransform = transform,
                    ) { DeferredBlock() }
                }
            }

            state.defer(true)
            awaitIdle()
            previewOffset = 10
            mainClock.advanceTimeByFrame()
            assertEquals(10, paintedDeferredContentLeft())

            mainClock.autoAdvance = false
            state.animateTo(true)
            repeat(6) { mainClock.advanceTimeByFrame() }
            assertTrue(
                paintedDeferredContentLeft() in 1 until 10,
                "the enter did not carry the preview offset in: ${paintedDeferredContentLeft()}",
            )
            repeat(70) { mainClock.advanceTimeByFrame() }
            assertEquals(0, paintedDeferredContentLeft())
        }

    @Test
    fun visibility_midAnimation_updateTargetState_combinesTransformAndTransition() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f),
                        exit = scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            mainClock.autoAdvance = false
            state.animateTo(true)
            repeat(30) { mainClock.advanceTimeByFrame() }
            val widthDuringEnter = onRoot().onChild().paintedWidth()
            assertTrue(widthDuringEnter in 1 until 100)

            previewScale = 0.5f
            state.defer(false)
            mainClock.advanceTimeByFrame()
            val widthWithPreview = onRoot().onChild().paintedWidth()
            assertTrue(
                widthWithPreview < 100 * 0.55f,
                "the width did not animate towards the preview scale: $widthDuringEnter -> $widthWithPreview",
            )

            mainClock.advanceTimeByFrame()
            val widthOnNextFrame = onRoot().onChild().paintedWidth()
            assertTrue(
                widthOnNextFrame <= widthWithPreview,
                "the visual width did not stay at the preview target: $widthWithPreview -> $widthOnNextFrame",
            )
        }

    @Test
    fun visibility_doublePreview_doesNotSnap() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            var applyScalePreview by mutableStateOf(true)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null && applyScalePreview) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = onRoot().onChild().paintedWidth()
            mainClock.autoAdvance = false

            state.defer(false)
            previewScale = 0.5f
            repeat(2) { mainClock.advanceTimeByFrame() }
            assertEquals(fullWidth / 2, onRoot().onChild().paintedWidth())

            state.animateTo(false)
            repeat(6) { mainClock.advanceTimeByFrame() }
            val widthDuringExit = onRoot().onChild().paintedWidth()
            assertEquals(fullWidth / 2, widthDuringExit)

            applyScalePreview = false
            state.defer(true)
            repeat(2) { mainClock.advanceTimeByFrame() }
            val widthAfterSecondPreview = onRoot().onChild().paintedWidth()
            state.animateTo(true)
            mainClock.advanceTimeByFrame()
            assertTrue(
                widthAfterSecondPreview < fullWidth * 0.9f,
                "the scale snapped to full size after the second preview: " +
                    "$widthDuringExit -> $widthAfterSecondPreview of $fullWidth",
            )
            repeat(70) { mainClock.advanceTimeByFrame() }
            assertEquals(fullWidth, onRoot().onChild().paintedWidth())
        }

    @Test
    fun visibility_previewScale_handoff_thenInterrupted_isSeamless() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f),
                        exit = scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = onRoot().onChild().paintedWidth()
            mainClock.autoAdvance = false

            state.defer(false)
            previewScale = 0.8f
            mainClock.advanceTimeByFrame()
            assertEquals((fullWidth * 0.8f).toInt(), onRoot().onChild().paintedWidth())

            state.animateTo(false)
            mainClock.advanceTimeByFrame()
            advanceTimeBy(500)
            val widthBeforeInterruption = onRoot().onChild().paintedWidth()
            assertEquals(fullWidth * 0.4f, widthBeforeInterruption.toFloat(), 5f)

            state.animateTo(true)
            mainClock.advanceTimeByFrame()
            val widthAfterInterruption = onRoot().onChild().paintedWidth()
            assertEquals(widthBeforeInterruption.toFloat(), widthAfterInterruption.toFloat(), 5f)
            repeat(6) { mainClock.advanceTimeByFrame() }
            assertTrue(onRoot().onChild().paintedWidth() > widthAfterInterruption)
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(fullWidth.toFloat(), onRoot().onChild().paintedWidth().toFloat(), 1f)
        }

    @Test
    fun visibility_previewScale_handoff_sustainUnlessSpecified_thenInterrupted_isSeamless() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update { scale = previewScale }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(1000, easing = LinearEasing)),
                        exit = fadeOut(tween(1000, easing = LinearEasing)),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = onRoot().onChild().paintedWidth()
            mainClock.autoAdvance = false

            state.defer(false)
            previewScale = 0.8f
            mainClock.advanceTimeByFrame()
            assertEquals(fullWidth * 0.8f, onRoot().onChild().paintedWidth().toFloat(), 1f)

            state.animateTo(false)
            mainClock.advanceTimeByFrame()
            advanceTimeBy(500)
            val widthBeforeInterruption = onRoot().onChild().paintedWidth()
            assertEquals(fullWidth * 0.8f, widthBeforeInterruption.toFloat(), 1f)

            state.animateTo(true)
            mainClock.advanceTimeByFrame()
            val widthAfterInterruption = onRoot().onChild().paintedWidth()
            assertTrue(
                widthAfterInterruption < fullWidth * 0.95f,
                "the width snapped back to full size after the interruption: " +
                    "$widthBeforeInterruption -> $widthAfterInterruption",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(fullWidth.toFloat(), onRoot().onChild().paintedWidth().toFloat(), 1f)
        }

    @Test
    fun visibility_previewScale_handoffUnspecifiedEnter_animatesToVisibleValue_thenInterrupted_isSeamless() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update { scale = previewScale }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(1000, easing = LinearEasing), initialAlpha = 1f),
                        exit = fadeOut(tween(1000, easing = LinearEasing)),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            mainClock.autoAdvance = false

            state.defer(true)
            previewScale = 0.8f
            mainClock.advanceTimeByFrame()
            assertEquals(80f, onRoot().onChild().paintedWidth().toFloat(), 1f)

            state.animateTo(true)
            mainClock.advanceTimeByFrame()
            advanceTimeBy(50)
            val widthBeforeInterruption = onRoot().onChild().paintedWidth()

            state.animateTo(false)
            mainClock.advanceTimeByFrame()
            val widthAfterInterruption = onRoot().onChild().paintedWidth()
            assertTrue(
                widthAfterInterruption < 95,
                "the width snapped back to full size after the interruption: " +
                    "$widthBeforeInterruption -> $widthAfterInterruption",
            )

            advanceTimeBy(100)
            val widthLater = onRoot().onChild().paintedWidth()
            assertTrue(
                widthLater > widthAfterInterruption,
                "the width did not grow towards full size: $widthAfterInterruption -> $widthLater",
            )

            var lastPaintedWidth = widthLater
            mainClock.advanceTimeUntil {
                val width = onRoot().onChild().paintedWidth()
                if (width > 0) lastPaintedWidth = width
                width == 0
            }
            assertEquals(100f, lastPaintedWidth.toFloat(), 5f)

            mainClock.autoAdvance = true
            awaitIdle()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(0)
        }

    @Test
    fun deferredAnimatedVisibility_interruptedEnter_doesNotFreezeVeil() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var activeHandoff by mutableStateOf(false)
            var capturedColor = Color(0, 0, 0, 255)
            val handoffTransform = MutableTransform()
            handoffTransform.update { scale = 0.5f }
            mainClock.autoAdvance = false
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter =
                            unveilIn(
                                initialColor = Color.BLACK,
                                animationSpec = tween(500, easing = LinearEasing),
                            ),
                        exit = ExitTransition.None,
                        mutableTransform = if (activeHandoff) handoffTransform else null,
                    ) {
                        val veilAnim = transition.animations.find { it.label.contains("veil") }
                        if (veilAnim != null) {
                            capturedColor = veilAnim.value as Color
                        }
                        Block()
                    }
                }
            }

            state.animateTo(true)
            mainClock.advanceTimeByFrame()
            advanceTimeBy(250)

            activeHandoff = true
            state.defer(false)
            mainClock.advanceTimeByFrame()

            activeHandoff = false
            state.animateTo(false)
            mainClock.advanceTimeByFrame()
            val handoffVeilAlpha = capturedColor.alpha

            advanceTimeBy(100)

            assertTrue(
                handoffVeilAlpha > capturedColor.alpha,
                "expected the veil alpha to animate towards 0, but it froze or increased " +
                    "(handoff alpha: $handoffVeilAlpha, current alpha: ${capturedColor.alpha})",
            )
        }

    @Test
    fun visibility_previewOffset_handoffVelocity() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var velocity = Point2D.Float()
            val transform = MutableTransform(offsetVelocityProvider = { velocity })
            transform.update { if (state.pendingTargetState != null) offset = Point(10, 0) }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160)),
                        exit = slideOutHorizontally(spring(stiffness = Spring.StiffnessVeryLow)) { it },
                        mutableTransform = transform,
                    ) { DeferredBlock() }
                }
            }
            mainClock.autoAdvance = false

            state.defer(false)
            mainClock.advanceTimeByFrame()
            assertEquals(10, paintedDeferredContentLeft())
            velocity = Point2D.Float(2000f, 0f)
            state.animateTo(false)
            repeat(3) { mainClock.advanceTimeByFrame() }
            val xWithVelocity = paintedDeferredContentLeft()

            mainClock.autoAdvance = true
            state.animateTo(true)
            awaitIdle()
            mainClock.autoAdvance = false
            velocity = Point2D.Float()
            state.defer(false)
            mainClock.advanceTimeByFrame()
            assertEquals(10, paintedDeferredContentLeft())
            state.animateTo(false)
            repeat(3) { mainClock.advanceTimeByFrame() }
            val xWithoutVelocity = paintedDeferredContentLeft()
            assertTrue(
                xWithVelocity > xWithoutVelocity,
                "the supplied positive velocity did not carry the offset farther: $xWithoutVelocity -> $xWithVelocity",
            )
        }

    @Test
    fun visibility_previewScale_interrupt_deferred_by_original_state_is_seamless() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberDeferredTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f),
                        exit = scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = onRoot().onChild().paintedWidth()
            mainClock.autoAdvance = false

            state.defer(false)
            previewScale = 0.8f
            mainClock.advanceTimeByFrame()
            assertEquals(fullWidth * 0.8f, onRoot().onChild().paintedWidth().toFloat(), 1f)

            state.animateTo(true)
            mainClock.advanceTimeByFrame()
            val widthAfterInterruption = onRoot().onChild().paintedWidth()
            assertEquals((fullWidth * 0.8f).toInt(), widthAfterInterruption)
            repeat(6) { mainClock.advanceTimeByFrame() }
            assertTrue(onRoot().onChild().paintedWidth() > widthAfterInterruption)
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(fullWidth.toFloat(), onRoot().onChild().paintedWidth().toFloat(), 1f)
        }
}
