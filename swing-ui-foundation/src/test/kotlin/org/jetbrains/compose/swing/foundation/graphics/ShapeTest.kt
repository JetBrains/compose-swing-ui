package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [Shape]: the outline each value resolves to at the size it is asked for, and
 * the equality that decides whether a rebuilt modifier chain declares the same shape or a new one.
 */
class ShapeTest {
    @Test
    fun rectangleShapeCoversTheFullBoundsAndNothingOutside() {
        val outline = RectangleShape.outline(120, 40)

        assertTrue(
            outline.contains(Rectangle2D.Double(0.0, 0.0, 120.0, 40.0)),
            "a rectangle shape should cover every pixel of the component it decorates",
        )
        assertFalse(outline.contains(-1.0, 20.0), "a rectangle shape should not reach left of the component")
        assertFalse(outline.contains(120.0, 20.0), "a rectangle shape should not reach right of the component")
        assertFalse(outline.contains(60.0, 40.0), "a rectangle shape should not reach below the component")
    }

    @Test
    fun circleShapeOnASquareCoversTheCenterAndNotTheCorners() {
        val outline = CircleShape.outline(100, 100)

        assertTrue(outline.contains(50.0, 50.0), "a circle should cover the middle of a square component")
        assertFalse(outline.contains(2.0, 2.0), "a circle should leave the corners of a square component uncovered")
    }

    @Test
    fun roundedCornersNeverExceedHalfTheShorterSideAndRejectANegativeRadius() {
        val rounded = RoundedCornerShape(16f).outline(100, 100)
        assertTrue(rounded.contains(6.0, 6.0), "a radius of 16 keeps what lies within 16 pixels of the corner's center")
        assertFalse(rounded.contains(4.0, 4.0), "a radius of 16 cuts away what lies beyond it")
        assertTrue(
            RoundedCornerShape(50f).outline(100, 40).contains(20.0, 2.0),
            "a radius past half the height rounds each end into a half circle, not a quarter ellipse",
        )
        assertTrue(
            CircleShape.outline(200, 100).contains(50.0, 2.0),
            "a circle shape on a component that is not square is a pill, not the inscribed ellipse",
        )
        assertFalse(CircleShape.outline(200, 100).contains(2.0, 2.0), "a pill leaves the corners uncovered")
        assertFailsWith<IllegalArgumentException>("a corner can't be rounded by a negative radius") {
            RoundedCornerShape(-1f)
        }
    }

    @Test
    fun roundedCornerShapeWithoutARadiusIsTheFullRectangle() {
        val outline = RoundedCornerShape(0f).outline(100, 100)

        assertEquals(
            RectangleShape.outline(100, 100).bounds2D,
            outline.bounds2D,
            "a radius of zero should span the same bounds as a plain rectangle",
        )
        assertTrue(outline.contains(0.0, 0.0), "a radius of zero should leave the corner covered")
    }

    @Test
    fun roundedCornerShapesWithTheSameRadiusAreEqualSoAnUnchangedChainDoesNotRepaint() {
        assertEquals(
            RoundedCornerShape(8f),
            RoundedCornerShape(8f),
            "a shape rebuilt from the same radius should compare equal, or every recomposition repaints",
        )
        assertEquals(
            RoundedCornerShape(8f).hashCode(),
            RoundedCornerShape(8f).hashCode(),
            "equal shapes should share a hash code",
        )
    }

    @Test
    fun roundedCornerShapesWithDifferentRadiiAreNotEqual() {
        assertNotEquals(
            RoundedCornerShape(8f),
            RoundedCornerShape(4f),
            "a changed radius should compare unequal so the new shape reaches the component",
        )
        assertNotEquals(
            RoundedCornerShape(0f),
            RoundedCornerShape(-0f),
            "zero and negative zero should compare unequal, or a hash set could hold two equal shapes",
        )
    }

    @Test
    fun shapeOfIsEqualOnlyToOneWrappingTheSameShape() {
        val rectangle = Rectangle(0, 0, 10, 10)

        assertEquals(
            Shape.of(rectangle),
            Shape.of(rectangle),
            "wrapping one shape twice should give equal declarations",
        )
        assertEquals(
            Shape.of(rectangle).hashCode(),
            Shape.of(rectangle).hashCode(),
            "equal shapes should share a hash code",
        )
        assertNotEquals(
            Shape.of(rectangle),
            Shape.of(Rectangle(0, 0, 10, 10)),
            "two distinct outlines should compare unequal even when the shapes they wrap are equal",
        )
        assertNotEquals<Shape>(
            Shape.of(RectangleShape.outline(10, 10)),
            RectangleShape,
            "a wrapped outline is not the built-in shape it was taken from",
        )
    }

    @Test
    fun shapeOfResolvesToTheShapeItWrapsAtAnySize() {
        val rectangle = Rectangle(0, 0, 10, 10)
        val shape = Shape.of(rectangle)

        assertSame(rectangle, shape.outline(10, 10), "Shape.of should hand back the shape it wraps")
        assertSame(rectangle, shape.outline(500, 30), "Shape.of should ignore the size it is asked for")
    }
}
