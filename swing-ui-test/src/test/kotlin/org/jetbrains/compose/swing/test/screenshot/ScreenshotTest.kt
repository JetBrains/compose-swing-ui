package org.jetbrains.compose.swing.test.screenshot

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenshotTest {
    @Test
    fun captureProducesImageSizedToComponent() = runComposeSwingTest {
        setContent { Button(text = "OK", onClick = {}) }

        val node = onNodeWithText("OK")
        val component = node.resolve()
        val image = node.captureToImage()

        assertTrue(image.width > 0 && image.height > 0, "captured image should have a positive size")
        assertEquals(component.width, image.width, "captured image width should match the component")
        assertEquals(component.height, image.height, "captured image height should match the component")
    }

    /** A hand-built reference needs no layout pass of its own: the capture lays it out at the size given. */
    @Test
    fun aHandBuiltComponentIsCapturedAtTheSizeItCarriesOrAsksFor() = runComposeSwingTest {
        val unsized = JLabel("hi")
        val sized = JPanel().apply { setSize(120, 40) }

        assertEquals(unsized.preferredSize.width, unsized.captureToImage().width)
        assertEquals(120, sized.captureToImage().width)
        assertEquals(40, sized.captureToImage().height)
    }

    /** A non-Swing component can render itself into the capture's off-screen graphics context. */
    @Test
    fun aRawAwtComponentIsCapturedOffscreen() = runComposeSwingTest {
        val component = PaintedAwtComponent()

        val image = component.captureToImage()

        assertEquals(120, image.width, "the image is as wide as the component")
        assertEquals(40, image.height, "the image is as tall as the component")
        assertEquals(Color.RED.rgb, image.getRGB(10, 10), "the component's own painting is captured")
    }

    @Test
    fun captureToImagesProducesOneImagePerMatchedComponent() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box()) {
                Button(text = "First", onClick = {})
                Button(text = "Second longer label", onClick = {})
            }
        }

        val buttons = onAllNodesOfType<JButton>()
        val components = buttons.fetchAll<JButton>()
        val images = buttons.captureToImages()

        assertEquals(components.size, images.size, "one image is produced per matched component")
        assertEquals(2, images.size, "both buttons should be captured")

        // Depth-first pre-order: image[i] belongs to component[i], each sized to its own bounds.
        components.forEachIndexed { index, component ->
            assertEquals(component.width, images[index].width, "image $index width should match its component")
            assertEquals(component.height, images[index].height, "image $index height should match its component")
        }

        // Distinct components are captured independently, so their images differ.
        assertFailsWith<AssertionError>("each match is captured independently") {
            assertImagesPixelPerfect(images[0], images[1])
        }
    }

    @Test
    fun rootCaptureMatchesRootSize() = runComposeSwingTest {
        setContent { Label(text = "hi") }

        val image = captureToImage()
        assertEquals(root.width, image.width, "root capture width should match the root")
        assertEquals(root.height, image.height, "root capture height should match the root")
    }

    @Test
    fun captureMatchesItselfExactly() = runComposeSwingTest {
        setContent { Button(text = "Click me", onClick = {}) }

        val image = onNodeWithText("Click me").captureToImage()
        val pixels = image.toArgbIntArray()
        val result = PixelPerfectMatcher.compare(pixels, pixels.copyOf(), image.width, image.height)

        assertTrue(result.matches, result.statistics)
    }

    @Test
    fun captureMatchesItselfStructurally() = runComposeSwingTest {
        setContent { Button(text = "Click me", onClick = {}) }

        val image = onNodeWithText("Click me").captureToImage()
        // MSSIM of an image against itself is exactly 1.0, comfortably above the default threshold.
        assertImageMatches(expected = image, image = image)
    }

    @Test
    fun smallElementMoveStillPassesDefaultThreshold() = runComposeSwingTest {
        val expected = renderWithElementAt()
        val nudged = renderWithElementAt(elementX = 38)

        // Nudging a single element a couple of pixels keeps MSSIM above the default threshold.
        assertImageMatches(expected = expected, image = nudged)
    }

    @Test
    fun clearlyDifferentImageFails() = runComposeSwingTest {
        setContent { Button(text = "Click me", onClick = {}) }

        val image = onNodeWithText("Click me").captureToImage()
        val inverted = invert(image)

        val failure =
            assertFailsWith<AssertionError> {
                assertImageMatches(expected = image, image = inverted)
            }
        assertTrue(
            failure.message.orEmpty().contains("Image does not match the expected image."),
            "the failure should say the images did not match: ${failure.message}",
        )
    }

    @Test
    fun sizeMismatchFails() = runComposeSwingTest {
        setContent { Label(text = "hi") }

        val image = onNodeWithText("hi").captureToImage()
        val smaller = BufferedImage(image.width + 10, image.height, BufferedImage.TYPE_INT_ARGB)

        assertFailsWith<AssertionError> {
            assertImageMatches(expected = smaller, image = image)
        }
    }

    @Test
    fun pixelPerfectMatcherFlagsAnElementMove() {
        val expected = renderWithElementAt()
        val nudged = renderWithElementAt(elementX = 38)

        val result =
            PixelPerfectMatcher.compare(
                expected.toArgbIntArray(),
                nudged.toArgbIntArray(),
                expected.width,
                expected.height,
            )

        assertTrue(!result.matches, "expected a pixel-perfect mismatch")
        assertNotNull(result.diff, "a diff image should be produced on mismatch")
    }

    @Test
    fun blankImagesMatchAndOneWithContentDoesNot() = runComposeSwingTest {
        val blank = filled(Color.WHITE)
        val marked = filled(Color.WHITE)
        marked.createGraphics().apply {
            color = Color.BLACK
            fillRect(0, 0, 4, 4)
            dispose()
        }

        // A region both images leave blank is not compared, so blank images match. Content in one of them
        // still has to fail, or the skip would be hiding differences rather than ignoring blanks.
        assertImageMatches(expected = blank, image = filled(Color.WHITE))
        assertFailsWith<AssertionError> { assertImageMatches(expected = blank, image = marked) }
    }

    @Test
    fun pixelPerfectAllowsNoMoreDifferingPixelsThanItIsGiven() = runComposeSwingTest {
        val expected = renderWithElementAt()
        val oneOff = renderWithElementAt().also { it.setRGB(0, 0, Color.RED.rgb) }

        assertImagesPixelPerfect(expected, renderWithElementAt())
        assertImagesPixelPerfect(expected, oneOff, maxDifferentPixels = 1)

        val failure = assertFailsWith<AssertionError> { assertImagesPixelPerfect(expected, oneOff) }
        assertTrue(
            failure.message.orEmpty().contains("Images differ in 1 pixel(s), more than the allowed 0"),
            "the failure counts the pixels that differ against the tolerance: ${failure.message}",
        )
    }

    @Test
    fun pixelPerfectRefusesANegativeTolerance() = runComposeSwingTest {
        val image = renderWithElementAt()

        assertFailsWith<IllegalArgumentException> {
            assertImagesPixelPerfect(image, image, maxDifferentPixels = -1)
        }
    }

    @Test
    fun differingPixelBoundsIsNullForIdenticalImages() {
        assertNull(differingPixelBounds(filled(Color.WHITE), filled(Color.WHITE)))
    }

    @Test
    fun differingPixelBoundsIsTheSmallestRectangleHoldingEveryDifference() {
        val changed =
            filled(Color.WHITE).also {
                it.setRGB(3, 4, Color.RED.rgb)
                it.setRGB(10, 7, Color.RED.rgb)
            }

        assertEquals(Rectangle(3, 4, 8, 4), differingPixelBounds(filled(Color.WHITE), changed))
    }

    @Test
    fun differingPixelBoundsRefusesImagesOfDifferentSizes() {
        assertFailsWith<IllegalArgumentException> {
            differingPixelBounds(filled(Color.WHITE), BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
        }
    }

    /** An image of the scene size filled with [color] and nothing else. */
    private fun filled(color: Color): BufferedImage {
        val image = BufferedImage(88, 25, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.dispose()
        return image
    }

    /**
     * Renders a small button-like image with a single low-contrast element positioned at
     * [elementX]. Rendering the same scene twice with element positions a couple of pixels apart
     * models "one UI element moved slightly": the change is structurally small (MSSIM stays above
     * the default threshold) yet not pixel-identical (the strict matcher still flags it).
     */
    private fun renderWithElementAt(elementX: Int = 36): BufferedImage {
        val image = BufferedImage(88, 25, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color(0xEE, 0xEE, 0xEE)
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = Color(0xD2, 0xD2, 0xD2)
        graphics.fillRect(elementX, 10, 16, 5)
        graphics.dispose()
        return image
    }

    private fun invert(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getRGB(x, y)
                val alpha = argb and -0x1000000
                val inverted = alpha or (argb.inv() and 0x00FFFFFF)
                copy.setRGB(x, y, inverted)
            }
        }
        return copy
    }

    private class PaintedAwtComponent : Component() {
        override fun getPreferredSize(): Dimension = Dimension(120, 40)

        override fun paint(graphics: Graphics) {
            graphics.color = Color.RED
            graphics.fillRect(0, 0, width, height)
        }
    }
}
