package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.zIndex
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which container an [AnimatedVisibility] is: a Foundation parent lays the scoped overload out through the transition,
 * and the unscoped overload runs the transition over its own content under any parent.
 */
class AnimatedVisibilityContainerTest {
    @Test
    fun `an expanding container in a column never paints over the sibling below it`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Column {
                    // Raised above the sibling, so only the clip keeps the content that has not expanded yet off it.
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.zIndex(1f),
                        enter = expandVertically(tween(1600), expandFrom = Alignment.Top),
                        exit = ExitTransition.None,
                    ) {
                        Column(SwingModifier.testTag(CONTENT)) {
                            Filled(color = Color.RED, width = 80, height = 40)
                            Button(text = "Hover", onClick = {})
                        }
                    }
                    Filled(SwingModifier.testTag(BELOW), width = 80, height = 20)
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) {
                driveOneFrame()
                assertContentReachesBelow("frame $it")
                assertBelowUncovered("frame $it")
            }
        }

    @Test
    fun `content scaled up inside a clipping expand adds no paint room to the container`() =
        runComposeSwingTest {
            assertEquals(Insets(0, 0, 0, 0), scaledExpandPaintOutsets(clip = true), "the clipped scale took paint room")
        }

    @Test
    fun `content scaled up inside an expand that does not clip paints past the container`() =
        runComposeSwingTest {
            val outsets = scaledExpandPaintOutsets(clip = false)
            assertTrue(outsets.left > 0 && outsets.top > 0, "the unclipped scale took no paint room: $outsets")
        }

    /** The container's paint outsets partway into an enter that scales the content up from twice its size. */
    private suspend fun ComposeSwingTest.scaledExpandPaintOutsets(clip: Boolean): Insets {
        var visible by mutableStateOf(false)
        setContent {
            Column {
                AnimatedVisibility(
                    visible = visible,
                    modifier = SwingModifier.testTag(CONTAINER),
                    enter = scaleIn(tween(1600), initialScale = 2f) + expandVertically(tween(1600), clip = clip),
                    exit = ExitTransition.None,
                ) {
                    Filled(color = Color.RED, width = 80, height = 40)
                }
            }
        }
        mainClock.autoAdvance = false
        visible = true
        repeat(3) { driveOneFrame() }
        return (onNodeWithTag(CONTAINER).fetch() as Decoratable).decoration.paintOutsets()
    }

    @Test
    fun `a child of an expanding container repainting on its own never paints over the sibling below`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val child = ClipRecordingComponent()
            var visible by mutableStateOf(false)
            setContent {
                Window(onCloseRequest = {}, state = WindowState(size = Dimension(400, 400)), title = CLIP_WINDOW) {
                    Column(SwingModifier.testTag(COLUMN)) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = expandVertically(expandFrom = Alignment.Top),
                            exit = ExitTransition.None,
                        ) {
                            SwingNode(factory = { child }, modifier = SwingModifier.preferredSize(80, 40))
                        }
                        Filled(SwingModifier.testTag(BELOW), width = 80, height = 20)
                    }
                }
            }
            awaitIdle()

            mainClock.autoAdvance = false
            visible = true
            repeat(7) { driveOneFrame() }
            child.clips.clear()

            // What the repaint manager runs for a repaint the child asks for, as a hover does.
            child.paintImmediately(0, 0, child.width, child.height)

            val window = onWindowWithTitle(CLIP_WINDOW)
            val column = window.onNodeWithTag(COLUMN).fetch()
            val below = window.onNodeWithTag(BELOW).fetch()
            val clip = SwingUtilities.convertRectangle(child, checkNotNull(child.clips.lastOrNull()), column)
            assertTrue(below.y in 1 until 40, "precondition: the sibling stands partway into the expand, at ${below.y}")
            assertFalse(clip.intersects(below.bounds), "the child's repaint reached ${below.bounds} below, at $clip")
        }

    @Test
    fun `a fade inside a layout leaves a background declared before it unfaded`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Box {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag(FADED).background(brush = { _, _ -> Color.RED }),
                        enter = fadeIn(HeldAlpha, initialAlpha = HALF),
                        exit = ExitTransition.None,
                    ) {
                        Filled()
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            assertHalfBlueOverOpaqueRed(Color(onNodeWithTag(FADED).captureToImage().getRGB(SAMPLE, SAMPLE), true))
        }

    @Test
    fun `a fade under any parent leaves a background declared before it unfaded`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    modifier = SwingModifier.background(brush = { _, _ -> Color.RED }),
                    enter = fadeIn(HeldAlpha, initialAlpha = HALF),
                    exit = ExitTransition.None,
                ) {
                    Filled()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            assertHalfBlueOverOpaqueRed(Color(animatedContainer().captureToImage().getRGB(SAMPLE, SAMPLE), true))
        }

    @Test
    fun `in a border layout region the container fades and its bounds expand frame by frame`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(200, 200)) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.north().testTag(CONTAINER),
                        enter = fadeIn(tween(TRANSITION_MILLIS)) + expandVertically(tween(TRANSITION_MILLIS)),
                        exit = ExitTransition.None,
                    ) {
                        Filled(width = 80, height = 40)
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            val frames =
                List(FRAMES_INTO_THE_SIZE_CHANGE) {
                    driveOneFrame()
                    val bounds = onNodeWithTag(CONTAINER).fetch().bounds
                    val alpha =
                        if (bounds.height >
                            0
                        ) {
                            onNodeWithTag(CONTAINER).paintedAlpha(bounds.width / 2, bounds.height / 2)
                        } else {
                            0f
                        }
                    bounds.height to alpha
                }

            val heights = frames.map { it.first }
            assertEquals(heights.sorted(), heights, "the region's height did not grow steadily: $frames")
            assertTrue(heights.any { it in 1 until 40 }, "the region did not expand frame by frame: $frames")
            assertTrue(frames.any { it.second in 0.01f..0.99f }, "the content did not fade in: $frames")
        }

    @Test
    fun `as a scroll pane's view the container fades and asks for a size that expands frame by frame`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                ScrollPane(modifier = SwingModifier.preferredSize(40, 40)) {
                    Viewport {
                        AnimatedVisibility(
                            visible = visible,
                            modifier = SwingModifier.testTag(CONTAINER),
                            enter = fadeIn(tween(TRANSITION_MILLIS)) + expandIn(tween(TRANSITION_MILLIS)),
                            exit = ExitTransition.None,
                        ) {
                            Filled(width = 80, height = 80)
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            val view = { onNodeOfType<JScrollPane>().fetch().viewport.view }
            val frames =
                List(FRAMES_INTO_THE_SIZE_CHANGE) {
                    driveOneFrame()
                    val shown = view()?.size
                    val alpha =
                        if (shown != null && shown.width > 1 && shown.height > 1) {
                            onNodeWithTag(CONTAINER).paintedAlpha(1, 1)
                        } else {
                            0f
                        }
                    (view()?.preferredSize?.width ?: 0) to alpha
                }
            val widths = frames.map { it.first }
            assertTrue(widths.any { it in 1 until 80 }, "the view did not expand frame by frame: $frames")
            assertEquals(widths.sorted(), widths, "the view's size did not grow steadily: $frames")
            assertTrue(frames.any { it.second in 0.01f..0.99f }, "the content did not fade in: $frames")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(80, view()?.preferredSize?.width, "the view settled short of its content")
        }

    @Test
    fun `under any parent a slide paints nothing outside the container`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(200, 40)) {
                    // Declared first, so it paints over the sibling where its slide is not clipped.
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.west().testTag(CONTAINER),
                        enter = slideInHorizontally(tween(1600)) { it },
                        exit = ExitTransition.None,
                    ) {
                        Filled(width = 40, height = 40)
                    }
                    Filled(SwingModifier.center(), color = Color.GREEN, width = 160, height = 40)
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { driveOneFrame() }

            val container = onNodeWithTag(CONTAINER).fetch()
            val content = (container as Container).getComponent(0)
            assertTrue(
                content.x in 1 until container.width,
                "precondition: the content stands partway through its slide, at ${content.x} in ${container.width}",
            )
            val outside = SwingUtilities.convertPoint(container, container.width + 2, container.height / 2, root)
            assertEquals(
                Color.GREEN.rgb,
                captureToImage().getRGB(outside.x, outside.y),
                "the slide painted past the container's bounds",
            )
        }

    @Test
    fun `inside a box, a row and a column the container slides within its parent`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                val slide = slideInHorizontally(tween(1600)) { it }
                Box(SwingModifier.preferredSize(200, 60)) {
                    AnimatedVisibility(visible, SwingModifier.testTag("box"), slide, ExitTransition.None) { Filled() }
                }
                Row(SwingModifier.preferredSize(200, 60)) {
                    AnimatedVisibility(visible, SwingModifier.testTag("row"), slide, ExitTransition.None) { Filled() }
                }
                Column(SwingModifier.preferredSize(200, 60)) {
                    AnimatedVisibility(visible, SwingModifier.testTag("column"), slide, ExitTransition.None) {
                        Filled()
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { driveOneFrame() }

            for (tag in listOf("box", "row", "column")) {
                val container = onNodeWithTag(tag).fetch<Container>()
                assertTrue(
                    container.x in 1 until 40,
                    "$tag: the container did not move within its parent as it slid, it stands at ${container.x}",
                )
                assertEquals(0, container.getComponent(0).x, "$tag: the content slid inside the container instead")
            }
        }

    /** Fails unless the content stands over the sibling below, so only the clip can keep it off that sibling. */
    private fun ComposeSwingTest.assertContentReachesBelow(moment: String) {
        val content = onNodeWithTag(CONTENT).fetch()
        val below = onNodeWithTag(BELOW).fetch()
        val bounds = SwingUtilities.convertRectangle(content.parent, content.bounds, root)
        val belowBounds = SwingUtilities.convertRectangle(below.parent, below.bounds, root)
        assertTrue(
            bounds.intersects(belowBounds),
            "$moment: precondition: the content at $bounds does not reach the sibling below at $belowBounds",
        )
    }

    private fun ComposeSwingTest.assertBelowUncovered(moment: String) {
        val below = onNodeWithTag(BELOW).fetch()
        val point = SwingUtilities.convertPoint(below, below.width / 2, 1, root)
        assertEquals(
            Color.BLUE.rgb,
            captureToImage().getRGB(point.x, point.y),
            "$moment: the expanding container painted over the sibling below it",
        )
    }
}

private fun assertHalfBlueOverOpaqueRed(pixel: Color) {
    assertEquals(255, pixel.alpha, "the background was faded along with the content: $pixel")
    assertTrue(pixel.red in 120..135 && pixel.blue in 120..135, "the content was not faded to half over it: $pixel")
}

/** Records the clip each of its paints ran under. */
private class ClipRecordingComponent : JComponent() {
    val clips = ArrayList<Rectangle>()

    override fun paintComponent(g: Graphics) {
        clips += g.clipBounds
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

/** A fade that holds its initial opacity past every frame these tests send. */
private val HeldAlpha: FiniteAnimationSpec<Float> = tween(durationMillis = 320, delayMillis = 320)

private const val HALF = 0.5f
private const val SAMPLE = 4
private const val FRAMES_TO_MEASURE = 3
private const val FRAMES_INTO_THE_SIZE_CHANGE = 10
private const val TRANSITION_MILLIS = 320
private const val BELOW = "below"
private const val CONTENT = "content"
private const val FADED = "faded"
private const val CONTAINER = "container"
private const val CLIP_WINDOW = "expand-clip"
private const val COLUMN = "column"
