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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FastOutLinearInEasing
import org.jetbrains.compose.swing.animation.core.FastOutSlowInEasing
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.LinearOutSlowInEasing
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.swing.animation.core.advanceTimeBy as advanceAnimationTimeBy

/** Upstream visibility assertions adapted to Swing types and its frame clock. */
class AnimationUpstreamParityTest {
    @Test
    fun `seeking visibility preserves upstream enter and exit values at each play time`() =
        runComposeSwingTest {
            var rootTransition: Transition<Boolean>? = null
            setContent {
                Box {
                    updateTransition(false, label = "test")
                        .apply { rootTransition = this }
                        .AnimatedVisibility(
                            visible = { it },
                            enter =
                                fadeIn(animationSpec = tween(200, easing = LinearEasing)) +
                                    slideInVertically(animationSpec = tween(200, easing = LinearEasing)) { 200 },
                            exit =
                                scaleOut(animationSpec = tween(200, easing = LinearEasing)) +
                                    shrinkHorizontally(animationSpec = tween(200, easing = LinearEasing)),
                        ) {
                            Box(SwingModifier.preferredSize(width = 200, height = 200))
                        }
                }
            }
            awaitIdle()

            val transition = requireNotNull(rootTransition)
            seekVisibility(transition, visible = true)
            seekVisibility(transition, visible = false)
        }

    @Test
    fun `content leaves only after one frame and one additional millisecond`() =
        runComposeSwingTest {
            var startExit by mutableStateOf(false)
            var contentPresent = false
            setContent {
                val state = remember { MutableTransitionState(true) }
                state.targetState = !startExit
                AnimatedVisibility(
                    visibleState = state,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        contentPresent = true
                        onDispose { contentPresent = false }
                    }
                }
            }
            assertTrue(contentPresent)

            mainClock.autoAdvance = false
            startExit = true
            driveOneFrame()
            assertTrue(contentPresent, "content left at the inclusive frame boundary")
            mainClock.advanceTimeBy(1.milliseconds, ignoreFrameDuration = true)
            awaitIdle()

            assertFalse(contentPresent, "content remained after the non-inclusive frame boundary")
        }

    @Test
    fun `slide places the content at the upstream offset on every frame`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var visibilityTransition: Transition<EnterExitState>? = null
            setContent { SlideVisibilityContent(visible) { visibilityTransition = it } }
            mainClock.autoAdvance = false
            awaitIdle()
            visible = true
            driveOneFrame()

            assertSlideEnter { visibilityTransition }
            val content = animatedContainer().fetch<Container>().getComponent(0)

            visible = false
            driveOneFrame()
            assertEquals(EnterExitState.PostExit, requireNotNull(visibilityTransition).targetState)
            assertSlideExit(visibilityTransition = { visibilityTransition }, content = content)
            assertEquals(0, root.componentCount)
        }

    @Test
    fun `scale paints the expected width and pivot position on every frame`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var visibilityTransition: Transition<EnterExitState>? = null
            val enterEasing = FastOutLinearInEasing
            val exitEasing = FastOutSlowInEasing
            setContent {
                Box {
                    AnimatedVisibility(
                        visible = visible,
                        enter = scaleIn(animationSpec = tween(500, easing = enterEasing)),
                        exit = scaleOut(animationSpec = tween(300, easing = exitEasing)),
                    ) {
                        visibilityTransition = transition
                        Block(width = 20, height = 20)
                    }
                }
            }
            awaitIdle()
            mainClock.autoAdvance = false
            awaitIdle()
            visible = true
            driveOneFrame()
            requireNotNull(visibilityTransition)

            var frame = 0
            while (frame < 32) {
                val currentTransition = requireNotNull(visibilityTransition)
                assertScaleRaster(
                    currentTransition,
                    enterEasing.transform((currentTransition.playTimeNanos / 1_000_000f / 500f).coerceIn(0f, 1f)),
                )
                driveOneFrame()
                if (requireNotNull(visibilityTransition).currentState == EnterExitState.Visible) break
                frame++
            }
            assertEquals(
                EnterExitState.Visible,
                requireNotNull(visibilityTransition).currentState,
                "the enter exceeded its duration",
            )
            assertScaleRaster(requireNotNull(visibilityTransition), 1f)
            visible = false
            driveOneFrame()
            assertEquals(EnterExitState.PostExit, requireNotNull(visibilityTransition).targetState)
            frame = 0
            while (frame < 20) {
                val currentTransition = requireNotNull(visibilityTransition)
                val fraction =
                    exitEasing.transform((currentTransition.playTimeNanos / 1_000_000f / 300f).coerceIn(0f, 1f))
                assertScaleRaster(currentTransition, 1f - fraction)
                driveOneFrame()
                if (requireNotNull(visibilityTransition).currentState == EnterExitState.PostExit) break
                frame++
            }
            assertEquals(
                EnterExitState.PostExit,
                requireNotNull(visibilityTransition).currentState,
                "the exit exceeded its duration",
            )
        }

    @Test
    fun `removing the veil while enter is interrupted lets the veil finish clearing`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var veilColor = Color(0, 0, 0, 0)
            var enterTransition by
                mutableStateOf(unveilIn(tween(160, easing = LinearEasing), initialColor = Color.RED))
            var exitTransition by
                mutableStateOf(veilOut(tween(160, easing = LinearEasing), targetColor = Color.BLUE))
            setContent {
                AnimatedVisibility(visible = visible, enter = enterTransition, exit = exitTransition) {
                    veilColor = transition.animationValue("veil") as? Color ?: veilColor
                    Block(width = 100, height = 100)
                }
            }
            mainClock.autoAdvance = false

            visible = true
            repeat(2) { driveOneFrame() }
            advanceAnimationTimeBy(80)
            val interruptedColor = veilColor
            assertEquals(0.5f, interruptedColor.alpha / 255f, 0.02f)
            assertEquals(255, interruptedColor.red)
            assertEquals(0, interruptedColor.green)
            assertEquals(0, interruptedColor.blue)

            enterTransition = EnterTransition.None
            exitTransition = ExitTransition.None
            repeat(3) { driveOneFrame() }
            assertEquals(interruptedColor.red, veilColor.red)
            assertEquals(interruptedColor.green, veilColor.green)
            assertEquals(interruptedColor.blue, veilColor.blue)
            assertTrue(veilColor.alpha < interruptedColor.alpha)
        }

    @Test
    fun `veil exit follows the upstream color curve on every frame`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var veilColor = Color(0, 0, 0, 0)
            var visibilityTransition: Transition<EnterExitState>? = null
            mainClock.autoAdvance = false
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = EnterTransition.None,
                    exit = veilOut(tween(160, easing = FastOutSlowInEasing), targetColor = Color.RED),
                ) {
                    visibilityTransition = transition
                    veilColor = transition.animationValue("veil") as? Color ?: veilColor
                    Block(width = 100, height = 100)
                }
            }
            awaitIdle()
            visible = false
            driveOneFrame()
            driveOneFrame()

            for (playTimeMillis in 0..160 step 16) {
                val transition = requireNotNull(visibilityTransition)
                if (transition.currentState == EnterExitState.PostExit) {
                    assertEquals(160, playTimeMillis)
                    assertEquals(Color.RED.red.toFloat(), veilColor.red.toFloat(), 1f)
                    assertEquals(255f, veilColor.alpha.toFloat(), 1f)
                    break
                }
                val fraction = FastOutSlowInEasing.transform(playTimeMillis / 160f)
                assertEquals(Color.RED.red.toFloat(), veilColor.red.toFloat(), 1f)
                assertEquals(Color.RED.green.toFloat(), veilColor.green.toFloat(), 1f)
                assertEquals(Color.RED.blue.toFloat(), veilColor.blue.toFloat(), 1f)
                assertEquals(fraction * 255f, veilColor.alpha.toFloat(), 2f)
                advanceAnimationTimeBy(16)
                awaitIdle()
            }
            assertEquals(EnterExitState.PostExit, requireNotNull(visibilityTransition).currentState)
        }

    @Test
    fun `an uninterrupted veil enter and exit reaches both endpoint colors and disposes content`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var veilColor = Color(0, 0, 0, 0)
            var disposed = false
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = unveilIn(tween(160, easing = LinearEasing), initialColor = Color.RED),
                    exit = veilOut(tween(160, easing = LinearEasing), targetColor = Color.BLUE),
                ) {
                    veilColor = transition.animationValue("veil") as? Color ?: veilColor
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        onDispose { disposed = true }
                    }
                    Block(width = 100, height = 100)
                }
            }
            awaitIdle()
            visible = true
            awaitIdle()
            assertEquals(Color.RED.red, veilColor.red)
            assertEquals(Color.RED.green, veilColor.green)
            assertEquals(Color.RED.blue, veilColor.blue)
            assertEquals(0, veilColor.alpha)

            visible = false
            awaitIdle()
            assertEquals(Color.BLUE.red, veilColor.red)
            assertEquals(Color.BLUE.green, veilColor.green)
            assertEquals(Color.BLUE.blue, veilColor.blue)
            assertTrue(veilColor.alpha > 0)
            assertTrue(disposed)
            assertEquals(0, root.componentCount)
        }

    @Test
    fun `changing direction resets the accumulated veil before the replacement exit`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var veilColor = Color(0, 0, 0, 0)
            var hasVeilAnimation = false
            val enterTransition = EnterTransition.None
            var exitTransition by mutableStateOf(veilOut(tween(160, easing = LinearEasing), targetColor = Color.BLUE))
            setContent {
                AnimatedVisibility(visible = visible, enter = enterTransition, exit = exitTransition) {
                    hasVeilAnimation = transition.animations.any { it.label.contains("veil") }
                    veilColor = transition.animationValue("veil") as? Color ?: veilColor
                    Block(width = 100, height = 100)
                }
            }
            mainClock.autoAdvance = false

            visible = false
            repeat(2) { driveOneFrame() }
            advanceAnimationTimeBy(80)

            visible = true
            repeat(2) { driveOneFrame() }
            advanceAnimationTimeBy(40)

            exitTransition = fadeOut()
            visible = false
            repeat(2) { driveOneFrame() }
            advanceAnimationTimeBy(80)
            assertTrue(hasVeilAnimation, "the old veil was dropped when direction changed")

            val alphaBeforeReplacementFinishes = veilColor.alpha
            advanceAnimationTimeBy(80)
            assertTrue(
                veilColor.alpha < alphaBeforeReplacementFinishes,
                "the accumulated veil did not animate back to transparent",
            )
        }
}

private suspend fun ComposeSwingTest.seekVisibility(
    transition: Transition<Boolean>,
    visible: Boolean,
) {
    var playTimeMillis = 0
    while (playTimeMillis <= 220) {
        transition.setPlaytimeAfterInitialAndTargetStateEstablished(
            initialState = !visible,
            targetState = visible,
            playTimeNanos = playTimeMillis * 1_000_000L,
        )
        awaitIdle()
        assertEquals(200_000_000L, transition.totalDurationNanos)

        val visibility = transition.transitions.single()
        if (visible) {
            val alpha = visibility.animationValue("alpha") as Float
            val slide = visibility.animationValue("slide") as Point
            assertEquals(if (playTimeMillis > 200) 1f else playTimeMillis / 200f, alpha, 0.01f)
            assertEquals(if (playTimeMillis > 200) Point(0, 0) else Point(0, 200 - playTimeMillis), slide)
        } else {
            val scale = visibility.animationValue("scale") as Float
            val shrink = visibility.animationValue("shrink/expand") as Dimension
            assertEquals(if (playTimeMillis > 200) 0f else 1f - playTimeMillis / 200f, scale, 0.01f)
            assertEquals(if (playTimeMillis > 200) Dimension(0, 200) else Dimension(200 - playTimeMillis, 200), shrink)
        }
        playTimeMillis += 20
    }
}

@Composable
private fun SlideVisibilityContent(
    visible: Boolean,
    onTransition: (Transition<EnterExitState>) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideIn(tween(160, easing = LinearOutSlowInEasing)) { Point(it.width / 4, -it.height / 2) },
        exit = slideOut(tween(160, easing = FastOutSlowInEasing)) { Point(-it.width / 10, it.height / 5) },
    ) {
        onTransition(transition)
        Block(width = 100, height = 100)
    }
}

private suspend fun ComposeSwingTest.assertSlideEnter(transition: () -> Transition<EnterExitState>?) {
    for (frame in 0 until 12) {
        driveOneFrame()
        val currentTransition = requireNotNull(transition())
        val content = animatedContainer().fetch<Container>().getComponent(0)
        assertEquals(Dimension(100, 100), animatedContainer().fetch().preferredSize)
        if (currentTransition.currentState == EnterExitState.Visible) {
            assertEquals(Point(0, 0), content.location)
            return
        }
        val fraction =
            LinearOutSlowInEasing.transform((currentTransition.playTimeNanos / 1_000_000f / 160f).coerceIn(0f, 1f))
        assertEquals((25f * (1f - fraction)).roundToInt(), content.x, "enter x at frame $frame")
        assertEquals((-50f * (1f - fraction)).roundToInt(), content.y, "enter y at frame $frame")
    }
    assertEquals(EnterExitState.Visible, requireNotNull(transition()).currentState, "the enter exceeded its duration")
}

private suspend fun ComposeSwingTest.assertSlideExit(
    visibilityTransition: () -> Transition<EnterExitState>?,
    content: Component,
) {
    for (frame in 0 until 12) {
        driveOneFrame()
        val currentTransition = requireNotNull(visibilityTransition())
        if (currentTransition.currentState == EnterExitState.PostExit) {
            assertEquals(Point(-10, 20), content.location)
            return
        }
        val fraction =
            FastOutSlowInEasing.transform((currentTransition.playTimeNanos / 1_000_000f / 160f).coerceIn(0f, 1f))
        assertEquals((-10f * fraction).roundToInt(), content.x, "exit x at frame $frame")
        assertEquals((20f * fraction).roundToInt(), content.y, "exit y at frame $frame")
    }
    val finalTransition = requireNotNull(visibilityTransition())
    assertEquals(
        EnterExitState.PostExit,
        finalTransition.currentState,
        "the exit exceeded its duration: target=${finalTransition.targetState}, " +
            "playTime=${finalTransition.playTimeNanos}",
    )
}

private fun Transition<*>.animationValue(labelPart: String): Any? =
    animations.firstOrNull { it.label.contains(labelPart, ignoreCase = true) }?.value

private fun ComposeSwingTest.assertScaleRaster(
    transition: Transition<EnterExitState>,
    scale: Float,
) {
    val image = animatedContainer().captureToImage()
    var left = image.width
    var top = image.height
    var right = -1
    var bottom = -1
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            if (Color(image.getRGB(x, y), true).alpha > 0) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
    }
    val bounds =
        if (right < left ||
            bottom < top
        ) {
            Rectangle(0, 0, 0, 0)
        } else {
            Rectangle(left, top, right - left + 1, bottom - top + 1)
        }
    val expectedWidth = (20 * scale).roundToInt()
    assertTrue(
        kotlin.math.abs(expectedWidth - bounds.width) <= 1,
        "painted width at ${transition.playTimeNanos}ns: expected $expectedWidth, got ${bounds.width}",
    )
    val expectedHeight = (20 * scale).roundToInt()
    assertTrue(
        kotlin.math.abs(expectedHeight - bounds.height) <= 1,
        "painted height at ${transition.playTimeNanos}ns: expected $expectedHeight, got ${bounds.height}",
    )
    if (bounds.width > 1) {
        val expectedPivot = ((20 - bounds.width) / 2f).roundToInt()
        assertTrue(
            kotlin.math.abs(expectedPivot - bounds.x) <= 1,
            "painted horizontal pivot at ${transition.playTimeNanos}ns: expected $expectedPivot, got ${bounds.x}",
        )
    }
    if (bounds.height > 1) {
        val expectedPivot = ((20 - bounds.height) / 2f).roundToInt()
        assertTrue(
            kotlin.math.abs(expectedPivot - bounds.y) <= 1,
            "painted vertical pivot at ${transition.playTimeNanos}ns: expected $expectedPivot, got ${bounds.y}",
        )
    }
}
