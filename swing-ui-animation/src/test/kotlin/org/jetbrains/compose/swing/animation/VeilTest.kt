package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a veil puts on screen: the scrim it fills over the content while it runs, the scrim it falls back
 * on when the caller names none, the rectangle [unveilIn]'s and [veilOut]'s `matchParentSize` picks for
 * it, and the directions the two travel in.
 */
class VeilTest {
    @Test
    fun `a veil darkens the content while it runs and leaves no trace once settled`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = visible, enter = unveilIn(HeldVeil), exit = ExitTransition.None) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            val veiled = Color(animatedContainer().captureToImage().getRGB(SAMPLE, SAMPLE))

            mainClock.autoAdvance = true
            awaitIdle()
            val settled = animatedContainer().captureToImage()

            assertTrue(veiled.blue < Content.blue, "the content was painted unveiled, as $veiled")
            assertImagesPixelPerfect(filled(settled.width, settled.height), settled)
        }

    @Test
    fun `the scrim a veil fills when the caller names no color is black at half alpha`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = visible, enter = unveilIn(HeldVeil), exit = ExitTransition.None) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            val veiled = animatedContainer().captureToImage()

            assertImagesPixelPerfect(filled(veiled.width, veiled.height, DefaultScrim), veiled)
        }

    @Test
    fun `a veil matching the panel covers it beyond the content`() =
        runComposeSwingTest {
            assertTrue(
                veiledBeyondContent(matchParentSize = true),
                "the veil covered only the content the panel holds",
            )
        }

    @Test
    fun `a veil not matching the panel covers only the content`() =
        runComposeSwingTest {
            assertFalse(
                veiledBeyondContent(matchParentSize = false),
                "the veil covered the panel beyond the content it holds",
            )
        }

    @Test
    fun `the veil color animates rather than snapping to its target`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = visible, enter = unveilIn(TravelingVeil), exit = ExitTransition.None) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            val clearing = sampledVeil()

            assertEqualsSorted(clearing)
            assertTrue(clearing.distinct().size > 2, "the veil snapped to its target instead of animating: $clearing")
        }

    @Test
    fun `the content is unveiled on the first frame of its exit`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = veilOut(HeldVeil)) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = false
            driveOneFrame()

            assertEquals(
                Content,
                Color(animatedContainer().captureToImage().getRGB(SAMPLE, SAMPLE)),
                "the exit started from a scrim that already stood over the content",
            )
        }

    @Test
    fun `no scrim is filled over content that is painted at nothing`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(HeldFade) + unveilIn(HeldVeil),
                    exit = ExitTransition.None,
                ) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }

            assertEquals(
                0,
                Color(animatedContainer().captureToImage().getRGB(SAMPLE, SAMPLE), true).alpha,
                "the scrim stood on its own over content the fade had left nothing of",
            )
        }

    @Test
    fun `unveilIn and veilOut run in opposite directions`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = unveilIn(TravelingVeil),
                    exit = veilOut(TravelingVeil),
                ) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
            val entering = sampledVeil()

            mainClock.autoAdvance = true
            awaitIdle()
            mainClock.autoAdvance = false
            visible = false
            val leaving = sampledVeil()

            assertTrue(entering.first() < entering.last(), "the unveil darkened the content: $entering")
            assertTrue(leaving.first() > leaving.last(), "the veil cleared the content: $leaving")
        }

    /**
     * Whether the veil covers a point the panel holds outside the content standing in it, while the veil
     * is at the color it starts from.
     *
     * The panel is declared larger than the content it holds, so the two rectangles a veil can cover are
     * different rectangles and the point below falls inside only one of them.
     */
    private suspend fun ComposeSwingTest.veiledBeyondContent(matchParentSize: Boolean): Boolean {
        var visible by mutableStateOf(false)
        setContent {
            AnimatedVisibility(
                visible = visible,
                modifier = SwingModifier.preferredSize(width = 80, height = 40),
                enter = unveilIn(HeldVeil, matchParentSize = matchParentSize),
                exit = ExitTransition.None,
            ) {
                Block()
            }
        }

        mainClock.autoAdvance = false
        visible = true
        repeat(FRAMES_TO_MEASURE) { driveOneFrame() }
        val panel = animatedContainer().captureToImage()
        return Color(panel.getRGB(60, 30), true).alpha != 0
    }

    /** How much of the content shows through the veil, one reading per frame over the frames sampled. */
    private suspend fun ComposeSwingTest.sampledVeil(): List<Int> =
        List(6) {
            driveOneFrame()
            Color(animatedContainer().captureToImage().getRGB(SAMPLE, SAMPLE)).blue
        }
}

/** Asserts [readings] never go backwards, which a veil whose spec does not overshoot never does. */
private fun assertEqualsSorted(readings: List<Int>) =
    assertTrue(readings == readings.sorted(), "the veil did not clear steadily: $readings")

/** A content that fills itself, so what the veil leaves of it can be read off one pixel. */
@Composable
private fun Block() =
    Label(
        text = "",
        modifier =
            SwingModifier
                .preferredSize(width = BLOCK_WIDTH, height = BLOCK_HEIGHT)
                .opaque(true)
                .background(Content),
    )

/**
 * An opaque image of the content's own color with [scrim] filled over it, which is what the block alone
 * rasterizes to under that scrim. A `null` scrim leaves the block bare.
 */
private fun filled(
    width: Int,
    height: Int,
    scrim: Color? = null,
): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.color = Content
        graphics.fillRect(0, 0, width, height)
        if (scrim != null) {
            graphics.color = scrim
            graphics.fillRect(0, 0, width, height)
        }
    } finally {
        graphics.dispose()
    }
    return image
}

/**
 * A veil that holds the color it starts from for the whole test: the delay outlasts the frames the test
 * sends, so a captured pixel carries the color the factory names rather than a sample of a curve.
 */
private val HeldVeil: FiniteAnimationSpec<Color> = tween(durationMillis = 320, delayMillis = 320)

/** A fade that holds the opacity it starts from for the whole test; see [HeldVeil]. */
private val HeldFade: FiniteAnimationSpec<Float> = tween(durationMillis = 320, delayMillis = 320)

/** A veil that travels for long enough to be sampled frame by frame while it is still under way. */
private val TravelingVeil: FiniteAnimationSpec<Color> = tween(durationMillis = 640)

/** The color of the content the veil is filled over. */
private val Content = Color.BLUE

/** The scrim [unveilIn] and [veilOut] document as their default: black, half transparent. */
private val DefaultScrim = Color(0f, 0f, 0f, 0.5f)

/** The size of the content. */
private const val BLOCK_WIDTH = 40
private const val BLOCK_HEIGHT = 20

/** Frames to send before the entering content has been measured and stands at its own size. */
private const val FRAMES_TO_MEASURE = 3

/** A pixel inside the content, whatever the veil has left of it. */
private const val SAMPLE = 2
