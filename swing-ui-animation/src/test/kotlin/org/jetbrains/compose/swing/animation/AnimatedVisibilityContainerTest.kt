package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanelScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Constrainable
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.WRAPPING_TEXT
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
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
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
    fun `under a stock parent the container asks its height at the width it asks for`() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    AnimatedVisibility(visible = true, modifier = SwingModifier.testTag(CONTAINER)) {
                        Label("Preview", modifier = SwingModifier.testTag(CONTENT).aspectRatio(RATIO))
                    }
                }
            }
            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            val atRatio = Dimension(label.preferredSize.width, (label.preferredSize.width / RATIO).roundToInt())

            assertEquals(atRatio, container.preferredSize, "the preferred height is the ratio's height at that width")
            assertEquals(Rectangle(Point(), atRatio), label.bounds, "the label keeps its full width at the ratio")
        }

    @Test
    fun `under a stock parent a slide out asked its size before its first layout runs its whole duration`() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDE, PARENT_HEIGHT)) {
                    val state = remember { MutableTransitionState(true).apply { targetState = false } }
                    AnimatedVisibility(
                        visibleState = state,
                        modifier = SwingModifier.north().testTag(CONTAINER),
                        enter = EnterTransition.None,
                        exit = slideOutVertically(tween(EXIT_MILLIS)),
                    ) {
                        // Asked before its first layout, the label is as wide as its text; laid out, it fills the
                        // region.
                        Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(RATIO))
                    }
                }
            }

            mainClock.advanceTimeBy((EXIT_MILLIS - MARGIN_MILLIS).milliseconds)
            awaitIdle()
            val running = onAllNodesWithTag(CONTAINER).fetchAll<Component>()
            assertEquals(1, running.size, "the exit is still running")
            mainClock.advanceTimeBy(EXIT_MILLIS.milliseconds)
            awaitIdle()
            onNodeWithTag(CONTAINER).assertDoesNotExist()
        }

    @Test
    fun `under a box layout that stretches it an exit runs its whole duration`() =
        assertStretchedExitRunsItsWholeDuration { exiting ->
            Panel(PanelLayout.Box(BoxLayout.Y_AXIS), modifier = SwingModifier.north()) { exiting(SwingModifier) }
        }

    @Test
    fun `under a grid bag layout that stretches it an exit runs its whole duration`() =
        assertStretchedExitRunsItsWholeDuration { exiting ->
            Panel(PanelLayout.GridBag, modifier = SwingModifier.north()) {
                exiting(SwingModifier.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
            }
        }

    /**
     * Shows an exit inside the stock [parent], which stretches the container to the width of a region without asking
     * its height there, and paints frames until the layout settles: the exit is still running part-way through it.
     * The content's height follows its width: asked before its first layout, it is as wide as its text, and laid out,
     * it fills the region.
     */
    private fun assertStretchedExitRunsItsWholeDuration(
        parent: @Composable BorderPanelScope.(exiting: @Composable (SwingModifier) -> Unit) -> Unit,
    ) {
        val contents: Map<String, @Composable AnimatedVisibilityScope.() -> Unit> =
            mapOf(
                "a label at a ratio" to {
                    Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(RATIO))
                },
                "wrapping HTML text" to {
                    Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.fillMaxWidth())
                },
            )
        for ((name, content) in contents) {
            runComposeSwingTest {
                mainClock.autoAdvance = false
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDE, PARENT_HEIGHT)) {
                        parent { modifier ->
                            val state = remember { MutableTransitionState(true).apply { targetState = false } }
                            AnimatedVisibility(
                                visibleState = state,
                                modifier = modifier.testTag(CONTAINER),
                                enter = EnterTransition.None,
                                exit = slideOutVertically(tween(EXIT_MILLIS)),
                                content = content,
                            )
                        }
                    }
                }
                cyclesUntilStable(onNodeWithTag(CONTAINER).fetch<JComponent>())

                mainClock.advanceTimeBy((EXIT_MILLIS - MARGIN_MILLIS).milliseconds)
                awaitIdle()
                val running = onAllNodesWithTag(CONTAINER).fetchAll<Component>()
                assertEquals(1, running.size, "$name: the exit is still running")
                mainClock.advanceTimeBy(EXIT_MILLIS.milliseconds)
                awaitIdle()
                onNodeWithTag(CONTAINER).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `under a stock parent the container answers its max intrinsic width at the height asked`() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    AnimatedVisibility(visible = true, modifier = SwingModifier.testTag(CONTAINER)) {
                        Label("Preview", modifier = SwingModifier.aspectRatio(RATIO))
                    }
                }
            }
            val container = onNodeWithTag(CONTAINER).fetch<JComponent>() as Constrainable

            assertEquals(160, container.maxIntrinsicWidth(90), "the ratio's width at that height")
        }

    @Test
    fun `under a stock parent a fade asks the height at the width it asks for`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Panel(PanelLayout.Flow()) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag(CONTAINER),
                        enter = fadeIn(HeldAlpha),
                        exit = ExitTransition.None,
                    ) {
                        Label("Preview", modifier = SwingModifier.testTag(CONTENT).aspectRatio(RATIO))
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            val atRatio = Dimension(label.preferredSize.width, (label.preferredSize.width / RATIO).roundToInt())

            assertEquals(
                atRatio,
                onNodeWithTag(CONTAINER).fetch().preferredSize,
                "a transition that animates no size asks the ratio's height at that width",
            )
            assertEquals(Rectangle(Point(), atRatio), label.bounds, "the label keeps its full width at the ratio")
        }

    @Test
    fun `under a stock parent narrowed during a fade the container asks the height of the width its content has`() =
        runComposeSwingTest {
            narrowParentDuringFade()

            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            assertEquals(label.height, container.height, "the container is as tall as its content at that width")
        }

    @Test
    fun `under a stock parent narrowed during a fade the container's minimum height is its content's at that width`() =
        runComposeSwingTest {
            narrowParentDuringFade()

            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            assertEquals(label.height, container.minimumSize.height, "the minimum height is the content's height")
        }

    @Test
    fun `under a stock parent narrowed during a fade the container's min width is for the height its content has`() =
        runComposeSwingTest {
            narrowParentDuringFade()

            val container = onNodeWithTag(CONTAINER).fetch<JComponent>() as Constrainable
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            assertEquals(label.width, container.minIntrinsicWidth(label.height / 2), "the min width is the content's")
        }

    @Test
    fun `under a stock parent narrowed during a fade the container's max width is for the height its content has`() =
        runComposeSwingTest {
            narrowParentDuringFade()

            val container = onNodeWithTag(CONTAINER).fetch<JComponent>() as Constrainable
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            assertEquals(label.width, container.maxIntrinsicWidth(label.height / 2), "the max width is the content's")
        }

    @Test
    fun `under a stock parent an enter from hidden that keeps the size lays the content out at the granted width`() {
        val enters = mapOf("fadeIn" to fadeIn(HeldAlpha), "slideInVertically" to slideInVertically(HeldOffset))
        for ((kind, enter) in enters) {
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent {
                    Panel(PanelLayout.Flow()) {
                        Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(NARROW, PARENT_HEIGHT)) {
                            AnimatedVisibility(
                                visible = visible,
                                modifier = SwingModifier.north().testTag(CONTAINER),
                                enter = enter,
                                exit = ExitTransition.None,
                            ) {
                                Filled(SwingModifier.testTag(CONTENT).aspectRatio(RATIO), width = WIDE)
                            }
                        }
                    }
                }

                mainClock.autoAdvance = false
                visible = true
                repeat(FRAMES_TO_MEASURE) { driveOneFrame() }

                val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
                assertEquals(NARROW, container.width, "$kind: precondition: the parent grants the container its width")
                assertEquals(
                    Dimension(NARROW, (NARROW / RATIO).roundToInt()),
                    onNodeWithTag(CONTENT).fetch<JComponent>().size,
                    "$kind: the content is laid out at the width the parent grants, at the ratio's height",
                )
            }
        }
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

    /**
     * Fades the container out under a stock parent that is then narrowed below the content, which the fade keeps at the
     * width it had.
     */
    private suspend fun ComposeSwingTest.narrowParentDuringFade() {
        var visible by mutableStateOf(true)
        var parentWidth by mutableStateOf(WIDE)
        setContent {
            Panel(PanelLayout.Flow()) {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(parentWidth, 320)) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.north().testTag(CONTAINER),
                        enter = EnterTransition.None,
                        exit = fadeOut(HeldAlpha),
                    ) {
                        Label("Preview", modifier = SwingModifier.testTag(CONTENT).aspectRatio(RATIO))
                    }
                }
            }
        }

        mainClock.autoAdvance = false
        visible = false
        repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
        parentWidth = NARROW
        repeat(FRAMES_TO_MEASURE) { driveOneFrame() }

        val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
        val label = onNodeWithTag(CONTENT).fetch<JComponent>()
        assertEquals(NARROW, container.width, "precondition: the parent grants the container its narrowed width")
        assertEquals(WIDE, label.width, "precondition: the fade keeps the content at the width it had")
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
private const val WIDE = 320
private const val NARROW = 160
private const val RATIO = 16f / 9f
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

/** A slide that holds its initial offset past every frame these tests send. */
private val HeldOffset: FiniteAnimationSpec<Point> = tween(durationMillis = 320, delayMillis = 320)

private const val PARENT_HEIGHT = 320
private const val EXIT_MILLIS = 1000

/** The time an exit has left when a test checks that it is still running. */
internal const val MARGIN_MILLIS = 320
