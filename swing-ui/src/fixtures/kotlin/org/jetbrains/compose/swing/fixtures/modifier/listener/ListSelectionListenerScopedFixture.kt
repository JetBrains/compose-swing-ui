// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.reflect.KClass

/** The parameters [ListSelectionListenerScopedFixture] varies: the target list type and the receiver callback. */
public data class ListSelectionListenerScopedParameters(
    val targetType: KClass<out JList<*>>,
    val onSelectionChange: JList<*>.(ListSelectionEvent) -> Unit,
)

/**
 * A [SwingModifier.listSelectionListener] scoped to a list type, with the list as receiver, against a raw [JList]
 * with the same callback added by hand.
 */
public object ListSelectionListenerScopedFixture :
    ComposableFixture<ListSelectionListenerScopedParameters, JList<String>>(
        initial = ListSelectionListenerScopedParameters(targetType = JList::class, onSelectionChange = {}),
    ) {
    /** A list subclass that the node is built from, so that either target type accepts its events. */
    private class ScopedSelectionList : JList<String>(arrayOf("Before", "After"))

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListSelectionListenerScopedParameters> =
        listOf(
            initial.copy(targetType = ScopedSelectionList::class),
            initial.copy(onSelectionChange = {}),
        )

    @Composable
    override fun Content(parameters: ListSelectionListenerScopedParameters): Unit =
        Content(parameters.targetType, parameters.onSelectionChange)

    /** Composes a list whose selection callback runs with the list as receiver for nodes of [targetType]. */
    @Composable
    public fun Content(
        targetType: KClass<out JList<*>>,
        onSelectionChange: JList<*>.(ListSelectionEvent) -> Unit,
    ) {
        SwingNode<JList<String>>(
            factory = { ScopedSelectionList() },
            modifier =
                SwingModifier.listSelectionListener(
                    targetType = targetType,
                    onSelectionChange = onSelectionChange,
                ),
        )
    }

    /** Builds a bare [JList] with the same items and no selection callback attached yet. */
    override fun createRawSwing(): JList<String> = JList(arrayOf("Before", "After"))

    /**
     * Adds the selection listener on the first call; a later call with the same target type only replaces its
     * callback with that of [parameters], and one with another target type replaces the listener.
     */
    override fun updateRawSwing(
        component: JList<String>,
        parameters: ListSelectionListenerScopedParameters,
    ) {
        val existing = component.listSelectionListeners.firstNotNullOfOrNull { it as? ScopedRelay }
        if (existing != null && existing.targetType == parameters.targetType) {
            existing.onSelectionChange = parameters.onSelectionChange
            return
        }
        if (existing != null) component.removeListSelectionListener(existing)
        component.addListSelectionListener(
            ScopedRelay(component, parameters.targetType, parameters.onSelectionChange),
        )
    }
}

/** The selection listener [ListSelectionListenerScopedFixture] installs on its raw list, so it finds only its own. */
private class ScopedRelay(
    private val list: JList<*>,
    val targetType: KClass<out JList<*>>,
    var onSelectionChange: JList<*>.(ListSelectionEvent) -> Unit,
) : ListSelectionListener {
    override fun valueChanged(event: ListSelectionEvent) {
        list.onSelectionChange(event)
    }
}
