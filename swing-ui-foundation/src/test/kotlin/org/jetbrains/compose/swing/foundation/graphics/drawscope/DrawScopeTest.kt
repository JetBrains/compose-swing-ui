package org.jetbrains.compose.swing.foundation.graphics.drawscope

import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.LoadingImage
import org.jetbrains.compose.swing.foundation.graphics.renderImage
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Paint
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import java.awt.Toolkit
import java.awt.font.TextLayout
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for [DrawScope], [DrawStyle] and their extension functions, drawn through the
 * public [Canvas] with no rendering hints, so a drawing and its reference land on the same pixels.
 */
class DrawScopeTest {
    @Test
    fun drawScopeProvidesSizeWidthHeightAndCenter() =
        runComposeSwingTest {
            var observed = emptyList<Any>()

            captureEach(100, 60, { observed = listOf(size, width, height, center) })

            assertEquals(listOf(Dimension(100, 60), 100, 60, Point2D.Float(50f, 30f)), observed)
        }

    @Test
    fun writingToASizeReadInsideADrawBlockLeavesTheScopeUnaffected() =
        runComposeSwingTest {
            var reportedAfterWrite: Dimension? = null

            val (image) =
                captureEach(40, 40, {
                    size.width = 999
                    size.height = 999
                    reportedAfterWrite = size
                    drawRect(Color.RED)
                })

            assertEquals(Dimension(40, 40), reportedAfterWrite, "writing to a read of size does not change the scope")
            assertImagesPixelPerfect(renderImage(40, 40) { it.fill(Color.RED, Rectangle(0, 0, 40, 40)) }, image)
        }

    @Test
    fun centerOfAnOddSizeFallsBetweenPixels() =
        runComposeSwingTest {
            var observed: Point2D? = null

            captureEach(5, 5, { observed = center })

            assertEquals(Point2D.Float(2.5f, 2.5f), observed)
        }

    @Test
    fun anImageStillLoadingRepaintsTheCanvasOnceItHasLoaded() =
        runComposeSwingTest {
            val loading = LoadingImage(200, 200, Color.RED)

            var drawsImage = false
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(20, 20)) {
                    if (drawsImage) drawImage(loading.image)
                }
            }
            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            onNodeWithTag("canvas").captureToImage()

            withRecordedRepaints { repaints ->
                drawsImage = true
                onNodeWithTag("canvas").captureToImage()
                loading.finishLoading()

                assertTrue(repaints.repaintsOf(canvas) > 0, "the loaded image repaints the canvas it was drawn on")
            }
        }

    @Test
    fun anImageStillLoadingDrawnScaledRepaintsTheCanvasOnceItHasLoaded() =
        runComposeSwingTest {
            val png = ByteArrayOutputStream()
            ImageIO.write(renderImage(200, 200) { it.fill(Color.RED, Rectangle(0, 0, 200, 200)) }, "png", png)
            val image = Toolkit.getDefaultToolkit().createImage(png.toByteArray())

            var drawsImage = false
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(20, 20)) {
                    if (drawsImage) drawImage(image, x = 0f, y = 0f, width = 20f, height = 20f)
                }
            }
            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            onNodeWithTag("canvas").captureToImage()

            withRecordedRepaints { repaints ->
                drawsImage = true
                onNodeWithTag("canvas").captureToImage()

                waitUntil(timeout = 5.seconds) { repaints.repaintsOf(canvas) > 0 }
            }
        }

    @Test
    fun drawRectFillsCanvasWithSpecifiedColor() =
        runComposeSwingTest {
            val (image) = captureEach(40, 40, { drawRect(Color.RED) })

            assertImagesPixelPerfect(renderImage(40, 40) { it.fill(Color.RED, Rectangle(0, 0, 40, 40)) }, image)
        }

    @Test
    fun drawRectWithBoundsAndStyleStroke() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 40, {
                    drawRect(Color.WHITE)
                    drawRect(
                        paint = Color.BLUE,
                        x = 5f,
                        y = 5f,
                        width = 30f,
                        height = 30f,
                        style = DrawStyle.Stroke(2f),
                    )
                })

            assertEquals(Color.WHITE.rgb, image.getRGB(20, 20), "the outline leaves the inside alone")
            assertEquals(Color.BLUE.rgb, image.getRGB(5, 5), "the outline runs along the bounds")
        }

    @Test
    fun drawCircleRendersAtSpecifiedCenterAndRadius() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 40, {
                    drawRect(Color.WHITE)
                    drawCircle(Color.GREEN, radius = 10f, centerX = 20f, centerY = 20f)
                })

            assertEquals(Color.GREEN.rgb, image.getRGB(20, 20), "the circle is filled")
            assertEquals(Color.WHITE.rgb, image.getRGB(0, 0), "outside the circle is untouched")
        }

    @Test
    fun drawLineDrawsColoredLine() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 40, {
                    drawRect(Color.BLACK)
                    drawLine(Color.YELLOW, 0f, 20f, 40f, 20f, strokeWidth = 3f)
                })

            assertEquals(Color.YELLOW.rgb, image.getRGB(20, 20), "the line is drawn")
            assertEquals(Color.BLACK.rgb, image.getRGB(20, 0), "off the line is untouched")
        }

    @Test
    fun drawRoundRectRendersRoundedCorners() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 40, {
                    drawRect(Color.WHITE)
                    drawRoundRect(
                        Color.RED,
                        x = 0f,
                        y = 0f,
                        width = 40f,
                        height = 40f,
                        cornerRadiusX = 10f,
                        cornerRadiusY = 10f,
                    )
                })

            assertEquals(Color.RED.rgb, image.getRGB(20, 20), "the body is filled")
            assertEquals(Color.WHITE.rgb, image.getRGB(2, 2), "a corner arc of radius 10 leaves this pixel out")
        }

    @Test
    fun drawOvalRendersEllipse() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 20, {
                    drawRect(Color.WHITE)
                    drawOval(Color.MAGENTA, x = 0f, y = 0f, width = 40f, height = 20f)
                })

            assertEquals(Color.MAGENTA.rgb, image.getRGB(20, 10), "the oval's center is filled")
            assertEquals(Color.WHITE.rgb, image.getRGB(0, 0), "outside the oval is untouched")
        }

    @Test
    fun drawArcTurnsClockwiseFromThreeOClock() =
        runComposeSwingTest {
            val (wedge, open) =
                captureEach(
                    40,
                    40,
                    {
                        drawRect(Color.WHITE)
                        drawArc(Color.CYAN, startAngle = 0f, sweepAngle = 90f, useCenter = true)
                    },
                    {
                        drawRect(Color.WHITE)
                        drawArc(Color.RED, startAngle = 180f, sweepAngle = 90f, useCenter = false)
                    },
                )

            assertEquals(Color.CYAN.rgb, wedge.getRGB(30, 30), "a quarter from 3 o'clock turns down to 6 o'clock")
            assertEquals(Color.WHITE.rgb, wedge.getRGB(30, 10), "and leaves the quarter above 3 o'clock alone")
            assertEquals(Color.RED.rgb, open.getRGB(4, 12), "a quarter from 9 o'clock turns up to 12 o'clock")
            assertEquals(Color.WHITE.rgb, open.getRGB(17, 17), "an open arc does not close through the center")
        }

    @Test
    fun drawPathRendersArbitraryShape() =
        runComposeSwingTest {
            val path =
                Path2D.Float().apply {
                    moveTo(10f, 10f)
                    lineTo(30f, 10f)
                    lineTo(20f, 30f)
                    closePath()
                }

            val (image) =
                captureEach(40, 40, {
                    drawRect(Color.WHITE)
                    drawPath(path, Color.ORANGE)
                })

            assertEquals(Color.ORANGE.rgb, image.getRGB(20, 15), "inside the triangle")
            assertEquals(Color.WHITE.rgb, image.getRGB(5, 5), "outside the triangle")
        }

    @Test
    fun drawImageStretchesTheImageToTheRequestedSize() =
        runComposeSwingTest {
            val diagonal = BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB)
            repeat(3) { diagonal.setRGB(it, it, Color.RED.rgb) }

            val (image) = captureEach(90, 90, { drawImage(diagonal, x = 0f, y = 0f, width = 90f, height = 90f) })

            val stretched =
                renderImage(90, 90) { graphics ->
                    repeat(3) { graphics.fill(Color.RED, Rectangle(it * 30, it * 30, 30, 30)) }
                }
            assertImagesPixelPerfect(stretched, image)
        }

    @Test
    fun measureTextMeasuresAndRefusesAnEmptyString() =
        runComposeSwingTest {
            var measuredAdvance = 0f
            var emptyMeasure: Result<TextLayout>? = null

            captureEach(100, 40, {
                measuredAdvance = measureText("TEST", Font(Font.MONOSPACED, Font.PLAIN, 16)).advance
                emptyMeasure = runCatching { measureText("") }
            })

            assertTrue(measuredAdvance > 0f, "Measured advance should be positive")
            assertIs<IllegalArgumentException>(emptyMeasure?.exceptionOrNull(), "an empty string has no layout")
        }

    @Test
    fun anEmptyTextDrawsNothing() =
        runComposeSwingTest {
            val (drawn) = captureEach(20, 20, { drawText("") })

            assertImagesPixelPerfect(BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), drawn)
        }

    @Test
    fun alphaMultipliesIntoTheCompositeItFindsAndDoesNotCarryOver() =
        runComposeSwingTest {
            val (faded, reference) =
                captureEach(
                    60,
                    20,
                    {
                        drawRect(Color.RED, x = 0f, y = 0f, width = 20f, height = 20f, alpha = 0.5f)
                        drawRect(Color.GREEN, x = 20f, y = 0f, width = 20f, height = 20f)
                        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                        drawRect(Color.BLUE, x = 40f, y = 0f, width = 20f, height = 20f, alpha = 0.5f)
                    },
                    {
                        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                        drawRect(Color.RED, x = 0f, y = 0f, width = 20f, height = 20f)
                        graphics.composite = AlphaComposite.SrcOver
                        drawRect(Color.GREEN, x = 20f, y = 0f, width = 20f, height = 20f)
                        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)
                        drawRect(Color.BLUE, x = 40f, y = 0f, width = 20f, height = 20f)
                    },
                )

            assertImagesPixelPerfect(reference, faded)
        }

    @Test
    fun anAlphaOutsideTheRangeDrawsAtTheNearerEnd() =
        runComposeSwingTest {
            val drawn =
                captureEach(
                    20,
                    20,
                    { drawRect(Color.RED) },
                    { drawRect(Color.RED, alpha = 1.5f) },
                    {},
                    { drawRect(Color.RED, alpha = -1f) },
                    { drawRect(Color.RED, alpha = Float.NaN) },
                )
            val (opaque, above) = drawn
            val (blank, below, notANumber) = drawn.drop(2)

            assertImagesPixelPerfect(opaque, above)
            assertImagesPixelPerfect(blank, below)
            assertImagesPixelPerfect(blank, notANumber)
        }

    @Test
    fun drawingWithStrokeOrAlphaRestoresState() =
        runComposeSwingTest {
            var found: List<Any?> = emptyList()
            var left: List<Any?> = emptyList()

            captureEach(40, 40, {
                found = listOf<Any?>(graphics.stroke, graphics.composite, graphics.paint)
                drawRect(Color.RED, style = DrawStyle.Stroke(width = 5f), alpha = 0.5f)
                left = listOf<Any?>(graphics.stroke, graphics.composite, graphics.paint)
            })

            assertEquals(found, left, "a draw puts back the stroke, composite and paint it found")
        }

    @Test
    fun everyLineOverloadDrawsWhatItsFloatPrimaryDraws() =
        runComposeSwingTest {
            val stroke = BasicStroke(3f)
            val strokeOfWidth = DrawStyle.Stroke(width = 3f).stroke
            val red: Paint = Color.RED

            assertEachDrawsAlike(
                listOf(
                    listOf(
                        { drawLine(red, Point2D.Float(3f, 5f), Point2D.Float(30f, 20f), stroke, 0.5f) },
                        { drawLine(red, 3f, 5f, 30f, 20f, stroke, 0.5f) },
                    ),
                    listOf(
                        { drawLine(red, x1 = 3f, y1 = 5f, x2 = 30f, y2 = 20f, strokeWidth = 3f, alpha = 0.5f) },
                        { drawLine(red, 3f, 5f, 30f, 20f, strokeOfWidth, 0.5f) },
                    ),
                ),
            )
        }

    @Test
    fun everyShapeOverloadDrawsWhatItsFloatPrimaryDraws() =
        runComposeSwingTest {
            val outline = DrawStyle.Stroke(2f)
            val red: Paint = Color.RED

            assertEachDrawsAlike(
                listOf(
                    listOf(
                        {
                            drawCircle(
                                red,
                                radius = 7f,
                                center = Point2D.Float(12f, 15f),
                                style = outline,
                                alpha = 0.5f,
                            )
                        },
                        { drawCircle(red, 7f, 12f, 15f, outline, 0.5f) },
                    ),
                    listOf(
                        { drawRect(red, Point2D.Float(3f, 5f), Dimension(20, 11), outline, 0.5f) },
                        { drawRect(red, 3f, 5f, 20f, 11f, outline, 0.5f) },
                    ),
                    listOf(
                        { drawRoundRect(red, Point2D.Float(3f, 5f), Dimension(20, 11), 6f, 4f, outline, 0.5f) },
                        { drawRoundRect(red, 3f, 5f, 20f, 11f, 6f, 4f, outline, 0.5f) },
                    ),
                    listOf(
                        { drawOval(red, Point2D.Float(3f, 5f), Dimension(20, 11), outline, 0.5f) },
                        { drawOval(red, 3f, 5f, 20f, 11f, outline, 0.5f) },
                    ),
                    listOf(
                        { drawArc(red, 30f, 100f, true, Point2D.Float(3f, 5f), Dimension(20, 11), outline, 0.5f) },
                        { drawArc(red, 30f, 100f, true, 3f, 5f, 20f, 11f, outline, 0.5f) },
                    ),
                ),
            )
        }

    @Test
    fun everyShapeOverloadLeftAtItsDefaultsDrawsWhatItsFloatPrimaryDraws() =
        runComposeSwingTest {
            val red: Paint = Color.RED

            assertEachDrawsAlike(
                listOf(
                    listOf(
                        { drawRect(red, Point2D.Float(3f, 5f), Dimension(20, 11)) },
                        { drawRect(red, 3f, 5f, 20f, 11f) },
                    ),
                    listOf(
                        { drawRoundRect(red, Point2D.Float(3f, 5f), Dimension(20, 11)) },
                        { drawRoundRect(red, 3f, 5f, 20f, 11f) },
                    ),
                    listOf(
                        { drawOval(red, Point2D.Float(3f, 5f), Dimension(20, 11)) },
                        { drawOval(red, 3f, 5f, 20f, 11f) },
                    ),
                    listOf(
                        { drawArc(red, 30f, 100f, true, Point2D.Float(3f, 5f), Dimension(20, 11)) },
                        { drawArc(red, 30f, 100f, true, 3f, 5f, 20f, 11f) },
                    ),
                ),
            )
        }

    @Test
    fun everyImageOverloadDrawsWhatItsFloatPrimaryDraws() =
        runComposeSwingTest {
            val image = BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB).apply { setRGB(1, 2, Color.BLUE.rgb) }

            assertEachDrawsAlike(
                listOf(
                    listOf(
                        { drawImage(image, Point2D.Float(3f, 5f), Dimension(20, 11), 0.5f) },
                        { drawImage(image, 3f, 5f, 20f, 11f, 0.5f) },
                    ),
                    listOf({ drawImage(image, topLeft = Point2D.Float(3f, 5f)) }, { drawImage(image, x = 3f, y = 5f) }),
                ),
            )
        }

    @Test
    fun drawStyleStrokesWithTheSameParametersAreEqual() {
        val stroke = DrawStyle.Stroke(width = 2f, cap = BasicStroke.CAP_ROUND)

        assertEquals(
            DrawStyle.Stroke(width = 2f, cap = BasicStroke.CAP_ROUND),
            stroke,
            "strokes built with the same parameters are equal",
        )
        assertEquals(
            DrawStyle.Stroke(width = 2f, cap = BasicStroke.CAP_ROUND).hashCode(),
            stroke.hashCode(),
            "equal strokes hash the same",
        )
        assertNotEquals(DrawStyle.Stroke(width = 3f), stroke, "a different width makes strokes unequal")
        assertNotEquals<DrawStyle>(stroke, DrawStyle.Fill, "switching a stroke to a fill is a change")
    }

    @Test
    fun strokeCarriesEveryParameterIntoItsBasicStroke() {
        val dash = floatArrayOf(2f, 3f)

        val stroke =
            DrawStyle.Stroke(
                width = 10f,
                cap = BasicStroke.CAP_ROUND,
                join = BasicStroke.JOIN_BEVEL,
                miterLimit = 4f,
                dash = dash,
                dashPhase = 1f,
            )

        assertEquals(BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 4f, dash, 1f), stroke.stroke)
    }

    @Test
    fun shapeDrawsLeftAtTheirDefaultsCoverTheWholeSurface() =
        runComposeSwingTest {
            val filled = renderImage(40, 40) { it.fill(Color.RED, Rectangle(0, 0, 40, 40)) }

            val drawn =
                captureEach(
                    40,
                    40,
                    { drawRoundRect(Color.RED) },
                    { drawImage(filled) },
                    { drawImage(filled, topLeft = Point2D.Float(0f, 0f)) },
                )

            drawn.forEach { assertImagesPixelPerfect(filled, it) }
        }

    @Test
    fun roundShapeDrawsLeftAtTheirDefaultsFillTheSurfaceButItsCorners() =
        runComposeSwingTest {
            val drawn =
                captureEach(
                    40,
                    40,
                    { drawCircle(Color.RED) },
                    { drawCircle(Color.RED, center = Point(20, 20)) },
                    { drawCircle(Color.RED, center = Point2D.Float(20f, 20f)) },
                    { drawOval(Color.RED) },
                    { drawArc(Color.RED, 0f, 360f, true) },
                )

            drawn.forEachIndexed { index, image ->
                assertEquals(Color.RED.rgb, image.getRGB(20, 20), "draw $index covers the center")
                assertEquals(Color.RED.rgb, image.getRGB(20, 1), "draw $index reaches the top edge")
                assertEquals(0, image.getRGB(0, 0), "draw $index leaves the corner blank")
            }
        }

    @Test
    fun linesLeftAtTheirDefaultsAreOnePixelWideAndOpaque() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 40, {
                    drawLine(Color.RED, 0f, 10f, 40f, 10f)
                    drawLine(Color.GREEN, Point2D.Float(0f, 20f), Point2D.Float(40f, 20f))
                    drawLine(Color.BLUE, x1 = 0f, y1 = 30f, x2 = 40f, y2 = 30f, stroke = DrawStyle.Stroke().stroke)
                })

            val rows =
                renderImage(40, 40) { graphics ->
                    listOf(10 to Color.RED, 20 to Color.GREEN, 30 to Color.BLUE).forEach { (row, color) ->
                        graphics.fill(color, Rectangle(0, row, 40, 1))
                    }
                }
            assertImagesPixelPerfect(rows, image)
        }

    @Test
    fun aStrokedLineCapsButtRatherThanSquare() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 40, {
                    drawLine(Color.RED, x1 = 10f, y1 = 10f, x2 = 30f, y2 = 10f, strokeWidth = 4f)
                })

            assertEquals(Color.RED.rgb, image.getRGB(10, 10), "the cap starts exactly at the line's end")
            assertEquals(Color.RED.rgb, image.getRGB(29, 10), "and reaches the other end")
            assertEquals(0, image.getRGB(8, 10), "a square cap would have painted past the end")
            assertEquals(0, image.getRGB(31, 10), "on either side")
        }

    @Test
    fun aDefaultStrokeIsAHairlineUnderAnyScale() =
        runComposeSwingTest {
            val (image) =
                captureEach(40, 40, { scale(4f, pivotX = 0f, pivotY = 0f) { drawLine(Color.RED, 0f, 2f, 10f, 2f) } })

            assertEquals(1, (0 until 40).count { image.getRGB(20, it) != 0 }, "a hairline is one device pixel wide")
        }

    @Test
    fun strokesDefaultToAndroidxButtCapAndMiterLimit() {
        assertEquals(
            BasicStroke(0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f),
            DrawStyle.Stroke().stroke,
            "a default Stroke matches androidx's butt cap and miter limit defaults.",
        )
        assertEquals(
            BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f),
            DrawStyle.Stroke(width = 2f).stroke,
        )
    }

    @Test
    fun textLeftAtItsDefaultsDrawsFromTheTopLeftWithTheCurrentPaint() =
        runComposeSwingTest {
            val font = Font(Font.MONOSPACED, Font.PLAIN, 16)
            val inCurrentPaint: (DrawScope.() -> Unit) -> (DrawScope.() -> Unit) = { draw ->
                {
                    graphics.paint = Color.RED
                    graphics.font = font
                    draw()
                }
            }

            val images =
                captureEach(
                    40,
                    40,
                    { drawText("W", x = 0f, y = 0f, paint = Color.RED, font = font) },
                    inCurrentPaint { drawText("W") },
                    inCurrentPaint { drawText("W", topLeft = Point(0, 0)) },
                    inCurrentPaint { drawText(measureText("W", graphics.font)) },
                    inCurrentPaint { drawText(measureText("W", graphics.font), topLeft = Point(0, 0)) },
                )

            val reference = images.first()
            val painted =
                assertNotNull(differingPixelBounds(BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB), reference))
            assertTrue(
                Rectangle(0, 0, 20, 20).contains(painted),
                "the text is drawn from the top-left, but covered $painted",
            )
            images.drop(1).forEach { assertImagesPixelPerfect(reference, it) }
        }

    /** Draws each overload and the primary it pairs with, and fails unless the two draw the same visible pixels. */
    private fun ComposeSwingTest.assertEachDrawsAlike(overloads: List<List<DrawScope.() -> Unit>>) {
        val images = captureEach(40, 40, *overloads.flatten().toTypedArray())

        val blank = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
        images.chunked(2).forEachIndexed { index, (overload, primary) ->
            assertNotNull(differingPixelBounds(blank, primary), "the primary of overload $index draws something")
            assertImagesPixelPerfect(primary, overload)
        }
    }
}

/** Fills [shape] with [paint]. */
private fun Graphics2D.fill(
    paint: Paint,
    shape: Shape,
) {
    this.paint = paint
    fill(shape)
}
