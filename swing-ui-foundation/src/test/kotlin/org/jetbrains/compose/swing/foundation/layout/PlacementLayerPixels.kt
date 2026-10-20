package org.jetbrains.compose.swing.foundation.layout

import java.awt.image.BufferedImage

/** The alpha channel of the pixel at [x], [y], as a number a test compares against an expected opacity. */
internal fun BufferedImage.alphaAt(
    x: Int,
    y: Int,
): Double = (getRGB(x, y) ushr 24).toDouble()
