// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.style.icon
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.DebugGraphics
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Paints [icon] with its corner at [at] on a transparent image of [canvas], for [component] when one is given.
 * The graphics has antialiasing switched on beforehand when [antialiased] is `true`, and off otherwise.
 */
internal fun painted(
    icon: Icon,
    canvas: Dimension = Dimension(icon.iconWidth, icon.iconHeight),
    at: Point = Point(0, 0),
    component: Component? = null,
    antialiased: Boolean = false,
): BufferedImage {
    val image = BufferedImage(canvas.width, canvas.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        if (antialiased) graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        icon.paintIcon(component, graphics, at.x, at.y)
    } finally {
        graphics.dispose()
    }
    return image
}

class SvgIconTest {
    @Test
    fun `the document is drawn onto the icon`() =
        runComposeSwingTest {
            var icon: Icon? = null
            val document = TestSvg.Static.load()
            setContent { icon = rememberSvgIcon(document) }

            assertTrue(drawnPixels(painted(assertNotNull(icon))) > 0, "the icon painted nothing")
        }

    @Test
    fun `an icon reaches a component through the icon style setter`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent {
                val icon = rememberSvgIcon(document)
                Label(text = "labelled", style = { icon(icon) })
            }

            onNodeOfType<JLabel>().assertProperty(24) { assertNotNull(icon).iconWidth }
            onNodeOfType<JLabel>().assertProperty(16) { assertNotNull(icon).iconHeight }
        }

    @Test
    fun `a stated size is reported and bounds the drawing at the painted offset`() =
        runComposeSwingTest {
            var icon: Icon? = null
            val document = TestSvg.Static.load()
            setContent { icon = rememberSvgIcon(document, size = Dimension(30, 20)) }
            val sized = assertNotNull(icon)

            val image = painted(sized, canvas = Dimension(60, 40), at = Point(10, 5))

            assertEquals(Dimension(30, 20), Dimension(sized.iconWidth, sized.iconHeight))
            val outside =
                (0 until image.width).sumOf { x ->
                    (0 until image.height).count { y ->
                        val inside = x in 10 until 40 && y in 5 until 25
                        !inside && (image.getRGB(x, y) ushr 24) != 0
                    }
                }
            assertEquals(0, outside, "the icon drew outside the rectangle it was painted at")
            assertTrue(drawnPixels(image) > 0, "the icon painted nothing inside its rectangle")
        }

    @Test
    fun `visible svg overflow is clipped at the icon's painted offset`() =
        runComposeSwingTest {
            var icon: Icon? = null
            val document = TestSvg.VisibleSlice.load()
            setContent { icon = rememberSvgIcon(document, size = Dimension(8, 24)) }

            val image = painted(assertNotNull(icon), canvas = Dimension(18, 30), at = Point(5, 3))

            assertEquals(0, image.getRGB(2, 15) ushr 24)
            assertEquals(255, image.getRGB(9, 15) ushr 24)
            assertEquals(0, image.getRGB(15, 15) ushr 24)
        }

    @Test
    fun `a changed document or size makes a different icon and the same inputs the same one`() =
        runComposeSwingTest {
            var document by mutableStateOf(TestSvg.Static.load())
            val replacement = TestSvg.Finite.load()
            var size by mutableStateOf(Dimension(24, 16))
            var unrelated by mutableStateOf(0)
            val composed = mockk<() -> Unit>(relaxed = true)
            var icon: Icon? = null
            setContent {
                unrelated.toString()
                icon = rememberSvgIcon(document, size)
                composed()
            }
            val first = assertNotNull(icon)
            clearMocks(composed)

            unrelated = 1
            awaitIdle()
            // The content should have recomposed.
            verify { composed() }
            assertSame(first, icon, "unchanged inputs should keep the icon")

            document = replacement
            awaitIdle()
            val otherDocument = assertNotNull(icon)
            assertNotSame(first, otherDocument, "a changed document should make a new icon")

            size = Dimension(48, 32)
            awaitIdle()
            assertNotSame(otherDocument, icon, "a changed size should make a new icon")
            assertEquals(48, assertNotNull(icon).iconWidth)
        }

    @Test
    fun `remembered icons capture the dimensions from each composition`() =
        runComposeSwingTest {
            val recycledSize = Dimension(16, 12)
            var generation by mutableStateOf(0)
            var icon: Icon? = null
            val document = TestSvg.Static.load()
            setContent {
                val size = if (generation == 1) Dimension(32, 24) else recycledSize
                icon = rememberSvgIcon(document, size)
            }
            val original = assertNotNull(icon)

            recycledSize.setSize(32, 24)
            generation = 1
            awaitIdle()

            val resized = assertNotNull(icon)
            assertEquals(Dimension(32, 24), Dimension(resized.iconWidth, resized.iconHeight))
            assertEquals(Dimension(16, 12), Dimension(original.iconWidth, original.iconHeight))

            generation = 2
            awaitIdle()

            assertSame(resized, icon, "the recycled and fresh dimensions describe the same size")

            recycledSize.setSize(32, 20)
            generation = 3
            awaitIdle()

            assertEquals(20, assertNotNull(icon).iconHeight)
        }

    @Test
    fun `debug painting a label keeps the icon offset and transparent pixels`() =
        runComposeSwingTest {
            var full: Icon? = null
            var empty: Icon? = null
            val document = TestSvg.Static.load()
            setContent {
                full = rememberSvgIcon(document)
                empty = rememberSvgIcon(document, size = Dimension(0, 16))
            }
            val label =
                JLabel(assertNotNull(full), SwingConstants.LEFT).apply {
                    isDoubleBuffered = false
                    isOpaque = false
                    debugGraphicsOptions = DebugGraphics.LOG_OPTION
                    border = BorderFactory.createEmptyBorder(5, 7, 5, 7)
                    setSize(38, 26)
                }
            val image = BufferedImage(38, 26, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                label.paint(graphics)
                assertEquals(Color(0x3b82f6).rgb, image.getRGB(7, 5))
                assertEquals(0, image.getRGB(6, 5))
                assertEquals(0, image.getRGB(17, 5))
                assertEquals(100, drawnPixels(image))

                graphics.background = Color(0, true)
                graphics.clearRect(0, 0, image.width, image.height)
                label.icon = assertNotNull(empty)
                label.paint(graphics)
                assertEquals(0, drawnPixels(image))
            } finally {
                graphics.dispose()
            }
        }

    @Test
    fun `a changed antialias makes an icon that paints the other way`() =
        runComposeSwingTest {
            var antialias by mutableStateOf(true)
            var icon: Icon? = null
            val document = TestSvg.Curved.load()
            setContent { icon = rememberSvgIcon(document, antialias = antialias) }
            val smoothed = assertNotNull(icon)

            antialias = false
            awaitIdle()

            val hard = assertNotNull(icon)
            assertNotSame(smoothed, hard, "a changed antialias should make a new icon")
            assertTrue(partlyCoveredPixels(painted(smoothed)) > 20, "the first icon should smooth the curve")
            assertTrue(
                partlyCoveredPixels(painted(smoothed, antialiased = true)) > 20,
                "the first icon should smooth the curve on antialiased graphics",
            )
            assertEquals(
                0,
                partlyCoveredPixels(painted(hard, antialiased = true)),
                "the second icon should have hard edges on antialiased graphics",
            )
        }

    @Test
    fun `painting for a component draws the picture too`() =
        runComposeSwingTest {
            var icon: Icon? = null
            val document = TestSvg.Static.load()
            setContent { icon = rememberSvgIcon(document) }

            val image = painted(assertNotNull(icon), component = JLabel())

            assertTrue(drawnPixels(image) > 0, "the icon painted nothing for a component")
        }
}
