// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The width of the content a slide out of the container leaves behind. */
private const val LEAVING_WIDTH = 40

/** The width of the content a slide out of the container brings in, which is not the width it replaces. */
private const val ARRIVING_WIDTH = 160

/** The height of the content a slide out of the container brings in, which is not the height it replaces. */
private const val ARRIVING_HEIGHT = 100

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
            AnimatedContentBody(text = text, width = 40)
        }
    }
}

/** The behavior of `AnimatedContent` that the library adds to what the upstream suite asserts. */
class AnimatedContentSwingTest {
    @Test
    fun `a settled container holds the content of one state`() =
        runComposeSwingTest {
            setContent {
                AnimatedContent(targetState = "a") { AnimatedContentBody(text = it, width = 40) }
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
                    AnimatedContentBody(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            mainClock.advanceTimeByFrame()
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
                    AnimatedContentBody(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            mainClock.advanceTimeByFrame()
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
                    AnimatedContentBody(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            mainClock.advanceTimeByFrame()
            assertEquals(1, container.componentCount, "two states with one content key were composed twice")
            assertEquals("b", container.contentText(0), "the content did not follow the state it shares a key with")
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
                    AnimatedContentBody(text = it, width = if (it == "a") 40 else 160)
                }
            }
            assertEquals(40, container.preferredSize.width, "the settled container did not take the room it needs")

            mainClock.autoAdvance = false
            state = "b"
            val widths =
                List(8) {
                    mainClock.advanceTimeByFrame()
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
                    mainClock.advanceTimeByFrame()
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
                    modifier = SwingModifier.styleable { preferredSize(width = 200, height = 20) },
                    contentAlignment = alignment + Alignment.Top,
                ) {
                    AnimatedContentBody(text = it, width = 40)
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
                    modifier = SwingModifier.styleable { preferredSize(width = 40, height = 100) },
                    contentAlignment = Alignment.Start + alignment,
                ) {
                    AnimatedContentBody(text = it, width = 40)
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
                    AnimatedContentBody(text = it, width = 40)
                }
            }

            contentKey = { it.length }
            awaitIdle()

            mainClock.autoAdvance = false
            state = "b"
            mainClock.advanceTimeByFrame()
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
            // No slide of the container arrives at 7 on its own, so only the caller can have chosen it.
            val offset =
                slideIntoContainerOffset(ComponentOrientation.LEFT_TO_RIGHT) {
                    fullSlide = it
                    7
                }
            assertEquals(40, fullSlide, "the offset the caller was handed was not the full slide")
            assertEquals(
                7,
                offset,
                "the arriving content started somewhere other than where the caller put it",
            )
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
                AnimatedContent(targetState = state) {
                    AnimatedContentBody(text = it, width = if (it == "a") 40 else 160)
                }
            }

            state = "b"
            // A layout pass writes the measurement the size animation aims at, so an animation whose own
            // output fed that measurement would never run out of frames and this would never return.
            awaitIdle()
            assertEquals(160, container.preferredSize.width)
        }

    @Test
    fun `by default the arriving content fades and scales in while the content left behind fades out`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(targetState = state) { AnimatedContentBody(text = it, width = 40) }
            }

            mainClock.autoAdvance = false
            state = "b"
            mainClock.advanceTimeByFrame()

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
    fun `an exit that does not hold drops the content being left as soon as it has finished`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(ENTER_MILLIS)) togetherWith fadeOut(tween(EXIT_MILLIS)) },
                ) {
                    AnimatedContentBody(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            // The same frame count the held content survives, which is what makes that test's assertion
            // about holding rather than about an exit that has not run out yet.
            repeat(FRAMES_PAST_THE_EXIT) { mainClock.advanceTimeByFrame() }
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
                    AnimatedContentBody(text = it, width = 40)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(FRAMES_PAST_THE_EXIT) { mainClock.advanceTimeByFrame() }
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
    private fun ComposeSwingTest.slideIntoContainerOffset(
        orientation: ComponentOrientation,
        initialOffset: (offsetForFullSlide: Int) -> Int = { it },
    ): Int {
        var state by mutableStateOf("a")
        setContent {
            AnimatedContent(
                targetState = state,
                modifier = SwingModifier.styleable { componentOrientation(orientation) },
                transitionSpec = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(3200),
                        initialOffset = initialOffset,
                    ) togetherWith ExitTransition.None
                },
            ) {
                AnimatedContentBody(text = it, width = 40)
            }
        }

        mainClock.autoAdvance = false
        state = "b"
        repeat(2) { mainClock.advanceTimeByFrame() }
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

    private fun ComposeSwingTest.slideOutOfContainerOffset(
        towards: AnimatedContentTransitionScope.SlideDirection,
        orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
        contentAlignment: Alignment = Alignment.TopStart,
        targetOffset: (offsetForFullSlide: Int) -> Int = { it },
    ): Point {
        var state by mutableStateOf("a")
        setContent {
            AnimatedContent(
                targetState = state,
                modifier = SwingModifier.styleable { componentOrientation(orientation) },
                contentAlignment = contentAlignment,
                transitionSpec = {
                    fadeIn(tween(ENTER_MILLIS)) togetherWith
                        (
                            slideOutOfContainer(towards, tween(EXIT_MILLIS), targetOffset) +
                                ExitTransition.KeepUntilTransitionsFinished
                        )
                },
            ) {
                AnimatedContentBody(
                    text = it,
                    width = if (it == "a") LEAVING_WIDTH else ARRIVING_WIDTH,
                    height = if (it == "a") LEAVING_HEIGHT else ARRIVING_HEIGHT,
                )
            }
        }

        mainClock.autoAdvance = false
        state = "b"
        repeat(FRAMES_PAST_THE_EXIT) { mainClock.advanceTimeByFrame() }
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
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertEquals(
                2,
                container.componentCount,
                "the content being left went before the exit of its part had finished",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, container.componentCount, "the content being left stayed once its part had finished")
        }
}
