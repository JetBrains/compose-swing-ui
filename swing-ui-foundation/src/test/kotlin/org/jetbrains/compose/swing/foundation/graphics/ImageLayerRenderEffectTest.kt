package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.awt.image.LookupOp
import java.awt.image.RescaleOp
import java.awt.image.ShortLookupTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Behavioral tests for [ImageLayer.renderEffect], read back from an off-screen [BufferedImage] destination. */
class ImageLayerRenderEffectTest {
    @Test
    fun aClampedBlurSoftensEveryEdgeAndKeepsThemOpaque() {
        val layer = ImageLayer()
        layer.record(FRAME, FRAME) { graphics -> graphics.paintFrame() }
        layer.renderEffect = BlurEffect(4f)

        val painted = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }

        val middle = FRAME / 2
        for ((x, y) in listOf(1 to middle, FRAME - 2 to middle, middle to 1, middle to FRAME - 2)) {
            assertMixed(painted.getRGB(x, y), "The blur reaches the stripe along the edge at $x, $y.")
        }
        for ((x, y) in listOf(0 to 0, 0 to middle, middle to 0, FRAME - 1 to FRAME - 1)) {
            assertEquals(0xFF, painted.getRGB(x, y) ushr 24, "A clamped edge stays opaque at $x, $y.")
        }
        assertRedAt(painted, middle, middle, "The middle, out of the blur's reach, keeps its color.")
    }

    @Test
    fun aBlurLeavesTheRecordingSharp() {
        val layer = ImageLayer()
        layer.record(FRAME, FRAME) { graphics -> graphics.paintFrame() }
        val recorded = layer.toBufferedImage()
        val sharp = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }

        layer.renderEffect = BlurEffect(4f)
        val blurred = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }
        layer.renderEffect = null
        val again = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }

        assertNotNull(differingPixelBounds(sharp, blurred), "The effect blurs what is drawn.")
        assertImagesPixelPerfect(sharp, again)
        assertImagesPixelPerfect(recorded, layer.toBufferedImage())
    }

    @Test
    fun aNewRecordingIsBlurredInPlaceOfTheOneBefore() {
        val layer = ImageLayer()
        layer.renderEffect = BlurEffect(4f)
        layer.record(FRAME, FRAME) { graphics -> graphics.paintFrame() }
        renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }

        layer.record(FRAME, FRAME) { graphics -> graphics.paintHalves() }
        val redrawn = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }

        val fresh =
            ImageLayer().run {
                record(FRAME, FRAME) { graphics -> graphics.paintHalves() }
                renderEffect = BlurEffect(4f)
                renderImage(FRAME, FRAME) { graphics -> draw(graphics) }
            }
        assertImagesPixelPerfect(fresh, redrawn)
    }

    @Test
    fun aBlurRadiusMustBeFinite() {
        assertFailsWith<IllegalArgumentException> { BlurEffect(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { BlurEffect(4f, Float.POSITIVE_INFINITY) }
    }

    @Test
    fun aBlurRadiusIsInTheLogicalUnitsOfTheRecording() {
        val effect = BlurEffect(4f)
        val plain =
            ImageLayer().run {
                record(FRAME, FRAME / 2) { graphics -> graphics.paintHalves() }
                renderEffect = effect
                renderImage(FRAME, FRAME / 2) { graphics -> draw(graphics) }
            }
        val scaled =
            ImageLayer().run {
                record(FRAME, FRAME / 2, scale = 2.0) { graphics -> graphics.paintHalves() }
                renderEffect = effect
                renderImage(FRAME * 2, FRAME) { graphics ->
                    graphics.scale(2.0, 2.0)
                    draw(graphics)
                }
            }
        val aligned =
            ImageLayer().run {
                renderImage(FRAME * 2, FRAME) { graphics ->
                    graphics.scale(2.0, 2.0)
                    record(graphics, FRAME, FRAME / 2) { recording -> recording.paintHalves() }
                    renderEffect = effect
                    draw(graphics)
                }
            }

        val spread = mixedRun(plain, FRAME / 4)
        assertTrue(spread >= 3, "The blur mixes the halves across $spread pixels.")
        for ((name, image) in listOf("scale 2" to scaled, "aligned under a scale of 2" to aligned)) {
            val doubled = mixedRun(image, FRAME / 2)
            assertTrue(
                doubled in spread * 2 - 2..spread * 2 + 2,
                "A recording at $name mixes twice as many pixels as one at scale 1 ($spread), not $doubled.",
            )
        }
    }

    @Test
    fun aDecalBlurFadesTheEdgesAndSpreadsPastThem() {
        val layer = ImageLayer()
        layer.recordSquare()
        val inset = SQUARE

        layer.renderEffect = BlurEffect(2f, edgeTreatment = TileMode.Decal)
        val decal = renderImage(SQUARE * 3, SQUARE * 3) { graphics -> layer.draw(graphics, inset, inset) }
        layer.renderEffect = BlurEffect(2f)
        val clamped = renderImage(SQUARE * 3, SQUARE * 3) { graphics -> layer.draw(graphics, inset, inset) }

        val middle = inset + SQUARE / 2
        assertTrue(decal.getRGB(inset - 1, middle) ushr 24 > 0, "A decal blur spreads past the recording.")
        assertTrue(decal.getRGB(inset, middle) ushr 24 in 1..0xFE, "A decal blur fades the recording's edge.")
        assertEquals(0, clamped.getRGB(inset - 1, middle) ushr 24, "A clamped blur stays within the recording.")
        assertEquals(0xFF, clamped.getRGB(inset, middle) ushr 24, "A clamped blur keeps the edge opaque.")
    }

    @Test
    fun aDecalBlurReservesItsReachOnEachBlurredSide() {
        assertEquals(
            Insets(24, 24, 24, 24),
            BlurEffect(8f, edgeTreatment = TileMode.Decal).outsets(1.0),
            "A decal blur reserves its reach on every side.",
        )
        assertEquals(
            Insets(40, 40, 40, 40),
            BlurEffect(8f, edgeTreatment = TileMode.Decal).outsets(2.0),
            "The reach grows with the scale.",
        )
        // A zero-radius axis is never reduced, but the other axis still reduces by its own radius.
        assertEquals(
            Insets(0, 24, 0, 24),
            BlurEffect(8f, 0f, TileMode.Decal).outsets(1.0),
            "Only the blurred axis reserves a reach.",
        )
        assertEquals(Insets(0, 0, 0, 0), BlurEffect(8f).outsets(1.0), "A clamped blur reserves nothing.")
        // A recording at the scale the blur reduces to is blurred there: the reach and two pixels, unenlarged.
        assertEquals(
            Insets(6, 6, 6, 6),
            BlurEffect(8f, edgeTreatment = TileMode.Decal).outsets(0.25),
            "A reduced recording reserves the reach and two pixels.",
        )
        assertEquals(
            Insets(15, 15, 15, 15),
            BlurEffect(25f, edgeTreatment = TileMode.Decal).outsets(7.0 / 25),
            "A reduced recording at a fractional scale reserves the reach and two pixels.",
        )
        // A blur reduces by at most four, and past that its kernel grows: a radius of 32 reduces to a reach of 15,
        // and reserves that reach and two pixels, enlarged four times.
        assertEquals(
            Insets(68, 68, 68, 68),
            BlurEffect(32f, edgeTreatment = TileMode.Decal).outsets(1.0),
            "A blur reduced by four reserves its reach enlarged four times.",
        )
    }

    @Test
    fun aZeroRadiusLeavesThatDirectionSharp() {
        val layer = ImageLayer()
        layer.record(FRAME, FRAME) { graphics -> graphics.paintFrame() }
        val middle = FRAME / 2

        layer.renderEffect = BlurEffect(radiusX = 4f, radiusY = 0f)
        val acrossOnly = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }
        assertMixed(acrossOnly.getRGB(STRIPE, middle), "The blur spreads across.")
        val acrossStripe = acrossOnly.getRGB(middle, STRIPE - 1)
        assertTrue(
            ((acrossStripe shr 16) and 0xFF) < 0x10 && (acrossStripe and 0xFF) > 0xF0,
            "Nothing spreads down into the stripe. Pixel was 0x${Integer.toHexString(acrossStripe)}.",
        )
        assertRedAt(acrossOnly, middle, STRIPE, "Nothing spreads down out of the stripe.")

        layer.renderEffect = BlurEffect(radiusX = 0f, radiusY = 4f)
        val downOnly = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }
        assertMixed(downOnly.getRGB(middle, STRIPE), "The blur spreads down.")
        val downStripe = downOnly.getRGB(STRIPE - 1, middle)
        assertTrue(
            ((downStripe shr 16) and 0xFF) < 0x10 && (downStripe and 0xFF) > 0xF0,
            "Nothing spreads across into the stripe. Pixel was 0x${Integer.toHexString(downStripe)}.",
        )
        assertRedAt(downOnly, STRIPE, middle, "Nothing spreads across out of the stripe.")
    }

    @Test
    fun aZeroOrNegativeRadiusLeavesTheRecordingSharp() {
        val layer = ImageLayer()
        layer.record(FRAME, FRAME) { graphics -> graphics.paintFrame() }
        val sharp = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }

        for (radius in listOf(0f, -1f)) {
            layer.renderEffect = BlurEffect(radius, edgeTreatment = TileMode.Decal)
            assertImagesPixelPerfect(sharp, renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) })
        }
    }

    @Test
    fun anyPositiveRadiusSpreadsTheRecordingHoweverSlightly() {
        // Skia's radius-to-sigma mapping adds a constant, so even a radius near zero blurs a little.
        val layer = ImageLayer()
        layer.record(FRAME, FRAME) { graphics -> graphics.paintFrame() }
        val sharp = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }

        for (radius in listOf(Float.MIN_VALUE, 0.4f)) {
            layer.renderEffect = BlurEffect(radius, edgeTreatment = TileMode.Decal)
            val blurred = renderImage(FRAME, FRAME) { graphics -> layer.draw(graphics) }
            assertNotNull(differingPixelBounds(sharp, blurred), "A radius of $radius spreads the recording.")
        }
    }

    @Test
    fun anOpDrawsInPlaceOfTheRecordingAtEachScale() {
        val inverted = ShortArray(256) { (255 - it).toShort() }
        val kept = ShortArray(256) { it.toShort() }
        val invert = LookupOp(ShortLookupTable(0, arrayOf(inverted, inverted, inverted, kept)), null)
        assertEquals(invert.asRenderEffect(), invert.asRenderEffect(), "Effects made from the same op are equal.")
        for (scale in 1..2) {
            val layer = ImageLayer()
            layer.recordSquare(scale = scale.toDouble())
            layer.renderEffect = invert.asRenderEffect()

            val side = SQUARE * scale
            val painted =
                renderImage(side, side) { graphics ->
                    graphics.scale(scale.toDouble(), scale.toDouble())
                    layer.draw(graphics)
                }

            val expected = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
            expected.createGraphics().apply {
                color = Color(0xFF00FFFF.toInt(), true)
                fillRect(0, 0, side, side)
                dispose()
            }
            assertImagesPixelPerfect(expected, painted)
        }
    }

    @Test
    fun anOpEffectIsNeverEqualToABlurEffect() {
        val inverted = ShortArray(256) { (255 - it).toShort() }
        val kept = ShortArray(256) { it.toShort() }
        val invert = LookupOp(ShortLookupTable(0, arrayOf(inverted, inverted, inverted, kept)), null)

        assertEquals(
            invert.asRenderEffect().hashCode(),
            invert.asRenderEffect().hashCode(),
            "equal effects must hash alike",
        )
        assertNotEquals<RenderEffect>(BlurEffect(1f), invert.asRenderEffect(), "An op's effect is only ever that op.")
        assertNotEquals<RenderEffect>(invert.asRenderEffect(), BlurEffect(1f), "A blur is only ever a blur.")
    }

    @Test
    fun aRenderEffectGetsPremultipliedPixels() {
        val layer = ImageLayer()
        layer.record(SQUARE, SQUARE) { graphics ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, SQUARE / 2, SQUARE)
        }
        val weights = FloatArray(5) { 1f / 5 }
        layer.renderEffect = ConvolveOp(Kernel(5, 1, weights), ConvolveOp.EDGE_ZERO_FILL, null).asRenderEffect()

        val painted = renderImage(SQUARE, SQUARE) { graphics -> layer.draw(graphics) }
        val spread = painted.getRGB(SQUARE / 2, SQUARE / 2)

        assertTrue(spread ushr 24 in 1..0xFE, "The edge spread, so it is neither solid nor empty there.")
        assertEquals(
            Color.WHITE.rgb,
            spread or (0xFF shl 24),
            "White spread over transparent stays white. A straight-alpha op would have mixed the color of the " +
                "transparent pixels in and turned the edge gray.",
        )
    }

    @Test
    fun anEqualEffectIsAppliedOncePerRecording() {
        val unchanged = RescaleOp(1f, 0f, null)
        var applied = 0
        val counted =
            object : BufferedImageOp by unchanged {
                override fun filter(
                    src: BufferedImage,
                    dst: BufferedImage?,
                ): BufferedImage = unchanged.filter(src, dst).also { applied++ }
            }
        val layer = ImageLayer()
        layer.recordSquare()
        layer.renderEffect = counted.asRenderEffect()
        renderImage(SQUARE, SQUARE) { graphics -> layer.draw(graphics) }

        layer.renderEffect = counted.asRenderEffect()
        renderImage(SQUARE, SQUARE) { graphics -> layer.draw(graphics) }
        assertEquals(1, applied, "An equal effect draws what it made of the recording before.")

        layer.recordSquare()
        renderImage(SQUARE, SQUARE) { graphics -> layer.draw(graphics) }
        assertEquals(2, applied, "A new recording is applied again.")
    }

    @Test
    fun anOpThatDrawsSrcOverDstDoesNotKeepThePreviousResult() {
        val identity = RescaleOp(1f, 0f, null)
        val compositeOver =
            object : BufferedImageOp by identity {
                override fun filter(
                    src: BufferedImage,
                    dst: BufferedImage?,
                ): BufferedImage {
                    val result = dst ?: identity.createCompatibleDestImage(src, null)
                    val graphics = result.createGraphics()
                    try {
                        graphics.composite = AlphaComposite.SrcOver
                        graphics.drawImage(src, 0, 0, null)
                    } finally {
                        graphics.dispose()
                    }
                    return result
                }
            }
        val effect = compositeOver.asRenderEffect()

        val layer = ImageLayer()
        layer.record(SQUARE, SQUARE) { graphics ->
            graphics.color = Color.RED
            graphics.fillRect(0, 0, SQUARE / 2, SQUARE)
        }
        layer.renderEffect = effect
        renderImage(SQUARE, SQUARE) { graphics -> layer.draw(graphics) }

        layer.record(SQUARE, SQUARE) { graphics ->
            graphics.color = Color.RED
            graphics.fillRect(SQUARE / 2, 0, SQUARE / 2, SQUARE)
        }
        layer.renderEffect = effect
        val redrawn = renderImage(SQUARE, SQUARE) { graphics -> layer.draw(graphics) }

        val fresh =
            ImageLayer().run {
                record(SQUARE, SQUARE) { graphics ->
                    graphics.color = Color.RED
                    graphics.fillRect(SQUARE / 2, 0, SQUARE / 2, SQUARE)
                }
                renderEffect = effect
                renderImage(SQUARE, SQUARE) { graphics -> draw(graphics) }
            }
        assertImagesPixelPerfect(fresh, redrawn)
    }

    @Test
    fun anEffectDrawsWhatItSpreadsPastTheRecordingInItsOutsets() {
        // Adds opaque green to every pixel: a transparent one turns green and the red square turns yellow.
        val backdrop = RescaleOp(floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(0f, 255f, 0f, 255f), null)
        for (scale in 1..2) {
            val scales = mutableListOf<Double>()
            val effect =
                object : RenderEffect {
                    override fun createOp(scale: Double): BufferedImageOp = backdrop.also { scales += scale }

                    override fun outsets(scale: Double): Insets {
                        val reach = (2 * scale).toInt()
                        return Insets(reach, reach, reach, reach)
                    }
                }
            val layer = ImageLayer()
            layer.recordSquare(scale = scale.toDouble())
            layer.renderEffect = effect

            val painted =
                renderImage(SQUARE * 3 * scale, SQUARE * 3 * scale) { graphics ->
                    graphics.scale(scale.toDouble(), scale.toDouble())
                    layer.draw(graphics, SQUARE, SQUARE)
                }

            assertEquals(listOf(scale.toDouble()), scales, "The op is made once, for the recording's scale.")
            val middle = (SQUARE + SQUARE / 2) * scale
            val expected =
                listOf(
                    SQUARE - 3 to 0,
                    SQUARE - 2 to 0xFF00FF00.toInt(),
                    SQUARE - 1 to 0xFF00FF00.toInt(),
                    SQUARE to 0xFFFFFF00.toInt(),
                    SQUARE * 2 - 1 to 0xFFFFFF00.toInt(),
                    SQUARE * 2 to 0xFF00FF00.toInt(),
                    SQUARE * 2 + 1 to 0xFF00FF00.toInt(),
                    SQUARE * 2 + 2 to 0,
                )
            for ((x, argb) in expected) {
                for (pixel in x * scale until (x + 1) * scale) {
                    assertEquals(argb, painted.getRGB(pixel, middle), "Pixel $pixel across at scale $scale.")
                }
            }
        }
    }

    private fun Graphics2D.paintFrame() {
        color = Color.BLUE
        fillRect(0, 0, FRAME, FRAME)
        color = Color.RED
        fillRect(STRIPE, STRIPE, FRAME - STRIPE * 2, FRAME - STRIPE * 2)
    }

    private fun Graphics2D.paintHalves() {
        color = Color.BLUE
        fillRect(0, 0, FRAME / 2, FRAME)
        color = Color.RED
        fillRect(FRAME / 2, 0, FRAME / 2, FRAME)
    }

    /** Fails unless [argb] holds both the red and the blue the frame is painted in, as a blur across it mixes. */
    private fun assertMixed(
        argb: Int,
        message: String,
    ) {
        val red = (argb shr 16) and 0xFF
        val blue = argb and 0xFF
        assertTrue(red in MIXED && blue in MIXED, "$message Pixel was 0x${Integer.toHexString(argb)}.")
    }

    /** How many pixels of row [y] of [image] hold both red and blue. */
    private fun mixedRun(
        image: BufferedImage,
        y: Int,
    ): Int =
        (0 until image.width).count { x ->
            val argb = image.getRGB(x, y)
            ((argb shr 16) and 0xFF) in MIXED && (argb and 0xFF) in MIXED
        }
}

/** The side of the recording the frame and halves are painted across. */
private const val FRAME = 40

/** The width of the blue stripe framing the red middle. */
private const val STRIPE = 3

/** A band that holds neither nothing of a color nor all of it. */
private val MIXED = 0x10..0xEF
