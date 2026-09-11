package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ColorModel
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.awt.image.LookupOp
import java.awt.image.ShortLookupTable
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Behavioral tests for recording an [ImageLayer] again: a layer reuses the surfaces it holds, what it
 * draws is only ever the newest recording, and its pixels are those of a recording into a fresh buffer.
 *
 * The pixel references record into a fresh non-premultiplied buffer and premultiply it only to filter,
 * which is where translucent antialiased edges round differently than a premultiplied recording would.
 */
class ImageLayerReRecordingTest {
    @Test
    fun reRecordingAtTheSameSizeShowsOnlyTheNewContent() {
        for (scale in listOf(1.0, 2.0)) {
            val layer = ImageLayer()
            layer.recordSquare(Color.RED, scale)

            repeat(2) {
                layer.record(SQUARE, SQUARE, scale) { graphics ->
                    graphics.color = Color.BLUE
                    graphics.fillRect(0, 0, SQUARE / 2, SQUARE)
                }
            }

            val destination = renderImage(SQUARE * 4, SQUARE * 4) { layer.draw(it) }
            assertEquals(Color.BLUE.rgb, destination.getRGB(1, 1), "The newest recording is drawn at scale $scale.")
            assertEquals(
                0,
                destination.getRGB(SQUARE - 2, 1),
                "Nothing of an earlier recording at the same size is left where the newest drew nothing, " +
                    "at scale $scale.",
            )
        }
    }

    @Test
    fun aLayerDrawnOntoAScreenAndBackRecordsThePixelsOfAFreshLayerAgain() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val size = SQUARE * 2 + 1
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val onScreen = screen.createCompatibleVolatileImage(size, size, Transparency.TRANSLUCENT)
        val layer = ImageLayer()
        val recordAndDraw: (Graphics2D) -> Unit = { graphics ->
            layer.record(size, size, block = ::drawTranslucentEllipses)
            layer.draw(graphics)
        }
        val reference = renderImage(size, size, block = recordAndDraw)

        val graphics = onScreen.createGraphics()
        try {
            repeat(2) { recordAndDraw(graphics) }
        } finally {
            graphics.dispose()
            onScreen.flush()
        }
        // The first recording is made for the screen, the next for the raster drawn onto.
        repeat(2) { renderImage(size, size, block = recordAndDraw) }

        assertImagesPixelPerfect(reference, renderImage(size, size, block = recordAndDraw))
    }

    @Test
    fun aLayerRecordedForARasterAndThenForAScaledScreenRecordsWhatAFreshLayerDoes() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        assumeFalse(screen.defaultTransform.isIdentity, "requires a screen with more than one pixel per unit")
        val size = SQUARE * 2
        val onScreen = screen.createCompatibleVolatileImage(size, size, Transparency.TRANSLUCENT)
        val record: (ImageLayer, Graphics2D) -> Unit = { layer, graphics ->
            layer.record(graphics, size, size, ::drawTranslucentEllipses)
        }
        val layer = ImageLayer()
        // Recorded for a raster at as many pixels as the screen has units, the size the screen's recording asks for.
        repeat(2) { renderImage(size, size) { raster -> record(layer, raster) } }

        val graphics = onScreen.createGraphics()
        try {
            // Once right after the raster, and once after the recording made for the raster is replaced.
            repeat(2) {
                val fresh = ImageLayer()
                record(fresh, graphics)
                record(layer, graphics)

                assertImagesPixelPerfect(fresh.toBufferedImage(), layer.toBufferedImage())
            }
        } finally {
            graphics.dispose()
            onScreen.flush()
        }
    }

    @Test
    fun aLayerRecordedForAnAcceleratedScreenAtOneScaleAndThenAnotherRecordsWhatAFreshLayerDoes() {
        val size = SQUARE * 2
        val record: (ImageLayer, Graphics2D) -> Unit = { layer, graphics ->
            layer.record(graphics, size, size, ::drawTranslucentEllipses)
        }
        val layer = ImageLayer()
        val one = FakeScreen(1.0).graphics(size, size)
        try {
            // A window on a 1x monitor, recorded for the screen twice.
            repeat(2) { record(layer, one) }
        } finally {
            one.dispose()
        }

        // The window moves to a 2x monitor: both are accelerated configurations, only their scale differs.
        val two = FakeScreen(2.0).graphics(size, size)
        try {
            repeat(3) {
                val fresh = ImageLayer()
                record(fresh, two)
                record(layer, two)

                assertImagesPixelPerfect(fresh.toBufferedImage(), layer.toBufferedImage())
            }
        } finally {
            two.dispose()
        }
    }

    @Test
    fun aLayerRecordedForAScaledScreenAndThenAnUnscaledOneRecordsWhatAFreshLayerDoes() {
        val size = SQUARE * 2
        val record: (ImageLayer, Graphics2D) -> Unit = { layer, graphics ->
            layer.record(graphics, size, size, ::drawTranslucentEllipses)
        }
        val layer = ImageLayer()
        val two = FakeScreen(2.0).graphics(size, size)
        try {
            // A window on a 2x monitor, recorded for the screen twice.
            repeat(2) { record(layer, two) }
        } finally {
            two.dispose()
        }

        // The window moves to a 1x monitor: both are accelerated configurations, only their scale differs.
        val one = FakeScreen(1.0).graphics(size, size)
        try {
            repeat(3) {
                val fresh = ImageLayer()
                record(fresh, one)
                record(layer, one)

                assertImagesPixelPerfect(fresh.toBufferedImage(), layer.toBufferedImage())
            }
        } finally {
            one.dispose()
        }
    }

    @Test
    fun aFilteredLayerDrawnOntoAScreenFiltersThePixelsOfAFreshRecording() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val size = SQUARE * 2
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val onScreen = screen.createCompatibleVolatileImage(size, size, Transparency.TRANSLUCENT)
        val reference = freshRecording()
        reference.coerceData(true)
        val filtered = BLUR_DOWN.filter(BLUR_ACROSS.filter(reference, null), null)
        val layer = ImageLayer()

        val graphics = onScreen.createGraphics()
        try {
            repeat(3) {
                layer.record(size, size, block = ::drawTranslucentEllipses)
                layer.filter(BLUR_ACROSS)
                layer.filter(BLUR_DOWN)
                assertImagesPixelPerfect(filtered, layer.toBufferedImage())
                layer.draw(graphics)
            }
        } finally {
            graphics.dispose()
            onScreen.flush()
        }
    }

    @Test
    fun recordingAtASmallerSizeDrawsOnlyThatSize() {
        val layer = ImageLayer()
        layer.record(SQUARE * 2, SQUARE * 2) { graphics ->
            graphics.color = Color.BLUE
            graphics.fillRect(0, 0, SQUARE * 2, SQUARE * 2)
        }

        layer.recordSquare(Color.RED, 1.0)

        assertEquals(Dimension(SQUARE, SQUARE), layer.size, "size is the newest recording's.")
        val destination = renderImage(SQUARE * 4, SQUARE * 4) { layer.draw(it) }
        assertEquals(Color.RED.rgb, destination.getRGB(SQUARE - 1, SQUARE - 1), "The newest recording is drawn.")
        assertEquals(0, destination.getRGB(SQUARE + 1, SQUARE + 1), "Nothing is drawn past the newest size.")
    }

    @Test
    fun aRecordingMadeWhileRecordingTheSameLayerIsReplacedByTheOuterOne() {
        val layer = ImageLayer()
        layer.record(SQUARE, SQUARE) { outer ->
            outer.color = Color.RED
            outer.fillRect(0, 0, SQUARE / 2, SQUARE)
            layer.record(SQUARE, SQUARE) { inner ->
                inner.color = Color.BLUE
                inner.fillRect(0, 0, SQUARE, SQUARE)
            }
            layer.draw(outer, x = SQUARE / 2)
        }

        val destination = renderImage(SQUARE * 4, SQUARE * 4) { layer.draw(it) }
        assertEquals(
            Color.RED.rgb,
            destination.getRGB(1, 1),
            "The outer recording keeps what it drew before the inner one.",
        )
        assertEquals(
            Color.BLUE.rgb,
            destination.getRGB(SQUARE - 2, 1),
            "The inner recording is drawn into the outer one, and the outer one is what the layer holds.",
        )
    }

    @Test
    fun aReRecordingReadsBackTheTranslucentEdgesOfAFreshRecording() {
        val layer = ImageLayer()
        repeat(3) {
            layer.record(SQUARE * 2, SQUARE * 2, block = ::drawTranslucentEllipses)
            assertImagesPixelPerfect(freshRecording(), layer.toBufferedImage())
        }
    }

    @Test
    fun aReFilteredRecordingHasTheTranslucentEdgesOfAFreshFilter() {
        val layer = ImageLayer()
        repeat(3) {
            layer.record(SQUARE * 2, SQUARE * 2, block = ::drawTranslucentEllipses)
            layer.filter(BLUR_ACROSS)
            layer.filter(BLUR_DOWN)

            val reference = freshRecording()
            reference.coerceData(true)
            val filtered = BLUR_DOWN.filter(BLUR_ACROSS.filter(reference, null), null)
            assertImagesPixelPerfect(filtered, layer.toBufferedImage())
        }
    }

    @Test
    fun aFilterThatReturnsAViewOfItsSourceKeepsItsPixelsThroughLaterRecordingsAndFilters() {
        val layer = ImageLayer()
        repeat(3) {
            layer.record(SQUARE * 2, SQUARE * 2, block = ::drawTranslucentEllipses)
            // Any public operation may return a result sharing its source's pixels.
            layer.filter(
                object : BufferedImageOp by BLUR_ACROSS {
                    override fun filter(
                        src: BufferedImage,
                        dest: BufferedImage?,
                    ): BufferedImage = src.getSubimage(0, 0, src.width, src.height)
                },
            )
            layer.filter(BLUR_ACROSS)
            layer.filter(BLUR_DOWN)

            val reference = freshRecording()
            reference.coerceData(true)
            val filtered = BLUR_DOWN.filter(BLUR_ACROSS.filter(reference, null), null)
            assertImagesPixelPerfect(filtered, layer.toBufferedImage())
            val painted = renderImage(SQUARE * 4, SQUARE * 4) { graphics -> layer.draw(graphics) }
            val expected = renderImage(SQUARE * 4, SQUARE * 4) { graphics -> graphics.drawImage(filtered, 0, 0, null) }
            assertImagesPixelPerfect(expected, painted)
        }
    }

    @Test
    fun aFilterThatFailsAfterAViewKeepsTheViewsPixels() {
        // The view reads the premultiplied copy's pixels as straight ones, so the next filter premultiplies
        // them again, into a buffer of its own unless the layer reuses the copy the view shares.
        val straightView =
            object : BufferedImageOp by BLUR_ACROSS {
                override fun filter(
                    src: BufferedImage,
                    dest: BufferedImage?,
                ): BufferedImage = BufferedImage(ColorModel.getRGBdefault(), src.raster, false, null)
            }
        val failing =
            object : BufferedImageOp by BLUR_ACROSS {
                override fun filter(
                    src: BufferedImage,
                    dest: BufferedImage?,
                ): BufferedImage = error("The operation failed.")
            }
        val layer = ImageLayer()
        layer.record(SQUARE * 2, SQUARE * 2, block = ::drawTranslucentEllipses)
        layer.filter(straightView)
        val reference = layer.toBufferedImage()

        assertFailsWith<IllegalStateException> { layer.filter(failing) }

        assertImagesPixelPerfect(reference, layer.toBufferedImage())
    }

    @Test
    fun anImageAFilterReturnsIsNeverWrittenByLaterRecordingsOrFilters() {
        val size = SQUARE * 2
        val kept = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE)
        val keeping =
            object : BufferedImageOp by BLUR_ACROSS {
                override fun filter(
                    src: BufferedImage,
                    dest: BufferedImage?,
                ): BufferedImage = kept.apply { raster.setRect(src.raster) }
            }
        val layer = ImageLayer()
        layer.record(size, size, block = ::drawTranslucentEllipses)
        layer.filter(keeping)
        val reference = BufferedImage(kept.colorModel, kept.copyData(null), true, null)

        layer.record(size, size) { graphics ->
            graphics.color = Color.RED
            graphics.fillRect(0, 0, size, size)
        }
        layer.filter(BLUR_ACROSS)
        layer.filter(BLUR_DOWN)

        assertImagesPixelPerfect(reference, kept)
    }

    @Test
    fun aFilterThatWritesSomeBandsSeesNoEarlierPixels() {
        // Its Java fallback writes only the bands its table covers, into whatever destination it is given.
        val lookup = LookupOp(ShortLookupTable(0, ShortArray(256)), null)
        val white = BufferedImage(SQUARE, SQUARE, BufferedImage.TYPE_INT_ARGB_PRE)
        white.setRGB(0, 0, SQUARE, SQUARE, IntArray(SQUARE * SQUARE) { Color.WHITE.rgb }, 0, SQUARE)
        val expected = lookup.filter(lookup.filter(white, null), null).getRGB(1, 1)
        val layer = ImageLayer()
        layer.recordSquare(Color.WHITE, 1.0)

        layer.filter(lookup)
        layer.filter(lookup)

        assertEquals(
            expected,
            layer.toBufferedImage().getRGB(1, 1),
            "The second filter matches the operation run on fresh images, not the recording it replaced.",
        )
    }

    @Test
    fun anAntialiasedClipPaintedAgainKeepsTheTranslucentEdgesOfAFreshRecording() =
        runComposeSwingTest {
            val size = SQUARE * 2
            setContent {
                Canvas(
                    modifier =
                        decorated {
                            SwingModifier
                                .testTag(
                                    "clipped",
                                ).preferredSize(size, size)
                                .clip(CircleShape, antialias = true)
                        },
                    renderingHints = null,
                ) { drawTranslucentEllipses(graphics) }
            }
            val component = onNodeWithTag("clipped").fetch<JComponent>()
            val reference = renderImage(size, size) { freshClip(it, size) }
            repeat(3) {
                assertImagesPixelPerfect(reference, renderImage(size, size) { graphics -> component.paint(graphics) })
            }
        }

    /** What an antialiased circle clip paints over [drawTranslucentEllipses] from a fresh recording. */
    private fun freshClip(
        destination: Graphics2D,
        size: Int,
    ) {
        val recording = freshRecording()
        val graphics = recording.createGraphics()
        try {
            val outside = Area(Rectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat()))
            outside.subtract(Area(CircleShape.outline(size, size)))
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.composite = AlphaComposite.DstOut
            graphics.color = Color.WHITE
            graphics.fill(outside)
        } finally {
            graphics.dispose()
        }
        destination.drawImage(recording, 0, 0, null)
    }

    private fun freshRecording(): BufferedImage = renderImage(SQUARE * 2, SQUARE * 2, block = ::drawTranslucentEllipses)

    /** Antialiased ellipses at alphas low enough that premultiplying loses precision at their edges. */
    private fun drawTranslucentEllipses(graphics: Graphics2D) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color(200, 120, 40, 37)
        graphics.fill(Ellipse2D.Double(0.5, 0.5, SQUARE * 2 - 1.0, SQUARE * 2 - 1.0))
        graphics.color = Color(30, 90, 220, 180)
        graphics.fill(Ellipse2D.Double(3.3, 2.7, SQUARE.toDouble(), SQUARE * 1.3))
    }

    private companion object {
        private val WEIGHTS = floatArrayOf(0.2f, 0.5f, 0.3f)
        val BLUR_ACROSS = ConvolveOp(Kernel(3, 1, WEIGHTS), ConvolveOp.EDGE_NO_OP, null)
        val BLUR_DOWN = ConvolveOp(Kernel(1, 3, WEIGHTS), ConvolveOp.EDGE_NO_OP, null)
    }
}
