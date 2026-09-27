package org.jetbrains.compose.swing.preview.host

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAMPLES = "org.jetbrains.compose.swing.preview.host.PreviewSamplesKt"

/** One image pixel per layout pixel: what every case that is not about the scale renders at. */
private const val UNSCALED = "1"

/** No limit on how wide a rendering may be laid out: what every case that is not about width uses. */
private const val UNLIMITED = "0"

/**
 * Measures the argument contract, the output directory's shape and the exit codes the host answers
 * with, without spawning a process.
 *
 * `PreviewHostProcessTest` drives the same code as an IDE does, in a JVM of its own; this one is what
 * lets the individual outcomes be asserted, since a child process runs no coverage and returns only a
 * number.
 */
class PreviewHostTest {
    private val directory = createTempDirectory("compose-swing-preview-host").toFile()
    private val report = ByteArrayOutputStream()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `writes one image per rendering, beside a manifest naming each`() {
        val output = File(directory, "under/a/new/path")

        assertEquals(EXIT_OK, render(output.path, "$SAMPLES.RepeatedPreview"))

        assertEquals("", reported())
        assertNotNull(ImageIO.read(File(output, "0.png")), "the first rendering is not a readable image")
        assertNotNull(ImageIO.read(File(output, "1.png")), "the second rendering is not a readable image")
        assertEquals(
            listOf("0\t120\t40\t$SAMPLES.RepeatedPreview\tNarrow", "1\t400\t40\t$SAMPLES.RepeatedPreview\tWide"),
            manifestOf(output),
        )
    }

    @Test
    fun `renders every composable it is named, in one process, saying which is which`() {
        val output = File(directory, "several")

        assertEquals(EXIT_OK, render(output.path, "$SAMPLES.LabelPreview", "$SAMPLES.RepeatedPreview"))

        assertEquals(
            listOf("$SAMPLES.LabelPreview", "$SAMPLES.RepeatedPreview", "$SAMPLES.RepeatedPreview"),
            manifestOf(output).map { it.split('\t')[3] },
        )
    }

    @Test
    fun `names an unnamed rendering with the empty string rather than inventing one`() {
        val output = File(directory, "unnamed")

        assertEquals(EXIT_OK, render(output.path, "$SAMPLES.SizedPreview"))

        assertEquals(listOf("0\t320\t200\t$SAMPLES.SizedPreview\t"), manifestOf(output))
    }

    @Test
    fun `lays each rendering out at the size its own annotation states`() {
        val output = File(directory, "sized")

        assertEquals(EXIT_OK, render(output.path, "$SAMPLES.SizedPreview"))

        assertEquals(listOf("0\t320\t200"), manifestOf(output).map { it.split('\t').take(3).joinToString("\t") })
    }

    @Test
    fun `writes what rendered beside the reasons the rest did not`() {
        val output = File(directory, "partial")

        assertEquals(EXIT_OK, render(output.path, "$SAMPLES.EmptyPreview", "$SAMPLES.LabelPreview"))

        assertEquals(listOf("$SAMPLES.LabelPreview"), manifestOf(output).map { it.split('\t')[3] })
        val failure =
            File(output, FAILURES_NAME)
                .readText()
                .lines()
                .first()
                .split('\t')
        assertEquals("$SAMPLES.EmptyPreview", failure[0])
        assertTrue(failure[1].contains("widthPx and heightPx"), "got: ${failure[1]}")
    }

    @Test
    fun `reports a failure behind the marker and writes nothing`() {
        val output = File(directory, "absent")

        assertEquals(EXIT_FAILED, render(output.path, "$SAMPLES.AbsentPreview"))

        assertTrue(reported().startsWith(PreviewHost.FAILURE_MARKER), "got: ${reported()}")
        assertTrue(reported().contains("cannot take parameters"), "got: ${reported()}")
        assertTrue(
            reported().contains("$SAMPLES.AbsentPreview"),
            "the report must name the composable, got: ${reported()}",
        )
        assertTrue(!output.exists(), "a render that produced nothing must leave no directory behind")
    }

    @Test
    fun `rasterizes at the scale it is given, without changing the size it reports`() {
        val output = File(directory, "scaled")

        assertEquals(EXIT_OK, renderAt("2", output.path, "$SAMPLES.SizedPreview"))

        assertEquals(listOf("0\t320\t200"), manifestOf(output).map { it.split('\t').take(3).joinToString("\t") })
        val image = assertNotNull(ImageIO.read(File(output, "0.png")))
        assertEquals(640, image.width)
        assertEquals(400, image.height)
    }

    @Test
    fun `states its argument contract when named no composable at all`() {
        assertEquals(
            EXIT_USAGE,
            PreviewHost.render(arrayOf("a-directory", UNSCALED, UNLIMITED), PrintStream(report)),
        )

        assertTrue(reported().startsWith("usage:"), "got: ${reported()}")
    }

    @Test
    fun `refuses a width limit that is not a number of pixels`() {
        val arguments = arrayOf(File(directory, "unlimited").path, UNSCALED, "wide", "$SAMPLES.LabelPreview")

        assertEquals(EXIT_USAGE, PreviewHost.render(arguments, PrintStream(report)))

        assertTrue(reported().contains("'wide'"), "the report must name what it was given, got: ${reported()}")
    }

    @Test
    fun `lays content out no wider than the limit it is given`() {
        assertEquals(listOf(80), widthsOf("limited", "80", "$SAMPLES.WidePreview"))
    }

    @Test
    fun `leaves content narrower than the limit at its own width`() {
        val unlimited = widthsOf("unbounded", UNLIMITED, "$SAMPLES.NarrowPreview")

        assertEquals(unlimited, widthsOf("roomy", "4000", "$SAMPLES.NarrowPreview"))
    }

    @Test
    fun `leaves a rendering that states its own width to it, however little room there is`() {
        assertEquals(listOf(320), widthsOf("stated", "80", "$SAMPLES.SizedPreview"))
    }

    @Test
    fun `refuses a scale that is not a positive number`() {
        assertEquals(EXIT_USAGE, renderAt("none", File(directory, "unscaled").path, "$SAMPLES.LabelPreview"))

        assertTrue(reported().contains("'none'"), "the report must name what it was given, got: ${reported()}")
    }

    /** The width each rendering settled at, having been laid out within [maxWidth]. */
    private fun widthsOf(
        under: String,
        maxWidth: String,
        vararg fqNames: String,
    ): List<Int> {
        val output = File(directory, under)
        val arguments = arrayOf(output.path, UNSCALED, maxWidth) + fqNames
        assertEquals(EXIT_OK, PreviewHost.render(arguments, PrintStream(report)), "got: ${reported()}")
        return manifestOf(output).map { it.split('\t')[1].toInt() }
    }

    private fun render(vararg arguments: String): Int = renderAt(UNSCALED, *arguments)

    private fun renderAt(
        scale: String,
        vararg arguments: String,
    ): Int =
        PreviewHost.render(
            arrayOf(arguments.first(), scale, UNLIMITED) + arguments.drop(1),
            PrintStream(report),
        )

    // Lines are not trimmed: an unnamed rendering's line ends in the tab before its empty name, and
    // trimming it away would hide exactly the case these assertions are about.
    private fun manifestOf(directory: File): List<String> =
        File(directory, MANIFEST_NAME).readText().lines().filter { it.isNotEmpty() }

    private fun reported(): String = report.toString().trim()
}
