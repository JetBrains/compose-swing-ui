package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [Brush]: the paint each gradient resolves to at the size it is asked for,
 * what it falls back to when there is no span to run along, the equality that decides whether a
 * rebuilt modifier chain declares the same fill, and where stops out of order or off the gradient sit.
 */
class BrushTest {
    @Test
    fun horizontalGradientSpansTheWidth() {
        val paint = Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE).paint(100, 20)

        val gradient = assertIs<LinearGradientPaint>(paint, "a horizontal gradient should resolve to a linear paint")
        assertEquals(Point2D.Double(0.0, 0.0), gradient.startPoint, "it should start at the component's left edge")
        assertEquals(Point2D.Double(100.0, 0.0), gradient.endPoint, "it should end at the component's right edge")
    }

    @Test
    fun verticalGradientSpansTheHeight() {
        val paint = Brush.verticalGradient(0f to Color.RED, 1f to Color.BLUE).paint(100, 20)

        val gradient = assertIs<LinearGradientPaint>(paint, "a vertical gradient should resolve to a linear paint")
        assertEquals(Point2D.Double(0.0, 0.0), gradient.startPoint, "it should start at the component's top edge")
        assertEquals(Point2D.Double(0.0, 20.0), gradient.endPoint, "it should end at the component's bottom edge")
    }

    @Test
    fun linearGradientKeepsItsOwnPointsAtAnySize() {
        val brush =
            Brush.linearGradient(
                0f to Color.RED,
                1f to Color.BLUE,
                start = Point2D.Double(4.0, 8.0),
                end = Point2D.Double(24.0, 48.0),
            )

        val small = assertIs<LinearGradientPaint>(brush.paint(30, 60), "a linear gradient should resolve to a paint")
        val large = assertIs<LinearGradientPaint>(brush.paint(600, 900), "a linear gradient should resolve to a paint")
        assertEquals(Point2D.Double(4.0, 8.0), small.startPoint, "it should start where it was declared")
        assertEquals(Point2D.Double(24.0, 48.0), small.endPoint, "it should end where it was declared")
        assertEquals(small.startPoint, large.startPoint, "a larger component should not move the declared start")
        assertEquals(small.endPoint, large.endPoint, "a larger component should not move the declared end")
    }

    @Test
    fun radialGradientIsCenteredAndReachesTheNearerEdge() {
        val paint = Brush.radialGradient(0f to Color.RED, 1f to Color.BLUE).paint(100, 40)

        val gradient = assertIs<RadialGradientPaint>(paint, "a radial gradient should resolve to a radial paint")
        assertEquals(Point2D.Double(50.0, 20.0), gradient.centerPoint, "it should spread from the component's center")
        assertEquals(20f, gradient.radius, "its radius should reach the nearer of the two edges")
    }

    @Test
    fun aGradientFollowsEachSizeItIsAskedFor() {
        val horizontal = Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE)
        val radial = Brush.radialGradient(0f to Color.RED, 1f to Color.BLUE)

        horizontal.paint(100, 20)
        radial.paint(100, 40)
        val wider = assertIs<LinearGradientPaint>(horizontal.paint(200, 20))
        val taller = assertIs<RadialGradientPaint>(radial.paint(100, 80))
        val back = assertIs<LinearGradientPaint>(horizontal.paint(100, 20))

        assertEquals(Point2D.Double(200.0, 0.0), wider.endPoint, "a new width resolves a new span")
        assertEquals(Point2D.Double(50.0, 40.0), taller.centerPoint, "a new size resolves a new center")
        assertEquals(Point2D.Double(100.0, 0.0), back.endPoint, "an earlier size resolves its own span again")
    }

    @Test
    fun gradientsCarryTheirStopsAndClampPastTheEnds() {
        val stops = arrayOf(0f to Color.RED, 0.25f to Color.GREEN, 1f to Color.BLUE)
        val gradients =
            listOf(
                Brush.horizontalGradient(*stops),
                Brush.linearGradient(*stops, start = Point2D.Double(4.0, 8.0), end = Point2D.Double(24.0, 48.0)),
                Brush.radialGradient(*stops),
            ).map { assertIs<MultipleGradientPaint>(it.paint(100, 20)) }

        for (gradient in gradients) {
            assertContentEquals(floatArrayOf(0f, 0.25f, 1f), gradient.fractions, "the stops keep their fractions")
            assertContentEquals(arrayOf(Color.RED, Color.GREEN, Color.BLUE), gradient.colors, "and their colors")
            assertEquals(
                MultipleGradientPaint.CycleMethod.NO_CYCLE,
                gradient.cycleMethod,
                "past either end a gradient keeps the color of its last stop there",
            )
        }
    }

    @Test
    fun aCollapsedSpanResolvesToTheLastStopColor() {
        val reason =
            "Java2D refuses a gradient that runs between two equal points, so a component with no span " +
                "along the gradient's axis has to be handed the last stop's color, as Skia resolves its own " +
                "degenerate gradients, rather than a throw out of the middle of its own paint"

        assertEquals(
            Color.BLUE,
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE).paint(0, 20),
            "a horizontal gradient across no width: $reason",
        )
        assertEquals(
            Color.BLUE,
            Brush.verticalGradient(0f to Color.RED, 1f to Color.BLUE).paint(20, 0),
            "a vertical gradient down no height: $reason",
        )
        assertEquals(
            Color.BLUE,
            Brush.radialGradient(0f to Color.RED, 1f to Color.BLUE).paint(0, 0),
            "a radial gradient with no radius: $reason",
        )
    }

    @Test
    fun aSingleColorStopResolvesToThatSolidAtAnySize() {
        assertEquals(
            Color.GREEN,
            Brush.horizontalGradient(0f to Color.GREEN).paint(100, 20),
            "one stop has nothing to run to, so it should paint as a solid",
        )
        assertEquals(
            Color.GREEN,
            Brush.radialGradient(0f to Color.GREEN).paint(400, 400),
            "one stop should stay a solid however large the component is",
        )
    }

    @Test
    fun gradientsWithTheSameStopsAreEqualSoAnUnchangedChainDoesNotRepaint() {
        assertEquals(
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE),
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE),
            "a gradient rebuilt from the same stops should compare equal, or every recomposition repaints",
        )
        assertEquals(
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE).hashCode(),
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE).hashCode(),
            "equal brushes should share a hash code",
        )
        assertEquals(
            Brush.radialGradient(0f to Color.RED, 1f to Color.BLUE),
            Brush.radialGradient(0f to Color.RED, 1f to Color.BLUE),
            "a radial gradient rebuilt from the same stops should compare equal too",
        )
    }

    @Test
    fun gradientsWithDifferentStopsAreNotEqual() {
        assertNotEquals(
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE),
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.GREEN),
            "a changed color should compare unequal so the new fill reaches the component",
        )
        assertNotEquals(
            Brush.horizontalGradient(0f to Color.RED, 1f to Color.BLUE),
            Brush.horizontalGradient(0f to Color.RED, 0.5f to Color.BLUE),
            "a moved stop should compare unequal so the new fill reaches the component",
        )
    }

    @Test
    fun brushOfIsEqualOnlyToOneWrappingTheSamePaint() {
        val color = Color(10, 20, 30)

        assertEquals(Brush.of(color), Brush.of(color), "wrapping one paint twice should give equal declarations")
        assertEquals(Brush.of(color).hashCode(), Brush.of(color).hashCode(), "equal brushes should share a hash code")
        assertNotEquals(
            Brush.of(color),
            Brush.of(Color(10, 20, 30)),
            "two distinct paints should compare unequal even when the paints they wrap are equal",
        )
        assertNotEquals<Brush>(
            Brush.of(color),
            Brush.horizontalGradient(0f to color, 1f to color),
            "swapping a solid for a gradient repaints",
        )
    }

    @Test
    fun aGradientAskedAgainAtTheSameSizeHandsBackTheSamePaint() {
        val axis = Brush.verticalGradient(0f to Color.RED, 1f to Color.BLUE)
        val radial = Brush.radialGradient(0f to Color.RED, 1f to Color.BLUE)

        assertSame(axis.paint(40, 20), axis.paint(40, 20), "repainting at a steady size builds no new paint")
        assertSame(radial.paint(40, 20), radial.paint(40, 20), "repainting at a steady size builds no new paint")
        assertNotSame(axis.paint(40, 20), axis.paint(40, 30), "a new size builds the paint that fits it")
    }

    @Test
    fun gradientFactoriesRejectHavingNoStopsAtAll() {
        assertFailsWith<IllegalArgumentException>("a horizontal gradient needs at least one color stop") {
            Brush.horizontalGradient()
        }
        assertFailsWith<IllegalArgumentException>("a vertical gradient needs at least one color stop") {
            Brush.verticalGradient()
        }
        assertFailsWith<IllegalArgumentException>("a linear gradient needs at least one color stop") {
            Brush.linearGradient(start = Point2D.Double(0.0, 0.0), end = Point2D.Double(10.0, 0.0))
        }
        assertFailsWith<IllegalArgumentException>("a radial gradient needs at least one color stop") {
            Brush.radialGradient()
        }
    }

    @Test
    fun aStopOffTheGradientSitsAtTheNearerEnd() {
        val gradient =
            assertIs<LinearGradientPaint>(
                Brush.horizontalGradient(-0.5f to Color.RED, 0.5f to Color.GREEN, 1.5f to Color.BLUE).paint(100, 20),
            )

        assertContentEquals(floatArrayOf(0f, 0.5f, 1f), gradient.fractions, "each stop is held within 0f..1f")
    }

    @Test
    fun aStopBehindTheOneBeforeItSitsOnItAsAHardEdge() {
        val painted =
            paintAcross(
                Brush.horizontalGradient(
                    0f to Color.RED,
                    0.5f to Color.RED,
                    0.25f to Color.BLUE,
                    1f to Color.BLUE,
                ),
            )

        assertEquals(Color.RED.rgb, painted.getRGB(45, 0), "before the edge the first color holds")
        assertEquals(Color.BLUE.rgb, painted.getRGB(55, 0), "past the edge the stop moved onto it takes over")
    }

    @Test
    fun equalStopsAtEitherEndOfTheGradientPaint() {
        val atStart =
            paintAcross(
                Brush.horizontalGradient(
                    0f to Color.RED,
                    0f to Color.GREEN,
                    0.5f to Color.GREEN,
                    1f to Color.BLUE,
                ),
            )
        val atEnd = paintAcross(Brush.radialGradient(0f to Color.RED, 1f to Color.GREEN, 1f to Color.BLUE))
        val backwards = paintAcross(Brush.verticalGradient(1f to Color.RED, 0f to Color.BLUE))

        // Java2D samples a pixel at its left edge, where the first pixel sits on the edge between the two stops.
        assertEquals(Color.GREEN.rgb, atStart.getRGB(10, 0), "the later of two stops at 0f starts the gradient")
        // Java2D's last lookup entry stops one step short of the last stop's color.
        val corner = Color(atEnd.getRGB(0, 0))
        assertTrue(
            corner.red == 0 && corner.green <= 1 && corner.blue >= 0xFE,
            "past the last of two stops at 1f its color holds, but the corner was $corner",
        )
        assertEquals(
            Color.RED.rgb,
            backwards.getRGB(0, 0),
            "a stop at 0f after one at 1f sits on it, so the first color holds across the whole gradient",
        )
    }

    /** What [brush] paints across a [WIDTH] x [WIDTH] square. */
    private fun paintAcross(brush: Brush): BufferedImage {
        val image = BufferedImage(WIDTH, WIDTH, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.paint = brush.paint(WIDTH, WIDTH)
        graphics.fillRect(0, 0, WIDTH, WIDTH)
        graphics.dispose()
        return image
    }

    private companion object {
        const val WIDTH = 100
    }
}
