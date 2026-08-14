package org.jetbrains.compose.swing.test.screenshot

import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.math.pow

/**
 * Outcome of comparing two equally-sized images.
 *
 * @property matches whether the images are considered a match under the chosen metric.
 * @property statistics a one-line, human-readable summary of the comparison.
 * @property diff an image highlighting differing pixels in magenta over a transparent background,
 *   or `null` when the images match.
 * @property differentPixelCount the number of differing pixels when known (the pixel-exact matcher),
 *   or `-1` when not computed.
 */
internal data class MatchResult(
    val matches: Boolean,
    val statistics: String,
    val diff: BufferedImage?,
    val differentPixelCount: Int = -1,
)

/** Compares two equally-sized ARGB pixel arrays (row-major, length = width * height). */
internal interface BitmapMatcher {
    fun compare(
        expected: IntArray,
        actual: IntArray,
        width: Int,
        height: Int,
    ): MatchResult
}

/** Reads a [BufferedImage] into a row-major ARGB int array. */
internal fun BufferedImage.toArgbIntArray(): IntArray = getRGB(0, 0, width, height, null, 0, width)

/** Builds an ARGB [BufferedImage] of [width] x [height] from a row-major ARGB int array. */
internal fun IntArray.toArgbImage(
    width: Int,
    height: Int,
): BufferedImage =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
        it.setRGB(0, 0, width, height, this, 0, width)
    }

/** Magenta overlay for a differing pixel; transparent for a matching pixel. */
private const val DIFF_COLOR: Int = -0x10000 - 0xFF00 // 0xFFFF00FF (opaque magenta)
private const val DIFF_TRANSPARENT: Int = 0x00000000

/** Builds a magenta-over-transparent diff image marking each index where [differs] is true. */
private fun buildDiff(
    expected: IntArray,
    actual: IntArray,
    width: Int,
    height: Int,
    differs: (Int, Int) -> Boolean,
): BufferedImage {
    val pixels = IntArray(expected.size)
    for (i in expected.indices) {
        pixels[i] = if (differs(expected[i], actual[i])) DIFF_COLOR else DIFF_TRANSPARENT
    }
    return pixels.toArgbImage(width, height)
}

/**
 * Pixel-perfect (byte-exact) matcher. Any differing ARGB pixel fails, and the produced diff marks
 * every differing pixel in magenta.
 */
internal object PixelPerfectMatcher : BitmapMatcher {
    override fun compare(
        expected: IntArray,
        actual: IntArray,
        width: Int,
        height: Int,
    ): MatchResult {
        var different = 0
        for (i in expected.indices) {
            if (expected[i] != actual[i]) different++
        }
        val total = width * height
        val matches = different == 0
        val statistics = "different pixels: $different/$total (exact match required)"
        val diff = if (matches) null else buildDiff(expected, actual, width, height) { a, b -> a != b }
        return MatchResult(matches, statistics, diff, differentPixelCount = different)
    }
}

/**
 * Structural-similarity (MSSIM) matcher. Computes the mean SSIM over 10x10 sliding windows and
 * matches when it meets or exceeds [threshold]. Tolerates antialiasing and font-rasterization drift
 * while still catching real visual changes. On a mismatch, the diff is produced by an exact
 * per-pixel comparison.
 *
 * Port of the androidx/Compose Multiplatform MSSIM algorithm operating on int-array pixels.
 */
internal class MSSIMMatcher(
    private val threshold: Double = DEFAULT_THRESHOLD,
) : BitmapMatcher {
    override fun compare(
        expected: IntArray,
        actual: IntArray,
        width: Int,
        height: Int,
    ): MatchResult {
        val score = calculateSSIM(expected, actual, width, height)
        val matches = score >= threshold
        val statistics =
            "MSSIM: ${String.format(Locale.ROOT, "%.4f", score)} " +
                "(threshold ${String.format(Locale.ROOT, "%.4f", threshold)})"
        val diff =
            if (matches) {
                null
            } else {
                buildDiff(expected, actual, width, height) { a, b -> a != b }
            }
        return MatchResult(matches, statistics, diff)
    }

    private fun calculateSSIM(
        expected: IntArray,
        actual: IntArray,
        width: Int,
        height: Int,
    ): Double {
        var ssimTotal = 0.0
        var windows = 0
        var currentWindowY = 0
        while (currentWindowY < height) {
            val windowHeight = computeWindowSize(currentWindowY, height)
            var currentWindowX = 0
            while (currentWindowX < width) {
                val windowWidth = computeWindowSize(currentWindowX, width)
                val window =
                    Window(
                        start = indexOf(currentWindowX, currentWindowY, width),
                        width = width,
                        windowWidth = windowWidth,
                        windowHeight = windowHeight,
                    )
                if (isWindowWhite(expected, window) && isWindowWhite(actual, window)) {
                    currentWindowX += WINDOW_SIZE
                    continue
                }
                windows++
                val means = getMeans(expected, actual, window)
                val meanX = means[0]
                val meanY = means[1]
                val variances = getVariances(expected, actual, window, meanX, meanY)
                val varX = variances[0]
                val varY = variances[1]
                val stdBoth = variances[2]
                ssimTotal += ssim(meanX, meanY, varX, varY, stdBoth)
                currentWindowX += WINDOW_SIZE
            }
            currentWindowY += WINDOW_SIZE
        }
        if (windows == 0) return 1.0
        return ssimTotal / windows
    }

    private fun computeWindowSize(
        coordinateStart: Int,
        dimension: Int,
    ): Int =
        if (coordinateStart + WINDOW_SIZE <= dimension) {
            WINDOW_SIZE
        } else {
            dimension - coordinateStart
        }

    /** A square region within a pair of pixel arrays, identified by its [start] index in row-major order. */
    private data class Window(
        val start: Int,
        val width: Int,
        val windowWidth: Int,
        val windowHeight: Int,
    )

    private fun isWindowWhite(
        colors: IntArray,
        window: Window,
    ): Boolean {
        for (y in 0 until window.windowHeight) {
            for (x in 0 until window.windowWidth) {
                if (colors[indexOf(x, y, window.width) + window.start] != WHITE) {
                    return false
                }
            }
        }
        return true
    }

    private fun ssim(
        muX: Double,
        muY: Double,
        sigX: Double,
        sigY: Double,
        sigXy: Double,
    ): Double {
        var ssim = (2.0 * muX * muY + C1) * (2.0 * sigXy + C2)
        val denom = (muX * muX + muY * muY + C1) * (sigX + sigY + C2)
        ssim /= denom
        return ssim
    }

    private fun getMeans(
        expected: IntArray,
        actual: IntArray,
        window: Window,
    ): DoubleArray {
        var sumX = 0.0
        var sumY = 0.0
        for (y in 0 until window.windowHeight) {
            for (x in 0 until window.windowWidth) {
                val index = indexOf(x, y, window.width) + window.start
                sumX += getLuminance(expected[index])
                sumY += getLuminance(actual[index])
            }
        }
        val n = (window.windowWidth * window.windowHeight).toDouble()
        return doubleArrayOf(sumX / n, sumY / n)
    }

    private fun getVariances(
        expected: IntArray,
        actual: IntArray,
        window: Window,
        meanX: Double,
        meanY: Double,
    ): DoubleArray {
        var sumX = 0.0
        var sumY = 0.0
        var sumXy = 0.0
        for (y in 0 until window.windowHeight) {
            for (x in 0 until window.windowWidth) {
                val index = indexOf(x, y, window.width) + window.start
                val currentX = getLuminance(expected[index])
                val currentY = getLuminance(actual[index])
                sumX += (currentX - meanX).pow(2.0)
                sumY += (currentY - meanY).pow(2.0)
                sumXy += (currentX - meanX) * (currentY - meanY)
            }
        }
        val n = (window.windowWidth * window.windowHeight).toDouble() - 1.0
        return doubleArrayOf(sumX / n, sumY / n, sumXy / n)
    }

    private fun getLuminance(color: Int): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return SSIM_R * r + SSIM_G * g + SSIM_B * b
    }

    private fun indexOf(
        x: Int,
        y: Int,
        width: Int,
    ): Int = y * width + x

    companion object {
        const val DEFAULT_THRESHOLD: Double = 0.96
        private const val WINDOW_SIZE = 10
        private const val WHITE = -0x1 // 0xFFFFFFFF
        private const val SSIM_R = 0.21
        private const val SSIM_G = 0.72
        private const val SSIM_B = 0.07
        private const val L = 254.0
        private const val K1 = 0.00001
        private const val K2 = 0.00003
        private val C1 = (L * K1).pow(2.0)
        private val C2 = (L * K2).pow(2.0)
    }
}
