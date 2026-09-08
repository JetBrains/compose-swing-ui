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
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Point
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The container an `AnimatedContent` stands on, which the harness mounts as the root's one child. */
internal val ComposeSwingTest.container: Container
    get() = root.getComponent(0) as Container

/** The panel holding one content of the container, counting from the one painted on top. */
internal fun Container.contentPanel(index: Int): Container = getComponent(index) as Container

/** What the label inside one content of the container reads. */
internal fun Container.contentText(index: Int): String = (contentPanel(index).getComponent(0) as JLabel).text

/** How opaque one content of the container paints its opaque label at a pixel inside it, from `0f` to `1f`. */
internal fun Container.contentAlpha(index: Int): Float =
    Color(contentPanel(index).captureToImage().getRGB(2, 2), true).alpha / 255f

/** How many pixels of the middle row one content of the container paints: less than its width while it scales. */
internal fun Container.contentPaintedWidth(index: Int): Int {
    val image = contentPanel(index).captureToImage()
    return (0 until image.width).count { Color(image.getRGB(it, image.height / 2), true).alpha > 0 }
}

/** How long the arriving content takes to fade in, which is long enough to outlast [FRAMES_PAST_THE_EXIT]. */
private const val ENTER_MILLIS = 800

/** How long the content being left takes to fade out, which is short enough to end within [FRAMES_PAST_THE_EXIT]. */
private const val EXIT_MILLIS = 160

/** Frames that carry a transition past the end of its exit and leave its enter still running. */
private const val FRAMES_PAST_THE_EXIT = 20

/** The width of the content a slide out of the container leaves behind. */
private const val LEAVING_WIDTH = 40

/** The height of the content a slide out of the container leaves behind. */
private const val LEAVING_HEIGHT = 20

/** The width of the content a slide out of the container brings in, which is not the width it replaces. */
private const val ARRIVING_WIDTH = 160

/** The height of the content a slide out of the container brings in, which is not the height it replaces. */
private const val ARRIVING_HEIGHT = 100

/** An offset no slide of the container would arrive at on its own, so only a caller can have chosen it. */
private const val CALLER_CHOSEN_OFFSET = 7

/** A content of a known size, so a size or a placement can be asserted in pixels. */
@Composable
private fun Body(
    text: String,
    width: Int,
    height: Int = LEAVING_HEIGHT,
) = Label(text = text, modifier = SwingModifier.preferredSize(width, height).opaque(true).background(Color.BLUE))

/**
 * A content of a container that animates between states, declared against that container's own scope
 * rather than against the scope every animated container shares, and animating a part of itself on the
 * transition it is handed.
 */
@Composable
private fun AnimatedContentScope.HeldBody(text: String) {
    // A Box: animateEnterExit is a layout modifier a Foundation parent runs, on a component that paints a layer.
    val part = SwingModifier.animateEnterExit(enter = EnterTransition.None, exit = fadeOut(tween(EXIT_MILLIS)))
    Box {
        Box(part) {
            Body(text = text, width = 40)
        }
    }
}

@Suppress("LargeClass") // Keep the upstream test suite together for direct source synchronization.
class AnimatedContentTest {
    @Test
    fun `a settled container holds the content of one state`() =
        runComposeSwingTest {
            setContent {
                AnimatedContent(targetState = "a") { Body(text = it, width = 40) }
            }
            assertEquals(1, container.componentCount, "a settled container held content for more than one state")
            assertEquals("a", container.contentText(0))
        }

    @Test
    fun `both contents stand while the transition runs, and the arriving one paints on top`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Body(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            assertEquals(2, container.componentCount, "the content being left was dropped before its exit ran")
            // Swing paints the highest index first, so index 0 lands on top.
            assertEquals("b", container.contentText(0), "the arriving content did not paint over the one it replaces")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, container.componentCount, "the content that finished exiting was left in the tree")
            assertEquals("b", container.contentText(0))
        }

    @Test
    fun `a target z-index sorts the arriving content under the content it replaces`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    // Each content carries the z-index the transform named while it was the target,
                    // so only the arriving one is pushed under.
                    transitionSpec = {
                        ContentTransform(
                            fadeIn(tween(320)),
                            fadeOut(tween(320)),
                            targetContentZIndex = if (targetState == "b") -1f else 0f,
                        )
                    },
                ) {
                    Body(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            assertEquals("a", container.contentText(0), "the z-index did not sort above the composed order")
        }

    @Test
    fun `states that share a content key produce no transition`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                    contentKey = { it.length },
                ) {
                    Body(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            assertEquals(1, container.componentCount, "two states with one content key were composed twice")
            assertEquals("b", container.contentText(0), "the content did not follow the state it shares a key with")
        }

    @Test
    @Suppress("ktlint:standard:function-naming") // Keep the pinned AndroidX test name for direct source comparison.
    fun AnimatedContentWithKeysTest() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            var targetState by mutableStateOf(1)
            val list = mutableListOf<Int>()
            setContent {
                val transition = updateTransition(targetState)
                val holder = rememberSaveableStateHolder()
                transition.AnimatedContent(contentKey = { it > 2 }) {
                    if (it <= 2) {
                        holder.SaveableStateProvider(11) {
                            var count by rememberSaveable { mutableStateOf(0) }
                            LaunchedEffect(Unit) { list.add(++count) }
                        }
                    }
                    Label(text = it.toString())
                }
                LaunchedEffect(Unit) {
                    assertFalse(transition.isRunning)
                    targetState = 2
                    withFrameMillis {
                        assertFalse(transition.isRunning)
                        assertEquals(1, transition.currentState)
                        assertEquals(1, transition.targetState)

                        // This state change should now cause an animation
                        targetState = 3
                    }
                    withFrameMillis { assertTrue(transition.isRunning) }
                }
            }
            awaitIdle()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            mainClock.autoAdvance = true
            awaitIdle()
            // Check that save worked
            assertEquals(1, list.size)
            assertEquals(1, list[0])
            targetState = 1
            awaitIdle()
            assertEquals(2, list.size)
            assertEquals(1, list[0])
            assertEquals(2, list[1])
        }

    @Test
    fun `the container's size travels from the size it stood at to the size the arriving content needs`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                ) {
                    Body(text = it, width = if (it == "a") 40 else 160)
                }
            }
            assertEquals(40, container.preferredSize.width, "the settled container did not take the room it needs")

            mainClock.autoAdvance = false
            state = "b"
            val widths =
                List(8) {
                    driveOneFrame()
                    container.preferredSize.width
                }
            assertTrue(widths.any { it in 41..159 }, "the container's size jumped rather than traveled: $widths")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(160, container.preferredSize.width, "the container did not settle at the arriving size")

            // Back to a state that has been on screen and left: its size has to be measured afresh, since
            // the content it was measured with was disposed.
            mainClock.autoAdvance = false
            state = "a"
            val widthsBack =
                List(8) {
                    driveOneFrame()
                    container.preferredSize.width
                }
            assertTrue(
                widthsBack.any { it in 41..159 },
                "the container did not travel back to a size it had already shown: $widthsBack",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(40, container.preferredSize.width, "the container did not settle back at the first size")
        }

    @Test
    fun `a horizontal alignment changed on a settled composition moves the content`() =
        runComposeSwingTest {
            var alignment by mutableStateOf<Alignment.Horizontal>(Alignment.Start)
            setContent {
                AnimatedContent(
                    targetState = "a",
                    modifier = SwingModifier.preferredSize(width = 200, height = 20),
                    contentAlignment = alignment + Alignment.Top,
                ) {
                    Body(text = it, width = 40)
                }
            }
            assertEquals(0, container.contentPanel(0).x, "leading content did not start at the leading edge")

            alignment = Alignment.End
            awaitIdle()
            assertEquals(160, container.contentPanel(0).x, "the changed alignment never reached the layout")
        }

    @Test
    fun `a vertical alignment changed on a settled composition moves the content`() =
        runComposeSwingTest {
            var alignment by mutableStateOf<Alignment.Vertical>(Alignment.Top)
            setContent {
                AnimatedContent(
                    targetState = "a",
                    modifier = SwingModifier.preferredSize(width = 40, height = 100),
                    contentAlignment = Alignment.Start + alignment,
                ) {
                    Body(text = it, width = 40)
                }
            }
            assertEquals(0, container.contentPanel(0).y, "leading content did not start at the top edge")

            alignment = Alignment.Bottom
            awaitIdle()
            assertEquals(80, container.contentPanel(0).y, "the changed alignment never reached the layout")
        }

    @Test
    fun `a content key changed on a settled composition is the one two states are matched by`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            var contentKey by mutableStateOf<(String) -> Any?>({ it })
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                    contentKey = contentKey,
                ) {
                    Body(text = it, width = 40)
                }
            }

            contentKey = { it.length }
            awaitIdle()

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            assertEquals(1, container.componentCount, "the content key the last recomposition declared was not used")
        }

    @Test
    fun `a slide out towards the left carries the content being left off the container's leading edge`() =
        runComposeSwingTest {
            assertEquals(
                Point(-LEAVING_WIDTH, 0),
                slideOutOfContainerOffset(AnimatedContentTransitionScope.SlideDirection.Left),
                "the content being left did not travel its own width past the leading edge",
            )
        }

    @Test
    fun `a slide out towards the right carries the content being left off the container's trailing edge`() =
        runComposeSwingTest {
            assertEquals(
                Point(ARRIVING_WIDTH, 0),
                slideOutOfContainerOffset(AnimatedContentTransitionScope.SlideDirection.Right),
                "the content being left did not travel past the width the arriving content gives the container",
            )
        }

    @Test
    fun `a slide out towards the top carries the content being left off the container's top edge`() =
        runComposeSwingTest {
            assertEquals(
                Point(0, -LEAVING_HEIGHT),
                slideOutOfContainerOffset(AnimatedContentTransitionScope.SlideDirection.Up),
                "the content being left did not travel its own height past the top edge",
            )
        }

    @Test
    fun `a slide out towards the bottom carries the content being left off the container's bottom edge`() =
        runComposeSwingTest {
            assertEquals(
                Point(0, ARRIVING_HEIGHT),
                slideOutOfContainerOffset(AnimatedContentTransitionScope.SlideDirection.Down),
                "the content being left did not travel past the height the arriving content gives the container",
            )
        }

    @Test
    fun `a slide out towards the leading edge travels left under a left-to-right reading order`() =
        runComposeSwingTest {
            assertEquals(
                Point(-LEAVING_WIDTH, 0),
                slideOutOfContainerOffset(AnimatedContentTransitionScope.SlideDirection.Start),
                "a slide towards the leading edge did not resolve to a slide left",
            )
        }

    @Test
    fun `a slide out towards the leading edge travels right under a right-to-left reading order`() =
        runComposeSwingTest {
            assertEquals(
                Point(ARRIVING_WIDTH, 0),
                slideOutOfContainerOffset(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    ComponentOrientation.RIGHT_TO_LEFT,
                ),
                "a right-to-left container did not mirror the edge the slide leaves by",
            )
        }

    @Test
    fun `a slide out towards the trailing edge travels right under a left-to-right reading order`() =
        runComposeSwingTest {
            assertEquals(
                Point(ARRIVING_WIDTH, 0),
                slideOutOfContainerOffset(AnimatedContentTransitionScope.SlideDirection.End),
                "a slide towards the trailing edge did not resolve to a slide right",
            )
        }

    @Test
    fun `a caller's target offset decides how far content sliding out of the container travels`() =
        runComposeSwingTest {
            var fullSlide = 0
            val offset =
                slideOutOfContainerOffset(AnimatedContentTransitionScope.SlideDirection.Left) {
                    fullSlide = it
                    it / 2
                }
            assertEquals(-LEAVING_WIDTH, fullSlide, "the offset the caller was handed was not the full slide")
            assertEquals(
                Point(-LEAVING_WIDTH / 2, 0),
                offset,
                "the content being left traveled somewhere other than where the caller sent it",
            )
        }

    @Test
    fun `a caller's initial offset decides where content sliding into the container starts`() =
        runComposeSwingTest {
            var fullSlide = 0
            val offset =
                slideIntoContainerOffset(ComponentOrientation.LEFT_TO_RIGHT) {
                    fullSlide = it
                    CALLER_CHOSEN_OFFSET
                }
            assertEquals(40, fullSlide, "the offset the caller was handed was not the full slide")
            assertEquals(
                CALLER_CHOSEN_OFFSET,
                offset,
                "the arriving content started somewhere other than where the caller put it",
            )
        }

    @Test
    fun `a transition spec changed on a settled composition is the one that runs`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            var enter by mutableStateOf(EnterTransition.None)
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { enter togetherWith ExitTransition.None },
                ) {
                    Body(text = it, width = 40)
                }
            }

            enter = fadeIn(tween(320))
            awaitIdle()

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            assertTrue(container.contentAlpha(0) < 1f, "the enter the last recomposition declared did not run")
        }

    @Test
    fun `a slide into the container comes from the trailing edge under a left-to-right reading order`() =
        runComposeSwingTest {
            val offset = slideIntoContainerOffset(ComponentOrientation.LEFT_TO_RIGHT)
            assertTrue(
                offset in 30..40,
                "content sliding towards the leading edge did not start a container's width off the trailing edge: " +
                    "$offset",
            )
        }

    @Test
    fun `a right-to-left container mirrors which edge a slide towards the leading edge comes from`() =
        runComposeSwingTest {
            val offset = slideIntoContainerOffset(ComponentOrientation.RIGHT_TO_LEFT)
            assertTrue(
                offset in -40..-30,
                "a right-to-left container did not mirror the edge the slide comes from: $offset",
            )
        }

    @Test
    fun `a container whose content size changes settles`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(targetState = state) { Body(text = it, width = if (it == "a") 40 else 160) }
            }

            state = "b"
            // A layout pass writes the measurement the size animation aims at, so an animation whose own
            // output fed that measurement would never run out of frames and this would never return.
            awaitIdle()
            assertEquals(160, container.preferredSize.width)
        }

    @Test
    fun `containers sharing one transition change their contents together off its states`() =
        runComposeSwingTest {
            var page by mutableStateOf("a")
            setContent {
                val transition = updateTransition(page)
                transition.AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Body(text = "body $it", width = 40)
                }
                transition.AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Body(text = "footer $it", width = 40)
                }
            }
            val body = root.getComponent(0) as Container
            val footer = root.getComponent(1) as Container
            assertEquals(1, body.componentCount)
            assertEquals(1, footer.componentCount)

            mainClock.autoAdvance = false
            page = "b"
            driveOneFrame()
            assertEquals(2, body.componentCount, "the first container did not run the state change")
            assertEquals(2, footer.componentCount, "the second container did not run the same state change")
            assertEquals("body b", body.contentText(0))
            assertEquals("footer b", footer.contentText(0))

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, body.componentCount, "the content that finished exiting was left in the tree")
            assertEquals(1, footer.componentCount, "the content that finished exiting was left in the tree")
        }

    @Test
    fun `by default the arriving content fades and scales in while the content left behind fades out`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(targetState = state) { Body(text = it, width = 40) }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()

            // Index 0 is the hit-test overlay the scale adds; the arriving and leaving contents follow it.
            assertEquals("b", container.contentText(1))
            assertTrue(container.contentPaintedWidth(1) < 40, "the arriving content did not scale in")
            assertTrue(container.contentAlpha(1) < 1f, "the arriving content did not fade in")
            assertEquals(
                40,
                container.contentPaintedWidth(2),
                "the content being left scaled, so the default scales out too",
            )
        }

    @Test
    fun `an exit that holds keeps the content being left until the whole transition has finished`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(tween(ENTER_MILLIS)) togetherWith
                            (fadeOut(tween(EXIT_MILLIS)) + ExitTransition.KeepUntilTransitionsFinished)
                    },
                ) {
                    Body(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(FRAMES_PAST_THE_EXIT) { driveOneFrame() }
            assertEquals(2, container.componentCount, "the held content was dropped as its own exit finished")
            assertEquals("a", container.contentText(1), "the content held over is not the one being left")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, container.componentCount, "the held content outlived the transition holding it")
            assertEquals("b", container.contentText(0))
        }

    @Test
    fun `an exit that does not hold drops the content being left as soon as it has finished`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(ENTER_MILLIS)) togetherWith fadeOut(tween(EXIT_MILLIS)) },
                ) {
                    Body(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            // The same frame count the held content survives, which is what makes that test's assertion
            // about holding rather than about an exit that has not run out yet.
            repeat(FRAMES_PAST_THE_EXIT) { driveOneFrame() }
            assertEquals(1, container.componentCount, "the content being left stood past the end of its exit")
            assertEquals("b", container.contentText(0))
        }

    @Test
    fun `a hold on the left of a combination holds too`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(tween(ENTER_MILLIS)) togetherWith
                            (ExitTransition.KeepUntilTransitionsFinished + fadeOut(tween(EXIT_MILLIS)))
                    },
                ) {
                    Body(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(FRAMES_PAST_THE_EXIT) { driveOneFrame() }
            assertEquals(2, container.componentCount, "the held content was dropped as its own exit finished")
            assertEquals("a", container.contentText(1))
        }

    /**
     * Where content sliding towards the container's leading edge starts, under [orientation], with
     * [initialOffset] deciding what becomes of the full slide it is handed.
     *
     * The container holds content 40 pixels wide, so a slide towards the leading edge starts the
     * arriving content a container's width off the trailing edge under a left-to-right reading order and
     * its own width off the leading edge under a right-to-left one.
     *
     * The offset is sampled two frames in: the arriving content is measured one pass after it is
     * mounted, and the slide has nothing to be set up from until then.
     */
    private suspend fun ComposeSwingTest.slideIntoContainerOffset(
        orientation: ComponentOrientation,
        initialOffset: (offsetForFullSlide: Int) -> Int = { it },
    ): Int {
        var state by mutableStateOf("a")
        setContent {
            AnimatedContent(
                targetState = state,
                modifier = SwingModifier.componentOrientation(orientation),
                transitionSpec = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(3200),
                        initialOffset = initialOffset,
                    ) togetherWith ExitTransition.None
                },
            ) {
                Body(text = it, width = 40)
            }
        }

        mainClock.autoAdvance = false
        state = "b"
        repeat(2) { driveOneFrame() }
        assertEquals("b", container.contentText(0))
        // The content being left exits at once, so the arriving content stands alone at the leading edge but for its
        // slide.
        return container.contentPanel(0).x
    }

    /**
     * Where content sliding out of the container [towards] one of its edges comes to rest, under
     * [orientation], with [targetOffset] deciding what becomes of the full slide it is handed.
     *
     * The arriving content is larger than the one it replaces on both axes, so an offset worked out
     * from the container tells itself apart from one worked out from the content that is leaving.
     *
     * The exit holds the content being left past the end of its own slide, which is what lets the
     * offset be read where it has settled rather than somewhere along the way.
     */
    @Test
    fun `a slide out carries content the container placed at its trailing edge from where it stands`() =
        runComposeSwingTest {
            assertEquals(
                Point(-ARRIVING_WIDTH, 0),
                slideOutOfContainerOffset(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    contentAlignment = Alignment.TopEnd,
                ),
                "the content being left traveled by its own width rather than from where it was placed",
            )
        }

    private suspend fun ComposeSwingTest.slideOutOfContainerOffset(
        towards: AnimatedContentTransitionScope.SlideDirection,
        orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
        contentAlignment: Alignment = Alignment.TopStart,
        targetOffset: (offsetForFullSlide: Int) -> Int = { it },
    ): Point {
        var state by mutableStateOf("a")
        setContent {
            AnimatedContent(
                targetState = state,
                modifier = SwingModifier.componentOrientation(orientation),
                contentAlignment = contentAlignment,
                transitionSpec = {
                    fadeIn(tween(ENTER_MILLIS)) togetherWith
                        (
                            slideOutOfContainer(towards, tween(EXIT_MILLIS), targetOffset) +
                                ExitTransition.KeepUntilTransitionsFinished
                        )
                },
            ) {
                Body(
                    text = it,
                    width = if (it == "a") LEAVING_WIDTH else ARRIVING_WIDTH,
                    height = if (it == "a") LEAVING_HEIGHT else ARRIVING_HEIGHT,
                )
            }
        }

        mainClock.autoAdvance = false
        state = "b"
        repeat(FRAMES_PAST_THE_EXIT) { driveOneFrame() }
        // The content being left was composed first, so it stands behind the arriving one.
        assertEquals("a", container.contentText(1), "the content being left was dropped before its slide settled")
        // Both contents are placed at the alignment inside the room the larger one needs, so the slide is what sets
        // the content being left apart from where the arriving one stands.
        val leaving = container.contentPanel(1)
        val arriving = container.contentPanel(0)
        val aligned =
            contentAlignment.align(leaving.size, arriving.size, ComponentOrientation.LEFT_TO_RIGHT)
        return Point(leaving.x - arriving.x - aligned.x, leaving.y - arriving.y - aligned.y)
    }

    @Test
    fun `content declared against the container's scope animates a part of itself on the transition`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                ) { shown ->
                    HeldBody(text = shown)
                }
            }
            assertEquals(1, container.componentCount, "the container did not mount one content")

            mainClock.autoAdvance = false
            state = "b"
            repeat(3) { driveOneFrame() }
            assertEquals(
                2,
                container.componentCount,
                "the content being left went before the exit of its part had finished",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, container.componentCount, "the content being left stayed once its part had finished")
        }

    @Test
    fun `the label names the transition the container builds`() =
        runComposeSwingTest {
            var default: String? = null
            var declared: String? = null
            setContent {
                AnimatedContent(targetState = "a") {
                    default = transition.parentTransition?.label
                    Body(text = it, width = LEAVING_WIDTH)
                }
                AnimatedContent(targetState = "a", label = "page") {
                    declared = transition.parentTransition?.label
                    Body(text = it, width = LEAVING_WIDTH)
                }
            }
            assertEquals("AnimatedContent", default, "the container named its transition something else")
            assertEquals("page", declared, "the declared label did not reach the transition")
        }
}
