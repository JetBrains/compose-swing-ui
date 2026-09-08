/*
 * Copyright 2021 The Android Open Source Project
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope.SlideDirection
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.keyframes
import org.jetbrains.compose.swing.animation.core.snap
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AnimatedContentUpstreamParityTest {
    @Test
    fun contentAlignmentPlacesBothContentsDuringExpansionAndContraction() =
        runComposeSwingTest {
            val initialSize = Dimension(80, 80)
            val targetSize = Dimension(160, 240)
            val alignments =
                listOf(
                    Alignment.TopStart,
                    Alignment.BottomStart,
                    Alignment.Center,
                    Alignment.BottomEnd,
                    Alignment.TopEnd,
                )
            var target by mutableStateOf(true)
            var alignment by mutableStateOf(Alignment.TopStart)
            var scope: AnimatedContentTransitionScopeImpl<Boolean>? = null
            setContent {
                AnimatedContent(
                    targetState = target,
                    contentAlignment = alignment,
                    transitionSpec = {
                        scope = this as AnimatedContentTransitionScopeImpl<Boolean>
                        fadeIn(tween(80)) togetherWith
                            fadeOut(tween(80)) using SizeTransform { _, _ -> tween(80) }
                    },
                ) {
                    SizedContent(it, if (it) initialSize else targetSize)
                }
            }
            mainClock.autoAdvance = false

            for (nextAlignment in alignments) {
                alignment = nextAlignment
                mainClock.advanceTimeByFrame()
                awaitIdle()
                repeat(2) {
                    target = !target
                    awaitIdle()
                    val transition = assertNotNull(scope, "the transition spec was not read").transition
                    var frames = 0
                    while (transition.currentState != transition.targetState && frames < 20) {
                        for (index in 0 until container.componentCount) {
                            val content = container.contentPanel(index)
                            assertEquals(
                                nextAlignment.align(content.size, container.size, ComponentOrientation.LEFT_TO_RIGHT),
                                Point(content.x, content.y),
                                "content ${container.contentText(index)} did not follow $nextAlignment",
                            )
                        }
                        mainClock.advanceTimeByFrame()
                        frames++
                    }
                    assertTrue(frames < 20, "the transition did not settle under $nextAlignment")
                    assertEquals(1, container.componentCount, "the completed transition kept its outgoing content")
                    assertEquals(Point(0, 0), container.contentPanel(0).location)
                }
            }
        }

    @Test
    fun sizeTransformUsesItsDirectionalSpecsAndAnimatesEachAxisAtItsKeyframe() =
        runComposeSwingTest {
            val initialSize = Dimension(40, 40)
            val targetSize = Dimension(200, 200)
            var target by mutableStateOf(true)
            setContent {
                AnimatedContent(
                    targetState = target,
                    transitionSpec = {
                        if (true isTransitioningTo false) {
                            (fadeIn() togetherWith fadeOut()) using
                                SizeTransform { initial, end ->
                                    keyframes {
                                        durationMillis = 320
                                        Dimension(end.width, initial.height) at 160 using LinearEasing
                                        end at 320 using LinearEasing
                                    }
                                }
                        } else {
                            (fadeIn() togetherWith fadeOut()) using SizeTransform { _, _ -> tween(80) }
                        }
                    },
                ) {
                    SizedContent(it, if (it) initialSize else targetSize)
                }
            }
            mainClock.autoAdvance = false

            target = false
            repeat(5) { mainClock.advanceTimeByFrame() }
            val widthPhase = container.preferredSize
            assertTrue(widthPhase.width in (initialSize.width + 1) until targetSize.width)
            assertEquals(initialSize.height, widthPhase.height, "the height started before the width keyframe")

            var frames = 0
            while (container.preferredSize.width < targetSize.width && frames < 20) {
                mainClock.advanceTimeByFrame()
                frames++
            }
            val heightPhase = container.preferredSize
            assertEquals(targetSize.width, heightPhase.width, "the width did not reach its keyframe")
            assertTrue(heightPhase.height in (initialSize.height + 1) until targetSize.height)

            frames = 0
            while (container.preferredSize != targetSize && frames < 20) {
                mainClock.advanceTimeByFrame()
                frames++
            }
            assertEquals(targetSize, container.preferredSize, "the second axis did not reach its target")

            target = true
            mainClock.advanceTimeBy(160.milliseconds)
            assertEquals(initialSize, container.preferredSize, "the reverse segment did not use its shorter size spec")
        }

    @Test
    fun aHeldExitWaitsForTheEnterAndContainerSizeAnimationsToFinish() =
        runComposeSwingTest {
            val oldSize = Dimension(80, 80)
            val newSize = Dimension(160, 240)
            var target by mutableStateOf(true)
            var oldContentDisposed = false
            var newContentEntered = false
            setContent {
                AnimatedContent(
                    targetState = target,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith
                            (fadeOut(tween(16)) + ExitTransition.KeepUntilTransitionsFinished) using
                            SizeTransform { _, _ -> tween(360) }
                    },
                ) { state ->
                    if (state) {
                        DisposableEffect(Unit) { onDispose { oldContentDisposed = true } }
                    } else {
                        newContentEntered =
                            transition.currentState == EnterExitState.Visible &&
                            transition.targetState == EnterExitState.Visible
                    }
                    SizedContent(state, if (state) oldSize else newSize)
                }
            }
            mainClock.autoAdvance = false

            target = false
            repeat(16) { mainClock.advanceTimeByFrame() }
            assertTrue(newContentEntered, "the arriving content's enter did not finish first")
            assertTrue(container.preferredSize.width in (oldSize.width + 1) until newSize.width)
            assertEquals(2, container.componentCount, "the held content left before the size animation finished")
            assertTrue(!oldContentDisposed, "the held content was disposed while the container was resizing")

            var frames = 0
            while (!oldContentDisposed && frames < 40) {
                mainClock.advanceTimeByFrame()
                frames++
            }
            assertTrue(oldContentDisposed, "the outgoing content outlived the complete transition")
            assertEquals(newSize, container.preferredSize)
            assertEquals(1, container.componentCount)
            assertEquals("false", container.contentText(0))
        }

    @Test
    fun aContentKeyChangeStartsTheSlideOnlyWhenTheKeyChanges() =
        runComposeSwingTest {
            var target by mutableStateOf(1)
            setContent {
                AnimatedContent(
                    targetState = target,
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            (
                                slideOutHorizontally(animationSpec = snap(), targetOffsetX = { it }) +
                                    fadeOut(tween(200))
                            )
                    },
                    contentKey = { it > 3 },
                ) {
                    SizedContent(it.toString(), Dimension(200, 200))
                }
            }
            mainClock.autoAdvance = false

            for (next in 2..3) {
                target = next
                repeat(3) { mainClock.advanceTimeByFrame() }
                assertEquals(1, container.componentCount, "states with one content key started a transition")
                assertEquals(next.toString(), container.contentText(0))
            }

            target = 4
            awaitIdle()
            var frames = 0
            while (container.componentCount < 2 && frames < 5) {
                mainClock.advanceTimeByFrame()
                frames++
            }
            assertEquals(2, container.componentCount, "a changed content key did not start a transition")
            assertEquals("4", container.contentText(0))
            assertEquals("3", container.contentText(1))
            var incoming = container.contentPanel(0)
            var outgoing = container.contentPanel(1)
            assertEquals(Point(-200, 0), incoming.location)
            assertEquals(Point(0, 0), outgoing.location)

            var lastOutgoingLocation = outgoing.location
            frames = 0
            while (incoming.location != Point(0, 0) && frames < 40) {
                mainClock.advanceTimeByFrame()
                incoming = panelShowing("4")
                if (container.componentCount == 2) {
                    outgoing = panelShowing("3")
                    lastOutgoingLocation = outgoing.location
                }
                frames++
            }
            assertEquals(Point(0, 0), incoming.location, "the incoming content did not reach the container")
            assertEquals(Point(200, 0), lastOutgoingLocation, "the outgoing content did not slide out to the right")
        }

    @Test
    fun containerSlidesMoveBothContentsAndDropTheContentThatLeaves() =
        runComposeSwingTest {
            val contentSize = Dimension(200, 200)
            var target by mutableStateOf(true)
            setContent {
                AnimatedContent(
                    targetState = target,
                    transitionSpec = {
                        val direction = if (true isTransitioningTo false) SlideDirection.Start else SlideDirection.End
                        slideIntoContainer(direction, tween(200, easing = LinearEasing)) togetherWith
                            slideOutOfContainer(direction, tween(200, easing = LinearEasing)) using null
                    },
                ) {
                    SizedContent(it, contentSize)
                }
            }
            mainClock.autoAdvance = false

            target = false
            awaitIdle()
            assertSlideProgress(incoming = "false", outgoing = "true", startsOnRight = true)
            finishTransition()
            assertEquals(1, container.componentCount, "the content that left remained mounted")
            assertEquals(Point(0, 0), panelShowing("false").location)

            target = true
            awaitIdle()
            assertSlideProgress(incoming = "true", outgoing = "false", startsOnRight = false)
            finishTransition()
            assertEquals(1, container.componentCount, "the content that left remained mounted")
            assertEquals(Point(0, 0), panelShowing("true").location)
        }

    private fun ComposeSwingTest.panelShowing(text: String): Container =
        container.contentPanel((0 until container.componentCount).first { container.contentText(it) == text })

    private fun ComposeSwingTest.advanceUntilBothContents() {
        var frames = 0
        while (container.componentCount < 2 && frames < 5) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        assertEquals(2, container.componentCount, "the state change did not mount both contents")
        mainClock.advanceTimeByFrame()
    }

    private fun ComposeSwingTest.assertSlideProgress(
        incoming: String,
        outgoing: String,
        startsOnRight: Boolean,
    ) {
        advanceUntilBothContents()
        var entering = panelShowing(incoming)
        var leaving = panelShowing(outgoing)
        assertTrue(
            if (startsOnRight) entering.x > 0 else entering.x < 0,
            "the incoming content did not start on the expected side",
        )
        assertEquals(0, leaving.x, "the outgoing content did not start at its resting place")
        var previousEnteringX = entering.x
        var previousLeavingX = leaving.x
        val travelDirection = if (startsOnRight) -1 else 1
        repeat(5) {
            mainClock.advanceTimeByFrame()
            entering = panelShowing(incoming)
            leaving = panelShowing(outgoing)
            assertTrue(
                (entering.x - previousEnteringX) * travelDirection > 0,
                "the incoming content did not move towards its resting place",
            )
            assertTrue(
                (leaving.x - previousLeavingX) * travelDirection > 0,
                "the outgoing content did not move in the transition direction",
            )
            assertEquals(
                if (startsOnRight) container.width else -container.width,
                entering.x - leaving.x,
                "the two contents did not keep their full-width separation",
            )
            previousEnteringX = entering.x
            previousLeavingX = leaving.x
        }
    }

    private fun ComposeSwingTest.finishTransition() {
        var frames = 0
        while (container.componentCount > 1 && frames < 40) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        assertEquals(1, container.componentCount, "the transition did not settle")
    }
}

@Composable
private fun SizedContent(
    text: Any,
    size: Dimension,
) {
    Label(text = text.toString(), modifier = SwingModifier.preferredSize(size.width, size.height))
}
