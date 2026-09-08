package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.zIndex
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Component
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Which container an [AnimatedContent] is: a Foundation parent lays the scoped overload out through the size
 * animation, and the unscoped overload runs the size animation over its own contents under any parent.
 */
class AnimatedContentContainerTest {
    @Test
    fun `inside a row the parent lays the container out at the size as it travels`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Row {
                    AnimatedContent(targetState = state, transitionSpec = { traveling() }) {
                        Filled(width = if (it == "a") 40 else 160)
                    }
                    Filled(SwingModifier.testTag(SIBLING))
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            val offsets =
                List(10) {
                    driveOneFrame()
                    onNodeWithTag(SIBLING).fetch().x
                }
            assertTrue(
                offsets.any { it in 41 until 160 },
                "the row did not place the sibling past a traveling size: $offsets",
            )
            assertEquals(offsets.sorted(), offsets, "the size did not travel steadily: $offsets")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(160, onNodeWithTag(SIBLING).fetch().x, "the row did not settle on the arriving size")
        }

    /**
     * An interrupted size change travels on from the size the container stood at, and its size transform is asked
     * from that size, never from the size the content being left measures.
     */
    @Test
    fun `a size change interrupted by a third state travels on from the size it stood at`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            val askedWidths = mutableListOf<Int>()
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        EnterTransition.None togetherWith ExitTransition.None using
                            SizeTransform { initialSize, _ ->
                                askedWidths += initialSize.width
                                tween(TRANSITION_MILLIS)
                            }
                    },
                ) {
                    Filled(width = mapOf("a" to 40, "b" to 200, "c" to 120).getValue(it))
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(4) { driveOneFrame() }
            val interruptedAt = container.width
            assertTrue(interruptedAt in 41 until 120, "precondition: the size stood at $interruptedAt")

            askedWidths.clear()
            state = "c"
            val widths =
                List(10) {
                    driveOneFrame()
                    container.width
                }
            assertTrue(
                widths.all { it in interruptedAt..120 } && widths.any { it in interruptedAt + 1 until 120 },
                "the size did not travel on from $interruptedAt to 120: $widths",
            )
            assertTrue(
                askedWidths.all { it in interruptedAt..120 },
                "the size transform was asked from a size the container never stood at: $askedWidths",
            )
        }

    /**
     * The scoped overload answers intrinsics by passing through to its contents, so a row under a Swing parent asks at
     * once for the room of the content arriving; the unscoped overload would answer the size as it travels.
     */
    @Test
    fun `a row under a swing parent asks at once for the size of the content arriving`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, 100)) {
                    Row(SwingModifier.west().testTag(CONTAINER)) {
                        AnimatedContent(targetState = state, transitionSpec = { traveling() }) {
                            Filled(width = if (it == "a") 40 else 160)
                        }
                        Filled()
                    }
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            val widths =
                List(10) {
                    driveOneFrame()
                    onNodeWithTag(CONTAINER).fetch().width
                }
            assertEquals(List(10) { 200 }, widths, "the row did not ask for the arriving size")
        }

    @Test
    fun `inside a column a slide between contents of different sizes settles`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Column {
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.testTag(CONTAINER),
                        transitionSpec = {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
                        },
                    ) {
                        Filled(width = if (it == "a") 180 else 340, height = if (it == "a") 44 else 96)
                    }
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            // A frame whose layout never settles fails here rather than returning.
            repeat(10) { driveOneFrame() }

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                340,
                onNodeWithTag(CONTAINER).fetch().width,
                "the container did not settle on the arriving size",
            )
        }

    @Test
    fun `a growing container in a column never paints over the sibling below it`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Column {
                    // Raised above the sibling, so only the clip keeps the content that has not grown yet off it.
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.zIndex(1f),
                        transitionSpec = { traveling() },
                    ) {
                        Filled(
                            SwingModifier.testTag("content $it"),
                            color = Color.RED,
                            width = 80,
                            height = if (it == "a") 20 else 80,
                        )
                    }
                    Filled(SwingModifier.testTag(BELOW), width = 80, height = 20)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(10) {
                driveOneFrame()
                val below = onNodeWithTag(BELOW).fetch()
                val content = onNodeWithTag("content b").fetch()
                val contentBounds = SwingUtilities.convertRectangle(content.parent, content.bounds, root)
                val belowBounds = SwingUtilities.convertRectangle(below.parent, below.bounds, root)
                assertTrue(
                    contentBounds.intersects(belowBounds),
                    "frame $it: precondition: the content at $contentBounds does not reach " +
                        "the sibling below at $belowBounds",
                )
                val point = SwingUtilities.convertPoint(below, below.width / 2, 1, root)
                assertEquals(
                    Color.BLUE.rgb,
                    captureToImage().getRGB(point.x, point.y),
                    "frame $it: the growing container painted over the sibling below it",
                )
            }
        }

    @Test
    fun `in a border layout region the container asks for a size that travels`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(200, 200)) {
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.north().testTag(CONTAINER),
                        transitionSpec = { traveling() },
                    ) {
                        Filled(height = if (it == "a") 20 else 80)
                    }
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            val heights =
                List(10) {
                    driveOneFrame()
                    onNodeWithTag(CONTAINER).fetch().height
                }
            assertTrue(heights.any { it in 21 until 80 }, "the region did not travel frame by frame: $heights")
            assertEquals(heights.sorted(), heights, "the region's height did not grow steadily: $heights")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(80, onNodeWithTag(CONTAINER).fetch().height, "the region settled short of its content")
        }

    /**
     * Pins CURRENT behavior, which DIFFERS FROM ANDROIDX: this library keeps Swing's default focus traversal,
     * so a Tab reaches the content arriving (component index 0) straight from the component before the
     * container, and a Tab from the content being left wraps back to that same component instead of landing
     * on the content arriving. androidx orders Tab by placement order (`OneDimensionalFocusSearch` sorts by
     * `placeOrder`), so there a Tab from the component before the container lands in the content being left,
     * and only a further Tab from it reaches the content arriving. Aligning this library's Tab order with
     * androidx is expected to turn this test red; assert the androidx order instead when that happens.
     */
    @Test
    fun `tab reaches the content arriving at component index 0, over the content being left`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var state by mutableStateOf("a")
            setContent {
                Window(onCloseRequest = {}, title = FOCUS_WINDOW) {
                    Panel(PanelLayout.Flow()) {
                        TextField("", onValueChange = {}, modifier = SwingModifier.testTag("before"))
                        AnimatedContent(
                            targetState = state,
                            transitionSpec = { fadeIn(tween(1600)) togetherWith fadeOut(tween(1600)) },
                        ) {
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("field $it"))
                        }
                    }
                }
            }
            awaitIdle()
            val window = onWindowWithTitle(FOCUS_WINDOW)
            val before = window.onNodeWithTag("before").fetch()
            val root = before.focusCycleRootAncestor
            // What a Tab and a Shift+Tab ask of the focus cycle, taking no focus from the machine running the test.
            val next = { from: Component -> root.focusTraversalPolicy.getComponentAfter(root, from) }
            val previous = { from: Component -> root.focusTraversalPolicy.getComponentBefore(root, from) }
            val fieldA = window.onNodeWithTag("field a").fetch()
            assertSame(fieldA, next(before), "precondition: settled content is a focus stop")

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            val fieldB = window.onNodeWithTag("field b").fetch()
            assertSame(fieldB, next(before), "a tab did not land in the content arriving")
            assertSame(
                before,
                next(fieldA),
                "a tab from the content being left did not wrap back to the component before the container",
            )
            assertSame(before, previous(fieldB), "a shift+tab from the content arriving did not leave the container")

            state = "a"
            driveOneFrame()
            assertSame(
                window.onNodeWithTag("field a").fetch(),
                next(before),
                "content arriving again stayed out of traversal",
            )
        }
}

/** A content that fills itself with [color]. */
@Composable
private fun Filled(
    modifier: SwingModifier = SwingModifier,
    color: Color = Color.BLUE,
    width: Int = 40,
    height: Int = 20,
) = Label(text = "", modifier = modifier.preferredSize(width, height).opaque(true).background(color))

/** A size that travels for longer than the frames each test sends, with contents that appear and leave at once. */
private fun AnimatedContentTransitionScope<String>.traveling(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None using SizeTransform { _, _ -> tween(TRANSITION_MILLIS) }

private const val TRANSITION_MILLIS = 320
private const val BELOW = "below"
private const val SIBLING = "sibling"
private const val CONTAINER = "container"
private const val FOCUS_WINDOW = "content-focus"
