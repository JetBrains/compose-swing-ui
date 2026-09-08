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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.advanceTimeBy
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.layout.layout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Point2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavioral parity cases ported from Compose Animation 1.12 DeferredAnimatedVisibilityTest. */
@OptIn(ExperimentalDeferredTransitionApi::class)
class DeferredVisibilityParityTest {
    @Test
    fun `visibility enter gate closed defers animation until ready`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var composed = false
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160, easing = LinearEasing)),
                        exit = fadeOut(tween(160, easing = LinearEasing)),
                    ) {
                        Block(width = 100, height = 100)
                        androidx.compose.runtime.DisposableEffect(Unit) {
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
            assertEquals(0f, deferredContainer().paintedAlpha(), "the enter ran while its gate was closed")

            mainClock.autoAdvance = false
            state.animateTo(true)
            advanceTimeBy(50)
            assertEquals(1, onAllNodesWithTag("deferredVisibility").fetchAll().size)
            advanceTimeBy(200)
            assertEquals(1f, deferredContainer().paintedAlpha())
        }

    @Test
    fun `visibility exit gate closed holds content in its exit state`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var composed = false
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(160)),
                    ) {
                        Block()
                        androidx.compose.runtime.DisposableEffect(Unit) {
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
            assertEquals(1f, deferredContainer().paintedAlpha(), "the exit ran while its gate was closed")

            state.animateTo(false)
            advanceTimeBy(200)
            assertFalse(composed, "the content remained after the exit finished")
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun `an enter gate cannot be closed after the enter starts`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            assertEquals(1f, deferredContainer().paintedAlpha())
        }

    @Test
    fun `an exit gate cannot be closed after the exit starts`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
    fun `the child transition stays at pre enter during preparation`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var childState: EnterExitState? = null
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            repeat(70) { driveOneFrame() }
            assertEquals(EnterExitState.Visible, childState)
        }

    @Test
    fun `expand size stays at its initial value during preparation`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var animatedSize = Dimension(-1, -1)
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            val childSize = content().size
            val childPreferredSize = content().preferredSize
            assertTrue(
                expandedSize.width > 0,
                "the expand had not advanced after the gate opened: container=$expandedSize, " +
                    "child=$childSize, childPreferred=$childPreferredSize",
            )
            advanceTimeBy(100)
            assertEquals(
                Dimension(100, 100),
                animatedSize,
            )
        }

    @Test
    fun `an interruption waits for the closed exit gate`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var animatedSize = Dimension(-1, -1)
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            repeat(5) { driveOneFrame() }
            val sizeBeforeDeferredExit = animatedSize.width
            assertTrue(sizeBeforeDeferredExit > 0)

            state.defer(false)
            repeat(3) { driveOneFrame() }
            repeat(3) { driveOneFrame() }
            val sizeWhileExitIsHeld = animatedSize.width
            assertTrue(
                sizeWhileExitIsHeld > sizeBeforeDeferredExit,
                "the enter reversed while the exit gate was closed: $sizeBeforeDeferredExit -> $sizeWhileExitIsHeld",
            )

            state.animateTo(false)
            repeat(5) { driveOneFrame() }
            val sizeBeforeExit = animatedSize.width
            repeat(3) { driveOneFrame() }
            assertTrue(
                animatedSize.width < sizeBeforeExit,
                "the size did not decrease after the exit gate opened",
            )
            repeat(70) { driveOneFrame() }
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun `a scale preview stays in place through a fade handoff`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = deferredContainer().paintedWidth()

            state.defer(false)
            awaitIdle()
            previewScale = 0.5f
            awaitIdle()
            assertEquals(fullWidth / 2, deferredContainer().paintedWidth())

            mainClock.autoAdvance = false
            state.animateTo(false)
            repeat(6) { driveOneFrame() }
            assertEquals(fullWidth / 2, deferredContainer().paintedWidth())
        }

    @Test
    fun `an offset preview stays in place through a fade handoff`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewOffset by mutableStateOf(0)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) offset = Point(previewOffset, 0)
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            repeat(6) { driveOneFrame() }
            assertEquals(10, paintedDeferredContentLeft())
        }

    @Test
    fun `a scale preview hands off to the exit scale`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = scaleIn(tween(160)),
                        exit = scaleOut(tween(320), targetScale = 0f),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = deferredContainer().paintedWidth()

            mainClock.autoAdvance = false
            state.defer(false)
            repeat(2) { driveOneFrame() }
            previewScale = 0.5f
            driveOneFrame()
            assertEquals(fullWidth / 2, deferredContainer().paintedWidth())

            state.animateTo(false)
            repeat(6) { driveOneFrame() }
            val width = deferredContainer().paintedWidth()
            assertTrue(width in 1 until fullWidth / 2, "the exit did not carry the preview scale down: $width")
            repeat(70) { driveOneFrame() }
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun `an offset preview hands off to the enter slide`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var previewOffset by mutableStateOf(0)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) offset = Point(previewOffset, 0)
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            driveOneFrame()
            assertEquals(DEFERRED_BLOCK_WIDTH + 10, paintedDeferredContentLeft())

            mainClock.autoAdvance = false
            state.animateTo(true)
            repeat(6) { driveOneFrame() }
            assertTrue(
                paintedDeferredContentLeft() in 1 until DEFERRED_BLOCK_WIDTH + 10,
                "the enter did not carry the preview offset in: ${paintedDeferredContentLeft()}",
            )
            repeat(70) { driveOneFrame() }
            assertEquals(0, paintedDeferredContentLeft())
        }

    @Test
    fun `a preview scale combines with an enter already in progress`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            repeat(30) { driveOneFrame() }
            val widthDuringEnter = deferredContainer().paintedWidth()
            assertTrue(widthDuringEnter in 1 until 100)

            previewScale = 0.5f
            state.defer(false)
            driveOneFrame()
            val widthWithPreview = deferredContainer().paintedWidth()
            assertTrue(widthWithPreview < widthDuringEnter)

            driveOneFrame()
            val widthOnNextFrame = deferredContainer().paintedWidth()
            assertTrue(
                widthOnNextFrame > widthWithPreview,
                "the enter stopped progressing beneath the preview: $widthWithPreview -> $widthOnNextFrame",
            )
        }

    @Test
    fun `a second preview does not snap straight to full scale`() =
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
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = deferredContainer().paintedWidth()
            mainClock.autoAdvance = false

            state.defer(false)
            previewScale = 0.5f
            repeat(2) { driveOneFrame() }
            assertEquals(fullWidth / 2, deferredContainer().paintedWidth())

            state.animateTo(false)
            repeat(6) { driveOneFrame() }
            val widthDuringExit = deferredContainer().paintedWidth()
            assertEquals(fullWidth / 2, widthDuringExit)

            applyScalePreview = false
            state.defer(true)
            repeat(2) { driveOneFrame() }
            val widthAfterSecondPreview = deferredContainer().paintedWidth()
            state.animateTo(true)
            driveOneFrame()
            assertTrue(
                widthAfterSecondPreview < fullWidth * 0.9f,
                "the scale snapped to full size after the second preview: " +
                    "$widthDuringExit -> $widthAfterSecondPreview of $fullWidth",
            )
            repeat(70) { driveOneFrame() }
            assertEquals(fullWidth, deferredContainer().paintedWidth())
        }

    @Test
    fun `an exit scale handoff interrupted by a new target remains seamless`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f),
                        exit = scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = deferredContainer().paintedWidth()
            mainClock.autoAdvance = false

            state.defer(false)
            previewScale = 0.8f
            driveOneFrame()
            assertEquals((fullWidth * 0.8f).toInt(), deferredContainer().paintedWidth())

            state.animateTo(false)
            driveOneFrame()
            advanceTimeBy(500)
            val widthBeforeInterruption = deferredContainer().paintedWidth()
            assertEquals(fullWidth * 0.4f, widthBeforeInterruption.toFloat(), 5f)

            state.animateTo(true)
            driveOneFrame()
            val widthAfterInterruption = deferredContainer().paintedWidth()
            assertEquals(widthBeforeInterruption.toFloat(), widthAfterInterruption.toFloat(), 5f)
            repeat(6) { driveOneFrame() }
            assertTrue(deferredContainer().paintedWidth() > widthAfterInterruption)
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(fullWidth.toFloat(), deferredContainer().paintedWidth().toFloat(), 1f)
        }

    @Test
    fun `an offset handoff uses the supplied velocity`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var velocity = Point2D.Float()
            val transform = MutableTransform(offsetVelocityProvider = { velocity })
            transform.update { if (state.pendingTargetState != null) offset = Point(10, 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
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
            driveOneFrame()
            assertEquals(10, paintedDeferredContentLeft())
            velocity = Point2D.Float(2000f, 0f)
            state.animateTo(false)
            repeat(3) { driveOneFrame() }
            val xWithVelocity = paintedDeferredContentLeft()

            mainClock.autoAdvance = true
            state.animateTo(true)
            awaitIdle()
            mainClock.autoAdvance = false
            velocity = Point2D.Float()
            state.defer(false)
            driveOneFrame()
            assertEquals(10, paintedDeferredContentLeft())
            state.animateTo(false)
            repeat(3) { driveOneFrame() }
            val xWithoutVelocity = paintedDeferredContentLeft()
            assertTrue(
                xWithVelocity > xWithoutVelocity,
                "the supplied positive velocity did not carry the offset farther: $xWithoutVelocity -> $xWithVelocity",
            )
        }

    @Test
    fun `a deferred scale interrupted by its original state returns without a jump`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            var previewScale by mutableStateOf(1f)
            val transform = MutableTransform()
            transform.update {
                if (state.pendingTargetState != null) scale = previewScale
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = scaleIn(tween(1000, easing = LinearEasing), initialScale = 0f),
                        exit = scaleOut(tween(1000, easing = LinearEasing), targetScale = 0f),
                        mutableTransform = transform,
                    ) { Block(width = 100, height = 100) }
                }
            }
            val fullWidth = deferredContainer().paintedWidth()
            mainClock.autoAdvance = false

            state.defer(false)
            previewScale = 0.8f
            driveOneFrame()
            assertEquals(fullWidth * 0.8f, deferredContainer().paintedWidth().toFloat(), 1f)

            state.animateTo(true)
            driveOneFrame()
            val widthAfterInterruption = deferredContainer().paintedWidth()
            assertEquals((fullWidth * 0.8f).toInt(), widthAfterInterruption)
            repeat(6) { driveOneFrame() }
            assertTrue(deferredContainer().paintedWidth() > widthAfterInterruption)
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(fullWidth.toFloat(), deferredContainer().paintedWidth().toFloat(), 1f)
        }
}
