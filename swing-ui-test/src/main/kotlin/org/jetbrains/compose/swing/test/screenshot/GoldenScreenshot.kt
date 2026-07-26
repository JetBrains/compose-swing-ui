package org.jetbrains.compose.swing.test.screenshot

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Captures the matched component and asserts it matches the stored golden image identified by
 * [goldenIdentifier], failing the test on a visual difference.
 *
 * The golden image is loaded from the test resources at `golden/<goldenIdentifier>.png` and compared
 * by structural similarity at [threshold] (higher is stricter). On a mismatch the captured, expected
 * and difference images are written to the build results directory for inspection and an
 * [AssertionError] is thrown describing the difference and the file locations.
 *
 * When record mode is enabled the captured image is written as the new golden into the test
 * resources and the assertion passes; this lets you record or refresh goldens on demand.
 *
 * @param goldenIdentifier identifies the golden image; allowed characters are letters, digits, '_'
 *   and '-'.
 * @param threshold the structural-similarity acceptance floor, where higher is stricter.
 * @throws AssertionError if the captured image differs from the golden beyond the threshold, or if
 *   the golden is missing while record mode is disabled.
 * @throws IllegalArgumentException if [goldenIdentifier] contains forbidden characters.
 */
public fun SwingNodeInteraction<*>.assertImageAgainstGolden(
    goldenIdentifier: String,
    threshold: Double = MSSIMMatcher.DEFAULT_THRESHOLD,
) {
    test.assertImageAgainstGolden(captureToImage(), goldenIdentifier, threshold)
}

/**
 * Captures the whole composition root and asserts it matches the stored golden image identified by
 * [goldenIdentifier].
 *
 * Behaves identically to [SwingNodeInteraction.assertImageAgainstGolden] for missing goldens, record
 * mode, mismatch output and naming rules.
 *
 * @param goldenIdentifier identifies the golden image; allowed characters are letters, digits, '_'
 *   and '-'.
 * @param threshold the structural-similarity acceptance floor, where higher is stricter.
 * @throws AssertionError if the captured image differs from the golden beyond the threshold, or if
 *   the golden is missing while record mode is disabled.
 * @throws IllegalArgumentException if [goldenIdentifier] contains forbidden characters.
 */
public fun ComposeSwingTest.assertImageAgainstGolden(
    goldenIdentifier: String,
    threshold: Double = MSSIMMatcher.DEFAULT_THRESHOLD,
) {
    assertImageAgainstGolden(captureToImage(), goldenIdentifier, threshold)
}

/**
 * Asserts an already-captured [image] against the golden image identified by [goldenIdentifier]. Use
 * this when the image was produced by [captureToImage] and possibly post-processed before comparing.
 *
 * Behaves identically to [SwingNodeInteraction.assertImageAgainstGolden] for missing goldens, record
 * mode, mismatch output and naming rules.
 *
 * @param image the captured image to compare.
 * @param goldenIdentifier identifies the golden image; allowed characters are letters, digits, '_'
 *   and '-'.
 * @param threshold the structural-similarity acceptance floor, where higher is stricter.
 * @throws AssertionError if [image] differs from the golden beyond the threshold, or if the golden
 *   is missing while record mode is disabled.
 * @throws IllegalArgumentException if [goldenIdentifier] contains forbidden characters.
 */
public fun ComposeSwingTest.assertImageAgainstGolden(
    image: BufferedImage,
    goldenIdentifier: String,
    threshold: Double = MSSIMMatcher.DEFAULT_THRESHOLD,
) {
    requireValidGoldenIdentifier(goldenIdentifier)
    GoldenScreenshotComparator(goldenIdentifier, threshold).assertAgainstGolden(image)
}

/**
 * Asserts an already-captured [image] matches [expected] by structural similarity at [threshold],
 * without involving any golden file. Useful for comparing two captured or synthesized images
 * directly inside a test.
 *
 * @param expected the reference image.
 * @param image the image under test.
 * @param threshold the structural-similarity acceptance floor, where higher is stricter.
 * @throws AssertionError if [image] differs from [expected] beyond the threshold, or if their sizes
 *   differ.
 */
public fun ComposeSwingTest.assertImageMatches(
    expected: BufferedImage,
    image: BufferedImage,
    threshold: Double = MSSIMMatcher.DEFAULT_THRESHOLD,
) {
    val result = compareImages(expected, image, MSSIMMatcher(threshold))
    if (!result.matches) {
        throw AssertionError("Image does not match the expected image. ${result.statistics}")
    }
}

/**
 * Captures the matched component and asserts it matches [expected] by structural similarity at
 * [threshold], without involving any golden file.
 *
 * @param expected the reference image.
 * @param threshold the structural-similarity acceptance floor, where higher is stricter.
 * @throws AssertionError if the captured image differs from [expected] beyond the threshold, or if
 *   their sizes differ.
 */
public fun SwingNodeInteraction<*>.assertImageMatches(
    expected: BufferedImage,
    threshold: Double = MSSIMMatcher.DEFAULT_THRESHOLD,
) {
    val actual = captureToImage()
    val result = compareImages(expected, actual, MSSIMMatcher(threshold))
    if (!result.matches) {
        throw AssertionError("Image does not match the expected image. ${result.statistics}")
    }
}

/**
 * Asserts [image] matches [expected] pixel-for-pixel, allowing at most [maxDifferentPixels] differing
 * pixels (default `0`, i.e. byte-exact). Use this for the strictest comparisons - proving two
 * independently rendered components rasterize identically - where the structural-similarity tolerance
 * of [assertImageMatches] would be too lenient to catch a stray border, margin or alignment shift.
 *
 * @param expected the reference image.
 * @param image the image under test.
 * @param maxDifferentPixels the maximum number of pixels permitted to differ; `0` requires an exact
 *   match.
 * @throws AssertionError if the images differ in size, or differ in more than [maxDifferentPixels]
 *   pixels.
 * @throws IllegalArgumentException if [maxDifferentPixels] is negative.
 */
public fun ComposeSwingTest.assertImagesPixelPerfect(
    expected: BufferedImage,
    image: BufferedImage,
    maxDifferentPixels: Int = 0,
) {
    require(maxDifferentPixels >= 0) { "maxDifferentPixels must be >= 0 but was $maxDifferentPixels." }
    val result = compareImages(expected, image, PixelPerfectMatcher)
    if (result.matches) return
    val different = result.differentPixelCount
    if (different in 0..maxDifferentPixels) return
    throw AssertionError(
        "Images differ in $different pixel(s), more than the allowed $maxDifferentPixels. " +
            result.statistics,
    )
}

private val GOLDEN_IDENTIFIER_PATTERN = Regex("[A-Za-z0-9_-]+")

private fun requireValidGoldenIdentifier(goldenIdentifier: String) {
    require(GOLDEN_IDENTIFIER_PATTERN.matches(goldenIdentifier)) {
        "Golden identifier '$goldenIdentifier' is invalid; allowed characters are letters, digits, " +
            "'_' and '-'."
    }
}

/** Compares two images by [matcher], failing with a size message if their dimensions differ. */
internal fun compareImages(
    expected: BufferedImage,
    actual: BufferedImage,
    matcher: BitmapMatcher,
): MatchResult {
    if (expected.width != actual.width || expected.height != actual.height) {
        return MatchResult(
            matches = false,
            statistics =
                "Size mismatch: expected ${expected.width}x${expected.height}, " +
                    "actual ${actual.width}x${actual.height}",
            diff = null,
        )
    }
    return matcher.compare(
        expected.toArgbIntArray(),
        actual.toArgbIntArray(),
        expected.width,
        expected.height,
    )
}

/**
 * Loads a golden, compares against it, and handles the missing-golden and record-mode flows for a
 * single [goldenIdentifier].
 */
private class GoldenScreenshotComparator(
    private val goldenIdentifier: String,
    private val threshold: Double,
) {
    fun assertAgainstGolden(actual: BufferedImage) {
        val golden = loadGolden()
        if (golden == null) {
            handleMissingGolden(actual)
            return
        }
        val result = compareImages(golden, actual, MSSIMMatcher(threshold))
        if (result.matches) {
            if (updateGoldens()) writeGolden(actual, updated = true)
            return
        }
        if (updateGoldens()) {
            dumpResults(actual, golden, result.diff)
            throw AssertionError(
                "Image mismatch for golden '$goldenIdentifier'. ${result.statistics}. " +
                    "The golden was not overwritten; review the diff in $RESULTS_DIR before " +
                    "re-running with -D$UPDATE_PROPERTY=true.",
            )
        }
        dumpResults(actual, golden, result.diff)
        throw AssertionError(
            "Image mismatch for golden '$goldenIdentifier'. ${result.statistics}. " +
                "Wrote actual, expected and diff images to $RESULTS_DIR.",
        )
    }

    private fun handleMissingGolden(actual: BufferedImage) {
        if (updateGoldens()) {
            writeGolden(actual, updated = false)
            return
        }
        dumpResults(actual, expected = null, diff = null)
        throw AssertionError(
            "Missing golden '$goldenIdentifier'. Re-run with -D$UPDATE_PROPERTY=true to record it. " +
                "The captured image was written to $RESULTS_DIR.",
        )
    }

    private fun loadGolden(): BufferedImage? {
        val resource =
            Thread
                .currentThread()
                .contextClassLoader
                ?.getResourceAsStream("golden/$goldenIdentifier.png")
                ?: javaClass.classLoader?.getResourceAsStream("golden/$goldenIdentifier.png")
                ?: return null
        return resource.use { ImageIO.read(it) }
    }

    private fun writeGolden(
        image: BufferedImage,
        updated: Boolean,
    ) {
        val dir = File(GOLDEN_SOURCE_DIR)
        dir.mkdirs()
        val file = File(dir, "$goldenIdentifier.png")
        ImageIO.write(image, "png", file)
        val verb = if (updated) "updated" else "recorded"
        println("Screenshot golden $verb: ${file.absolutePath}")
    }

    private fun dumpResults(
        actual: BufferedImage,
        expected: BufferedImage?,
        diff: BufferedImage?,
    ) {
        val dir = File(RESULTS_DIR)
        dir.mkdirs()
        ImageIO.write(actual, "png", File(dir, "${goldenIdentifier}_actual.png"))
        expected?.let { ImageIO.write(it, "png", File(dir, "${goldenIdentifier}_expected.png")) }
        diff?.let { ImageIO.write(it, "png", File(dir, "${goldenIdentifier}_diff.png")) }
    }

    private fun updateGoldens(): Boolean = System.getProperty(UPDATE_PROPERTY)?.toBooleanStrictOrNull() == true

    private companion object {
        const val UPDATE_PROPERTY = "SCREENSHOT_TEST_UPDATE_GOLDENS"
        const val GOLDEN_SOURCE_DIR = "src/test/resources/golden"
        const val RESULTS_DIR = "build/screenshot-test-results"
    }
}
