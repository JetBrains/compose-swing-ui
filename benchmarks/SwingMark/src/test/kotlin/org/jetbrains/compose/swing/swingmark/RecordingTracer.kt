package org.jetbrains.compose.swing.swingmark

import androidx.tracing.Counter
import androidx.tracing.DelicateTracingApi
import androidx.tracing.EventMetadataCloseable
import androidx.tracing.ExperimentalContextPropagation
import androidx.tracing.PropagationToken
import androidx.tracing.PropagationUnsupportedToken
import androidx.tracing.Tracer
import org.jetbrains.compose.swing.swingmark.harness.HARNESS_CATEGORY

/**
 * A [Tracer] that keeps the names the harness records under [HARNESS_CATEGORY], so a test can state what
 * a run reports of its own work.
 */
@OptIn(DelicateTracingApi::class, ExperimentalContextPropagation::class)
internal class RecordingTracer : Tracer() {
    private val recorded = mutableListOf<String>()

    /** The spans opened so far, in the order they opened. */
    val spans: List<String> get() = synchronized(recorded) { recorded.toList() }

    override fun isCategoryEnabled(category: String): Boolean = category == HARNESS_CATEGORY

    override fun beginSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable {
        synchronized(recorded) { recorded += name }
        return EventMetadataCloseable()
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

/** A counter that keeps nothing: the harness emits none, and a tracer still has to answer for one. */
private class SilentCounter(
    private val name: String,
) : Counter {
    override fun name(): String = name

    override fun setValue(value: Long): Unit = Unit

    override fun setValue(value: Double): Unit = Unit
}
