package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.awt.Shape as AwtShape

/**
 * Behavioral tests for the built-in decorations: what each one paints, and what it reserves.
 *
 * Each chain is declared through the public modifiers on a composed component whose bounds, paint outsets included, are
 * [STAGE_SIZE] square, and the content is a decoration declared after the chain. What is asserted is what the component
 * shows.
 */
class DecorationBuildersTest {
    @Test
    fun aClipCutsEverythingInsideIt() =
        runComposeSwingTest {
            val painted = stage().paint({ SwingModifier.clip(CircleShape) }, fill(Color.RED))

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(STAGE_SIZE / 2, STAGE_SIZE / 2),
                "The content should paint inside the circle.",
            )
            assertEquals(0, painted.getRGB(0, 0), "A corner lies outside the circle and should stay unpainted.")
        }

    @Test
    fun anAntialiasedClipKeepsWhatItsOutlineCoversInPart() =
        runComposeSwingTest {
            val stage = stage()
            val hard = stage.paint({ SwingModifier.clip(CircleShape) }, fill(Color.RED))
            val soft = stage.paint({ SwingModifier.clip(CircleShape, antialias = true) }, fill(Color.RED))

            assertFalse(hard.hasPartialCoverage(), "A clip covers a pixel or it does not; there is nothing between.")
            assertTrue(
                soft.hasPartialCoverage(),
                "An antialiased cut keeps a pixel its outline half covers, half over.",
            )
            assertEquals(
                Color.RED.rgb,
                soft.getRGB(STAGE_SIZE / 2, STAGE_SIZE / 2),
                "What lies well inside the outline is untouched.",
            )
            assertEquals(0, soft.getRGB(0, 0), "What lies well outside it is still cut away.")
        }

    @Test
    fun anAntialiasedClipToAWiderOutlinePaintsAsFarAsTheOutlineReserves() =
        runComposeSwingTest {
            val stage = stage()
            val wide =
                Shape { width, height ->
                    Rectangle2D.Float(
                        -STAGE_OUTSETS.toFloat(),
                        -STAGE_OUTSETS.toFloat(),
                        width + 2f * STAGE_OUTSETS,
                        height + 2f * STAGE_OUTSETS,
                    )
                }
            val overflowing: (Graphics2D, Int, Int) -> Unit = { graphics, width, height ->
                graphics.fill(
                    Color.RED,
                    -STAGE_OUTSETS,
                    -STAGE_OUTSETS,
                    width + 2 * STAGE_OUTSETS,
                    height + 2 * STAGE_OUTSETS,
                )
            }
            val outsets = ReservingElement(Insets(STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS))
            val hard = stage.paint({ SwingModifier.clip(wide).decoration(outsets) }, overflowing)
            val soft = stage.paint({ SwingModifier.clip(wide, antialias = true).decoration(outsets) }, overflowing)

            assertEquals(
                0xFF,
                hard.opacityAt(STAGE_OUTSETS / 2, STAGE_SIZE / 2),
                "A hard clip to the wider outline lets the overflow it reserves through.",
            )
            assertEquals(
                0xFF,
                soft.opacityAt(STAGE_OUTSETS / 2, STAGE_SIZE / 2),
                "An antialiased clip paints as far as the outline it reserves, the same as a hard one.",
            )
        }

    @Test
    fun aBackgroundPaintsUnderTheContent() =
        runComposeSwingTest {
            val painted =
                stage().paint({ SwingModifier.background(Brush.of(Color.BLUE)) }) { graphics, _, _ ->
                    graphics.fill(Color.RED, 0, 0, 10, 10)
                }

            assertEquals(Color.RED.rgb, painted.getRGB(5, 5), "The content paints over the background.")
            assertEquals(Color.BLUE.rgb, painted.getRGB(50, 50), "The background fills everything the content leaves.")
        }

    @Test
    fun aBorderPaintsOverTheContent() =
        runComposeSwingTest {
            val painted = stage().paint({ SwingModifier.border(2, Color.BLUE) }, fill(Color.RED))

            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(0, STAGE_SIZE / 2),
                "The border is painted after the content, so an opaque fill cannot wipe it.",
            )
            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(1, STAGE_SIZE / 2),
                "The border is as thick as it was declared.",
            )
            assertEquals(Color.RED.rgb, painted.getRGB(2, STAGE_SIZE / 2), "The border stops at its declared width.")
        }

    @Test
    fun aBorderWithNoWidthPaintsNothing() =
        runComposeSwingTest {
            val stage = stage()
            val content = fill(Color.RED)
            val unbordered = stage.paint(content = content)

            for (width in listOf(0, -5)) {
                for (shape in listOf(RectangleShape, CircleShape)) {
                    assertImagesPixelPerfect(
                        unbordered,
                        stage.paint({ SwingModifier.border(width, Color.BLUE, shape) }, content),
                    )
                }
            }
        }

    @Test
    fun aBorderThickerThanTheAreaFillsIt() =
        runComposeSwingTest {
            val stage = stage()
            val content = fill(Color.RED)
            for (shape in listOf(RectangleShape, CircleShape)) {
                val filled =
                    renderImage(STAGE_SIZE, STAGE_SIZE) { graphics ->
                        graphics.fill(Color.RED, 0, 0, STAGE_SIZE, STAGE_SIZE)
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        graphics.paint = Color.BLUE
                        graphics.fill(shape.outline(STAGE_SIZE, STAGE_SIZE))
                    }

                assertImagesPixelPerfect(
                    filled,
                    stage.paint({ SwingModifier.border(STAGE_SIZE * 2, Color.BLUE, shape) }, content),
                )
            }

            // A translucent line shows a row that lines from opposite edges both cover. The content fills the whole
            // component, whatever area the steps before it hand it.
            val translucent = Color(0, 0, 255, 128)
            assertImagesPixelPerfect(
                renderImage(STAGE_SIZE, STAGE_SIZE) { graphics ->
                    graphics.fill(Color.RED, 0, 0, STAGE_SIZE, STAGE_SIZE)
                    graphics.fill(translucent, 0, 0, STAGE_SIZE, 3)
                },
                stage.paint({
                    SwingModifier.decoration(Within(STAGE_SIZE, 3)).border(4, translucent)
                }) { graphics, _, _ ->
                    graphics.fill(Color.RED, 0, 0, STAGE_SIZE, STAGE_SIZE)
                },
            )
        }

    @Test
    fun aBorderRunsAlongTheInsideOfItsShape() =
        runComposeSwingTest {
            val stage = stage()
            // The diagonal moved a line's width across itself moves that width times the square root of 2 along an
            // axis.
            val diagonal = 10f * sqrt(2f)
            val insideOutlines =
                listOf(
                    RectangleShape to Rectangle2D.Float(10f, 10f, 44f, 44f),
                    RoundedCornerShape(16f) to RoundRectangle2D.Float(10f, 10f, 44f, 44f, 12f, 12f),
                    CircleShape to Ellipse2D.Float(10f, 10f, 44f, 44f),
                    Triangle to polygon(10f + diagonal, 10f, 54f, 10f, 54f, 54f - diagonal),
                )
            for ((shape, inside) in insideOutlines) {
                val band = Area(shape.outline(STAGE_SIZE, STAGE_SIZE)).apply { subtract(Area(inside)) }
                val expected =
                    renderImage(STAGE_SIZE, STAGE_SIZE) { graphics ->
                        graphics.fill(Color.RED, 0, 0, STAGE_SIZE, STAGE_SIZE)
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        graphics.paint = Color.BLUE
                        graphics.fill(band)
                    }

                assertImagesPixelPerfect(
                    expected,
                    stage.paint({ SwingModifier.border(10, Color.BLUE, shape) }, fill(Color.RED)),
                )
            }
        }

    @Test
    fun aClipBeforeABorderOnlyCutsTheLine() =
        runComposeSwingTest {
            val expected =
                renderImage(STAGE_SIZE, STAGE_SIZE) { graphics ->
                    graphics.clip(CircleShape.outline(STAGE_SIZE, STAGE_SIZE))
                    graphics.fill(Color.BLUE, 0, 0, STAGE_SIZE, STAGE_SIZE)
                    graphics.fill(Color.RED, 4, 4, STAGE_SIZE - 8, STAGE_SIZE - 8)
                }

            assertImagesPixelPerfect(
                expected,
                stage().paint({ SwingModifier.clip(CircleShape).border(4, Color.BLUE) }, fill(Color.RED)),
            )
        }

    @Test
    fun aBorderStrokeDeclaresTheBorderOfItsWidthAndBrush() {
        assertEquals(
            BorderStroke(2, Color.BLUE),
            BorderStroke(2, Color.BLUE),
            "Two strokes of the same width and brush are equal.",
        )
        assertEquals(
            BorderStroke(2, Color.BLUE).hashCode(),
            BorderStroke(2, Color.BLUE).hashCode(),
            "Equal strokes hash the same.",
        )
        assertNotEquals(
            BorderStroke(2, Color.BLUE),
            BorderStroke(3, Color.BLUE),
            "Strokes of different widths are not equal.",
        )
        assertNotEquals(BorderStroke(2, Color.RED), BorderStroke(2, Color.BLUE), "a different brush")
        assertEquals(
            decorated { SwingModifier.border(2, Color.BLUE, CircleShape) },
            decorated { SwingModifier.border(BorderStroke(2, Color.BLUE), CircleShape) },
            "A border declared from a stroke is the border declared from its width and brush.",
        )
    }

    @Test
    fun aBackgroundFillsItsShapeAndLeavesTheContentsAntialiasingAlone() =
        runComposeSwingTest {
            val stage = stage()
            var undecoratedAntialiasing: Any? = null
            stage.paint { graphics, _, _ ->
                undecoratedAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
            }
            for (shape in listOf(RectangleShape, RoundedCornerShape(16f), CircleShape)) {
                var contentAntialiasing: Any? = null
                val painted =
                    stage.paint({ SwingModifier.background(Color.BLUE, shape) }) { graphics, _, _ ->
                        contentAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                    }
                val expected =
                    renderImage(STAGE_SIZE, STAGE_SIZE) { graphics ->
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        graphics.paint = Color.BLUE
                        graphics.fill(shape.outline(STAGE_SIZE, STAGE_SIZE))
                    }

                assertImagesPixelPerfect(expected, painted)
                assertEquals(undecoratedAntialiasing, contentAntialiasing)
            }
        }

    @Test
    fun aBackgroundFillsAtItsAlpha() =
        runComposeSwingTest {
            val stage = stage()
            val half = stage.paint({ SwingModifier.background(Brush.of(Color.BLUE), alpha = 0.5f) }, PaintsNothing)
            val none = stage.paint({ SwingModifier.background(Brush.of(Color.BLUE), alpha = 0f) }, PaintsNothing)

            assertEquals(Color.BLUE.rgb, half.getRGB(5, 5) or 0xFF000000.toInt(), "An alpha changes the coverage.")
            assertTrue(half.opacityAt(5, 5) in 127..128, "Half the alpha covers half.")
            assertImagesPixelPerfect(stage.paint(content = PaintsNothing), none)
        }

    @Test
    fun aBackgroundAlphaOutsideTheRangeFillsAtTheNearerEnd() =
        runComposeSwingTest {
            val stage = stage()
            val full = stage.paint({ SwingModifier.background(Brush.of(Color.BLUE)) }, PaintsNothing)

            assertImagesPixelPerfect(
                full,
                stage.paint({
                    SwingModifier.background(Brush.of(Color.BLUE), alpha = 2f)
                }, PaintsNothing),
            )
            assertImagesPixelPerfect(
                stage.paint(content = PaintsNothing),
                stage.paint({ SwingModifier.background(Brush.of(Color.BLUE), alpha = -1f) }, PaintsNothing),
            )
            assertImagesPixelPerfect(
                stage.paint(content = PaintsNothing),
                stage.paint({ SwingModifier.background(Brush.of(Color.BLUE), alpha = Float.NaN) }, PaintsNothing),
            )
        }

    @Test
    fun aBlurSoftensWhatIsInsideIt() =
        runComposeSwingTest {
            val painted =
                stage().paint({ SwingModifier.blur(8) }) { graphics, width, height ->
                    graphics.fill(Color.RED, 0, 0, width / 2, height)
                }

            assertTrue(
                painted.opacityAt(STAGE_SIZE / 2 + 2, STAGE_SIZE / 2) > 0,
                "The fill spreads past the edge it had.",
            )
            assertTrue(
                painted.opacityAt(STAGE_SIZE / 2 - 2, STAGE_SIZE / 2) < 0xFF,
                "The edge it had is no longer solid, which is what spreading it means.",
            )
        }

    @Test
    fun aBlurReservesItsHaloAndSoftensAFillAtItsEdge() =
        runComposeSwingTest {
            val side = 96
            val stage = stage(side = side)
            val chain: () -> SwingModifier = { SwingModifier.blur(8).background(Brush.of(Color.RED)) }
            val painted = stage.paint(chain, PaintsNothing)
            val decorated = stage.declare(chain)
            val outsets = decorated.decoration.paintOutsets()
            val margin = outsets.left

            assertEquals(Insets(24, 24, 24, 24), outsets, "The blur reserves its halo on every side.")
            assertEquals(
                outsets,
                decorated.insets,
                "A blur takes nothing from the content: the insets hold only its paint outsets.",
            )
            assertEquals(0, painted.opacityAt(0, side / 2), "The halo fades out before the edge of the outsets.")
            assertTrue(
                painted.opacityAt(margin - 1, side / 2) in 1..0xFE,
                "The halo is partly transparent just outside the fill.",
            )
            assertTrue(painted.opacityAt(margin, side / 2) in 1..0xFE, "The fill's own edge is softened.")
            assertEquals(Color.RED.rgb, painted.getRGB(side / 2, side / 2), "The middle of the fill stays solid.")
        }

    @Test
    fun aBlurOfNoRadiusDecoratesNothing() =
        runComposeSwingTest {
            val stage = stage()
            for (radius in listOf(0, -1)) {
                val panel = stage.declare({ SwingModifier.blur(radius) })
                val decoration = panel.decoration
                assertFalse(decoration.isDecorated, "There is nothing to spread over no distance.")
                assertTrue(decoration.isOpaque(panel))
                assertEquals(
                    Insets(0, 0, 0, 0),
                    decoration.paintOutsets(),
                    "A blur of no radius reserves no paint outsets.",
                )
            }
        }

    @Test
    fun aShadowReservesWhatItsBlurNeedsAndCastsItThere() =
        runComposeSwingTest {
            val stage = stage()
            val chain: () -> SwingModifier = {
                SwingModifier
                    .shadow(
                        8,
                        Color.BLACK,
                    ).background(Brush.of(Color.RED))
            }
            val painted = stage.paint(chain, PaintsNothing)
            val panel = stage.declare(chain)
            val decoration = panel.decoration

            val reach = decoration.paintOutsets().left
            assertEquals(
                Insets(24, 24, 24, 24),
                decoration.paintOutsets(),
                "An unoffset shadow reserves the whole reach of its blur on every side.",
            )
            assertEquals(
                Color.RED.rgb,
                painted.getRGB(STAGE_SIZE / 2, STAGE_SIZE / 2),
                "What casts the shadow paints over it.",
            )
            assertTrue(painted.opacityAt(reach - 1, STAGE_SIZE / 2) > 0, "The shadow is cast into what it reserved.")
            assertFalse(decoration.isOpaque(panel), "A shadow leaves the area around what casts it partly covered.")
        }

    @Test
    fun aShadowTakesTheSilhouetteOfWhatCastsIt() =
        runComposeSwingTest {
            val stage = stage()
            val chain: () -> SwingModifier = {
                SwingModifier.shadow(8, Color.BLACK).clip(CircleShape).background(Brush.of(Color.RED))
            }
            val painted = stage.paint(chain, PaintsNothing)

            // As far outside the content as the radius less a pixel.
            val outside =
                stage
                    .declare(chain)
                    .decoration
                    .paintOutsets()
                    .top - 8 + 1
            assertTrue(
                painted.opacityAt(STAGE_SIZE / 2, outside) > painted.opacityAt(outside, outside),
                "The shadow follows the circle the content was cut to: more of it above the circle's top " +
                    "than beyond its corner, which a rectangular silhouette would have covered equally.",
            )
        }

    @Test
    fun aShadowBlursOutwardFromItsSilhouette() =
        runComposeSwingTest {
            val stage = stage()
            val chain: () -> SwingModifier = {
                SwingModifier
                    .shadow(8, Color.BLACK)
                    .background(Brush.of(Color.RED))
            }
            val margin =
                stage
                    .declare(chain)
                    .decoration
                    .paintOutsets()
                    .left
            val painted = stage.paint(chain, PaintsNothing)

            assertTrue(
                painted.opacityAt(margin / 2, STAGE_SIZE / 2) > painted.opacityAt(0, STAGE_SIZE / 2),
                "The blur fades outward, so what it reserved is more covered nearer what casts it.",
            )
        }

    @Test
    fun aShadowsOffsetMovesWhatItReserves() =
        runComposeSwingTest {
            val decoration =
                stage()
                    .declare(
                        { SwingModifier.shadow(8, Color.BLACK, offsetX = 16, offsetY = 8) },
                    ).decoration

            assertEquals(
                Insets(16, 8, 32, 40),
                decoration.paintOutsets(),
                "A shadow pushed down and right reaches that much less above and left of what casts it, and that " +
                    "much more below and right.",
            )
        }

    @Test
    fun aClipOrShadowLeftNoAreaPaintsNothing() =
        runComposeSwingTest {
            val stage = stage()
            val empty = stage.paint(content = PaintsNothing)
            for (squeezed in listOf(Within(STAGE_SIZE, 0), Within(0, STAGE_SIZE))) {
                val chains =
                    listOf<() -> SwingModifier>(
                        { SwingModifier.decoration(squeezed).clip(CircleShape, antialias = true) },
                        { SwingModifier.decoration(squeezed).shadow(1, Color.BLACK) },
                    )
                for (chain in chains) assertImagesPixelPerfect(empty, stage.paint(chain, fill(Color.RED)))
            }
        }

    @Test
    fun anAntialiasedClipAndAShadowPaintTheContentAtTheDestinationsResolution() =
        runComposeSwingTest {
            val stage = stage()
            // Stripes half a unit wide, which only a raster at twice the resolution holds.
            val stripes: (Graphics2D, Int, Int) -> Unit = { graphics, width, height ->
                graphics.fill(Color.WHITE, 0, 0, width, height)
                graphics.paint = Color.RED
                for (x in 0 until width) graphics.fill(Rectangle2D.Float(x.toFloat(), 0f, 0.5f, height.toFloat()))
            }
            val shadowOutsets =
                stage.declare({ SwingModifier.shadow(4, Color(0, 0, 0, 0)) }).decoration.paintOutsets()

            assertImagesPixelPerfect(
                stage.print(content = stripes, scale = 2),
                stage.print({ SwingModifier.clip(RectangleShape, antialias = true) }, stripes, scale = 2),
            )
            assertImagesPixelPerfect(
                stage.print({ SwingModifier.decoration(Spill(shadowOutsets)) }, stripes, scale = 2),
                stage.print({ SwingModifier.shadow(4, Color(0, 0, 0, 0)) }, stripes, scale = 2),
            )
        }

    /**
     * A blur and a shadow of the same radius reserve the same outsets, so the one taking the other's place writes
     * insets that already stand. Each holds them all the same, and the modifier restore check finds the insets owed
     * by neither departing declaration.
     */
    @Test
    fun aBlurAndAShadowTakingEachOthersPlaceHoldThePaintOutsets() =
        runComposeSwingTest {
            val stage = stage()
            val blurred = stage.declare({ SwingModifier.blur(8) }).decoration.paintOutsets()
            val shadowed = stage.declare({ SwingModifier.shadow(8, Color.BLACK) }).decoration.paintOutsets()
            stage.declare({ SwingModifier.blur(8) })

            assertEquals(blurred, shadowed, "Both reserve what a blur of the same radius reaches.")
        }

    /**
     * A decoration node of one's own reserving the outsets a blur did writes insets that already stand. Its element
     * holds them, as a node reserving paint outsets has to, and the modifier restore check finds the insets owed by
     * neither.
     */
    @Test
    fun aDecorationNodeOfOnesOwnTakingABlursPlaceHoldsThePaintOutsets() =
        runComposeSwingTest {
            val stage = stage()
            val blurred = stage.declare({ SwingModifier.blur(8) }).decoration.paintOutsets()
            val reserving = ReservingElement(blurred)
            val reserved = stage.declare({ SwingModifier.decoration(reserving) }).decoration.paintOutsets()

            assertEquals(blurred, reserved, "The node reserves the outsets of the blur it replaced.")
        }

    @Test
    fun aShadowIsCastInItsDeclaredColor() =
        runComposeSwingTest {
            val stage = stage()
            val painted = stage.paint({ SwingModifier.shadow(8, Color.BLUE) }, fill(Color.RED))
            val outsets = stage.declare({ SwingModifier.shadow(8, Color.BLUE) }).decoration.paintOutsets()
            val cast = Color(painted.getRGB(outsets.left - 1, STAGE_SIZE / 2), true)

            assertEquals(0, cast.red, "The shadow takes no red from the content: $cast")
            assertEquals(0, cast.green, "The shadow takes no green: $cast")
            assertTrue(cast.blue > 0, "The shadow is tinted the color it declares: $cast")
        }

    @Test
    fun insetsGrowsByTheDecorationsPaintOutsetsButLeavesABaseWithNoneAlone() =
        runComposeSwingTest {
            val stage = stage()

            val plain = stage.declare({ SwingModifier.background(Brush.of(Color.BLUE)) })
            assertEquals(
                Insets(0, 0, 0, 0),
                plain.decoration.insets(Insets(0, 0, 0, 0)),
                "It paints past nothing.",
            )

            val outsets = ReservingElement(Insets(STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS))
            val reserving = stage.declare({ SwingModifier.decoration(outsets) })
            assertEquals(
                Insets(STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS),
                reserving.decoration.insets(Insets(0, 0, 0, 0)),
                "insets(base) must grow by the paint outsets a decorator reserves.",
            )
        }

    /** Hands everything inside it an area of [width] by [height], as a step painting at a smaller box does. */
    private data class Within(
        private val width: Int,
        private val height: Int,
    ) : Decorator {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ): Unit = content(graphics, this.width, this.height)
    }

    /** Whether any pixel is neither fully painted nor fully bare, which is what an antialiased edge leaves. */
    private fun BufferedImage.hasPartialCoverage(): Boolean =
        (0 until height).any { y -> (0 until width).any { x -> opacityAt(x, y) in 1..0xFE } }

    private companion object {
        /** The upper-right half of the area, an outline that is neither a rectangle nor a rounded one. */
        val Triangle =
            Shape {
                width,
                height,
                ->
                polygon(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat())
            }

        /** The closed outline through the points at [xy], given as x and y in turn. */
        fun polygon(vararg xy: Float): AwtShape =
            Path2D.Float().apply {
                moveTo(xy[0], xy[1])
                for (index in 2 until xy.size step 2) lineTo(xy[index], xy[index + 1])
                closePath()
            }
    }
}
