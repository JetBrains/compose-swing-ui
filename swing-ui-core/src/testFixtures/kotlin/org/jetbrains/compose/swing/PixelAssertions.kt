// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage

/**
 * Asserts that the pixel at ([x], [y]) equals [expected], including alpha.
 * The failure includes [message] when provided. A transparent pixel is `Color(0, true)`.
 *
 * @throws IllegalArgumentException if the position is outside the image.
 * @throws AssertionError if the pixel differs.
 */
public fun BufferedImage.assertPixelColor(
    expected: Color,
    x: Int,
    y: Int,
    message: String? = null,
) {
    require(x in 0 until width && y in 0 until height) { "Pixel ($x, $y) is outside ${width}x$height image." }
    val actual = Color(getRGB(x, y), true)
    if (actual != expected) {
        throw AssertionError(
            withMessage("Pixel ($x, $y): expected ${expected.channels()}, actual ${actual.channels()}.", message),
        )
    }
}

/**
 * Asserts every pixel selected by [expectedColorProvider] equals its expected color, including alpha.
 * Return `null` to skip a pixel. The failure includes [message] when provided.
 *
 * @param expectedSize the required image size, or `null` to accept any size.
 * @param message an optional failure message.
 * @param expectedColorProvider the expected color at each position, or `null` to skip it.
 * @throws AssertionError if the size or a selected pixel differs, or no pixels are selected.
 */
public fun BufferedImage.assertPixels(
    expectedSize: Dimension? = null,
    message: String? = null,
    expectedColorProvider: (position: Point) -> Color?,
) {
    assertExpectedSize(expectedSize, message)
    var checked = 0
    var differences = 0
    var bounds: Rectangle? = null
    var first = ""
    for (y in 0 until height) {
        for (x in 0 until width) {
            val expected = expectedColorProvider(Point(x, y)) ?: continue
            checked++
            val actual = Color(getRGB(x, y), true)
            if (actual != expected) {
                differences++
                val pixel = Rectangle(x, y, 1, 1)
                bounds = bounds?.union(pixel) ?: pixel
                if (differences == 1) first = "($x, $y): expected ${expected.channels()}, actual ${actual.channels()}"
            }
        }
    }
    val failure =
        when {
            checked == 0 -> "No pixels were checked."
            differences > 0 -> "$differences pixels differ within $bounds. First $first."
            else -> null
        }
    if (failure != null) throw AssertionError(withMessage(failure, message))
}

private fun BufferedImage.assertExpectedSize(
    expectedSize: Dimension?,
    message: String?,
) {
    if (expectedSize != null && (width != expectedSize.width || height != expectedSize.height)) {
        throw AssertionError(
            withMessage(
                "Expected size ${expectedSize.width}x${expectedSize.height}, actual ${width}x$height.",
                message,
            ),
        )
    }
}

private fun Color.channels(): String = "(red=$red, green=$green, blue=$blue, alpha=$alpha)"

private fun withMessage(
    detail: String,
    message: String?,
): String = if (message == null) detail else "$message: $detail"
