// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Color

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
