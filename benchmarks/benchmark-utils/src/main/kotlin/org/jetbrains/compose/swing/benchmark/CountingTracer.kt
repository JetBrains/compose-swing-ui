// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.tracing.Counter
import androidx.tracing.DelicateTracingApi
import androidx.tracing.EventMetadataCloseable
import androidx.tracing.ExperimentalContextPropagation
import androidx.tracing.PropagationToken
import androidx.tracing.PropagationUnsupportedToken
import androidx.tracing.Tracer

/**
 * Counts the library's `androidx.tracing` spans into the active [ToggleMetrics], one column per span name.
 *
 * The library emits spans through the global tracer, so counting them needs a tracer of its own. It ignores every other
 * category because unrelated code shares the global tracer, and it records nothing else because the metrics only need
 * how often each span ran.
 *
 * @param metrics returns the metrics that receive the counts, or null when no toggle is being measured.
 */
@OptIn(DelicateTracingApi::class, ExperimentalContextPropagation::class)
internal class CountingTracer(
    private val metrics: () -> ToggleMetrics?,
) : Tracer() {
    override fun isCategoryEnabled(category: String): Boolean = category == "org.jetbrains.compose.swing"

    override fun beginSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable {
        if (isCategoryEnabled(category)) metrics()?.add("span.$name")
        return EventMetadataCloseable()
    }

    override suspend fun beginCoroutineSectionWithMetadata(
        category: String,
        name: String,
        token: PropagationToken?,
        isRoot: Boolean,
    ): EventMetadataCloseable = beginSectionWithMetadata(category, name, token, isRoot)

    override fun writeInstant(
        category: String,
        name: String,
        token: PropagationToken?,
    ): EventMetadataCloseable = EventMetadataCloseable()

    override fun counter(
        category: String,
        name: String,
    ): Counter = UnusedCounter(name)

    override fun tokenFromThreadContext(): PropagationToken = PropagationUnsupportedToken

    override suspend fun tokenFromCoroutineContext(): PropagationToken = PropagationUnsupportedToken

    override fun tokenForManualPropagation(flowIds: List<Long>): PropagationToken = PropagationUnsupportedToken
}
