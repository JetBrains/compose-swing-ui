package org.jetbrains.compose.swing.swingmark

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.image.BufferedImage
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves both arms of every SwingMark test paint the same screen.
 *
 * The structural gate says the two arms hold the same widgets in the same state; this says those
 * widgets rasterize to the same image, so neither arm is faster for having drawn less.
 */
@Ignore("TODO restore once swing-ui-test publishes layoutOffscreen: the cards are not laid out")
class ArmPixelTest {
    @Test
    fun textArea() = runComposeSwingTest { assertArmsPaintTheSame("TextArea") }

    @Test
    fun sliders() = runComposeSwingTest { assertArmsPaintTheSame("Sliders") }

    @Test
    fun lists() = runComposeSwingTest { assertArmsPaintTheSame("Lists") }

    @Test
    fun tableRows() = runComposeSwingTest { assertArmsPaintTheSame("Table Rows") }

    @Test
    fun tree() = runComposeSwingTest { assertArmsPaintTheSame("Tree") }

    @Test
    fun subMenus() = runComposeSwingTest { assertArmsPaintTheSame("Sub-Menus") }
}

private suspend fun ComposeSwingTest.assertArmsPaintTheSame(testName: String) =
    withArms(testName) { raw, declared ->
        val rawImage = raw.captureToImage().alsoAssertPainted("$testName: the raw arm")
        val declaredImage = declared.captureToImage().alsoAssertPainted("$testName: the declared arm")
        assertImagesPixelPerfect(rawImage, declaredImage)
    }

/**
 * Answers with this image, having first shown that it carries a screen.
 *
 * A capture of nothing matches another capture of nothing, so a comparison of two blank images passes
 * while proving nothing at all. An image holding one color throughout is that blank capture, whether
 * it came out transparent or as an unbroken background.
 */
private fun BufferedImage.alsoAssertPainted(what: String): BufferedImage {
    val colors = getRGB(0, 0, width, height, null, 0, width).distinct()
    assertTrue(colors.size > 1, "$what painted nothing: ${width}x$height of a single color")
    return this
}
