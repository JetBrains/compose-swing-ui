// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

/**
 * Counts the work of one toggle by metric name, with every metric present and zero until counted so that a missing
 * column cannot pass for no work.
 *
 * Producers outside the event dispatch thread add to it, so access is synchronized.
 */
internal class ToggleMetrics {
    private val counts = metricNames.associateWith { 0L }.toMutableMap()

    @Synchronized
    fun add(
        name: String,
        value: Long = 1,
    ) {
        counts[name] = counts.getOrDefault(name, 0L) + value
    }

    @Synchronized
    fun snapshot(): Map<String, Long> = counts.toSortedMap()

    companion object {
        val metricNames: List<String> =
            listOf(
                "compositions",
                "recomposedScopes",
                "invalidatedScopes",
                "stateReads",
                "revalidateRequests",
                "repaintRequests",
                "dirtyArea",
                "componentsInDirtyRegion",
                "layouts",
                "eventsPosted",
                "effectStarts",
                "componentsAdded",
                "listenersCreated",
                "compositionGroups",
                "compositionSlots",
            ) +
                listOf("frame", "apply", "insert", "attach", "remove", "move", "settle")
                    .map { "span.$it" }
    }
}
