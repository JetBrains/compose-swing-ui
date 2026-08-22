package org.jetbrains.compose.swing.swingmark.harness

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.tracing.Counter
import androidx.tracing.DelicateTracingApi
import androidx.tracing.EventMetadataCloseable
import androidx.tracing.ExperimentalContextPropagation
import androidx.tracing.PropagationToken
import androidx.tracing.Tracer
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink
import java.io.File

/** The category the composition's own spans are recorded under, beside the library's. */
private const val COMPOSITION_CATEGORY = "compose"

/** The extension the sink gives every trace it writes. */
private const val TRACE_SUFFIX = ".perfetto-trace"

/**
 * Records a run as a Perfetto trace, written into a directory of its own.
 *
 * Three sources reach the one trace. The library opens its own spans through the global [Tracer] - the
 * frame, the apply, a node arriving or leaving, a settle - the markers the Compose compiler leaves
 * around every restartable composable reach [CompositionTracer], which this turns into spans of the same
 * shape, and the harness names each painted frame at [PaintCounter]. So a frame reads as the composables
 * it recomposed, the widgets it applied to and the paint that followed, nested as they happened.
 *
 * Only the declared arm has pipeline spans to open: the raw arm builds widgets and calls setters, and
 * neither the library nor a composition is between them. Painting is named for both arms alike, since
 * both paint through the same repaint manager.
 *
 * A capture is installed once per process, so a run either records from its first frame or does not
 * record at all. [finish] ends the recording rather than taking that installation back.
 */
internal class TraceCapture private constructor(
    private val directory: File,
    private val driver: TraceDriver,
    private val tracer: StoppableTracer,
    private val before: Set<String>,
) {
    /**
     * Stops recording, flushes what is buffered, closes the file, and answers with the trace that was
     * written - or `null` where the sink left no file that was not already there.
     *
     * The packets are serialized off the thread that records them, so a run that ends without this
     * leaves a file holding whatever happened to have reached it. Recording stops before the flush, so
     * nothing opens a span against the driver while it is closing.
     */
    @OptIn(InternalComposeTracingApi::class)
    fun finish(): File? {
        tracer.stop()
        Composer.setTracer(null)
        driver.flush()
        driver.close()
        return (traceNames(directory) - before).singleOrNull()?.let { File(directory, it) }
    }

    companion object {
        /**
         * Starts recording into [directory], creating it if it is not there, and answers with the
         * capture that ends the recording.
         *
         * The sink names the file it writes itself, from the time it opened.
         */
        @OptIn(DelicateTracingApi::class)
        fun open(directory: File): TraceCapture {
            directory.mkdirs()
            val before = traceNames(directory)
            val driver = TraceDriver(sink = TraceSink(directory))
            val tracer = StoppableTracer(driver.tracer)
            // Installing a global tracer is delicate because it is process-wide and takes once. This
            // is that one installation, made before the suite builds its first screen.
            Tracer.setGlobalTracer(tracer)
            installCompositionSpans(tracer)
            return TraceCapture(directory, driver, tracer, before)
        }
    }
}

/** Hands the compiler's composable markers to [tracer], until [TraceCapture.finish] takes them back. */
@OptIn(InternalComposeTracingApi::class)
private fun installCompositionSpans(tracer: Tracer) {
    // Composer.setTracer is internal to the Compose runtime because a tracer sees every composable the
    // compiler marked. This module is published nowhere and pins the Compose version it composes
    // against, so nothing outside it depends on that shape holding.
    Composer.setTracer(CompositionSpans(tracer))
}

/** The traces already sitting in [directory], which tell the one this run writes from the rest. */
private fun traceNames(directory: File): Set<String> =
    directory.list { _, name -> name.endsWith(TRACE_SUFFIX) }?.toSet().orEmpty()

/**
 * Forwards to the tracer it was built over until [stop], and to the stub tracer after that.
 *
 * What a capture installs is what the process keeps, so a capture ends by stopping its own tracer rather
 * than by handing the global one back: every span opened afterwards reaches the stub and writes nothing.
 */
@OptIn(DelicateTracingApi::class, ExperimentalContextPropagation::class)
private class StoppableTracer(
    recording: Tracer,
) : Tracer() {
    // Written by the thread that ends the run, read by every thread that opens a span.
    @Volatile
    private var delegate: Tracer = recording

    fun stop() {
        delegate = Tracer.getStubTracer()
    }

    override fun tokenForManualPropagation(flowIds: List<Long>): PropagationToken =
        delegate.tokenForManualPropagation(flowIds)

    override fun tokenFromThreadContext(): PropagationToken = delegate.tokenFromThreadContext()

    override suspend fun tokenFromCoroutineContext(): PropagationToken = delegate.tokenFromCoroutineContext()

    override fun beginSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable = delegate.beginSectionWithMetadata(category, name, token, isRoot)

    override suspend fun beginCoroutineSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable = delegate.beginCoroutineSectionWithMetadata(category, name, token, isRoot)

    override fun isCategoryEnabled(category: String): Boolean = delegate.isCategoryEnabled(category)

    override fun counter(
        category: String,
        name: String,
    ): Counter = delegate.counter(category, name)

    override fun writeInstant(
        category: String,
        name: String,
        token: PropagationToken?,
    ): EventMetadataCloseable = delegate.writeInstant(category, name, token)
}

/**
 * Turns the markers the Compose compiler leaves around a restartable composable into spans.
 *
 * The runtime nests the markers and ends them by position rather than by name, so the open sections are
 * held on a stack. One stack per thread: a composition and the recompositions that follow it need not
 * run on the same one, and the runtime records without synchronizing.
 */
@OptIn(InternalComposeTracingApi::class)
private class CompositionSpans(
    private val tracer: Tracer,
) : CompositionTracer {
    private val open = ThreadLocal.withInitial { ArrayDeque<AutoCloseable>() }

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) {
        open.get().addLast(
            tracer.beginSection(COMPOSITION_CATEGORY, info, token = null, metadataBlock = {}),
        )
    }

    override fun traceEventEnd() {
        open.get().removeLastOrNull()?.close()
    }

    override fun isTraceInProgress(): Boolean = true
}
