package org.jetbrains.compose.swing.test.screenshot

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers a golden a comparison finds, one it does not, and what update mode changes about each.
 *
 * Every image here is drawn from filled rectangles rather than captured, so the comparisons say the
 * same thing whatever a platform rasterizes text to.
 */
class GoldenScreenshotTest {
    @Test
    fun anImageMatchingItsGoldenPassesAndWritesNothing() = runGoldenTest {
        val identifier = "matching-golden"
        val written = resultFiles(identifier) + goldenSource(identifier)
        written.forEach { it.delete() }

        withGoldenOnClasspath(identifier, scene()) {
            assertImageAgainstGolden(scene(), identifier)
        }

        written.forEach { assertFalse(it.exists(), "a comparison that matches leaves nothing behind: ${it.path}") }
    }

    @Test
    fun anImageDifferingFromItsGoldenFailsAndTheDifferenceIsWrittenOut() = runGoldenTest {
        val identifier = "mismatched-golden"
        val results = resultFiles(identifier)
        results.forEach { it.delete() }

        val failure =
            withGoldenOnClasspath(identifier, scene()) {
                assertFailsWith<AssertionError> { assertImageAgainstGolden(inverseScene(), identifier) }
            }

        assertTrue(
            failure.message.orEmpty().contains("Image mismatch for golden '$identifier'"),
            "the failure names the golden that did not match: ${failure.message}",
        )
        assertTrue(
            failure.message.orEmpty().contains("MSSIM:"),
            "and reports how similar the images were: ${failure.message}",
        )
        results.forEach {
            assertTrue(it.isFile, "the captured, expected and difference images are written out: ${it.path}")
        }
    }

    @Test
    fun aMissingGoldenFailsAndNothingIsRecorded() = runGoldenTest {
        val identifier = "unrecorded-golden"

        val failure = assertFailsWith<AssertionError> { assertImageAgainstGolden(scene(), identifier) }

        assertTrue(
            failure.message.orEmpty().contains("Missing golden '$identifier'"),
            "the failure names the golden that is not there: ${failure.message}",
        )
        assertFalse(goldenSource(identifier).exists(), "a golden is only recorded in update mode")
    }

    @Test
    fun updateModeRecordsAMissingGolden() = runGoldenTest {
        val identifier = "recorded-golden"
        val recorded = goldenSource(identifier)
        try {
            recordingGoldens { assertImageAgainstGolden(scene(), identifier) }

            assertRecorded(recorded, scene(), "update mode records the image it could not find a golden for")
        } finally {
            discard(recorded)
        }
    }

    @Test
    fun updateModeRefreshesAGoldenTheImageStillMatches() = runGoldenTest {
        val identifier = "refreshed-golden"
        val recorded = goldenSource(identifier)
        // Two pixels of drift stay within the default threshold: a match that is not identical, so the
        // recorded golden has to be the image just compared rather than the one it was compared against.
        val drifted = scene(elementX = 38)
        try {
            withGoldenOnClasspath(identifier, scene()) {
                recordingGoldens { assertImageAgainstGolden(drifted, identifier) }
            }

            assertRecorded(recorded, drifted, "a golden the image still matches is refreshed to what was compared")
        } finally {
            discard(recorded)
        }
    }

    @Test
    fun updateModeRefusesToOverwriteAGoldenTheImageNoLongerMatches() = runGoldenTest {
        val identifier = "refused-golden"
        val recorded = goldenSource(identifier)
        try {
            val failure =
                withGoldenOnClasspath(identifier, scene()) {
                    recordingGoldens {
                        assertFailsWith<AssertionError> { assertImageAgainstGolden(inverseScene(), identifier) }
                    }
                }

            assertTrue(
                failure.message.orEmpty().contains("The golden was not overwritten"),
                "update mode says it kept the golden it had: ${failure.message}",
            )
            assertFalse(recorded.exists(), "a mismatch is never accepted as the new baseline")
        } finally {
            discard(recorded)
        }
    }

    @Test
    fun aGoldenIdentifierNamingAFileTheStoreCannotHoldIsRejected() = runGoldenTest {
        assertFailsWith<IllegalArgumentException> { assertImageAgainstGolden(scene(), "bad name!") }
    }

    /** Asserts [recorded] holds [image] pixel for pixel. */
    private fun assertRecorded(
        recorded: File,
        image: BufferedImage,
        message: String,
    ) {
        assertTrue(recorded.isFile, message)
        assertContentEquals(image.toArgbIntArray(), ImageIO.read(recorded).toArgbIntArray(), message)
    }

    /** Runs [body] against a mounted composition, which the golden assertions are declared on. */
    private fun runGoldenTest(body: ComposeSwingTest.() -> Unit) = runComposeSwingTest {
        setContent { Label(text = "golden") }
        body()
    }

    /**
     * Makes [image] readable as the golden named [goldenIdentifier] for the duration of [body]. A
     * comparison reads its golden off the classpath, so this is what puts one there without checking
     * one in.
     */
    private fun <T> withGoldenOnClasspath(
        goldenIdentifier: String,
        image: BufferedImage,
        body: () -> T,
    ): T {
        val thread = Thread.currentThread()
        val standing = thread.contextClassLoader
        val root = Files.createTempDirectory("golden-store").toFile()
        try {
            val goldens = File(root, "golden").also { it.mkdirs() }
            ImageIO.write(image, "png", File(goldens, "$goldenIdentifier.png"))
            thread.contextClassLoader = URLClassLoader(arrayOf(root.toURI().toURL()), standing)
            return body()
        } finally {
            thread.contextClassLoader = standing
            root.deleteRecursively()
        }
    }

    /** Runs [body] in update mode, the mode that records a golden rather than only reading one. */
    private fun <T> recordingGoldens(body: () -> T): T {
        val standing = System.getProperty(UPDATE_PROPERTY)
        System.setProperty(UPDATE_PROPERTY, "true")
        try {
            return body()
        } finally {
            if (standing == null) {
                System.clearProperty(UPDATE_PROPERTY)
            } else {
                System.setProperty(UPDATE_PROPERTY, standing)
            }
        }
    }

    /** The file a recorded golden named [goldenIdentifier] is written to. */
    private fun goldenSource(goldenIdentifier: String) = File(GOLDEN_SOURCE_DIR, "$goldenIdentifier.png")

    /** The files a mismatch of [goldenIdentifier] is written out to. */
    private fun resultFiles(goldenIdentifier: String) =
        listOf("actual", "expected", "diff").map { File(RESULTS_DIR, "${goldenIdentifier}_$it.png") }

    /**
     * Removes a golden a test recorded, and the empty directories left holding it. Recording writes
     * into the test sources, which every other run expects to find as it left them.
     */
    private fun discard(recorded: File) {
        recorded.delete()
        File(GOLDEN_SOURCE_DIR).delete()
        File(GOLDEN_SOURCE_DIR).parentFile.delete()
    }

    /** A scene of one rectangle at [elementX] on a background, drawn the same way on every platform. */
    private fun scene(
        elementX: Int = 36,
        background: Color = Color(0xEE, 0xEE, 0xEE),
        element: Color = Color(0xD2, 0xD2, 0xD2),
    ): BufferedImage {
        val image = BufferedImage(88, 25, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = background
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = element
        graphics.fillRect(elementX, 10, 16, 5)
        graphics.dispose()
        return image
    }

    /** The scene in inverted colors, which is far enough from [scene] to fail any threshold. */
    private fun inverseScene(): BufferedImage =
        scene(background = Color(0x11, 0x11, 0x11), element = Color(0x2D, 0x2D, 0x2D))

    private companion object {
        const val UPDATE_PROPERTY = "SCREENSHOT_TEST_UPDATE_GOLDENS"
        const val GOLDEN_SOURCE_DIR = "src/test/resources/golden"
        const val RESULTS_DIR = "build/screenshot-test-results"
    }
}
