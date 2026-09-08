package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertTrue

/** The side of the square [recordSquare] records, in logical units. */
internal const val SQUARE = 8

/** Records a solid [color] square, [SQUARE] logical units on each side, at [scale] device pixels each. */
internal fun ImageLayer.recordSquare(
    color: Color = Color.RED,
    scale: Double = 1.0,
) = record(SQUARE, SQUARE, scale) { graphics ->
    graphics.color = color
    graphics.fillRect(0, 0, SQUARE, SQUARE)
}

/**
 * Fails unless the pixel at [x], [y] is the opaque red [recordSquare] draws, within the blending an interpolated
 * drawing leaves. Sample at least one pixel inside the drawing's boundary.
 */
internal fun assertRedAt(
    image: BufferedImage,
    x: Int,
    y: Int,
    message: String,
) {
    val argb = image.getRGB(x, y)
    val opaqueRed =
        (argb ushr 24) > 0xF0 &&
            ((argb shr 16) and 0xFF) > 0xF0 &&
            ((argb shr 8) and 0xFF) < 0x10 &&
            (argb and 0xFF) < 0x10
    assertTrue(opaqueRed, "$message Pixel $x, $y was 0x${Integer.toHexString(argb)}.")
}
