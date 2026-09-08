package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Behavioral tests for recording an [ImageLayer] aligned to a destination and drawing it back there: the pixels
 * land where painting directly puts them, the destination's clip bounds the recording, and the layer's alpha fades
 * it as one image.
 */
class ImageLayerAlignedRecordingTest {
    @Test
    fun anAlignedRecordingDrawsThePixelsPaintingDirectlyLeavesAt1x() = assertAlignedMatchesDirect(scale = 1.0)

    @Test
    fun anAlignedRecordingDrawsThePixelsPaintingDirectlyLeavesAt2x() = assertAlignedMatchesDirect(scale = 2.0)

    @Test
    fun anAlignedRecordingPaintsOnlyInsideTheDestinationClip() =
        runComposeSwingTest {
            val layer = ImageLayer()

            val image =
                paint(scale = 1.0) { graphics ->
                    graphics.clipRect(4, 4, 6, 6)
                    recordAndDraw(layer, graphics, Color.RED)
                }

            assertEquals(Dimension(7, 8), layer.size, "the recording covers the device pixels of the clip's bounds")
            // The destination is translated by (0.3, 0.6), so the clip covers device pixels 4..9 by 5..10.
            assertEquals(Color.RED.rgb, image.getRGB(6, 7), "inside the clip")
            assertEquals(0, image.getRGB(2, 7), "left of the clip")
            assertEquals(0, image.getRGB(12, 7), "right of the clip")
            assertEquals(0, image.getRGB(6, 2), "above the clip")
        }

    @Test
    fun aClipThatLeavesNothingEmptiesTheLayerWithoutPaintingAndTheNextPaintRecordsAgain() =
        runComposeSwingTest {
            val layer = ImageLayer()
            // Two frames, as a fade paints them, so the layer keeps a buffer for reuse besides its recording.
            repeat(2) { paint(scale = 1.0) { graphics -> recordAndDraw(layer, graphics, Color.BLUE) } }
            var painted = false

            val hidden =
                paint(scale = 1.0) { graphics ->
                    graphics.clipRect(SIZE + 4, 0, 4, 4)
                    layer.record(graphics, SIZE, SIZE) { painted = true }
                    layer.draw(graphics)
                }

            assertFalse(painted, "a clip outside the area leaves nothing to paint")
            assertFalse(layer.hasContent, "the earlier recording is dropped rather than drawn where nothing paints")
            assertImagesPixelPerfect(BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB), hidden)

            val shown = paint(scale = 1.0) { graphics -> recordAndDraw(layer, graphics, Color.RED) }

            assertEquals(Color.RED.rgb, shown.getRGB(10, 10), "the next paint records and draws its own content")
        }

    @Test
    fun anAlignedRecordingDrawsAtTheLayerAlphaAsOneImage() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.alpha = 0.5f

            val image =
                paint(scale = 1.0) { graphics ->
                    layer.record(graphics, SIZE, SIZE) { recording ->
                        recording.color = Color.RED
                        recording.fillRect(0, 0, 12, 12)
                        recording.color = Color.BLUE
                        recording.fillRect(8, 8, 12, 12)
                    }
                    layer.draw(graphics)
                }

            val overlap = image.getRGB(10, 10)
            assertEquals(Color.BLUE.rgb and 0xFFFFFF, overlap and 0xFFFFFF, "the lower square does not show through")
            assertEquals(128.0, (overlap ushr 24).toDouble(), 1.0, "the overlap is at the layer's alpha")
        }

    @Test
    fun anAlignedRecordingMovesInDevicePixelsWhateverTransformTheDestinationCarries() =
        runComposeSwingTest {
            val layer = ImageLayer()
            val square: (Graphics2D) -> Unit = { graphics ->
                graphics.color = Color.RED
                graphics.fillRect(4, 4, 8, 8)
            }
            val atDefaults =
                paint(scale = 2.0) { graphics ->
                    layer.record(graphics, SIZE, SIZE, square)
                    layer.draw(graphics)
                }
            layer.translationX = 1f

            val moved =
                renderImage(SIZE * 2, SIZE * 2) { destination ->
                    val recorded = destination.create() as Graphics2D
                    recorded.scale(2.0, 2.0)
                    recorded.translate(0.3, 0.6)
                    layer.record(recorded, SIZE, SIZE, square)
                    recorded.dispose()
                    // The destination drawn onto has a transform the recording was not made under.
                    destination.scale(2.0, 2.0)
                    layer.draw(destination)
                }

            val shifted = renderImage(SIZE * 2, SIZE * 2) { it.drawImage(atDefaults, 1, 0, null) }
            assertImagesPixelPerfect(shifted, moved)
        }

    @Test
    fun aPlainRecordingAfterAnAlignedOneDrawsUnderTheDestinationsOwnTransform() =
        runComposeSwingTest {
            val square: (Graphics2D) -> Unit = { graphics ->
                graphics.color = Color.RED
                graphics.fillRect(4, 4, 8, 8)
            }
            val reAligned = ImageLayer()
            paint(scale = 2.0) { graphics ->
                reAligned.record(graphics, SIZE, SIZE) { recording -> square(recording) }
            }
            reAligned.record(SIZE, SIZE, block = square)

            val plain = ImageLayer()
            plain.record(SIZE, SIZE, block = square)

            val afterAlignment = paint(scale = 2.0) { graphics -> reAligned.draw(graphics) }
            val direct = paint(scale = 2.0) { graphics -> plain.draw(graphics) }

            assertImagesPixelPerfect(direct, afterAlignment)
        }

    @Test
    fun aReleasedAlignedLayerRecordsForTheDestinationItIsDrawnOntoNext() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val onScreen = screen.createCompatibleVolatileImage(SIZE, SIZE, Transparency.TRANSLUCENT)
        // Antialiased ellipses at alphas low enough that a screen surface and a raster round their edges differently.
        val ellipses: (Graphics2D) -> Unit = { graphics ->
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(200, 120, 40, 37)
            graphics.fill(Ellipse2D.Double(0.5, 0.5, SIZE - 1.0, SIZE - 1.0))
            graphics.color = Color(30, 90, 220, 180)
            graphics.fill(Ellipse2D.Double(3.3, 2.7, SIZE / 2.0, SIZE * 0.65))
        }
        val layer = ImageLayer()
        val graphics = onScreen.createGraphics()
        try {
            layer.record(graphics, SIZE, SIZE, ellipses)
        } finally {
            graphics.dispose()
            onScreen.flush()
        }
        layer.release()

        val fresh = ImageLayer()
        for (it in listOf(layer, fresh)) {
            renderImage(SIZE, SIZE) { raster -> it.draw(raster) }
            it.record(SIZE, SIZE, block = ellipses)
        }

        assertImagesPixelPerfect(fresh.toBufferedImage(), layer.toBufferedImage())
    }

    /**
     * Antialiased squares at sub-pixel offsets, recorded aligned and drawn back, leave every pixel exactly as painting
     * them directly does.
     */
    private fun assertAlignedMatchesDirect(scale: Double) =
        runComposeSwingTest {
            val layer = ImageLayer()
            val squares: (Graphics2D) -> Unit = { graphics ->
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics.color = Color.RED
                graphics.fill(Rectangle2D.Double(2.25, 2.25, 10.5, 10.5))
                graphics.color = Color.BLUE
                graphics.fill(Rectangle2D.Double(7.75, 7.75, 10.5, 10.5))
            }
            val direct = paint(scale) { squares(it) }
            val aligned =
                paint(scale) { graphics ->
                    layer.record(graphics, SIZE, SIZE, squares)
                    layer.draw(graphics)
                }

            assertImagesPixelPerfect(direct, aligned)
        }

    private fun recordAndDraw(
        layer: ImageLayer,
        graphics: Graphics2D,
        color: Color,
    ) {
        layer.record(graphics, SIZE, SIZE) { recording ->
            recording.color = color
            recording.fillRect(0, 0, SIZE, SIZE)
        }
        layer.draw(graphics)
    }

    /** Paints [block] onto an image of [scale] device pixels per pixel, at a sub-pixel offset of the device grid. */
    private fun paint(
        scale: Double,
        block: (Graphics2D) -> Unit,
    ): BufferedImage =
        renderImage((SIZE * scale).toInt(), (SIZE * scale).toInt()) { graphics ->
            graphics.scale(scale, scale)
            graphics.translate(0.3, 0.6)
            block(graphics)
        }

    private companion object {
        const val SIZE = 20
    }
}
