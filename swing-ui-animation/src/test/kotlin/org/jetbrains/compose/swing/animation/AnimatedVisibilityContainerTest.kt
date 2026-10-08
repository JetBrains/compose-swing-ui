package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.Stretch.RATIO
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
import org.jetbrains.compose.swing.foundation.layout.Constrainable
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.WrappingTexts
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.swingParentSlots
import org.jetbrains.compose.swing.foundation.layout.zIndex
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
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
                    // The content stands at the region's origin, whatever its width: sample inside it.
                    val alpha = if (bounds.height > 1) onNodeWithTag(CONTAINER).paintedAlpha(1, 1) else 0f
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("restingEnters")
    fun `under a stock parent the container computes its preferred height at its preferred width`(
        shownAtFirst: Boolean,
        enter: EnterTransition,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(shownAtFirst)
        setContent {
            Panel(PanelLayout.Flow()) {
                AnimatedVisibility(
                    visible = visible,
                    modifier = SwingModifier.testTag(CONTAINER),
                    enter = enter,
                    exit = ExitTransition.None,
                ) {
                    Label("Preview", modifier = SwingModifier.testTag(CONTENT).aspectRatio(RATIO))
                }
            }
        }
        if (!shownAtFirst) {
            mainClock.autoAdvance = false
            visible = true
            repeat(3) { driveOneFrame() }
        }
        val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
        val label = onNodeWithTag(CONTENT).fetch<JComponent>()
        val atRatio = Stretch.atAspect(label.preferredSize.width)

        assertEquals(atRatio, container.preferredSize, "the ratio's height at that width")
        assertEquals(Rectangle(Point(), atRatio), label.bounds, "the label keeps its full width")
        assertEquals(160, (container as Constrainable).maxIntrinsicWidth(90), "finite max width follows the ratio")
    }

    /**
     * An exit inside a stock parent that stretches the container to the width of a region without reading preferences
     * its height there, with paints until the layout settles: the exit is still running part-way through it.
     * The content's height follows its width: read before its first layout, it is as wide as its text, and laid out,
     * it fills the region.
     */
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("stretchedExits")
    fun `an exit under a stock parent that stretches the container runs its whole duration`(
        parent: String,
        content: String,
    ) = runComposeSwingTest {
        mainClock.autoAdvance = false
        setContent {
            swingParentSlots(emptyList()).getValue(parent)(320) { modifier ->
                InitiallyExiting(
                    exit = slideOutVertically(tween(durationMillis = EXIT_DURATION_MILLIS)),
                    modifier = modifier.testTag(CONTAINER),
                    content = StretchedExitContents.getValue(content),
                )
            }
        }
        cyclesUntilStable(onNodeWithTag(CONTAINER).fetch<JComponent>())

        assertExitDuration(CONTAINER, "$parent, $content")
    }

    @Test
    fun `under a stock parent narrowed during a fade the content is laid out at the narrowed grant`() =
        runComposeSwingTest {
            fadeOutUnderNarrowedStockParent()

            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            assertEquals(160, container.width, "precondition: the parent grants the container its narrowed width")
            assertEquals(160, label.width, "the fade lays the content out at the narrowed grant")
            assertEquals(label.height, container.height, "the container is as tall as its content at that width")
        }

    @Test
    fun `under a stock parent narrowed during a fade the minimum height is the content's at its natural width`() =
        runComposeSwingTest {
            fadeOutUnderNarrowedStockParent()

            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            val intrinsic = container as Constrainable
            val natural = intrinsic.maxIntrinsicWidth(Constraints.Infinity)
            assertTrue(natural < container.width, "precondition: the natural width is short of the grant: $natural")
            assertEquals(
                intrinsic.minIntrinsicHeight(natural),
                container.minimumSize.height,
                "the minimum height is the ordinary minimum at the natural width",
            )
            assertEquals(
                Stretch.atAspect(natural).height,
                container.minimumSize.height,
                "the minimum height is the content's at the natural width, not at the granted ${label.width}",
            )
        }

    @Test
    fun `under a stock parent narrowed during a fade the intrinsic widths are the content's`() =
        runComposeSwingTest {
            fadeOutUnderNarrowedStockParent()

            val intrinsic = onNodeWithTag(CONTAINER).fetch<JComponent>() as Constrainable
            val label = onNodeWithTag(CONTENT).fetch<JComponent>()
            assertEquals(label.width, intrinsic.minIntrinsicWidth(label.height), "the min width is the content's")
            assertEquals(label.width, intrinsic.maxIntrinsicWidth(label.height), "the max width is the content's")
        }

    /**
     * A fade out of a ratio label whose minimum is 320 wide, in a stock parent that is narrowed from 320 to 160 once
     * the fade is under way.
     */
    private suspend fun ComposeSwingTest.fadeOutUnderNarrowedStockParent() {
        var visible by mutableStateOf(true)
        var parentWidth by mutableStateOf(320)
        setContent {
            Panel(PanelLayout.Flow()) {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(parentWidth, 320)) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.north().testTag(CONTAINER),
                        enter = EnterTransition.None,
                        exit = fadeOut(HeldAlpha),
                    ) {
                        Label(
                            "Preview",
                            modifier =
                                SwingModifier
                                    .testTag(CONTENT)
                                    .aspectRatio(RATIO)
                                    .minimumSize(320, (320 / RATIO).roundToInt()),
                        )
                    }
                }
            }
        }

        mainClock.autoAdvance = false
        visible = false
        repeat(3) { driveOneFrame() }
        parentWidth = 160
        repeat(3) { driveOneFrame() }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("heldEnters")
    fun `under a stock parent an enter from hidden that keeps the size lays the content out at the granted width`(
        enter: EnterTransition,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(false)
        setContent {
            Panel(PanelLayout.Flow()) {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(160, 320)) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.north().testTag(CONTAINER),
                        enter = enter,
                        exit = ExitTransition.None,
                    ) {
                        Filled(SwingModifier.testTag(CONTENT).aspectRatio(RATIO), width = 320)
                    }
                }
            }
        }

        mainClock.autoAdvance = false
        visible = true
        repeat(3) { driveOneFrame() }

        onNodeWithTag(CONTAINER).assertProperty(
            160,
            "precondition: the parent grants the container its width",
        ) { width }
        onNodeWithTag(CONTENT).assertProperty(
            Stretch.atAspect(160),
            "the content is laid out at the width the parent grants, at the ratio's height",
        ) { size }
    }

    companion object {
        @JvmStatic
        fun restingEnters(): List<Arguments> =
            listOf(
                Arguments.of(named("a shown container", true), fadeIn() + expandIn()),
                Arguments.of(named("a container that rests with expandVertically", true), expandVertically()),
                Arguments.of(named("a container that rests with expandIn", true), expandIn()),
                Arguments.of(named("a fade, which animates no size", false), fadeIn(HeldAlpha)),
            )

        /**
         * The stock parents that set the container's width; `FlowLayout` takes it from the width it prefers.
         */
        @JvmStatic
        fun stretchedExits(): List<Arguments> =
            (swingParentSlots(emptyList()).keys - "FlowLayout").flatMap { parent ->
                StretchedExitContents.keys.map { content -> Arguments.of(parent, content) }
            }

        @JvmStatic
        fun heldEnters(): List<Arguments> =
            listOf(
                Arguments.of(named("fadeIn", fadeIn(HeldAlpha))),
                // The slide holds its initial offset past every frame these tests send.
                Arguments.of(
                    named("slideInVertically", slideInVertically(tween(durationMillis = 320, delayMillis = 320))),
                ),
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

/** The contents an exit is run over, by name: one whose height follows a ratio, one whose height follows wrapping. */
private val StretchedExitContents: Map<String, @Composable AnimatedVisibilityScope.() -> Unit> =
    mapOf(
        "a label at a ratio" to {
            Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(RATIO))
        },
        "wrapping HTML text" to { WrappingTexts.getValue("HTML Label")(SwingModifier.fillMaxWidth()) },
    )

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
