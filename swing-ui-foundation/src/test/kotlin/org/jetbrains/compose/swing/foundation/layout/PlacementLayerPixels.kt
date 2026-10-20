package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.JComponent

/** The alpha channel of the pixel at [x], [y], as a number a test compares against an expected opacity. */
internal fun BufferedImage.alphaAt(
    x: Int,
    y: Int,
): Double = (getRGB(x, y) ushr 24).toDouble()

/**
 * Composes a 20-pixel canvas for each pair, tagged by its first and modified by its second, drawing [content],
 * a red square filling the canvas by default.
 */
internal fun ComposeSwingTest.setCanvases(
    vararg canvases: Pair<String, SwingModifier>,
    content: (Graphics2D) -> Unit = {
        it.color = Color.RED
        it.fillRect(0, 0, 20, 20)
    },
) {
    setContent {
        Row {
            for ((tag, modifier) in canvases) {
                Canvas(modifier = SwingModifier.testTag(tag).then(modifier).styleable { preferredSize(20, 20) }) {
                    content(graphics)
                }
            }
        }
    }
}

/**
 * Paints the component tagged [tag] onto an image of [scale] device pixels per pixel, at a sub-pixel offset
 * of the device grid, after [setUp] prepares the graphics.
 */
internal fun ComposeSwingTest.paintTagged(
    tag: String,
    scale: Double = 1.0,
    setUp: Graphics2D.() -> Unit = {},
): BufferedImage {
    val component = onNodeWithTag(tag).fetch<JComponent>()
    val image = BufferedImage((20 * scale).toInt(), (20 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.scale(scale, scale)
        graphics.translate(0.3, 0.6)
        graphics.setUp()
        component.printAll(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}
