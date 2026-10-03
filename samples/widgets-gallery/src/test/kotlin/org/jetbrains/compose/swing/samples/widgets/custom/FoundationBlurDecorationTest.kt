package org.jetbrains.compose.swing.samples.widgets.custom

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoundationBlurDecorationTest {
    @Test
    fun blurReachesTheSurfaceEdgeAndBorder() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            onNodeWithText("Shadow").performClick()
            val surface = onNodeWithTag(DECORATION_TAG).fetch<JComponent>()
            val decoratable = surface as Decoratable
            val sharp = surface.captureToImage()
            val sharpBounds = decoratable.decoration.localLayoutBounds(surface)
            onNodeWithText("Blur").performClick()
            onNodeWithTag(DECORATION_BLUR_RADIUS_TAG).fetch<JSlider>().value = 16
            awaitIdle()
            val blurred = surface.captureToImage()
            val blurredBounds = decoratable.decoration.localLayoutBounds(surface)
            // The gallery's default card blurred at this radius: the border at column 1, the gradient at column 20.
            val samples = mapOf(1 to 0xAAA760AD.toInt(), 20 to 0xFAB469CF.toInt())
            assertTrue(
                sharpBounds.width > samples.keys.max() && blurredBounds.width > samples.keys.max(),
                "the logical surface did not contain the border and gradient samples",
            )
            assertTrue(blurredBounds.x > sharpBounds.x, "blur did not reserve space for its halo")
            val sampleRow = blurredBounds.height / 2
            val sharpY = sharpBounds.y + sampleRow
            val blurredY = blurredBounds.y + sampleRow
            for ((x, expected) in samples) {
                assertEquals(
                    255,
                    sharp.getRGB(sharpBounds.x + x, sharpY) ushr 24,
                    "the sharp border/surface sample was not opaque at $x",
                )
                assertEquals(
                    expected,
                    blurred.getRGB(blurredBounds.x + x, blurredY),
                    "the blurred logical content pixel at $x",
                )
            }
            assertEquals(
                0,
                sharp.getRGB(sharpBounds.x - 1, sharpY) ushr 24,
                "the sharp reference already had a halo",
            )
            assertEquals(
                154,
                blurred.getRGB(blurredBounds.x - 1, blurredY) ushr 24,
                "the blur did not soften the border into its halo",
            )
        }
}
