package org.jetbrains.compose.swing.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [WindowPosition] is a value: the library writes a window's own placement back into the driving state as
 * an [WindowPosition.Absolute], and a caller tells one placement from another by comparing positions - so
 * two positions naming the same coordinates are the same position, and positions naming different ones are
 * not. A placement request carries no coordinates and stands only for itself.
 */
class WindowPositionTest {
    @Test
    fun positionsNamingTheSameCoordinatesAreEqual() {
        val position = WindowPosition.Absolute(x = 140, y = 90)
        val same = WindowPosition.Absolute(x = 140, y = 90)
        val movedRight = WindowPosition.Absolute(x = 141, y = 90)
        val movedDown = WindowPosition.Absolute(x = 140, y = 91)
        val transposed = WindowPosition.Absolute(x = 90, y = 140)

        assertEquals(same, position, "the same coordinates are the same position")
        assertEquals(position, position, "a position is itself")
        assertTrue(position != movedRight, "another horizontal coordinate is another position")
        assertTrue(position != movedDown, "another vertical coordinate is another position")
        assertTrue(position != transposed, "the coordinates are not interchangeable")
        assertTrue(position != Any(), "only a position is a position")
    }

    @Test
    fun positionsNamingDifferentCoordinatesHashApart() {
        val position = WindowPosition.Absolute(x = 140, y = 90)

        assertEquals(
            WindowPosition.Absolute(x = 140, y = 90).hashCode(),
            position.hashCode(),
            "equal positions must hash alike",
        )
        // Positions differing in either coordinate hash apart. That is more than the hashCode contract asks
        // - a constant would satisfy it - and it is what makes the equality above observable in a hash-based
        // collection: both coordinates reach the hash, and neither displaces the other.
        assertTrue(
            position.hashCode() != WindowPosition.Absolute(x = 141, y = 90).hashCode(),
            "another horizontal coordinate should hash apart, but both hashed ${position.hashCode()}",
        )
        assertTrue(
            position.hashCode() != WindowPosition.Absolute(x = 140, y = 91).hashCode(),
            "another vertical coordinate should hash apart, but both hashed ${position.hashCode()}",
        )
        assertTrue(
            position.hashCode() != WindowPosition.Absolute(x = 90, y = 140).hashCode(),
            "transposed coordinates should hash apart, but both hashed ${position.hashCode()}",
        )
    }

    @Test
    fun aPlacementRequestIsNotAPosition() {
        val requests =
            listOf(
                WindowPosition.PlatformDefault,
                WindowPosition.CenteredOnScreen,
                WindowPosition.CenteredOnOwner,
            )

        for (request in requests) {
            assertTrue(
                request != WindowPosition.Absolute(x = 0, y = 0),
                "$request asks for a placement and names no coordinates, so it is not a position",
            )
        }
        assertEquals(
            requests.size,
            requests.distinct().size,
            "each placement request stands only for itself, but two of $requests compared equal",
        )
    }

    /**
     * A position describes itself by the coordinates it names, and a placement request by the placement it
     * asks for, so a failed assertion over a position says which placement it was looking at.
     */
    @Test
    fun aPositionDescribesItself() {
        assertEquals("Absolute(x=140, y=90)", WindowPosition.Absolute(x = 140, y = 90).toString())
        assertEquals("PlatformDefault", WindowPosition.PlatformDefault.toString())
        assertEquals("CenteredOnScreen", WindowPosition.CenteredOnScreen.toString())
        assertEquals("CenteredOnOwner", WindowPosition.CenteredOnOwner.toString())
    }
}
