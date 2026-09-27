package org.jetbrains.compose.swing.preview.host

import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val SAMPLES = "org.jetbrains.compose.swing.preview.host.PreviewSamplesKt"
private val PROCESS_DEADLINE = 2L to TimeUnit.MINUTES

/** One image pixel per layout pixel: what the two ways are compared at, since neither has a display. */
private const val UNSCALED = "1"

/** No limit on how wide a rendering may be: neither way is shown in a pane. */
private const val UNLIMITED = "0"

/**
 * Measures the two ways the host is driven against each other: loaded into the caller's own process in
 * a class loader of its own, and run in a JVM of its own.
 *
 * An IDE offers both, and the first is offered on the promise that it shows the same thing as the
 * second. A difference between them is a defect in that promise rather than in a test, so these cases
 * compare pixel for pixel and state no tolerance.
 *
 * The class loader is built exactly as the caller of the in-process path builds it - the module's whole
 * classpath over the *platform* loader, so the JDK is shared and everything else is not - because what
 * the arrangement resolves is the thing under measurement. A look and feel the classpath supplies is
 * included for that reason: it is the case where the two ways can differ.
 */
class PreviewHostFidelityTest {
    private val directory = createTempDirectory("compose-swing-preview-fidelity").toFile()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `a JDK look and feel renders the same in a class loader as in a process`() {
        assertRendersAlike("$SAMPLES.LabelPreview")
    }

    @Test
    fun `a look and feel the classpath supplies renders the same in a class loader as in a process`() {
        assertRendersAlike("$SAMPLES.ThemedPreview")
    }

    @Test
    fun `a file of several previews renders the same in a class loader as in a process`() {
        assertRendersAlike("$SAMPLES.ThemedPreview", "$SAMPLES.RepeatedPreview", "$SAMPLES.MetalPreview")
    }

    private fun assertRendersAlike(vararg fqNames: String) {
        val inLoader = renderInClassLoader(File(directory, "loader"), fqNames)
        val inProcess = renderInProcess(File(directory, "process"), fqNames)

        assertEquals(inProcess.size, inLoader.size, "the two ways produced different numbers of renderings")
        for (index in inProcess.indices) {
            assertAlike(inLoader[index], inProcess[index], "rendering $index of ${fqNames.toList()}")
        }
    }

    /**
     * Runs the host inside this process, in a class loader over this test's own classpath whose parent
     * is the platform's - the arrangement an IDE uses when it renders without spawning anything.
     *
     * The look and feel is put back afterwards because installing one is process-global, which is the
     * cost of this path and the reason the other one exists.
     */
    private fun renderInClassLoader(
        output: File,
        fqNames: Array<out String>,
    ): List<BufferedImage> {
        val urls =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparatorChar)
                .map { File(it).toURI().toURL() }
                .toTypedArray()
        val report = ByteArrayOutputStream()
        val installed = UIManager.getLookAndFeel()
        // The caller of this path is an IDE, and a look and feel is installed on the event dispatch
        // thread, whose context class loader in an IDE knows nothing of the previewed project. Standing
        // in for it here is what stops this case passing for a reason no IDE would supply.
        val callerLoader = contextClassLoaderOfEventDispatchThread()
        try {
            setContextClassLoaderOfEventDispatchThread(ClassLoader.getPlatformClassLoader())
            URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
                val host = loader.loadClass(PreviewHost::class.java.name)
                val render = host.getMethod("render", Array<String>::class.java, PrintStream::class.java)
                val arguments = arrayOf(output.path, UNSCALED, UNLIMITED) + fqNames
                val exitCode = render.invoke(null, arguments, PrintStream(report, true)) as Int
                assertEquals(EXIT_OK, exitCode, "the host failed in a class loader:\n$report")
            }
        } finally {
            setContextClassLoaderOfEventDispatchThread(callerLoader)
            if (UIManager.getLookAndFeel() !== installed) UIManager.setLookAndFeel(installed)
        }
        return imagesIn(output)
    }

    /**
     * Runs the host in a JVM of its own, told to be exactly as headless as this one and to stay out of
     * the desktop: what differs between the two runs has to be the class loading, not the display.
     */
    private fun renderInProcess(
        output: File,
        fqNames: Array<out String>,
    ): List<BufferedImage> {
        val java = File(File(System.getProperty("java.home"), "bin"), "java").path
        val process =
            ProcessBuilder(
                java,
                "-Djava.awt.headless=${GraphicsEnvironment.isHeadless()}",
                "-Dapple.awt.UIElement=true",
                "-classpath",
                System.getProperty("java.class.path"),
                PreviewHost::class.java.name,
                output.path,
                UNSCALED,
                UNLIMITED,
                *fqNames,
            ).start()
        val standardOutput = process.inputStream.bufferedReader().use { it.readText() }
        val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
        val (timeout, unit) = PROCESS_DEADLINE
        assertTrue(process.waitFor(timeout, unit), "the host did not exit within $timeout $unit")
        assertEquals(EXIT_OK, process.exitValue(), "the host failed in a process:\n$errorOutput$standardOutput")
        return imagesIn(output)
    }

    private fun contextClassLoaderOfEventDispatchThread(): ClassLoader? {
        val loader = arrayOfNulls<ClassLoader>(1)
        SwingUtilities.invokeAndWait { loader[0] = Thread.currentThread().contextClassLoader }
        return loader[0]
    }

    private fun setContextClassLoaderOfEventDispatchThread(loader: ClassLoader?) {
        SwingUtilities.invokeAndWait { Thread.currentThread().contextClassLoader = loader }
    }

    /** The renderings in the order the manifest states them. */
    private fun imagesIn(output: File): List<BufferedImage> =
        File(output, MANIFEST_NAME)
            .readText()
            .lines()
            .filter { it.isNotEmpty() }
            .map { line ->
                val index = line.substringBefore('\t')
                assertNotNull(ImageIO.read(File(output, "$index.png")), "$index.png is not a readable image")
            }

    private fun assertAlike(
        inLoader: BufferedImage,
        inProcess: BufferedImage,
        what: String,
    ) {
        assertEquals(inProcess.width to inProcess.height, inLoader.width to inLoader.height, "$what differs in size")
        val differing =
            (0 until inProcess.height)
                .flatMap { y -> (0 until inProcess.width).map { x -> x to y } }
                .filter { (x, y) -> inLoader.getRGB(x, y) != inProcess.getRGB(x, y) }
        if (differing.isNotEmpty()) {
            fail(
                "$what differs at ${differing.size} of ${inProcess.width * inProcess.height} pixels, " +
                    "first at ${differing.first()}",
            )
        }
    }
}
