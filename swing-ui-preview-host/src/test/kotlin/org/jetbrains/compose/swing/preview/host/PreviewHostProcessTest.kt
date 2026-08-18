package org.jetbrains.compose.swing.preview.host

import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAMPLES = "org.jetbrains.compose.swing.preview.host.PreviewSamplesKt"
private const val EXIT_USAGE = 2
private const val EXIT_FAILED = 3
private val PROCESS_DEADLINE = 2L to TimeUnit.MINUTES

/**
 * Drives the host the way an IDE does: a separate JVM, given a classpath and a file to write, whose
 * result is read back from disk.
 *
 * This test's own classpath stands in for the previewed project's runtime classpath - it holds the
 * library, the Compose runtime and the sample previews, which is exactly what an IDE assembles from a
 * module's dependencies.
 */
class PreviewHostProcessTest {
    private val outputDirectory = createTempDirectory("compose-swing-preview").toFile()

    @AfterTest
    fun tearDown() {
        outputDirectory.deleteRecursively()
    }

    @Test
    fun `writes the renderings into the directory it is given`() {
        val output = File(outputDirectory, "nested/label")

        val result = runHost(output.path, "$SAMPLES.LabelPreview")

        assertEquals(0, result.exitCode, "the host failed:\n${result.errorOutput}")
        val image = assertNotNull(ImageIO.read(File(output, "0.png")), "the host wrote no readable image")
        assertTrue(image.width > 0 && image.height > 0)
        assertTrue(File(output, MANIFEST_NAME).exists(), "the host wrote no manifest")
    }

    @Test
    fun `renders a whole file whose previews do not all succeed`() {
        val output = File(outputDirectory, "whole-file")

        // Every top-level preview in the samples file, the way the IDE hands over a file's previews.
        // Three of them cannot be rendered by design, which is what this case is about: a file is not
        // written by one author at one moment, and the ones that work must survive the ones that do not.
        val result =
            runHost(
                output.path,
                "$SAMPLES.LabelPreview",
                "$SAMPLES.EmptyPreview",
                "$SAMPLES.UnannotatedPreview",
                "$SAMPLES.AbsentLookAndFeelPreview",
                "$SAMPLES.RepeatedPreview",
            )

        assertEquals(0, result.exitCode, "the host failed:\n${result.errorOutput}")
        assertEquals(
            listOf("$SAMPLES.LabelPreview", "$SAMPLES.RepeatedPreview", "$SAMPLES.RepeatedPreview"),
            File(output, MANIFEST_NAME)
                .readText()
                .lines()
                .filter { it.isNotEmpty() }
                .map { it.split('\t')[3] },
        )
        assertEquals(
            listOf("$SAMPLES.EmptyPreview", "$SAMPLES.UnannotatedPreview", "$SAMPLES.AbsentLookAndFeelPreview"),
            File(output, FAILURES_NAME)
                .readText()
                .lines()
                .filter { it.isNotEmpty() }
                .map { it.split('\t')[0] },
        )
    }

    @Test
    fun `writes one image per rendering a composable asks for`() {
        val output = File(outputDirectory, "repeated")

        val result = runHost(output.path, "$SAMPLES.RepeatedPreview")

        assertEquals(0, result.exitCode, "the host failed:\n${result.errorOutput}")
        assertNotNull(ImageIO.read(File(output, "0.png")))
        assertNotNull(ImageIO.read(File(output, "1.png")))
        assertEquals(
            2,
            File(output, MANIFEST_NAME)
                .readText()
                .trim()
                .lines()
                .size,
        )
    }

    @Test
    fun `lays the rendering out at the size its annotation states`() {
        val output = File(outputDirectory, "sized")

        val result = runHost(output.path, "$SAMPLES.SizedPreview")

        assertEquals(0, result.exitCode, "the host failed:\n${result.errorOutput}")
        val image = assertNotNull(ImageIO.read(File(output, "0.png")))
        assertEquals(320, image.width)
        assertEquals(200, image.height)
    }

    @Test
    fun `reports a failure behind the marker and writes no file`() {
        val output = File(outputDirectory, "absent")

        val result = runHost(output.path, "$SAMPLES.AbsentPreview")

        assertEquals(EXIT_FAILED, result.exitCode)
        assertTrue(
            result.errorOutput.contains(PreviewHost.FAILURE_MARKER),
            "expected the report to be marked, got:\n${result.errorOutput}",
        )
        assertTrue(
            result.errorOutput.substringAfter(PreviewHost.FAILURE_MARKER).contains("cannot take parameters"),
            "expected the failure itself to follow the marker, got:\n${result.errorOutput}",
        )
        assertTrue(!output.exists(), "a failed render must leave no directory behind")
    }

    @Test
    fun `whatever the preview prints stays out of the failure report`() {
        val output = File(outputDirectory, "noisy")

        val result = runHost(output.path, "$SAMPLES.PrintingPreview")

        assertEquals(0, result.exitCode, "the host failed:\n${result.errorOutput}")
        assertTrue(
            result.errorOutput.contains(PreviewHost.FAILURE_MARKER).not(),
            "a successful render must report no failure, got:\n${result.errorOutput}",
        )
        assertNotNull(ImageIO.read(File(output, "0.png")), "what the preview printed corrupted the image")
    }

    @Test
    fun `states its argument contract when called with the wrong number of arguments`() {
        val result = runHost("only-a-directory")

        assertEquals(EXIT_USAGE, result.exitCode)
        assertTrue(result.errorOutput.contains("usage:"), "got:\n${result.errorOutput}")
    }

    private class HostResult(
        val exitCode: Int,
        val errorOutput: String,
    )

    /**
     * Renders at one image pixel per layout pixel and within no width limit: nothing here is about how
     * finely a size is drawn, or about the room a preview is shown in.
     */
    private fun runHost(vararg arguments: String): HostResult {
        val java = File(File(System.getProperty("java.home"), "bin"), "java").path
        val process =
            ProcessBuilder(
                java,
                "-Djava.awt.headless=true",
                "-classpath",
                System.getProperty("java.class.path"),
                PreviewHost::class.java.name,
                arguments.first(),
                "1",
                "0",
                *arguments.drop(1).toTypedArray(),
            ).redirectErrorStream(false)
                .start()
        // Read both streams to completion before waiting: a full pipe buffer blocks the host itself.
        val standardOutput = process.inputStream.bufferedReader().use { it.readText() }
        val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
        val (timeout, unit) = PROCESS_DEADLINE
        assertTrue(process.waitFor(timeout, unit), "the host did not exit within $timeout $unit")
        assertTrue(
            standardOutput.none { it.code == 0x89 },
            "the host wrote image bytes to standard output",
        )
        return HostResult(process.exitValue(), errorOutput)
    }
}
