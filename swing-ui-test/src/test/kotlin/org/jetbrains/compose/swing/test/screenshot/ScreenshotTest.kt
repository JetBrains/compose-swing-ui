package org.jetbrains.compose.swing.test.screenshot

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

    @Test
    fun captureToImagesProducesOneImagePerMatchedComponent() = runComposeSwingTest {
        setContent {
            BoxPanel {
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
        assertTrue(imagesDiffer(images[0], images[1]), "each match is captured independently")
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
        val expected = renderWithElementAt(elementX = ELEMENT_X)
        val nudged = renderWithElementAt(elementX = ELEMENT_X + NUDGE_PIXELS)

        // Nudging a single element a couple of pixels keeps MSSIM above the default threshold.
        assertImageMatches(expected = expected, image = nudged)
    }

    @Test
    fun clearlyDifferentImageFails() = runComposeSwingTest {
        setContent { Button(text = "Click me", onClick = {}) }

        val image = onNodeWithText("Click me").captureToImage()
        val inverted = invert(image)

        assertFailsWith<AssertionError> {
            assertImageMatches(expected = image, image = inverted)
        }
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
        val expected = renderWithElementAt(elementX = ELEMENT_X)
        val nudged = renderWithElementAt(elementX = ELEMENT_X + NUDGE_PIXELS)

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
    fun invalidGoldenIdentifierIsRejected() = runComposeSwingTest {
        setContent { Label(text = "hi") }

        val image = onNodeWithText("hi").captureToImage()
        assertFailsWith<IllegalArgumentException> {
            assertImageAgainstGolden(image, "bad name!")
        }
    }

    @Test
    fun missingGoldenWithoutUpdateFails() = runComposeSwingTest {
        setContent { Label(text = "hi") }

        val image = onNodeWithText("hi").captureToImage()
        assertFailsWith<AssertionError> {
            assertImageAgainstGolden(image, "definitely-not-recorded-golden")
        }
    }

    /**
     * Renders a small button-like image with a single low-contrast element positioned at
     * [elementX]. Rendering the same scene twice with element positions a couple of pixels apart
     * models "one UI element moved slightly": the change is structurally small (MSSIM stays above
     * the default threshold) yet not pixel-identical (the strict matcher still flags it).
     */
    private fun renderWithElementAt(elementX: Int): BufferedImage {
        val image = BufferedImage(SCENE_WIDTH, SCENE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color(0xEE, 0xEE, 0xEE)
        graphics.fillRect(0, 0, SCENE_WIDTH, SCENE_HEIGHT)
        graphics.color = Color(0xD2, 0xD2, 0xD2)
        graphics.fillRect(elementX, ELEMENT_Y, ELEMENT_WIDTH, ELEMENT_HEIGHT)
        graphics.dispose()
        return image
    }

    /**
     * Two captures differ when they disagree in size or in any ARGB pixel. Differing dimensions are a
     * difference in themselves; equal dimensions fall back to an exact per-pixel comparison.
     */
    private fun imagesDiffer(
        first: BufferedImage,
        second: BufferedImage,
    ): Boolean {
        if (first.width != second.width || first.height != second.height) return true
        val result =
            PixelPerfectMatcher.compare(
                first.toArgbIntArray(),
                second.toArgbIntArray(),
                first.width,
                first.height,
            )
        return !result.matches
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

    private companion object {
        const val SCENE_WIDTH = 88
        const val SCENE_HEIGHT = 25
        const val ELEMENT_X = 36
        const val ELEMENT_Y = 10
        const val ELEMENT_WIDTH = 16
        const val ELEMENT_HEIGHT = 5
        const val NUDGE_PIXELS = 2
    }
}
