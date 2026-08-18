package org.jetbrains.compose.swing.preview.host

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.tooling.PreviewEnvironment
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.ServiceLoader
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.UIManager

/**
 * The most frames a settle will send before giving up and rasterizing what the composition holds.
 *
 * A preview that animates never reaches a quiescent state, so exhausting this budget is a legitimate
 * outcome rather than a failure: what it yields is an early frame of the animation, which is the same
 * thing every other preview renderer shows.
 */
private const val MAX_SETTLE_FRAMES = 1_000

/** Frame spacing for the settle clock. Only its monotonicity matters; nothing here runs in real time. */
private const val FRAME_NANOS = 16_000_000L

/**
 * One rendering: the composable it came from, the name the annotation that asked for it gave, the size
 * it was laid out at, and the raster.
 *
 * The raster is the layout scaled by whatever the run was asked to rasterize at, so [size] rather than
 * the image's own dimensions is the size the preview occupies.
 */
internal class RenderedPreview(
    val fqName: String,
    val name: String,
    val size: Dimension,
    val image: BufferedImage,
)

/** Why one composable produced no rendering, and which one. */
internal class FailedPreview(
    val fqName: String,
    val reason: String,
)

/** What a run produced: the renderings, and the composables that produced none. */
internal class PreviewRenderings(
    val rendered: List<RenderedPreview>,
    val failed: List<FailedPreview>,
)

/**
 * Renders every way each `@Preview`-annotated composable in [fqNames] asks to be rendered - a class
 * name and a method name, dot separated, where the class name of a top-level function is its file
 * facade (`…FooKt`).
 *
 * The environment prepares the process once, ahead of resolving any name, because it prepares Swing
 * and resolving a name loads the class that holds the composable.
 *
 * A composable that cannot be rendered is reported in place of its own rendering rather than ending the
 * run: the names come from one file, and one composable that throws would otherwise leave that file
 * with nothing to show.
 *
 * Every rendering is composed afresh, because a Swing component reads its defaults as it is
 * constructed: a look and feel one rendering asks for cannot be applied to a tree another already
 * built. For the same reason the look and feel the environment left installed is restored before a
 * rendering that asks for none, so what one rendering asks for does not carry into the next.
 *
 * [scale] is how many image pixels one layout pixel becomes. The layout itself is unaffected: a
 * rendering occupies the same size whatever it is rasterized at, and painting under the scale rather
 * than resampling afterwards is what keeps text and borders sharp on a display that has more pixels
 * than the layout does.
 *
 * [maxWidth] is the widest a rendering that states no width of its own may be laid out; zero leaves it
 * to the content alone. It is a limit rather than a size: content narrower than it keeps its own width,
 * and content that cannot be made to fit keeps the width it insists on.
 */
internal fun renderPreviews(
    fqNames: List<String>,
    scale: Float = 1f,
    maxWidth: Int = 0,
): PreviewRenderings =
    runBlocking {
        prepare(loadEnvironments())
        val resolved = fqNames.map { fqName -> fqName to attempt(fqName) { resolveRequests(fqName) } }
        withContext(Dispatchers.Swing) {
            val prepared = UIManager.getLookAndFeel()
            val rendered = mutableListOf<RenderedPreview>()
            val failed = mutableListOf<FailedPreview>()
            for ((fqName, requests) in resolved) {
                when (requests) {
                    is Outcome.Failed -> {
                        failed += FailedPreview(fqName, requests.reason)
                    }

                    is Outcome.Resolved -> {
                        for (request in requests.value) {
                            // Installing the look and feel belongs to the rendering that asked for it:
                            // one the platform cannot supply costs that rendering and no other.
                            val rasterized =
                                attempt(fqName) {
                                    installLookAndFeel(request.lookAndFeel, prepared)
                                    render(request, scale, maxWidth)
                                }
                            when (rasterized) {
                                is Outcome.Failed -> {
                                    failed += FailedPreview(fqName, rasterized.reason)
                                }

                                is Outcome.Resolved -> {
                                    val rendering = rasterized.value
                                    rendered += RenderedPreview(fqName, request.name, rendering.size, rendering.image)
                                }
                            }
                        }
                    }
                }
            }
            PreviewRenderings(rendered, failed)
        }
    }

private sealed interface Outcome<out T> {
    class Resolved<T>(
        val value: T,
    ) : Outcome<T>

    class Failed(
        val reason: String,
    ) : Outcome<Nothing>
}

/**
 * Runs [produce], keeping what it throws as the reason [fqName] produced nothing.
 *
 * Every type is contained because the code that throws is the previewed project's own: naming a
 * narrower set would leave whichever type went unnamed free to end the whole run, which is the outcome
 * this exists to prevent.
 */
@Suppress("TooGenericExceptionCaught")
private inline fun <T> attempt(
    fqName: String,
    produce: () -> T,
): Outcome<T> =
    try {
        Outcome.Resolved(produce())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Outcome.Failed(reasonFor(fqName, failure))
    }

/** The failure as one line: its own message, then each cause's, since a cause often carries the detail. */
private fun reasonFor(
    fqName: String,
    failure: Throwable,
): String =
    generateSequence(failure) { it.cause }
        .map { it.message?.takeIf(String::isNotBlank) ?: it.javaClass.name }
        .joinToString(" ")
        .replace('\t', ' ')
        .replace('\n', ' ')
        .ifBlank { "$fqName could not be rendered." }

/** The layout a preview came out at, and the raster of it. */
private class Rasterized(
    val size: Dimension,
    val image: BufferedImage,
)

/** Must run on the event dispatch thread. */
private suspend fun render(
    request: PreviewRequest,
    scale: Float,
    maxWidth: Int,
): Rasterized {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
    val recomposer = Recomposer(scope.coroutineContext)
    val failure = arrayOfNulls<Throwable>(1)
    scope.launch { recomposer.runUntilCancelled(failure) }

    // A component in an application sits on a panel, so a preview is painted on the one the installed
    // look and feel gives a panel. Left transparent, a rendering is whatever happens to be behind it
    // wherever it is shown - dark text on a dark editor. A bare look and feel states no such color.
    val background: Color? = UIManager.getColor("Panel.background")
    // A vertical box, so that a width the caller limits reaches the content: a box gives each child the
    // container's width, where a flow would hand every child its preferred width and lay it out past the
    // edge. Several components emitted side by side stack instead of flowing, which is what a preview
    // pane shows them as anyway.
    val root =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = background != null
            background?.let { this.background = it }
        }
    val handle = root.setContent(parent = recomposer) { request.method.invoke(currentComposer, request.receiver) }
    try {
        settle(clock, recomposer)
        failure[0]?.let { throw PreviewFailure("Composing '${request.method.asMethod().name}' failed.", it) }
        return Rasterized(layOut(root, request.size, maxWidth), rasterize(root, background, scale))
    } finally {
        handle.dispose()
        scope.cancel()
    }
}

/**
 * Drives the composition to a quiescent state: every published snapshot write has reached it and no
 * recomposition is outstanding. Returns early once that holds, and on exhausting [MAX_SETTLE_FRAMES]
 * returns anyway - see that constant.
 */
private suspend fun settle(
    clock: BroadcastFrameClock,
    recomposer: Recomposer,
) {
    var frameTimeNanos = 0L
    repeat(MAX_SETTLE_FRAMES) {
        Snapshot.sendApplyNotifications()
        if (!recomposer.hasPendingWork && !Snapshot.current.hasPendingChanges()) return
        // The recomposer recomposes and applies only from inside a frame, so a frame is what advances
        // it; the yield then lets it, and everything it schedules on the event queue, actually run.
        clock.sendFrame(frameTimeNanos)
        frameTimeNanos += FRAME_NANOS
        yield()
    }
}

/**
 * Recomposes until cancelled, keeping in [failure] whatever ended it.
 *
 * A throw from a node's update block, or from a listener a wrapper's own write provokes, ends
 * recomposition for good. Kept here it names the composition that stopped; left to escape it would
 * arrive as an uncaught exception on a coroutine with no request attached to it.
 *
 * Every type is contained because the code that throws is the previewed project's own: naming a
 * narrower set would leave whichever type went unnamed free to escape unreported.
 */
@Suppress("TooGenericExceptionCaught")
private suspend fun Recomposer.runUntilCancelled(failure: Array<Throwable?>) {
    try {
        runRecomposeAndApplyChanges()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (ended: Throwable) {
        failure[0] = ended
    }
}

/** Whatever the preview classpath offers to prepare the process with. */
internal fun loadEnvironments(): List<PreviewEnvironment> =
    ServiceLoader.load(PreviewEnvironment::class.java, PreviewEnvironment::class.java.classLoader).toList()

/**
 * Runs the one environment the preview classpath offers, before anything Swing is touched.
 *
 * Two environments are an error rather than a race: which of them ran would decide what every preview
 * on that classpath looks like, and neither the annotation nor the classpath states an order.
 */
internal fun prepare(environments: List<PreviewEnvironment>) {
    if (environments.size > 1) {
        throw PreviewFailure(
            "The preview classpath offers ${environments.size} PreviewEnvironments - " +
                environments.joinToString { it.javaClass.name } +
                " - and which of them prepared the process would decide what every preview looks like. " +
                "Leave one on the classpath.",
        )
    }
    val environment = environments.singleOrNull() ?: return
    try {
        environment.prepare()
    } catch (
        @Suppress("TooGenericExceptionCaught") failure: Throwable,
    ) {
        // Whatever an environment throws is the previewed project's own; contained here it is reported
        // as the reason there is no rendering, rather than escaping as an unexplained host crash.
        throw PreviewFailure("The preview environment ${environment.javaClass.name} failed to prepare.", failure)
    }
}
