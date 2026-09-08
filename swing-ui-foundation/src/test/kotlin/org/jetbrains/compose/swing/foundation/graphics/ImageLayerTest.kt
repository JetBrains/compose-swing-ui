package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ImageLayer]: what a recording draws, what the transforms move, and what each refusal
 * names.
 *
 * Pixels are read back from an off-screen [BufferedImage] destination, so every assertion is on the
 * drawing itself rather than on anything the layer reports about it. Where a drawing lands on the
 * destination's pixels one to one - an integral translation at the recorded size - the assertion is on
 * the exact ARGB value; where it goes through the interpolating transform pipeline it is on the color
 * within the tolerance resampling leaves, sampled a pixel inside the region's boundary except where a
 * test is about the boundary itself.
 *
 * The harness runs the whole test body and painting on the single event dispatch thread, which is the
 * thread [ImageLayer.record] of a component requires.
 */
class ImageLayerTest {
    @Test
    fun aFreshLayerIsEmpty() =
        runComposeSwingTest {
            val layer = ImageLayer()

            assertFalse(layer.hasContent, "A layer holds no recording until something is recorded into it.")
            assertEquals(Dimension(0, 0), layer.size, "An empty layer has no size.")
            assertEquals(1f, layer.alpha, "alpha defaults to 1f.")
            assertEquals(1f, layer.scaleX, "scaleX defaults to 1f.")
            assertEquals(1f, layer.scaleY, "scaleY defaults to 1f.")
            assertEquals(0f, layer.translationX, "translationX defaults to 0f.")
            assertEquals(0f, layer.translationY, "translationY defaults to 0f.")
            assertEquals(0f, layer.rotationZ, "rotationZ defaults to 0f.")
            assertEquals(null, layer.pivotOffset, "pivotOffset defaults to the center.")
            assertEquals(
                0,
                renderImage(64, 48) { layer.draw(it) }.getRGB(1, 1),
                "An empty layer draws nothing, leaving the destination untouched.",
            )
        }

    @Test
    fun aRecordingIsDrawnThroughAGraphicsClippedToIt() =
        runComposeSwingTest {
            var clip: Rectangle? = null
            ImageLayer().record(10, 6, scale = 0.5) { clip = it.clipBounds }

            assertEquals(Rectangle(0, 0, 10, 6), clip, "A recording's graphics clips to the recording.")
        }

    @Test
    fun aRecordingDrawsItsPixelsAtThePositionItWasDrawnAt() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            assertTrue(layer.hasContent, "A recorded layer holds content.")
            assertEquals(Dimension(SQUARE, SQUARE), layer.size, "size is the size the recording was made at.")

            val destination = renderImage(64, 48) { layer.draw(it, x = 2, y = 1) }

            assertEquals(
                Color.RED.rgb,
                destination.getRGB(2, 1),
                "The recording's origin lands at the x, y draw was given.",
            )
            assertEquals(
                Color.RED.rgb,
                destination.getRGB(9, 8),
                "The recording covers its full size from that origin.",
            )
            assertEquals(0, destination.getRGB(1, 1), "Nothing is drawn left of the origin.")
            assertEquals(0, destination.getRGB(2, 0), "Nothing is drawn above the origin.")
            assertEquals(0, destination.getRGB(10, 1), "Nothing is drawn right of the recording.")
            assertEquals(0, destination.getRGB(2, 9), "Nothing is drawn below the recording.")
        }

    @Test
    fun aScaledRecordingIsLogicallySizedAndBuffersDevicePixels() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare(scale = 2.0)

            assertEquals(Dimension(SQUARE, SQUARE), layer.size, "size is logical, whatever the scale recorded at.")

            val recorded = layer.toBufferedImage()
            assertEquals(SQUARE * 2, recorded.width, "The buffer is allocated in device pixels.")
            assertEquals(SQUARE * 2, recorded.height, "The buffer is allocated in device pixels.")
            assertEquals(BufferedImage.TYPE_INT_ARGB, recorded.type, "The buffer is TYPE_INT_ARGB.")

            val destination = renderImage(64, 48) { layer.draw(it) }
            assertRedAt(destination, SQUARE - 2, SQUARE - 2, "A scaled recording still occupies its logical size.")
            assertEquals(
                0,
                destination.getRGB(SQUARE + 2, 1),
                "A scaled recording covers no more ground than its size.",
            )
        }

    @Test
    fun aScaledRecordingKeepsDetailFinerThanALogicalUnit() =
        runComposeSwingTest {
            // Half a logical unit is a whole device pixel at scale 2.0 and nothing at all at scale 1.0, so a
            // recording that ignored the scale would have no row to draw here.
            val layer = ImageLayer()
            layer.record(SQUARE, SQUARE, scale = 2.0) { graphics ->
                graphics.color = Color.BLUE
                graphics.fillRect(0, 0, SQUARE, SQUARE)
                graphics.color = Color.RED
                graphics.fill(Rectangle2D.Double(0.0, 0.0, SQUARE.toDouble(), 0.5))
            }

            // A destination scaled to match draws the buffer's device pixels one for one, so the stripe and
            // the row under it stay separate rather than averaging into one logical unit of purple.
            val destination =
                renderImage(64, 48) { graphics ->
                    graphics.scale(2.0, 2.0)
                    layer.draw(graphics)
                }

            val stripe = destination.getRGB(1, 0)
            val below = destination.getRGB(1, 1)
            assertTrue(
                ((stripe shr 16) and 0xFF) > 0xC0 && (stripe and 0xFF) < 0x40,
                "The half-unit stripe holds a device row of its own color, but it was 0x${Integer.toHexString(
                    stripe,
                )}.",
            )
            assertTrue(
                ((below shr 16) and 0xFF) < 0x40 && (below and 0xFF) > 0xC0,
                "The row under the stripe is untouched by it, but it was 0x${Integer.toHexString(below)}.",
            )
        }

    @Test
    fun recordingAComponentCapturesTheBoundsItCarriesAndLaysNothingOut() =
        runComposeSwingTest {
            val layer = ImageLayer()
            val child = JLabel("child")
            val panel = JPanel(FlowLayout())
            panel.background = Color.RED
            panel.add(child)
            panel.setSize(20, 10)
            // Bounds no layout pass would produce, so a pass run by the capture would be visible below.
            val bounds = Rectangle(2, 2, 6, 4)
            child.bounds = bounds

            layer.record(panel)

            assertEquals(Dimension(20, 10), layer.size, "A capture is sized by the bounds the component carries.")
            assertEquals(bounds, child.bounds, "A capture lays nothing out, so a child keeps the bounds it carried.")
            assertEquals(
                Color.RED.rgb,
                layer.toBufferedImage().getRGB(18, 8),
                "A capture holds the component's own pixels.",
            )
        }

    @Test
    fun toBufferedImageReturnsACopyTheCallerOwns() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            val copy = layer.toBufferedImage()
            copy.setRGB(0, 0, Color.BLUE.rgb)

            assertEquals(
                Color.RED.rgb,
                renderImage(64, 48) { layer.draw(it) }.getRGB(0, 0),
                "Mutating the returned image must not change what the layer draws.",
            )

            layer.release()

            assertEquals(Color.BLUE.rgb, copy.getRGB(0, 0), "release must not touch an image already handed out.")
            assertEquals(Color.RED.rgb, copy.getRGB(1, 1), "release must not touch an image already handed out.")
        }

    @Test
    fun sizeAnswersWithAFreshDimension() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            layer.size.width = 99

            assertEquals(SQUARE, layer.size.width, "Each read of size answers with a fresh Dimension.")
        }

    @Test
    fun aSecondRecordingReplacesTheFirst() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            layer.record(SQUARE * 2, SQUARE) { graphics ->
                graphics.color = Color.BLUE
                graphics.fillRect(0, 0, SQUARE * 2, SQUARE)
            }

            assertEquals(Dimension(SQUARE * 2, SQUARE), layer.size, "A layer holds one recording: the newest.")
            val destination = renderImage(64, 48) { layer.draw(it) }
            assertEquals(Color.BLUE.rgb, destination.getRGB(1, 1), "The newest recording is what is drawn.")
            assertEquals(
                Color.BLUE.rgb,
                destination.getRGB(SQUARE + 2, 1),
                "The newest recording is drawn at its own size.",
            )
        }

    @Test
    fun aRecordingBlockThatThrowsLeavesTheLayerHoldingWhatItHeld() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            val failure =
                assertFailsWith<IllegalStateException> {
                    layer.record(SQUARE * 2, SQUARE * 2) { error("boom") }
                }

            assertEquals("boom", failure.message, "The block's own failure reaches the caller as it is.")
            assertEquals(Dimension(SQUARE, SQUARE), layer.size, "A failed recording leaves the previous size standing.")
            assertEquals(
                Color.RED.rgb,
                renderImage(64, 48) { layer.draw(it) }.getRGB(1, 1),
                "The previous recording is still drawn.",
            )
        }

    @Test
    fun releaseEmptiesTheLayerIsIdempotentAndAllowsRecordingAgain() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.alpha = 0.5f

            layer.release()
            layer.release()

            assertFalse(layer.hasContent, "A released layer holds no recording.")
            assertEquals(Dimension(0, 0), layer.size, "A released layer has no size.")
            assertEquals(0.5f, layer.alpha, "release leaves the transforms as they stand.")
            assertEquals(
                0,
                renderImage(64, 48) { layer.draw(it) }.getRGB(1, 1),
                "A released layer draws nothing.",
            )
            assertFailsWith<IllegalStateException>("A released layer has no pixels to hand back.") {
                layer.toBufferedImage()
            }

            layer.alpha = 1f
            layer.recordSquare(color = Color.BLUE)

            assertTrue(layer.hasContent, "release is a reset: the layer records again.")
            assertEquals(
                Color.BLUE.rgb,
                renderImage(64, 48) { layer.draw(it) }.getRGB(1, 1),
                "The layer draws what it recorded after.",
            )
        }

    @Test
    fun rememberImageLayerReleasesTheLayerWhenTheCompositionLeaves() =
        runComposeSwingTest {
            var present by mutableStateOf(true)
            var remembered: ImageLayer? = null
            setContent {
                Panel {
                    Label(text = "anchor")
                    if (present) {
                        remembered = rememberImageLayer()
                    }
                }
            }

            val layer = assertNotNull(remembered, "rememberImageLayer hands its layer to the composition that asked.")
            layer.recordSquare()
            assertTrue(layer.hasContent, "The remembered layer holds what was recorded into it.")

            present = false
            awaitIdle()

            assertFalse(layer.hasContent, "Leaving the composition releases the remembered layer.")
            assertEquals(Dimension(0, 0), layer.size, "A released layer has no size.")
        }

    @Test
    fun recordingRefusesANonPositiveSize() =
        runComposeSwingTest {
            val layer = ImageLayer()

            assertNames("width", assertFailsWith<IllegalArgumentException> { layer.record(0, SQUARE) { } })
            assertNames("width", assertFailsWith<IllegalArgumentException> { layer.record(-1, SQUARE) { } })
            assertNames("height", assertFailsWith<IllegalArgumentException> { layer.record(SQUARE, 0) { } })
            assertNames("height", assertFailsWith<IllegalArgumentException> { layer.record(SQUARE, -1) { } })
            assertFalse(layer.hasContent, "A refused recording leaves the layer as it was.")
        }

    @Test
    fun recordingRefusesANonPositiveScale() =
        runComposeSwingTest {
            val layer = ImageLayer()
            val panel = JPanel()
            panel.setSize(SQUARE, SQUARE)

            assertNames("scale", assertFailsWith<IllegalArgumentException> { layer.record(SQUARE, SQUARE, 0.0) { } })
            assertNames("scale", assertFailsWith<IllegalArgumentException> { layer.record(SQUARE, SQUARE, -1.0) { } })
            assertNames("scale", assertFailsWith<IllegalArgumentException> { layer.record(panel, scale = 0.0) })
            assertFalse(layer.hasContent, "A refused recording leaves the layer as it was.")
        }

    @Test
    fun recordingRefusesAnEmptyComponent() =
        runComposeSwingTest {
            val layer = ImageLayer()
            val flat = JPanel()
            flat.setSize(SQUARE, 0)
            val thin = JPanel()
            thin.setSize(0, SQUARE)

            val empty = assertFailsWith<IllegalArgumentException> { layer.record(JPanel()) }
            val noHeight = assertFailsWith<IllegalArgumentException> { layer.record(flat) }
            val noWidth = assertFailsWith<IllegalArgumentException> { layer.record(thin) }

            assertNames("0x0", empty)
            assertNames("${SQUARE}x0", noHeight)
            assertNames("0x$SQUARE", noWidth)
            // One empty dimension is as empty as two, and each is refused as a capture: an `or` in place
            // of the `and` would let a component with one positive dimension through to the recording
            // underneath, which refuses the empty one as a width or a height instead.
            for (failure in listOf(empty, noHeight, noWidth)) {
                assertNames("size the component first", failure)
            }
            assertFalse(layer.hasContent, "A refused capture leaves the layer as it was.")
        }

    @Test
    fun toBufferedImageRefusesALayerWithNothingRecorded() =
        runComposeSwingTest {
            val layer = ImageLayer()

            val failure = assertFailsWith<IllegalStateException> { layer.toBufferedImage() }

            assertNames("nothing recorded", failure)
        }

    @Test
    fun filterReplacesTheRecording() {
        val layer = ImageLayer()
        layer.recordSquare()
        layer.filter(ConvolveOp(Kernel(1, 1, floatArrayOf(0.5f)), ConvolveOp.EDGE_NO_OP, null))

        val painted = renderImage(64, 48) { graphics -> layer.draw(graphics) }
        val filtered = painted.getRGB(1, 1)

        assertEquals(
            Color.RED.rgb,
            filtered or (0xFF shl 24),
            "The recording kept its color; halving every premultiplied band halves coverage, not hue.",
        )
        assertTrue(
            filtered ushr 24 in 127..128,
            "The operation ran over the recording, and what it produced is what is drawn.",
        )
        assertEquals(Dimension(SQUARE, SQUARE), layer.size, "The recording still stands for the same area.")
    }

    @Test
    fun filteringAnEmptyLayerDoesNothing() {
        val layer = ImageLayer()

        layer.filter(ConvolveOp(Kernel(1, 1, floatArrayOf(0.5f)), ConvolveOp.EDGE_NO_OP, null))

        assertFalse(layer.hasContent, "There was nothing to filter, and nothing was recorded by trying.")
    }

    @Test
    fun aFilteredEdgeKeepsItsColor() {
        val layer = ImageLayer()
        layer.record(SQUARE, SQUARE) { graphics ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, SQUARE / 2, SQUARE)
        }
        val weights = FloatArray(5) { 1f / 5 }
        layer.filter(ConvolveOp(Kernel(5, 1, weights), ConvolveOp.EDGE_ZERO_FILL, null))

        val painted = renderImage(64, 48) { graphics -> layer.draw(graphics) }
        val spread = painted.getRGB(SQUARE / 2, SQUARE / 2)

        assertTrue(spread ushr 24 in 1..0xFE, "The edge spread, so it is neither solid nor empty there.")
        assertEquals(
            Color.WHITE.rgb,
            spread or (0xFF shl 24),
            "White spread over transparent stays white. Convolving straight alpha would have mixed the " +
                "color of the transparent pixels in and turned the edge gray.",
        )
    }

    @Test
    fun aRecordingTheDeviceDiscardedIsNoContent() {
        val layer = discardedRecording()

        assertFalse(layer.hasContent, "A recording the graphics device discarded is not content.")
    }

    @Test
    fun aRecordingTheDeviceDiscardedDrawsNothing() {
        val screen = FakeScreen(1.0, losable = true)
        val layer = ImageLayer()
        layer.recordSquare(screen)
        val kept = screen.surface(SQUARE, SQUARE)
        layer.draw(kept.graphics)
        assertEquals(Color.RED.rgb, kept.pixels.getRGB(1, 1), "A recording the device keeps is drawn.")

        screen.surfaces.single().lost = true
        val destination = screen.surface(SQUARE, SQUARE)
        layer.draw(destination.graphics)

        for (y in 0 until SQUARE) {
            for (x in 0 until SQUARE) {
                assertEquals(
                    0,
                    destination.pixels.getRGB(x, y),
                    "A recording the device discarded draws nothing, leaving the destination untouched at $x, $y.",
                )
            }
        }
    }

    @Test
    fun filteringARecordingTheDeviceDiscardedDoesNothing() {
        val layer = discardedRecording()

        layer.filter(ConvolveOp(Kernel(1, 1, floatArrayOf(0.5f)), ConvolveOp.EDGE_NO_OP, null))

        assertFalse(layer.hasContent, "Filtering a discarded recording makes no content of it.")
    }

    @Test
    fun toBufferedImageRefusesARecordingTheDeviceDiscarded() {
        val layer = discardedRecording()

        val failure = assertFailsWith<IllegalStateException> { layer.toBufferedImage() }

        assertNames("nothing recorded", failure)
    }

    /** A layer holding a red square recorded for a screen whose graphics device then discarded it. */
    private fun discardedRecording(): ImageLayer {
        val screen = FakeScreen(1.0, losable = true)
        val layer = ImageLayer()
        layer.recordSquare(screen)
        screen.surfaces.single().lost = true
        return layer
    }

    /** Records a red square aligned to a graphics of [screen], onto a surface [screen] makes. */
    private fun ImageLayer.recordSquare(screen: FakeScreen) =
        record(screen.graphics(SQUARE, SQUARE), SQUARE, SQUARE) { graphics ->
            graphics.color = Color.RED
            graphics.fillRect(0, 0, SQUARE, SQUARE)
        }

    /** Fails unless [failure] carries a message naming [problem]. */
    private fun assertNames(
        problem: String,
        failure: Throwable,
    ) {
        val message = assertNotNull(failure.message, "A refusal must carry a message.")
        assertTrue(problem in message, "The refusal should name the problem: $message")
    }
}
