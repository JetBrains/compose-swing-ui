package org.jetbrains.compose.swing.preview.idea

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import javax.imageio.ImageIO
import javax.swing.UIManager

private const val HOST_MAIN_CLASS = "org.jetbrains.compose.swing.preview.host.PreviewHost"
private const val FAILURE_MARKER = "<!--COMPOSE SWING PREVIEW FAILURE--!>"
private const val MANIFEST_NAME = "previews.tsv"
private const val FAILURES_NAME = "failures.tsv"
private const val FAILURE_FIELDS = 2
private const val MANIFEST_FIELDS = 5
private const val RENDER_TIMEOUT_MS = 120_000

/** Turns on rendering inside the IDE process; see the key's own description for what that costs. */
private const val IN_PROCESS_KEY = "compose.swing.preview.in.process"

/**
 * One rendering, under the name the annotation that asked for it gave.
 *
 * [size] is what the preview occupies, in layout pixels. The raster holds as many pixels per layout
 * pixel as the display it was rendered for has, so it is the size that says how large to show it and
 * the raster that says how finely.
 */
internal class SwingRendering(
    val name: String,
    val size: Dimension,
    val image: BufferedImage,
)

/**
 * Everything one composable produced, under the label it is shown behind: its renderings, and the
 * reason it produced none or produced fewer than it asked for.
 */
internal class SwingPreviewGroup(
    val label: String,
    val renderings: List<SwingRendering>,
    val failure: String?,
)

/** What one render attempt produced: the renderings, grouped by composable, or why there are none. */
internal sealed interface SwingPreviewResult {
    class Rendered(val groups: List<SwingPreviewGroup>) : SwingPreviewResult

    class Failed(val report: String) : SwingPreviewResult
}

/**
 * Renders every target in [targets] by running the preview host in a JVM of its own, on [module]'s
 * runtime classpath, rasterizing at [scale] image pixels per layout pixel and laying out no wider than
 * [maxWidth] whatever states no width of its own.
 *
 * Out of process because the composable is the user's own code: on the IDE's event dispatch thread a
 * preview that loops would freeze the IDE, and the look and feel a preview asks for is process-global
 * state the IDE is already using for itself.
 *
 * One run covers every target and every rendering each of them asks for, because a JVM per rendering
 * would pay a cold start for each and a file usually holds several previews.
 *
 * Must not be called on the event dispatch thread; the host takes as long as a JVM takes to start.
 */
internal fun renderPreviews(
    module: Module,
    targets: List<SwingPreviewTarget>,
    scale: Float,
    maxWidth: Int,
): SwingPreviewResult {
    val output = FileUtil.createTempDirectory("compose-swing-preview", null)
    return try {
        val arguments =
            arrayOf(output.absolutePath, scale.toString(), maxWidth.toString()) + targets.map { it.jvmName }
        val failure =
            if (Registry.`is`(IN_PROCESS_KEY, false)) {
                renderInProcess(module, arguments)
            } else {
                renderInOwnProcess(module, arguments)
            }
        failure ?: renderingsIn(output, targets)
    } finally {
        FileUtil.delete(output)
    }
}

/**
 * Runs the host in a JVM of its own, and returns the failure to report, or `null` where it succeeded.
 *
 * This is the path that holds whatever the previewed code does away from the IDE: on the IDE's event
 * dispatch thread a preview that loops would freeze the IDE, and the look and feel a preview asks for
 * is process-global state the IDE is already using for itself.
 */
private fun renderInOwnProcess(
    module: Module,
    arguments: Array<String>,
): SwingPreviewResult.Failed? {
    // The classpath of a real module runs to hundreds of entries, past the command-line length every
    // platform imposes, so it is handed over in an argument file instead.
    val argumentFile = FileUtil.createTempFile("compose-swing-preview-classpath", ".args")
    return try {
        argumentFile.writeText("-classpath\n\"${classpathOf(module).replace("\\", "\\\\")}\"\n")
        val commandLine =
            GeneralCommandLine(javaExecutable(module))
                .withParameters("@${argumentFile.absolutePath}", "-Djava.awt.headless=true", HOST_MAIN_CLASS)
                .withParameters(*arguments)
        val result = CapturingProcessHandler(commandLine).runProcess(RENDER_TIMEOUT_MS)
        when {
            result.isTimeout ->
                SwingPreviewResult.Failed("The preview did not render within ${RENDER_TIMEOUT_MS / 1000} seconds.")
            result.exitCode != 0 -> SwingPreviewResult.Failed(reportFrom(result.stderr))
            else -> null
        }
    } finally {
        FileUtil.delete(argumentFile)
    }
}

/**
 * Runs the host inside the IDE, in a class loader of its own over the module's classpath, and returns
 * the failure to report, or `null` where it succeeded.
 *
 * Off by default and behind [IN_PROCESS_KEY], because it takes the two protections the separate process
 * exists for. The previewed code composes on the IDE's own event dispatch thread, so a preview that
 * loops or deadlocks takes the IDE with it. And a look and feel is process-global, so a preview that
 * installs one installs it for the IDE: the one the IDE was using is put back afterwards, which returns
 * every component built after that point but not those built while the preview held it.
 *
 * What it buys is the case the separate process cannot serve: a preview of IDE user interface, rendered
 * under the look and feel the IDE has already installed and that a bare JVM cannot construct.
 *
 * The class loader's parent is the platform's, not the IDE's, so `javax.swing` and the rest of the JDK
 * are shared while the module's own Kotlin, Compose runtime and library resolve to the versions that
 * module builds against rather than to the IDE's.
 */
private fun renderInProcess(
    module: Module,
    arguments: Array<String>,
): SwingPreviewResult.Failed? {
    val urls = classpathOf(module).split(File.pathSeparatorChar).map { File(it).toURI().toURL() }
    val report = ByteArrayOutputStream()
    val installed = UIManager.getLookAndFeel()
    return try {
        URLClassLoader(urls.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
            val host = loader.loadClass(HOST_MAIN_CLASS)
            val render = host.getMethod("render", Array<String>::class.java, PrintStream::class.java)
            val exitCode = render.invoke(null, arguments, PrintStream(report, true)) as Int
            if (exitCode == 0) null else SwingPreviewResult.Failed(reportFrom(report.toString()))
        }
    } catch (failure: ReflectiveOperationException) {
        SwingPreviewResult.Failed("The preview host could not be run inside the IDE: $failure")
    } finally {
        if (UIManager.getLookAndFeel() !== installed) UIManager.setLookAndFeel(installed)
    }
}

/**
 * Reads what the host wrote: one image per line of its manifest, each line stating the index, the
 * width, the height, the composable the rendering came from and last the name, which may be empty and
 * is the only field that can hold arbitrary text. A second file states, for each composable that
 * produced no rendering, its name and the reason.
 *
 * Both are grouped by the composable they came from and shown under the label [targets] gave it, so a
 * group carries the source's own name for it rather than the JVM name the host was handed. A group
 * keeps the order the host wrote, and the groups keep the order [targets] states.
 */
private fun renderingsIn(
    output: File,
    targets: List<SwingPreviewTarget>,
): SwingPreviewResult {
    val manifest = File(output, MANIFEST_NAME)
    if (!manifest.isFile) return SwingPreviewResult.Failed("The preview host wrote no manifest.")
    val byComposable =
        rows(manifest, MANIFEST_FIELDS)
            .mapNotNull { fields -> renderingIn(output, fields)?.let { fields[3] to it } }
            .groupBy({ it.first }, { it.second })
    val failures = rows(File(output, FAILURES_NAME), FAILURE_FIELDS).associate { it[0] to it[1] }
    val groups =
        targets.mapNotNull { target ->
            val renderings = byComposable[target.jvmName].orEmpty()
            val failure = failures[target.jvmName]
            if (renderings.isEmpty() && failure == null) {
                null
            } else {
                SwingPreviewGroup(target.label, renderings, failure)
            }
        }
    return if (groups.isEmpty()) {
        SwingPreviewResult.Failed("The preview host wrote no readable image.")
    } else {
        SwingPreviewResult.Rendered(groups)
    }
}

/**
 * One manifest row's rendering, or `null` where its image is missing or unreadable.
 *
 * The size comes from the row rather than from the image, because the image holds the display's pixels
 * and the row holds the layout's.
 */
private fun renderingIn(
    output: File,
    fields: List<String>,
): SwingRendering? {
    val width = fields[1].toIntOrNull() ?: return null
    val height = fields[2].toIntOrNull() ?: return null
    val image = ImageIO.read(File(output, "${fields[0]}.png")) ?: return null
    return SwingRendering(fields[4], Dimension(width, height), image)
}

/** The tab-separated rows of [file], dropping any the host did not write in full. */
private fun rows(
    file: File,
    fields: Int,
): List<List<String>> =
    if (!file.isFile) {
        emptyList()
    } else {
        file
            .readText()
            .lines()
            .filter { it.isNotEmpty() }
            .map { it.split('\t', limit = fields) }
            .filter { it.size == fields }
    }

/**
 * The failure the host reported, separated from whatever the previewed code itself printed on the same
 * stream. Everything before the marker is the previewed code's own output and is dropped.
 */
private fun reportFrom(errorOutput: String): String =
    errorOutput
        .substringAfter(FAILURE_MARKER, missingDelimiterValue = errorOutput)
        .trim()
        .ifEmpty { "The preview host failed without reporting why." }

/**
 * The module's own output together with everything it depends on, and the host jar shipped inside this
 * plugin. Test roots are included: a preview is as likely to sit beside the tests as beside the code.
 */
private fun classpathOf(module: Module): String {
    val moduleClasspath =
        OrderEnumerator
            .orderEntries(module)
            .recursively()
            .withoutSdk()
            .classes()
            .pathsList
    moduleClasspath.add(hostJarPath())
    return moduleClasspath.pathsString
}

private fun hostJarPath(): String {
    // Loaded without initializing it: this jar is on the plugin's class path only so that it can be
    // found here, and nothing in it is meant to run inside the IDE.
    val hostClass = Class.forName(HOST_MAIN_CLASS, false, SwingRendering::class.java.classLoader)
    return PathManager.getJarPathForClass(hostClass)
        ?: error("The preview host jar is missing from the plugin distribution.")
}

/**
 * The JVM to render with: the one the module is built against, so a preview runs under the same Java
 * version the application does. Falls back to the IDE's own runtime where the module states no SDK.
 */
private fun javaExecutable(module: Module): String {
    val home = ModuleRootManager.getInstance(module).sdk?.homePath ?: System.getProperty("java.home")
    return File(File(home, "bin"), if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").path
}
