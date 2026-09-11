package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JComponent

/** Creates a transparent raster and gives its graphics to a test drawing directly without a Swing component. */
internal fun renderImage(
    width: Int,
    height: Int,
    type: Int = BufferedImage.TYPE_INT_ARGB,
    block: (Graphics2D) -> Unit,
): BufferedImage {
    val image = BufferedImage(width, height, type)
    val graphics = image.createGraphics()
    try {
        block(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}

/**
 * What this component paints onto a transparent [width] by [height] image through [transform], clipped to [clip] in
 * its own coordinates, as Swing paints it.
 */
internal fun JComponent.paintOnto(
    width: Int,
    height: Int,
    transform: AffineTransform = AffineTransform(),
    clip: Rectangle = Rectangle(size),
): BufferedImage =
    renderImage(width, height) {
        it.transform(transform)
        it.clip(clip)
        paint(it)
    }
