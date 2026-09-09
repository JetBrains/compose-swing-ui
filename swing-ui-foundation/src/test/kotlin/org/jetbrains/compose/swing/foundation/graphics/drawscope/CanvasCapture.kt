package org.jetbrains.compose.swing.foundation.graphics.drawscope

import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.image.BufferedImage

/** Draws each of [draws] on one [width] x [height] canvas with no rendering hints, and captures each drawing. */
internal fun ComposeSwingTest.captureEach(
    width: Int,
    height: Int,
    vararg draws: DrawScope.() -> Unit,
): List<BufferedImage> {
    var current: DrawScope.() -> Unit = {}
    setContent {
        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(width, height), renderingHints = null) {
            current()
        }
    }
    return draws.map { draw ->
        current = draw
        onNodeWithTag("canvas").captureToImage()
    }
}
