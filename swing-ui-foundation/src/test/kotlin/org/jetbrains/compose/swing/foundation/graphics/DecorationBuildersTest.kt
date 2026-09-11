package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
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
 * Behavioral tests for the built-in decorations: what each one paints.
 *
 * Each chain is declared through the public modifiers on a composed component whose bounds are [SIZE] square, and
 * the content is a decoration declared after the chain. What is asserted is what the component shows.
 */
class DecorationBuildersTest {
    @Test
    fun aClipCutsEverythingInsideIt() =
        runComposeSwingTest {
            val painted = stage().paint({ SwingModifier.clip(CircleShape) }, fill(Color.RED))

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(SIZE / 2, SIZE / 2),
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
                soft.getRGB(SIZE / 2, SIZE / 2),
                "What lies well inside the outline is untouched.",
            )
            assertEquals(0, soft.getRGB(0, 0), "What lies well outside it is still cut away.")
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
                painted.getRGB(0, SIZE / 2),
                "The border is painted after the content, so an opaque fill cannot wipe it.",
            )
            assertEquals(Color.BLUE.rgb, painted.getRGB(1, SIZE / 2), "The border is as thick as it was declared.")
            assertEquals(Color.RED.rgb, painted.getRGB(2, SIZE / 2), "The border stops at its declared width.")
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
                    renderImage(SIZE, SIZE) { graphics ->
                        graphics.fill(Color.RED, 0, 0, SIZE, SIZE)
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        graphics.paint = Color.BLUE
                        graphics.fill(shape.outline(SIZE, SIZE))
                    }

                assertImagesPixelPerfect(
                    filled,
                    stage.paint({ SwingModifier.border(SIZE * 2, Color.BLUE, shape) }, content),
                )
            }

            // A translucent line shows a row that lines from opposite edges both cover. The content fills the whole
            // component, whatever area the steps before it hand it.
            val translucent = Color(0, 0, 255, 128)
            assertImagesPixelPerfect(
                renderImage(SIZE, SIZE) { graphics ->
                    graphics.fill(Color.RED, 0, 0, SIZE, SIZE)
                    graphics.fill(translucent, 0, 0, SIZE, 3)
                },
                stage.paint({ SwingModifier.decoration(Within(SIZE, 3)).border(4, translucent) }) { graphics, _, _ ->
                    graphics.fill(Color.RED, 0, 0, SIZE, SIZE)
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
                val band = Area(shape.outline(SIZE, SIZE)).apply { subtract(Area(inside)) }
                val expected =
                    renderImage(SIZE, SIZE) { graphics ->
                        graphics.fill(Color.RED, 0, 0, SIZE, SIZE)
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
                renderImage(SIZE, SIZE) { graphics ->
                    graphics.clip(CircleShape.outline(SIZE, SIZE))
                    graphics.fill(Color.BLUE, 0, 0, SIZE, SIZE)
                    graphics.fill(Color.RED, 4, 4, SIZE - 8, SIZE - 8)
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
                    renderImage(SIZE, SIZE) { graphics ->
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        graphics.paint = Color.BLUE
                        graphics.fill(shape.outline(SIZE, SIZE))
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

            assertTrue(painted.opacityAt(SIZE / 2 + 2, SIZE / 2) > 0, "The fill spreads past the edge it had.")
            assertTrue(
                painted.opacityAt(SIZE / 2 - 2, SIZE / 2) < 0xFF,
                "The edge it had is no longer solid, which is what spreading it means.",
            )
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
            }
        }

    @Test
    fun aClipOrShadowLeftNoAreaPaintsNothing() =
        runComposeSwingTest {
            val stage = stage()
            val empty = stage.paint(content = PaintsNothing)
            for (squeezed in listOf(Within(SIZE, 0), Within(0, SIZE))) {
                val chains =
                    listOf<() -> SwingModifier>(
                        { SwingModifier.decoration(squeezed).clip(CircleShape, antialias = true) },
                        { SwingModifier.decoration(squeezed).shadow(1, Color.BLACK) },
                    )
                for (chain in chains) assertImagesPixelPerfect(empty, stage.paint(chain, fill(Color.RED)))
            }
        }

    @Test
    fun aFadePaintsTheContentTranslucently() =
        runComposeSwingTest {
            val painted = stage().paint({ SwingModifier.alpha(0.5f) }, fill(Color.RED))

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(5, 5) or 0xFF000000.toInt(),
                "A fade changes the coverage, not the color.",
            )
            assertTrue(painted.opacityAt(5, 5) in 127..128, "Half opacity should cover half.")
        }

    @Test
    fun aFadeReachesEachDrawingRatherThanTheAreaAsOne() =
        runComposeSwingTest {
            val painted =
                stage().paint({ SwingModifier.alpha(0.5f) }) { graphics, _, _ ->
                    graphics.fill(Color.RED, 0, 0, 20, 20)
                    graphics.fill(Color.BLUE, 10, 10, 20, 20)
                }

            assertTrue(
                painted.opacityAt(15, 15) > painted.opacityAt(5, 5),
                "Overlapping shapes are each faded as they are drawn, so the second covers more where it " +
                    "lands on the first. Fading the area as one image would have covered both the same.",
            )
        }

    @Test
    fun nestedFadesMultiply() =
        runComposeSwingTest {
            val painted = stage().paint({ SwingModifier.alpha(0.5f).alpha(0.5f) }, fill(Color.RED))

            assertTrue(painted.opacityAt(5, 5) in 63..64, "A fade inside a fade paints at the product of the two.")
        }

    @Test
    fun aFadeKeepsTheCompositeRuleItIsPaintedUnder() =
        runComposeSwingTest {
            val painted =
                stage().print(
                    { SwingModifier.alpha(0.5f) },
                    fill(Color.RED),
                    setUp = { graphics ->
                        graphics.fill(Color.BLACK, 0, 0, SIZE, SIZE)
                        graphics.composite = AlphaComposite.DstOut
                    },
                )

            assertTrue(
                painted.opacityAt(5, 5) in 127..128,
                "A half fade under DstOut clears half of what is there, rather than painting over it.",
            )
        }

    @Test
    fun aFadeToNothingPaintsNothing() =
        runComposeSwingTest {
            val stage = stage()
            val empty = stage.paint(content = PaintsNothing)

            assertImagesPixelPerfect(empty, stage.paint({ SwingModifier.alpha(0f) }, fill(Color.RED)))
            val faded = stage.declare({ SwingModifier.alpha(0f) })
            assertFalse(
                faded.decoration.isOpaque(faded),
                "What is behind an invisible component shows through it.",
            )
        }

    @Test
    fun aFadeRaisedToFullAlphaPaintsAndCoversAsNoFadeDoes() =
        runComposeSwingTest {
            val stage = stage()
            val unfaded = stage.paint(content = fill(Color.RED))
            stage.paint({ SwingModifier.alpha(0.5f) }, fill(Color.RED))

            assertImagesPixelPerfect(unfaded, stage.paint({ SwingModifier.alpha(1f) }, fill(Color.RED)))
            val full = stage.declare({ SwingModifier.alpha(1f).background(Brush.of(Color.BLUE)) })
            assertTrue(full.decoration.isOpaque(full), "A full alpha hides what is behind as the background does.")
        }

    @Test
    fun aFadeOutsideTheRangePaintsAtTheNearerEnd() =
        runComposeSwingTest {
            val stage = stage()

            assertImagesPixelPerfect(
                stage.paint(content = fill(Color.RED)),
                stage.paint({
                    SwingModifier.alpha(1.5f)
                }, fill(Color.RED)),
            )
            assertImagesPixelPerfect(
                stage.paint(content = PaintsNothing),
                stage.paint({
                    SwingModifier.alpha(-1f)
                }, fill(Color.RED)),
            )
            assertImagesPixelPerfect(
                stage.paint(content = PaintsNothing),
                stage.paint({
                    SwingModifier.alpha(Float.NaN)
                }, fill(Color.RED)),
            )
        }

    @Test
    fun aFadeAndAClipStopCoveringTheArea() =
        runComposeSwingTest {
            val stage = stage()

            suspend fun isOpaque(chain: () -> SwingModifier): Boolean {
                val panel = stage.declare(chain)
                return panel.decoration.isOpaque(panel)
            }

            assertTrue(isOpaque { SwingModifier }, "No steps paint nothing and hide nothing.")
            assertTrue(
                isOpaque { SwingModifier.background(Brush.of(Color.BLUE)) },
                "A background fills the whole area.",
            )
            assertFalse(isOpaque { SwingModifier.alpha(0.5f) }, "What is behind a faded component shows through it.")
            assertFalse(
                isOpaque { SwingModifier.clip(CircleShape) },
                "A clip leaves the corners of the area unpainted.",
            )
            assertFalse(isOpaque { SwingModifier.blur(8) }, "A blur fades out at the edges of the area.")
            assertFalse(
                isOpaque { SwingModifier.clip(CircleShape).background(Brush.of(Color.BLUE)) },
                "One step that stops covering the area is enough for them all.",
            )
        }

    /**
     * One decorated component, composed once, that each [declare] redeclares: [side] square, the chain it is given
     * outermost, and the content painting inside the chain in the component's layout
     * coordinates. [side] defaults to [SIZE].
     */
    private class Stage(
        private val test: ComposeSwingTest,
        private val side: Int = SIZE,
    ) {
        private var declared by mutableStateOf<() -> SwingModifier>({ SwingModifier })
        private var content by mutableStateOf<Decorator?>(null)

        init {
            test.setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .testTag(STAGE_TAG)
                                .opaque(false)
                                .preferredSize(side, side)
                                .then(declared())
                                .then(content?.let { SwingModifier.decoration(it) } ?: SwingModifier),
                    )
                }
            }
        }

        /**
         * Declares [chain] around [content], or [chain] alone where [content] is null, sized so the component's
         * bounds are [side] square.
         */
        suspend fun declare(
            chain: () -> SwingModifier = { SwingModifier },
            content: Decorator? = null,
        ): DecoratedPanel {
            declared = chain
            this.content = content
            test.awaitIdle()
            return component()
        }

        /** What the component shows with [chain] declared around [content]. */
        suspend fun paint(
            chain: () -> SwingModifier = { SwingModifier },
            content: (Graphics2D, Int, Int) -> Unit,
        ): BufferedImage {
            declare(chain, Content(content))
            return test.onNodeWithTag(STAGE_TAG).captureToImage()
        }

        /**
         * What the component prints with [chain] declared around [content], at [scale] device pixels per unit onto
         * an image [setUp] prepares first.
         */
        suspend fun print(
            chain: () -> SwingModifier = { SwingModifier },
            content: (Graphics2D, Int, Int) -> Unit,
            scale: Int = 1,
            setUp: (Graphics2D) -> Unit = {},
        ): BufferedImage {
            val component = declare(chain, Content(content))
            return renderImage(SIZE * scale, SIZE * scale) { graphics ->
                setUp(graphics)
                graphics.scale(scale.toDouble(), scale.toDouble())
                component.printAll(graphics)
            }
        }

        private fun component(): DecoratedPanel = test.onNodeWithTag(STAGE_TAG).fetch<DecoratedPanel>()
    }

    private fun ComposeSwingTest.stage(side: Int = SIZE): Stage = Stage(this, side)

    /** Paints [paint] as the content, inside every decoration declared before it. */
    private data class Content(
        private val paint: (Graphics2D, Int, Int) -> Unit,
    ) : Decorator {
        override val isOpaque: Boolean get() = false

        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) = paint(graphics, width, height)
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

    /** Content filling its whole area with [color]. */
    private fun fill(color: Color): (Graphics2D, Int, Int) -> Unit =
        { graphics, width, height -> graphics.fill(color, 0, 0, width, height) }

    /** Whether any pixel is neither fully painted nor fully bare, which is what an antialiased edge leaves. */
    private fun BufferedImage.hasPartialCoverage(): Boolean =
        (0 until height).any { y -> (0 until width).any { x -> opacityAt(x, y) in 1..0xFE } }

    /** How much of the pixel at [x], [y] the painting covers, from `0` to `255`. */
    private fun BufferedImage.opacityAt(
        x: Int,
        y: Int,
    ): Int = getRGB(x, y) ushr 24

    private companion object {
        const val SIZE = 64
        const val STAGE_TAG = "decorated"

        /** Content that paints nothing. */
        val PaintsNothing: (Graphics2D, Int, Int) -> Unit = { _, _, _ -> }

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

        fun Graphics2D.fill(
            color: Color,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            paint = color
            fillRect(x, y, width, height)
        }
    }
}
