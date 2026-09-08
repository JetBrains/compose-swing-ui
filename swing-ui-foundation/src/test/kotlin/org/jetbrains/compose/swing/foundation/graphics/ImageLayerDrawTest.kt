package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Composite
import java.awt.CompositeContext
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.Raster
import java.awt.image.WritableRaster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for how [ImageLayer.draw] draws a recording: its alpha, scale, translation, rotation and pivot, and
 * what it leaves on the destination.
 *
 * Pixels are read back from an off-screen [BufferedImage] destination. Where a drawing lands on the destination's
 * pixels one to one the assertion is on the exact ARGB value; where it goes through the interpolating transform
 * pipeline it is on the color within the tolerance resampling leaves.
 */
class ImageLayerDrawTest {
    @Test
    fun drawLeavesTheDestinationAsItFoundIt() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            // Every knob draw touches on its copy is set, so an escape through any of them shows up here.
            layer.alpha = 0.5f
            layer.rotationZ = 45f
            layer.scaleX = 2f
            layer.translationX = 3f

            val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                graphics.translate(3, 4)
                graphics.clipRect(0, 0, 10, 12)
                graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
                )
                val transform = graphics.transform
                val composite = graphics.composite
                val clip = graphics.clipBounds
                val interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION)

                layer.draw(graphics)

                // Restoring what was never disturbed is free, so the assertions below only mean something
                // once the drawing has actually reached the destination through all of it. The clip is the
                // destination's own translation plus its clipRect: device x in 3..12, y in 4..15.
                val drawn = differingPixelBounds(BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB), image)
                assertNotNull(drawn, "draw must draw the recording through the destination's composite.")
                assertTrue(
                    Rectangle(3, 4, 10, 12).contains(drawn),
                    "draw must draw only inside the clip, not $drawn.",
                )
                assertEquals(transform, graphics.transform, "draw must leave the destination's transform alone.")
                assertEquals(composite, graphics.composite, "draw must leave the destination's composite alone.")
                assertEquals(clip, graphics.clipBounds, "draw must leave the destination's clip alone.")
                assertEquals(
                    interpolation,
                    graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION),
                    "draw must leave the destination's rendering hints alone.",
                )
            } finally {
                graphics.dispose()
            }
        }

    @Test
    fun alphaDrawsTheRecordingTranslucently() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            val opaque = renderImage(64, 48) { layer.draw(it) }
            layer.alpha = 0.5f
            val translucent = renderImage(64, 48) { layer.draw(it) }
            layer.alpha = 0f
            val invisible = renderImage(64, 48) { layer.draw(it) }

            assertEquals(0xFF, opaque.getRGB(1, 1) ushr 24, "alpha 1f draws the recording opaque.")
            // Opaque red over a transparent destination under SRC_OVER leaves the source alpha itself, so
            // 0.5f is 0x80 and nothing else: an alpha squared, halved twice or ignored lands elsewhere.
            assertTrue(
                (translucent.getRGB(1, 1) ushr 24) in 0x7E..0x82,
                "alpha 0.5f draws the recording at half opacity, but the destination alpha was " +
                    "0x${Integer.toHexString(translucent.getRGB(1, 1) ushr 24)}.",
            )
            assertEquals(0, invisible.getRGB(1, 1), "alpha 0f draws nothing.")
        }

    @Test
    fun anAlphaOutsideTheRangeIsKeptAndDrawsAtTheNearerEnd() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            val opaque = renderImage(64, 48) { layer.draw(it) }

            layer.alpha = 1.5f
            assertEquals(1.5f, layer.alpha, "The layer keeps the alpha as assigned.")
            assertImagesPixelPerfect(opaque, renderImage(64, 48) { layer.draw(it) })
            layer.alpha = -1f
            assertImagesPixelPerfect(renderImage(64, 48) {}, renderImage(64, 48) { layer.draw(it) })
        }

    @Test
    fun aNaNAlphaDrawsNothing() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            layer.alpha = Float.NaN

            assertImagesPixelPerfect(renderImage(64, 48) {}, renderImage(64, 48) { layer.draw(it) })
        }

    @Test
    fun alphaMultipliesIntoTheTranslucencyTheDestinationCarries() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()

            val fading = { alpha: Float ->
                renderImage(64, 48) { graphics ->
                    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
                    layer.alpha = alpha
                    layer.draw(graphics)
                }.getRGB(1, 1) ushr 24
            }

            // A caller fading everything it draws is at 0.25f; a layer at 0.5f inside that is at 0.125f,
            // which is neither the layer's own alpha (0x80) nor the caller's alone (0x40).
            assertTrue(
                fading(0.5f) in 0x1E..0x22,
                "alpha must multiply into the destination's composite, but the destination alpha was " +
                    "0x${Integer.toHexString(fading(0.5f))}.",
            )
            assertTrue(
                fading(1f) in 0x3E..0x42,
                "alpha 1f must leave the destination's composite alone, but the destination alpha was " +
                    "0x${Integer.toHexString(fading(1f))}.",
            )
        }

    @Test
    fun alphaFallsBackToItsOwnCompositeWhereTheDestinationCarriesOneWithNoAlphaToRead() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            val atFullAlpha = ProbeComposite()
            val atHalfAlpha = ProbeComposite()

            // The control: a composite the layer leaves alone is what the drawing goes through, so the
            // assertion below - that this one is not - rests on the drawing having reached it at all.
            renderImage(64, 48) { graphics ->
                graphics.composite = atFullAlpha
                layer.draw(graphics)
            }

            assertTrue(atFullAlpha.used, "A layer at alpha 1f draws under the caller's composite untouched.")

            layer.alpha = 0.5f
            val destination =
                renderImage(64, 48) { graphics ->
                    graphics.composite = atHalfAlpha
                    layer.draw(graphics)
                }

            // A composite that is not an AlphaComposite carries no alpha to multiply into, so the layer
            // installs its own SRC_OVER rather than reading one out of it: a cast that assumed an
            // AlphaComposite fails here, and a fallback that left this composite standing would draw
            // through a composite that composites nothing at all.
            assertFalse(atHalfAlpha.used, "A composite carrying no alpha must not be what the drawing goes through.")
            val argb = destination.getRGB(1, 1)
            assertTrue(
                (argb ushr 24) in 0x7E..0x82 && ((argb shr 16) and 0xFF) > 0xF0,
                "alpha 0.5f must draw the recording at half opacity through the layer's own composite, but " +
                    "the pixel was 0x${Integer.toHexString(argb)}.",
            )
        }

    @Test
    fun clampedAlphaLeavesTheCallersCompositeUntouched() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            val aboveFullAlpha = ProbeComposite()
            layer.alpha = 1.5f

            renderImage(64, 48) { graphics ->
                graphics.composite = aboveFullAlpha
                layer.draw(graphics)
            }

            assertTrue(aboveFullAlpha.used, "A layer above alpha 1f draws under the caller's composite untouched.")
        }

    @Test
    fun scaleXStretchesHorizontallyAboutTheCenter() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.scaleX = 4f

            val destination = renderImage(64, 48) { layer.draw(it, x = 20) }

            // About the center, x = 4, the square spans 20 + 4 -/+ SQUARE * 4 / 2: x in [8, 40). About the origin it
            // would span [20, 52).
            assertRedAt(destination, 10, 1, "scaleX stretches the drawing to the left of its center as well.")
            assertRedAt(destination, 38, 1, "scaleX stretches the drawing to the right of its center.")
            assertEquals(0, destination.getRGB(44, 1), "A stretch about the origin would reach here.")
            assertEquals(0, destination.getRGB(22, SQUARE + 2), "scaleX leaves the vertical extent alone.")
        }

    @Test
    fun scaleYStretchesVerticallyAboutTheCenter() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.scaleY = 4f

            val destination = renderImage(64, 48) { layer.draw(it, y = 20) }

            assertRedAt(destination, 1, 10, "scaleY stretches the drawing above its center as well.")
            assertRedAt(destination, 1, 38, "scaleY stretches the drawing below its center.")
            assertEquals(0, destination.getRGB(1, 44), "A stretch about the origin would reach here.")
            assertEquals(0, destination.getRGB(SQUARE + 2, 22), "scaleY leaves the horizontal extent alone.")
        }

    @Test
    fun pivotOffsetIsThePointScaleAppliesAround() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.scaleX = 4f
            layer.pivotOffset = Point2D.Float(0f, 0f)

            val destination = renderImage(64, 48) { layer.draw(it, x = 20) }

            assertRedAt(destination, 50, 1, "About the origin, the stretch reaches x = 20 + SQUARE * 4.")
            assertEquals(0, destination.getRGB(10, 1), "About the origin, nothing is stretched left of it.")
        }

    @Test
    fun aResampledDrawInterpolatesRatherThanRepeatingWholePixels() =
        runComposeSwingTest {
            // Java2D resamples nearest-neighbor unless asked otherwise, and the two agree everywhere except
            // across an edge in the recording: a color boundary stretched over three destination pixels is
            // a run of blended pixels under bilinear and a hard step under nearest-neighbor.
            val layer = ImageLayer()
            layer.record(SQUARE, SQUARE) { graphics ->
                graphics.color = Color.RED
                graphics.fillRect(0, 0, SQUARE / 2, SQUARE)
                graphics.color = Color.BLUE
                graphics.fillRect(SQUARE / 2, 0, SQUARE / 2, SQUARE)
            }
            layer.scaleX = 3f
            layer.pivotOffset = Point2D.Float(0f, 0f)

            val destination = renderImage(64, 48) { layer.draw(it) }

            val bilinear =
                renderImage(64, 48) { graphics ->
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    graphics.scale(3.0, 1.0)
                    graphics.drawImage(layer.toBufferedImage(), 0, 0, null)
                }
            assertImagesPixelPerfect(bilinear, destination)
        }

    @Test
    fun aRecordingReducedOnlyInHeightInterpolatesAsItIsDrawnBackToItsSize() =
        runComposeSwingTest {
            // At a scale of 0.6, a width of 2 keeps its 2 device pixels while a height of 10 takes 6.
            val layer = ImageLayer()
            layer.record(2, 10, 0.6) { graphics ->
                graphics.color = Color.RED
                graphics.fillRect(0, 0, 2, 5)
                graphics.color = Color.BLUE
                graphics.fillRect(0, 5, 2, 5)
            }

            val destination = renderImage(2, 10) { layer.draw(it) }

            val bilinear =
                renderImage(2, 10) { graphics ->
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    graphics.drawImage(layer.toBufferedImage(), 0, 0, 2, 10, null)
                }
            assertImagesPixelPerfect(bilinear, destination)
        }

    @Test
    fun translationOffsetsTheRecordingAndAddsToTheDrawPosition() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.translationX = 20f
            layer.translationY = 12f

            val destination = renderImage(64, 48) { layer.draw(it, x = 4, y = 0) }

            assertEquals(
                Color.RED.rgb,
                destination.getRGB(26, 14),
                "The drawing lands at x + translationX, y + translationY.",
            )
            assertEquals(
                0,
                destination.getRGB(22, 14),
                "draw's x adds to translationX rather than being replaced by it.",
            )
            assertEquals(0, destination.getRGB(26, 1), "translationY moved the drawing down out of this row.")
            assertEquals(0, destination.getRGB(1, 14), "translationX moved the drawing right out of this column.")
        }

    @Test
    fun rotationZTurnsTheRecordingClockwiseInDegreesAboutThePivot() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.pivotOffset = Point2D.Float(0f, 0f)
            layer.rotationZ = 90f

            val destination = renderImage(64, 48) { layer.draw(it, x = 32, y = 0) }

            // A clockwise quarter turn about the top-left corner sweeps the square into the quadrant left of that
            // corner: x in (32 - SQUARE, 32]. A counter-clockwise one would sweep it above the destination.
            assertRedAt(destination, 28, 3, "rotationZ turns the drawing a clockwise quarter at 90 degrees.")
            assertEquals(
                0,
                destination.getRGB(36, 3),
                "The rotated drawing vacates the ground an unrotated one covers.",
            )
        }

    @Test
    fun rotationZTurnsTheRecordingAboutItsCenterByDefault() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.record(16, 4) { graphics ->
                graphics.color = Color.RED
                graphics.fillRect(0, 0, 16, 4)
            }
            layer.rotationZ = 90f

            val destination = renderImage(64, 48) { layer.draw(it, x = 20, y = 20) }

            // Turned a quarter about its center, (28, 22), the 16 x 4 bar stands upright: x in [26, 30), y in [14, 30).
            assertRedAt(destination, 28, 16, "The turned bar reaches above its center.")
            assertRedAt(destination, 28, 28, "The turned bar reaches below its center.")
            assertEquals(0, destination.getRGB(22, 22), "The turned bar vacates its left end.")
            assertEquals(0, destination.getRGB(34, 22), "The turned bar vacates its right end.")
        }

    @Test
    fun rotationTurnsTheScaledRecordingSoScaleAppliesFirst() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.pivotOffset = Point2D.Float(0f, 0f)
            layer.rotationZ = 90f
            layer.scaleX = 3f

            val destination = renderImage(64, 48) { layer.draw(it, x = 32, y = 0) }

            // Stretched across first, then turned a quarter, the square runs down the height: x in (32 - SQUARE, 32],
            // y in [0, SQUARE * 3). Turned first and stretched after, it would run across: x in (32 - SQUARE * 3, 32].
            assertRedAt(destination, 28, 20, "The quarter turn carries the stretch down the height.")
            assertEquals(0, destination.getRGB(12, 4), "A stretch applied after the turn would reach across to here.")
        }

    @Test
    fun alphaKeepsTheDestinationsCompositeRule() =
        runComposeSwingTest {
            val layer = ImageLayer()
            layer.recordSquare()
            layer.alpha = 0.5f

            val destination =
                renderImage(64, 48) { graphics ->
                    graphics.color = Color.BLACK
                    graphics.fillRect(0, 0, 64, 48)
                    graphics.composite = AlphaComposite.DstOut
                    layer.draw(graphics)
                }

            // A destination-out drawing at half strength takes half the destination's coverage away; a source-over one
            // in its place would cover the black with opaque-enough red instead.
            val opacity = destination.getRGB(1, 1) ushr 24
            assertTrue(
                opacity in 127..128,
                "alpha must fade the rule the destination carries, but opacity was $opacity.",
            )
        }

    /**
     * A destination composite that is not an [AlphaComposite], and so carries no alpha a layer could
     * multiply into. It records whether the drawing went through it, and composites nothing: a drawing that
     * reaches it leaves the destination exactly as it found it.
     */
    private class ProbeComposite : Composite {
        var used = false
            private set

        override fun createContext(
            srcColorModel: ColorModel,
            dstColorModel: ColorModel,
            hints: RenderingHints,
        ): CompositeContext {
            used = true
            return object : CompositeContext {
                override fun compose(
                    src: Raster,
                    dstIn: Raster,
                    dstOut: WritableRaster,
                ) = dstOut.setRect(dstIn)

                override fun dispose() = Unit
            }
        }
    }
}
