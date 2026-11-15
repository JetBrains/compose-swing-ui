// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalComposeRuntimeApi::class)

package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe

/**
 * Counts the compositions, recomposed scopes, state reads and invalidated scopes of the compositions that [recomposer]
 * registers. Each event adds one to the metric of its kind in the [ToggleMetrics] that [metrics] returns at that
 * moment, and counts nothing while it returns null.
 *
 * Observation starts at construction and ends at [close], which removes the registration and detaches the observers
 * from the compositions. Construct and close it on the event thread, where the recomposer composes.
 *
 * @param recomposer the recomposer whose compositions are observed.
 * @param metrics returns the destination of the counts, read on every event.
 */
internal class CompositionMetrics(
    recomposer: Recomposer,
    private val metrics: () -> ToggleMetrics?,
) : CompositionObserver,
    AutoCloseable {
    private val handles = mutableMapOf<ObservableComposition, CompositionObserverHandle>()
    private val registration =
        recomposer.observe(
            object : CompositionRegistrationObserver {
                override fun onCompositionRegistered(composition: ObservableComposition) {
                    handles[composition] = composition.setObserver(this@CompositionMetrics)
                }

                override fun onCompositionUnregistered(composition: ObservableComposition) {
                    handles.remove(composition)?.dispose()
                }
            },
        )

    override fun onBeginComposition(composition: ObservableComposition) {
        metrics()?.add("compositions")
    }

    override fun onScopeEnter(scope: RecomposeScope) {
        metrics()?.add("recomposedScopes")
    }

    override fun onReadInScope(
        scope: RecomposeScope,
        value: Any,
    ) {
        metrics()?.add("stateReads")
    }

    override fun onScopeInvalidated(
        scope: RecomposeScope,
        value: Any?,
    ) {
        metrics()?.add("invalidatedScopes")
    }

    override fun onScopeExit(scope: RecomposeScope): Unit = Unit

    override fun onEndComposition(composition: ObservableComposition): Unit = Unit

    override fun onScopeDisposed(scope: RecomposeScope): Unit = Unit

    override fun close() {
        registration.dispose()
        handles.values.toList().forEach { it.dispose() }
        handles.clear()
    }
}

/**
 * The number of composition groups and of slots in them across every tree of [data], keyed `compositionGroups` and
 * `compositionSlots`.
 */
internal fun compositionSize(data: Iterable<CompositionData>): Map<String, Long> {
    var groups = 0L
    var slots = 0L

    fun visit(parent: CompositionData) {
        parent.compositionGroups.forEach { group ->
            groups++
            slots += group.data.count()
            visit(group)
        }
    }
    data.forEach(::visit)
    return mapOf("compositionGroups" to groups, "compositionSlots" to slots)
}
