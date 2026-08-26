package org.jetbrains.compose.swing.core

import androidx.tracing.Tracer

/** The trace category every section this library opens is recorded under. */
internal const val TRACE_CATEGORY: String = "org.jetbrains.compose.swing"

/**
 * Runs [block] as one section of this library's own work: a stretch of the pipeline a declared change is
 * carried through, named for what that stretch is rather than for the function that runs it.
 *
 * A section costs one category check while no tracer is installed, which is what the library runs under
 * until something installs one.
 */
internal inline fun <T> trace(
    name: String,
    block: () -> T,
): T {
    val section = beginSection(name)
    try {
        return block()
    } finally {
        section.close()
    }
}

/**
 * Opens the section [trace] closes, or - while the category is off - a closeable that does nothing.
 *
 * Kept out of [trace]'s inline body so that what a section adds to the function it brackets is one call and
 * one close, whatever opening a section involves. That is what lets a section sit on work a pass repeats per
 * widget without changing how the code around it optimizes.
 */
internal fun beginSection(name: String): AutoCloseable {
    val tracer = Tracer.global
    return if (tracer.isCategoryEnabled(TRACE_CATEGORY)) {
        tracer.beginSection(TRACE_CATEGORY, name, token = null, metadataBlock = {})
    } else {
        ClosedSection
    }
}

/** Stands for a section that was never opened, because its category is off. */
private object ClosedSection : AutoCloseable {
    override fun close(): Unit = Unit
}
