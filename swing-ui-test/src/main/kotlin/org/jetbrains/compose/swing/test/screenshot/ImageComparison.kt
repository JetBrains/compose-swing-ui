@file:JvmMultifileClass
@file:JvmName("ScreenshotTestKt")

package org.jetbrains.compose.swing.test.screenshot

import java.awt.Rectangle
import java.awt.image.BufferedImage

/**
 * The smallest rectangle holding every pixel at which [expected] and [actual] differ, or `null` where
 * they are the same.
 *
 * @param expected the reference image.
 * @param actual the image under test.
 * @throws IllegalArgumentException if the images differ in size.
 */
public fun differingPixelBounds(
    expected: BufferedImage,
    actual: BufferedImage,
): Rectangle? {
    require(expected.width == actual.width && expected.height == actual.height) {
        "Images differ in size: expected ${expected.width}x${expected.height}, " +
            "actual ${actual.width}x${actual.height}."
    }
    var bounds: Rectangle? = null
    for (y in 0 until expected.height) {
        for (x in 0 until expected.width) {
            if (expected.getRGB(x, y) == actual.getRGB(x, y)) continue
            val pixel = Rectangle(x, y, 1, 1)
            bounds = bounds?.union(pixel) ?: pixel
        }
    }
    return bounds
}
