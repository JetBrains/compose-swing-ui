package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What an animated container puts on screen: which content's pixels land on top, which pivot a scale is
 * applied around, and that a fade blits the whole subtree once rather than painting each operation
 * through a composite.
 */
class AnimatedPaintingTest {
    @Test
    fun `the arriving content paints over the content it replaces`() =
        runComposeSwingTest {
            assertEquals(Arriving.rgb, arrivingOverLeaving(arrivingZIndex = 0f))
        }

    @Test
    fun `a negative target z-index sorts the arriving content under the content it replaces`() =
        runComposeSwingTest {
            assertEquals(Leaving.rgb, arrivingOverLeaving(arrivingZIndex = -1f))
        }

    @Test
    fun `a fade paints the whole subtree once at the alpha it holds`() =
        runComposeSwingTest {
            val (faded, opaque) = fadedAndOpaque { it.captureToImage() }
            assertImagesPixelPerfect(opaque.blendedAt(HELD_ALPHA), faded)
        }

    @Test
    fun `a fade paints its buffer in the device pixels it is drawn through`() =
        runComposeSwingTest {
            // A buffer sized in user pixels is blitted back under a transform reset to a plain
            // translation, so at twice the scale it would cover only the top left quarter of the panel.
            val (faded, opaque) = fadedAndOpaque { it.paintScaled(DEVICE_SCALE) }
            assertImagesPixelPerfect(opaque.blendedAt(HELD_ALPHA), faded, maxDifferentPixels = 0)
        }

    @Test
    fun `a scale beside a fade blits one buffer rasterized at the scale the content is shown at`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Box {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(HeldAlpha, initialAlpha = HELD_ALPHA) + scaleIn(HeldAlpha, HELD_SCALE),
                        exit = ExitTransition.None,
                    ) {
                        Block(color = Arriving)
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            val panel = animatedContainer().fetch()
            val held = panel.paintScaled(DEVICE_SCALE)
            val pivotX = panel.width / 2.0
            val pivotY = panel.height / 2.0

            mainClock.autoAdvance = true
            awaitIdle()
            // A buffer measured in the unscaled space would be blitted at the wrong size once the scale is
            // concatenated, which the device scale multiplies rather than hides.
            val settled =
                animatedContainer().fetch().paintScaled(DEVICE_SCALE) {
                    it.translate(pivotX, pivotY)
                    it.scale(HELD_SCALE.toDouble(), HELD_SCALE.toDouble())
                    it.translate(-pivotX, -pivotY)
                }
            assertImagesPixelPerfect(settled.blendedAt(HELD_ALPHA), held, maxDifferentPixels = 0)
        }

    @Test
    fun `a scale is applied around the pivot the half of the transition running names`() =
        runComposeSwingTest {
            assertEquals(
                TopLeft to BottomRight,
                pivotWhileEnteringAndLeaving(
                    scaleIn(HeldAlpha, HELD_SCALE, transformOrigin = TopLeft),
                    scaleOut(TravelingScale, HELD_SCALE, transformOrigin = BottomRight),
                ),
            )
        }

    @Test
    fun `an exit that scales nothing carries the pivot from the enter's towards the middle`() =
        runComposeSwingTest {
            val (entering, leaving) =
                pivotWhileEnteringAndLeaving(
                    scaleIn(HeldAlpha, HELD_SCALE, transformOrigin = TopLeft),
                    fadeOut(HeldAlpha),
                    runEnterOut = false,
                )
            assertEquals(TopLeft, entering)
            // Upstream's exit takes no pivot from the enter it interrupts: the pivot travels towards the one the
            // content rests at.
            assertTrue(leaving.pivotFractionX > 0f, "the exit kept the enter's pivot: $leaving")
        }

    @Test
    fun `an exit after an enter that scales nothing turns about the pivot the exit names`() =
        runComposeSwingTest {
            val (_, leaving) =
                pivotWhileEnteringAndLeaving(
                    fadeIn(HeldAlpha, initialAlpha = HELD_ALPHA),
                    scaleOut(TravelingScale, HELD_SCALE, transformOrigin = BottomRight),
                )
            assertEquals(BottomRight, leaving)
        }

    /**
     * The pivot the panel is scaled around while [enter] runs, and again while [exit] does.
     *
     * Both are read mid-transition: a settled panel is between segments, and the pivot it is left holding
     * is then whatever the last segment named.
     *
     * [runEnterOut] runs the enter to its end before the exit starts. A settled container drops the enter
     * it has finished, so an exit that names no scale of its own can only reach the enter's pivot with
     * `false`, which leaves the enter running for the exit to interrupt.
     */
    private suspend fun ComposeSwingTest.pivotWhileEnteringAndLeaving(
        enter: EnterTransition,
        exit: ExitTransition,
        runEnterOut: Boolean = true,
    ): Pair<TransformOrigin, TransformOrigin> {
        var visible by mutableStateOf(false)
        setContent {
            Box {
                AnimatedVisibility(visible = visible, enter = enter, exit = exit) { Block(color = Arriving) }
            }
        }

        mainClock.autoAdvance = false
        visible = true
        repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
        val entering = paintedPivot()

        if (runEnterOut) {
            mainClock.autoAdvance = true
            awaitIdle()
            mainClock.autoAdvance = false
        }
        visible = false
        repeat(FRAMES_INTO_THE_EXIT) { driveOneFrame() }
        return entering to paintedPivot()
    }

    /**
     * The pivot the container's [Block] is scaled around, read back off the rectangle it paints: a block scaled by
     * `s` around a pivot at fraction `f` of its width starts `f * (1 - s)` of that width in. `0.5f` on an axis the
     * block is not scaled along.
     */
    private fun ComposeSwingTest.paintedPivot(): TransformOrigin {
        val image = animatedContainer().captureToImage()
        val painted =
            (0 until image.width)
                .flatMap { x -> (0 until image.height).map { y -> x to y } }
                .filter { (x, y) -> Color(image.getRGB(x, y), true).alpha > 0 }
        val left = painted.minOf { it.first }
        val right = painted.maxOf { it.first } + 1
        val top = painted.minOf { it.second }
        val bottom = painted.maxOf { it.second } + 1

        fun fraction(
            start: Int,
            painted: Int,
            full: Int,
        ): Float = if (painted >= full) 0.5f else Math.round(start.toFloat() / (full - painted) * 4) / 4f
        return TransformOrigin(fraction(left, right - left, image.width), fraction(top, bottom - top, image.height))
    }

    /**
     * The color the container shows where both contents overlap, while the content being left still
     * stands at full opacity and the content arriving over it carries [arrivingZIndex].
     *
     * Each content carries the z-index the transform named while it was the target, so the one named
     * here reaches the arriving content alone.
     *
     * The exit holds its opacity through its delay, which is what keeps two opaque contents on screen at
     * once: an exit that animates nothing finishes on the frame it starts and the content it leaves is
     * disposed before anything can be read off it.
     */
    private suspend fun ComposeSwingTest.arrivingOverLeaving(arrivingZIndex: Float): Int {
        var state by mutableStateOf("a")
        setContent {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    ContentTransform(
                        EnterTransition.None,
                        fadeOut(HeldAlpha),
                        targetContentZIndex = if (targetState == "b") arrivingZIndex else 0f,
                    )
                },
            ) {
                Block(color = if (it == "a") Leaving else Arriving)
            }
        }

        mainClock.autoAdvance = false
        state = "b"
        repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
        val container = root.getComponent(0) as Container
        assertEquals(2, container.componentCount, "one of the two contents was gone before it was painted")
        return onRoot().onChild().captureToImage().getRGB(SAMPLE, SAMPLE)
    }

    /**
     * The container's fading content [rendered], at the alpha its enter holds and again once the enter
     * has finished and the container paints straight through.
     *
     * The opacity is exact rather than sampled: the enter's delay outlasts the frames the test sends, so
     * the alpha stays at the initial one the fade names.
     */
    private suspend fun ComposeSwingTest.fadedAndOpaque(
        rendered: (Component) -> BufferedImage,
    ): Pair<BufferedImage, BufferedImage> {
        var visible by mutableStateOf(false)
        setContent {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(HeldAlpha, initialAlpha = HELD_ALPHA),
                exit = ExitTransition.None,
            ) {
                Button(text = "Save", onClick = {})
            }
        }

        mainClock.autoAdvance = false
        visible = true
        repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
        val faded = rendered(animatedContainer().fetch())

        mainClock.autoAdvance = true
        awaitIdle()
        return faded to rendered(animatedContainer().fetch())
    }
}

/** A content that fills itself, so what is on top can be read off one pixel. */
@Composable
private fun Block(color: Color) =
    Label(
        text = "",
        modifier = SwingModifier.preferredSize(width = 40, height = 20).opaque(true).background(color),
    )

/**
 * Renders this component and its subtree through a graphics scaled by [scale], as a scaled display does,
 * with [through] concatenating whatever a running animation would put on top of that scale.
 */
private fun Component.paintScaled(
    scale: Double,
    through: (Graphics2D) -> Unit = {},
): BufferedImage {
    val image = BufferedImage((width * scale).toInt(), (height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.scale(scale, scale)
        through(graphics)
        // print, not paint: the harness renders the same way, because a component with no realized peer
        // paints nothing through the paint path.
        printAll(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}

/** This image blitted once at [alpha] over nothing, which is what a fade of the same subtree produces. */
private fun BufferedImage.blendedAt(alpha: Float): BufferedImage {
    val blended = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = blended.createGraphics()
    try {
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        graphics.drawImage(this, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return blended
}

/**
 * A transition that holds its initial value for the whole test: the delay outlasts the frames the test
 * sends, so a captured opacity is the one the fade names rather than a sample of a curve, and an exit
 * that would otherwise finish at once keeps the content it leaves on screen.
 */
private val HeldAlpha: FiniteAnimationSpec<Float> = tween(durationMillis = 320, delayMillis = 320)

/** A scale that starts at once, so a few frames into it the content is painted smaller around its pivot. */
private val TravelingScale: FiniteAnimationSpec<Float> = tween(durationMillis = 320)

/** Frames into an exit after which a [TravelingScale] has scaled the content and its pivot has settled. */
private const val FRAMES_INTO_THE_EXIT = 12

/** Two pivots away from the middle, one per half of a transition, so neither can pass for the default. */
private val TopLeft = TransformOrigin(0f, 0f)
private val BottomRight = TransformOrigin(1f, 1f)

/** The color of the content being left, and of the content arriving over it. */
private val Leaving = Color.RED
private val Arriving = Color.BLUE

/** The opacity the enter holds, and the one the reference blit is made at. */
private const val HELD_ALPHA = 0.5f

/** The scale the enter holds; with the device scale it composes to whole device pixels. */
private const val HELD_SCALE = 0.5f

/** The scale a two-times display draws through. */
private const val DEVICE_SCALE = 2.0

/** Frames to send before the arriving content has been measured and stands at its own size. */
private const val FRAMES_TO_MEASURE = 3

/** A pixel inside both contents, whichever of them is painted over the other. */
private const val SAMPLE = 2
