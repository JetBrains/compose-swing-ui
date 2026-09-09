package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.animation.core.AnimationVector4D
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What the color converter carries: a color survives the trip into a vector and back, a ramp is
 * interpolated perceptually rather than component by component, and an overshoot clamps.
 */
class ColorConverterTest {
    @Test
    fun `a color survives the trip into a vector and back`() {
        val colors =
            listOf(
                Color.BLACK,
                Color.WHITE,
                Color.RED,
                Color(0, 128, 0),
                Color(20, 120, 200, 90),
                Color(255, 255, 255, 0),
            )
        for (color in colors) {
            assertEquals(color, roundTrip(color), "$color did not survive the round trip")
        }
    }

    @Test
    fun `every component of the sRGB cube survives the trip into a vector and back`() {
        val levels = (0..255 step 17).toList()
        val lost =
            levels.flatMap { r ->
                levels.flatMap { g ->
                    levels.map { b -> Color(r, g, b) }.filter { it != roundTrip(it) }
                }
            }
        assertEquals(emptyList(), lost, "colors that did not survive the round trip")
    }

    @Test
    fun `the midpoint between black and white is not the midpoint of their components`() {
        val midpoint = midpointOf(Color.BLACK, Color.WHITE)
        assertEquals(midpoint.red, midpoint.green, "a gray ramp stayed gray")
        assertEquals(midpoint.red, midpoint.blue, "a gray ramp stayed gray")
        assertNotEquals(127, midpoint.red, "the ramp was interpolated in sRGB, not in Oklab")
        assertNotEquals(128, midpoint.red, "the ramp was interpolated in sRGB, not in Oklab")
        // Half the perceived distance from black to white is component 99: Oklab lightness 0.5 stands
        // for an eighth of the light of white, which the sRGB transfer function encodes as 0.389.
        assertEquals(99, midpoint.red, "half the perceived distance from black to white")
    }

    @Test
    fun `the midpoint between red and blue is lighter and greener than mixing the two`() {
        val midpoint = midpointOf(Color.RED, Color.BLUE)
        // Mixing the components would give (127, 0, 127). The perceptual path between red and blue bows
        // away from that line: it passes through a lighter purple that stands for some green light.
        assertTrue(
            midpoint.green >= 60,
            "the ramp ran down the straight line the sRGB components draw, at green ${midpoint.green}",
        )
        assertTrue(
            midpoint.red > 127 && midpoint.blue > 127,
            "the midpoint was no lighter than mixing the components: $midpoint",
        )
    }

    @Test
    fun `a component clamps rather than wraps when an animation overshoots`() {
        assertEquals(Color.WHITE, fromVector(1f, 1.5f, 0f, 0f), "past white")
        assertEquals(Color.BLACK, fromVector(1f, -1.5f, 0f, 0f), "past black")
        assertEquals(
            fromVector(1f, 0.5f, 0.5f, 0.5f),
            fromVector(1f, 0.5f, 3f, 3f),
            "past the chroma the space describes",
        )
        assertEquals(
            fromVector(1f, 0.5f, -0.5f, -0.5f),
            fromVector(1f, 0.5f, -3f, -3f),
            "past the chroma the space describes, the other way",
        )
        assertEquals(255, fromVector(1.4f, 0.5f, 0f, 0f).alpha, "past opaque")
        assertEquals(0, fromVector(-1.4f, 0.5f, 0f, 0f).alpha, "past transparent")
    }

    @Test
    fun `alpha is animated alongside the color it belongs to`() {
        val transparent = Color(255, 0, 0, 0)
        val opaque = Color(255, 0, 0, 255)
        assertEquals(0, roundTrip(transparent).alpha, "a transparent color came back opaque")
        assertEquals(128, midpointOf(transparent, opaque).alpha, "alpha was not interpolated")
        assertEquals(
            Color.RED,
            Color(midpointOf(transparent, opaque).rgb, false),
            "interpolating alpha moved the color it belongs to",
        )
    }

    private fun roundTrip(color: Color): Color = ColorToVector.convertFromVector(ColorToVector.convertToVector(color))

    private fun midpointOf(
        from: Color,
        to: Color,
    ): Color {
        val a = ColorToVector.convertToVector(from)
        val b = ColorToVector.convertToVector(to)
        return fromVector(
            (a.v1 + b.v1) / 2f,
            (a.v2 + b.v2) / 2f,
            (a.v3 + b.v3) / 2f,
            (a.v4 + b.v4) / 2f,
        )
    }

    private fun fromVector(
        alpha: Float,
        lightness: Float,
        chromaA: Float,
        chromaB: Float,
    ): Color = ColorToVector.convertFromVector(AnimationVector4D(alpha, lightness, chromaA, chromaB))
}
