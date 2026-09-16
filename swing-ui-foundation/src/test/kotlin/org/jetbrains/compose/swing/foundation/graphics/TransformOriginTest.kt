package org.jetbrains.compose.swing.foundation.graphics

import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The transform an origin builds, equality, hashing, [TransformOrigin.copy] and destructuring. */
class TransformOriginTest {
    @Test
    fun `negative and positive zero are unequal`() {
        assertNotEquals(TransformOrigin(-0f, 0f), TransformOrigin(0f, 0f))
    }

    @Test
    fun `two NaN origins are equal with matching hash codes`() {
        val first = TransformOrigin(Float.NaN, Float.NaN)
        val second = TransformOrigin(Float.NaN, Float.NaN)

        assertEquals(first, second, "NaN pivots compare equal under TransformOrigin's own equals.")
        assertEquals(first.hashCode(), second.hashCode(), "Equal origins hash the same.")
    }

    @Test
    fun `copy changes one axis and keeps the other`() {
        val origin = TransformOrigin(0.25f, 0.75f)

        assertEquals(
            TransformOrigin(0.1f, 0.75f),
            origin.copy(pivotFractionX = 0.1f),
            "copy(pivotFractionX) leaves pivotFractionY as is.",
        )
        assertEquals(
            TransformOrigin(0.25f, 0.4f),
            origin.copy(pivotFractionY = 0.4f),
            "copy(pivotFractionY) leaves pivotFractionX as is.",
        )
    }

    @Test
    fun `destructuring yields the two fractions`() {
        val (x, y) = TransformOrigin(0.3f, 0.6f)

        assertEquals(0.3f, x, "The first destructuring component is pivotFractionX.")
        assertEquals(0.6f, y, "The second destructuring component is pivotFractionY.")
    }

    @Test
    fun `a transform left at its defaults moves nothing`() {
        val transform = TransformOrigin.Center.createTransform(Rectangle2D.Double(10.0, 20.0, 40.0, 60.0))

        assertEquals(Point2D.Double(3.0, 7.0), transform.transform(Point2D.Double(3.0, 7.0), null))
    }

    @Test
    fun `a transform scales around the origin's point in the box and keeps that point in place`() {
        val box = Rectangle2D.Double(10.0, 20.0, 40.0, 60.0)
        val transform = TransformOrigin(0.25f, 0.5f).createTransform(box, scaleX = 2f, scaleY = 3f)

        assertEquals(Point2D.Double(20.0, 50.0), transform.transform(Point2D.Double(20.0, 50.0), null), "the pivot")
        assertEquals(
            Point2D.Double(30.0, 44.0),
            transform.transform(Point2D.Double(25.0, 48.0), null),
            "a point 5 right of and 2 above the pivot ends 10 right of and 6 above it",
        )
    }

    @Test
    fun `a transform scales first and then turns clockwise on screen around the origin`() {
        val box = Rectangle2D.Double(0.0, 0.0, 20.0, 20.0)
        val transform = TransformOrigin.Center.createTransform(box, scaleX = 2f, scaleY = 1f, rotationZ = 90f)

        val turned = transform.transform(Point2D.Double(15.0, 10.0), null)

        assertEquals(10.0, turned.x, 1e-9, "a point 5 right of the center, scaled to 10, turns to below it")
        assertEquals(20.0, turned.y, 1e-9, "y grows downward on screen, so a clockwise quarter turn points down")
    }

    @Test
    fun `an origin equals only another origin with the same fractions`() {
        val origin = TransformOrigin(0.25f, 0.75f)

        assertNotEquals<Any>(origin, "TransformOrigin")
        assertNotEquals(
            TransformOrigin(0.25f, 0.5f),
            TransformOrigin(0.25f, 0.75f),
            "a changed Y fraction should compare unequal",
        )
    }
}
