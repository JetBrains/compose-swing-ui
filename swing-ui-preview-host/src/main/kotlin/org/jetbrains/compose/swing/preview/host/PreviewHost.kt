package org.jetbrains.compose.swing.preview.host

import java.io.File
import java.io.PrintStream
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/** A rendering that could not be produced, carrying a message meant to be read by whoever asked for it. */
internal class PreviewFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal const val EXIT_OK = 0
internal const val EXIT_USAGE = 2
internal const val EXIT_FAILED = 3

/** Names the file listing what was rendered, beside the renderings themselves. */
internal const val MANIFEST_NAME: String = "previews.tsv"

/** Names the file listing the composables that produced no rendering, and why. */
internal const val FAILURES_NAME: String = "failures.tsv"

/** The directory, the scale and the width limit, which come before any name of anything to render. */
private const val POSITIONAL_ARGUMENTS = 3

private const val USAGE = "usage: <outputDirectory> <scale> <maxWidthPx> <fqName>..."

/** Renders every way each named composable asks to be rendered, into a directory, and exits. */
public object PreviewHost {
    /**
     * Marks the point in the error stream after which everything belongs to one failure report, so a
     * caller can separate it from whatever the previewed code itself printed.
     */
    public const val FAILURE_MARKER: String = "<!--COMPOSE SWING PREVIEW FAILURE--!>"

    /**
     * Renders the previews named by `args[3]` onwards into the directory named by `args[0]`, rasterized
     * at the scale `args[1]` states and laid out no wider than `args[2]`.
     *
     * A class name and a method name, dot separated, name each one; the class name of a top-level
     * function is its file facade. A rendering is the size its own annotation asks for, and in whichever
     * dimension that states nothing, the size its content prefers.
     *
     * The scale is how many image pixels one layout pixel becomes - the pixel ratio of the display the
     * rendering is bound for, so that it is drawn at that display's own resolution rather than magnified
     * to it. It changes no rendering's size; the manifest states each in layout pixels either way.
     *
     * The width limit is how wide the space a rendering is shown in is, and zero is no limit. It caps
     * only a rendering whose annotation states no width of its own, and content narrower than it is
     * unaffected: it is what the content is laid out within, not what it is laid out at.
     *
     * Run this on a classpath holding the previewed project's own runtime dependencies together with
     * this host, so the composition it builds is the one that project's own code was compiled against.
     * Every name is rendered in this one process, because a JVM per composable would pay a cold start
     * for each.
     *
     * The renderings leave through files, and a failure report through the error stream after
     * [FAILURE_MARKER], because the composable is user code and user code prints. A name that produces
     * no rendering is written to the failures file beside them rather than ending the run, so one
     * broken composable does not cost a file its other previews; only a run that renders nothing at all
     * exits non-zero.
     *
     * Every type is contained because the code that throws is the previewed project's own: naming a
     * narrower set would leave whichever type went unnamed free to end the process with no report at
     * all, which the caller cannot tell apart from a crash of the host itself.
     */
    @Suppress("TooGenericExceptionCaught")
    @JvmStatic
    public fun main(args: Array<String>) {
        val exitCode = render(args, System.err)
        // The event dispatch thread outlives main once Swing has been touched, and a preview may have
        // left non-daemon threads of its own behind.
        exitProcess(exitCode)
    }

    /**
     * The whole of [main] except ending the process, so the host can be driven without spawning one -
     * by a test, or by a caller that has put this jar and a project's classpath in a class loader of
     * its own and accepts what that means for the process it runs in.
     *
     * Reachable reflectively across class loaders, which is why it takes and returns nothing but types
     * the platform itself defines.
     */
    @Suppress("TooGenericExceptionCaught")
    @JvmStatic
    public fun render(
        args: Array<String>,
        report: PrintStream,
    ): Int {
        val scale = args.getOrNull(1)?.toFloatOrNull()?.takeIf { it > 0f }
        val maxWidth = args.getOrNull(2)?.toIntOrNull()?.takeIf { it >= 0 }
        if (args.size <= POSITIONAL_ARGUMENTS || scale == null || maxWidth == null) {
            report.println(usageFor(args))
            return EXIT_USAGE
        }
        val directory = File(args[0])
        return try {
            val renderings = renderPreviews(args.drop(POSITIONAL_ARGUMENTS), scale, maxWidth)
            if (renderings.rendered.isEmpty()) {
                report.println(FAILURE_MARKER)
                report.println(renderings.failed.joinToString("\n") { "${it.fqName}: ${it.reason}" })
                EXIT_FAILED
            } else {
                write(renderings, directory)
                EXIT_OK
            }
        } catch (failure: Throwable) {
            report.println(FAILURE_MARKER)
            report.println(failure.stackTraceToString())
            EXIT_FAILED
        }
    }

    /**
     * Writes each rendering beside a manifest of one tab-separated line per rendering: its index, its
     * width, its height, the composable it came from, and last the name its annotation gave it, which
     * may be empty and is the only field that can hold arbitrary text.
     *
     * The width and the height are the rendering's own, in layout pixels. The image beside them holds
     * as many pixels per layout pixel as the run was asked to rasterize at, so a reader that wants the
     * scale reads it from the two.
     *
     * Whatever produced no rendering goes to a second file of the same shape: the composable, then the
     * reason. Both last fields are written on one line, since a tab or a newline in either would be
     * read as the end of a field or of a record.
     */
    private fun write(
        renderings: PreviewRenderings,
        directory: File,
    ) {
        directory.mkdirs()
        writeRenderings(renderings.rendered, directory)
        File(directory, FAILURES_NAME).writeText(
            renderings.failed.joinToString("") { "${it.fqName}\t${it.reason.oneLine()}\n" },
        )
    }

    private fun writeRenderings(
        rendered: List<RenderedPreview>,
        directory: File,
    ) {
        val manifest = StringBuilder()
        rendered.forEachIndexed { index, preview ->
            ImageIO.write(preview.image, "png", File(directory, "$index.png"))
            manifest
                .append(index)
                .append('\t')
                .append(preview.size.width)
                .append('\t')
                .append(preview.size.height)
                .append('\t')
                .append(preview.fqName)
                .append('\t')
                .append(preview.name.oneLine())
                .append('\n')
        }
        File(directory, MANIFEST_NAME).writeText(manifest.toString())
    }

    private fun String.oneLine(): String = replace('\t', ' ').replace('\n', ' ')

    /** The contract, and where what was given meets its shape but not its values, what was wrong. */
    private fun usageFor(args: Array<String>): String =
        when {
            args.size <= POSITIONAL_ARGUMENTS -> {
                USAGE
            }

            args[1].toFloatOrNull()?.takeIf { it > 0f } == null -> {
                "$USAGE - the scale is a positive number, and '${args[1]}' is not one"
            }

            else -> {
                "$USAGE - the width limit is a number of pixels or zero, and '${args[2]}' is neither"
            }
        }
}
