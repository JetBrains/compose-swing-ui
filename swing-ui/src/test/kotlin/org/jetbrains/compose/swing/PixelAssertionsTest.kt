package org.jetbrains.compose.swing

import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.image.BufferedImage
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class PixelAssertionsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun pixelAssertions(assertion: () -> Unit) = assertion()

    companion object {
        @JvmStatic
        fun cases(): List<Named<() -> Unit>> = (matchingCases() + differingCases() + invalidCases()).map { case ->
            Named.of(case.name) {
                val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB)
                for ((position, color) in case.pixels) {
                    image.setRGB(position.x, position.y, color.rgb)
                }
                val pixels = image.getRGB(0, 0, 3, 2, null, 0, 3)
                case.assertion(image)
                assertContentEquals(pixels, image.getRGB(0, 0, 3, 2, null, 0, 3))
            }
        }

        private fun matchingCases(): List<Case> = buildList {
            for (y in 0..1) {
                for (x in 0..2) {
                    val position = Point(x, y)
                    val pixels = mapOf(position to Color(12, 34, 56, 78))
                    add(Case("pixel color at ($x, $y)", pixels) { it.assertPixelColor(Color(12, 34, 56, 78), x, y) })
                    add(
                        Case("all pixels at ($x, $y)", pixels) { image ->
                            image.assertPixels(Dimension(3, 2)) {
                                if (it == position) Color(12, 34, 56, 78) else Color(0, true)
                            }
                        },
                    )
                    add(
                        Case("skip marked pixel at ($x, $y)", pixels) { image ->
                            image.assertPixels { if (it == position) null else Color(0, true) }
                        },
                    )
                    add(
                        Case("select marked pixel at ($x, $y)", pixels) { image ->
                            image.assertPixels { if (it == position) Color(12, 34, 56, 78) else null }
                        },
                    )
                    add(
                        Case("mismatched pixel at ($x, $y)", pixels) { image ->
                            assertFailsWith<AssertionError> {
                                image.assertPixels(Dimension(3, 2), "sample") { Color(0, true) }
                            }
                        },
                    )
                }
            }
            add(Case("transparent pixel") { it.assertPixelColor(Color(0, true), 0, 0) })
            add(Case("transparent pixels") { it.assertPixels { Color(0, true) } })
        }

        private fun differingCases(): List<Case> = buildList {
            add(
                Case("multiple differing pixels") { image ->
                    assertFailsWith<AssertionError> {
                        image.assertPixels(Dimension(3, 2), "shape") {
                            if (it == Point(0, 0) || it == Point(2, 1)) Color.RED else null
                        }
                    }
                },
            )
            for ((channel, expected) in listOf(
                "red" to Color(13, 34, 56, 78),
                "green" to Color(12, 35, 56, 78),
                "blue" to Color(12, 34, 57, 78),
                "alpha" to Color(12, 34, 56, 79),
            )) {
                val pixels = mapOf(Point(1, 0) to Color(12, 34, 56, 78))
                add(
                    Case("pixel color differing $channel", pixels) { image ->
                        assertFailsWith<AssertionError> { image.assertPixelColor(expected, 1, 0, "sample") }
                    },
                )
                add(
                    Case("selected pixel differing $channel", pixels) { image ->
                        assertFailsWith<AssertionError> {
                            image.assertPixels { if (it == Point(1, 0)) expected else null }
                        }
                    },
                )
            }
        }

        private fun invalidCases(): List<Case> = buildList {
            for (size in listOf(Dimension(2, 2), Dimension(3, 3), Dimension(2, 3))) {
                add(
                    Case("size mismatch ${size.width} x ${size.height}") { image ->
                        assertFailsWith<AssertionError> {
                            image.assertPixels(size) { error("A size mismatch must fail before selecting pixels") }
                        }
                    },
                )
            }
            add(
                Case("empty selection") { image ->
                    assertFailsWith<AssertionError> { image.assertPixels { null } }
                },
            )
            for ((x, y) in listOf(-1 to 0, 3 to 0, 0 to -1, 0 to 2)) {
                add(
                    Case("out of bounds ($x, $y)") { image ->
                        assertFailsWith<IllegalArgumentException> { image.assertPixelColor(Color.RED, x, y) }
                    },
                )
            }
        }
    }

    private class Case(
        val name: String,
        val pixels: Map<Point, Color> = emptyMap(),
        val assertion: (BufferedImage) -> Unit,
    )
}
