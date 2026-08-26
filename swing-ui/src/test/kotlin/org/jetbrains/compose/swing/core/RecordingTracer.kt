package org.jetbrains.compose.swing.core

import androidx.tracing.Counter
import androidx.tracing.DelicateTracingApi
import androidx.tracing.EventMetadataCloseable
import androidx.tracing.ExperimentalContextPropagation
import androidx.tracing.PropagationToken
import androidx.tracing.PropagationUnsupportedToken
import androidx.tracing.Tracer

/**
 * One section this library opened: its [name], and the names of the sections that were still open on the same
 * thread when it opened, outermost first.
 */
internal class RecordedSection(
    val name: String,
    val enclosing: List<String>,
) {
    override fun toString(): String = enclosing.joinToString(separator = " > ", postfix = " > $name")
}

/**
 * A [Tracer] that keeps what this library reports, so a test can state which stretches of a pass were
 * named and how those names nest.
 *
 * Only [TRACE_CATEGORY] is recorded: a test states what the pipeline reports, and a category some other
 * component opens is not that.
 *
 * The enclosing names are taken as each section opens rather than reconstructed afterwards, so sections opened
 * on different threads never read as one another's parents.
 */
@OptIn(DelicateTracingApi::class, ExperimentalContextPropagation::class)
internal class RecordingTracer : Tracer() {
    private val recorded = mutableListOf<RecordedSection>()
    private val open = ThreadLocal.withInitial { ArrayDeque<String>() }

    /** The sections opened since the last [clear], in the order they opened. */
    val sections: List<RecordedSection> get() = synchronized(recorded) { recorded.toList() }

    /**
     * Drops what has been recorded, leaving later passes to be read on their own.
     *
     * The sections still open are left standing, so a section opened after a clear taken while a pass was
     * running still names that pass among its enclosing ones - and [passes] has no entry to attribute it to.
     * Take the anchor between passes, which is where a settled composition sits.
     */
    fun clear(): Unit = synchronized(recorded) { recorded.clear() }

    /**
     * The churn each change pass drove, in the order the passes ran: one entry per pass, holding the names
     * of the sections opened inside it.
     *
     * A pass that only wrote widget properties is an empty entry, which is what tells it apart from a pass
     * that took a component in or out of a container. Sections opened outside a pass - the frame a pass is
     * part of - belong to no entry.
     */
    fun passes(): List<List<String>> {
        val churn = mutableListOf<MutableList<String>>()
        for (section in sections) {
            when {
                section.name == APPLY_SECTION -> churn += mutableListOf<String>()
                APPLY_SECTION in section.enclosing -> churn.lastOrNull()?.add(section.name)
            }
        }
        return churn
    }

    override fun isCategoryEnabled(category: String): Boolean = category == TRACE_CATEGORY

    override fun beginSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable {
        val stack = open.get()
        synchronized(recorded) { recorded += RecordedSection(name, stack.toList()) }
        stack.addLast(name)
        return EventMetadataCloseable(closeable = { stack.removeLastOrNull() })
    }

    override suspend fun beginCoroutineSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable =
        beginSectionWithMetadata(category = category, name = name, token = token, isRoot = isRoot)

    override fun writeInstant(
        category: String,
        name: String,
        token: PropagationToken?,
    ): EventMetadataCloseable = EventMetadataCloseable()

    override fun counter(
        category: String,
        name: String,
    ): Counter = SilentCounter(name)

    override fun tokenFromThreadContext(): PropagationToken = PropagationUnsupportedToken

    override suspend fun tokenFromCoroutineContext(): PropagationToken = PropagationUnsupportedToken

    override fun tokenForManualPropagation(flowIds: List<Long>): PropagationToken = PropagationUnsupportedToken
}

/** The section the appliers open around one change pass, which is what [RecordingTracer.passes] groups on. */
private const val APPLY_SECTION: String = "apply"

/** A counter that keeps nothing: this library emits none, and a tracer still has to answer for one. */
private class SilentCounter(
    private val name: String,
) : Counter {
    override fun name(): String = name

    override fun setValue(value: Long): Unit = Unit

    override fun setValue(value: Double): Unit = Unit
}
