package org.jetbrains.compose.swing.swingmark.harness

import androidx.tracing.Tracer

/** The category the harness records its own spans under, beside the library's and the composition's. */
internal const val HARNESS_CATEGORY: String = "swingmark"

/**
 * Runs [block] as one span of the harness's own work, named for what the suite is doing rather than for
 * the function that runs it.
 *
 * A span costs one category check while nothing is recording, which is what a timed run stands under.
 */
internal inline fun <T> traceHarness(
    name: String,
    block: () -> T,
): T {
    val span = beginHarnessSpan(name)
    try {
        return block()
    } finally {
        span.close()
    }
}

/**
 * Opens a span under [HARNESS_CATEGORY], or - while no trace is being captured - a closeable that does
 * nothing.
 *
 * The category check sits behind a call of its own rather than inside the work being named, so what a
 * span costs a run that is not being traced is one check and one call to a close that does nothing. A
 * run measures the allocation a change makes, and a harness that allocated per paint would be measuring
 * itself.
 */
internal fun beginHarnessSpan(name: String): AutoCloseable {
    val tracer = Tracer.global
    return if (tracer.isCategoryEnabled(HARNESS_CATEGORY)) {
        tracer.beginSection(HARNESS_CATEGORY, name, token = null, metadataBlock = {})
    } else {
        ClosedSpan
    }
}

/** Stands for a span that was never opened, because nothing is recording. */
private object ClosedSpan : AutoCloseable {
    override fun close(): Unit = Unit
}
