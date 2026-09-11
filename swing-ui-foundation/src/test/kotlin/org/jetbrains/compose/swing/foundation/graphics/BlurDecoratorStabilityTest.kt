package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Rendering proof that wide shadows keep their requested radius and alignment as the blur reduces its recording. */
class BlurDecoratorStabilityTest {
    @Test
    fun adjacentWideShadowRadiiRemainDistinctAndAligned() =
        runComposeSwingTest {
            val radii = (16..24).toList()
            // Each radius offset by (5, 3), then unmoved.
            val canvases =
                decoratedCanvases(SIZE, SIZE, radii.size * 2, decoration = {
                    val moved = it < radii.size
                    val offset = if (moved) 5 to 3 else 0 to 0
                    SwingModifier.shadow(radii[it % radii.size], Color.BLACK, offset.first, offset.second)
                }) { paintFigure(graphics, width, height) }
            val shadows = canvases.take(radii.size).map { it.paintAtLayoutOrigin() }
            val unmovedShadows = canvases.drop(radii.size).map { it.paintAtLayoutOrigin() }

            for ((before, after) in shadows.zipWithNext()) {
                assertNotNull(differingPixelBounds(before, after), "Adjacent shadow radii render different pixels.")
            }
            for (index in radii.indices) {
                val unmoved = unmovedShadows[index].centroidOutside(heldAround(0, 0))
                val moved = shadows[index].centroidOutside(heldAround(5, 3))
                assertTrue(
                    abs(moved.first - unmoved.first - 5) < 0.5 && abs(moved.second - unmoved.second - 3) < 0.5,
                    "Shadow radius ${radii[index]} moved by (${moved.first - unmoved.first}, " +
                        "${moved.second - unmoved.second}) rather than by its offset (5, 3).",
                )
            }
        }

    @Test
    fun adjacentWideBlurRadiiRemainVisuallyDistinct() =
        runComposeSwingTest {
            val blurs =
                decoratedCanvases(SIZE, SIZE, 9, decoration = { SwingModifier.blur(16 + it) }) {
                    paintFigure(graphics, width, height)
                }.map { it.paintAtLayoutOrigin() }

            for ((before, after) in blurs.zipWithNext()) {
                assertNotNull(differingPixelBounds(before, after), "Adjacent blur radii render different pixels.")
            }
        }

    @Test
    fun aBlurSpreadsAThinLineEvenlyWhereverItFalls() =
        runComposeSwingTest {
            // A radius of 8 records at a quarter scale, so the four positions fall differently on its pixels.
            val totals =
                decoratedCanvases(SIZE, SIZE, 4, decoration = { SwingModifier.blur(8) }) { position ->
                    graphics.color = Color.BLACK
                    graphics.fillRect(SIZE / 2 + position, 0, 1, height)
                }.map { canvas ->
                    val painted = canvas.paintAtLayoutOrigin()
                    (0 until painted.width).sumOf { x -> painted.getRGB(x, SIZE / 2) ushr 24 }
                }

            // ConvolveOp rounds each pass's output down, so a radius of 8 loses up to 15 of 0xFF of a
            // thin line's coverage.
            assertTrue(
                totals.max() - totals.min() <= 2 && totals.all { abs(it - 0xFF) <= 0xFF / 17 },
                "A blurred line keeps its coverage wherever it falls: $totals.",
            )
        }

    @Test
    fun onlyABlurThatReducesItsRecordingAntialiasesTheContent() =
        runComposeSwingTest {
            val blurs = listOf(1 to false, 2 to false, 8 to true)
            val hints = arrayOfNulls<Any>(blurs.size)
            val canvases =
                decoratedCanvases(
                    SIZE,
                    SIZE,
                    blurs.size,
                    decoration = { SwingModifier.blur(blurs[it].first) },
                ) { hints[it] = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING) }

            for ((index, canvas) in canvases.withIndex()) {
                canvas.paintAtLayoutOrigin()
                val (radius, reduced) = blurs[index]
                val hint = hints[index]
                val antialiased = hint == RenderingHints.VALUE_ANTIALIAS_ON
                assertEquals(reduced, antialiased, "A blur of $radius antialiases its content: $hint.")
            }
        }

    @Test
    fun aShadowKeepsAThinLinesCoverageWhereverItFalls() =
        runComposeSwingTest {
            val canvases =
                decoratedCanvases(SIZE, SIZE, 8, decoration = {
                    SwingModifier.shadow(8, Color.BLACK, offsetX = 24, offsetY = 0)
                }) { position ->
                    graphics.color = Color.BLACK
                    graphics.fillRect(SIZE / 4 + position, 0, 1, height)
                }
            // A radius of 8 blurs at a quarter scale, which is an eighth of the pixels of a screen at a scale of 2.
            for (scale in 1..2) {
                val totals =
                    canvases.map { canvas ->
                        val painted = canvas.paintAtLayoutOrigin(scale.toDouble())
                        (0 until painted.width).sumOf { x -> painted.getRGB(x, painted.height / 2) ushr 24 } / scale
                    }

                // The line and its shadow, a line's coverage each.
                assertTrue(
                    totals.all { abs(it - 0xFF * 2) <= 0xFF / 12 },
                    "At a scale of $scale, a line and its shadow keep their coverage wherever it falls: $totals.",
                )
            }
        }

    /** What this canvas paints at [scale] onto a [SIZE] by [SIZE] image, scaled alike, from its layout origin. */
    private fun JComponent.paintAtLayoutOrigin(scale: Double = 1.0): BufferedImage {
        val side = (SIZE * scale).toInt()
        return paintOnto(side, side, AffineTransform.getScaleInstance(scale, scale))
    }

    private fun paintFigure(
        graphics: Graphics2D,
        width: Int,
        height: Int,
    ) {
        graphics.color = Color.WHITE
        graphics.fillRect(width / 2 - FIGURE / 2, height / 2 - FIGURE / 2, FIGURE, FIGURE)
    }

    /**
     * A square centered where a shadow offset by ([offsetX], [offsetY]) is centered, wide enough to hold the figure at
     * offsets up to (5, 5). What lies outside it is the shadow's alone, and symmetric about its center.
     */
    private fun heldAround(
        offsetX: Int,
        offsetY: Int,
    ): Rectangle {
        val reach = FIGURE / 2 + 5
        return Rectangle(SIZE / 2 + offsetX - reach, SIZE / 2 + offsetY - reach, reach * 2, reach * 2)
    }

    /** The center of the coverage outside [held]. */
    private fun BufferedImage.centroidOutside(held: Rectangle): Pair<Double, Double> {
        var mass = 0L
        var xMass = 0L
        var yMass = 0L
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (held.contains(x, y)) continue
                val alpha = getRGB(x, y) ushr 24
                mass += alpha
                xMass += alpha * x
                yMass += alpha * y
            }
        }
        return xMass.toDouble() / mass to yMass.toDouble() / mass
    }

    private companion object {
        const val SIZE = 128
        const val FIGURE = 8
    }
}
