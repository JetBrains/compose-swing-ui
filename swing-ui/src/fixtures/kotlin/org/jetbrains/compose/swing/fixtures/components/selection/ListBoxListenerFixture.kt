// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JList
import javax.swing.event.ListSelectionListener

/** The parameters [ListBoxListenerFixture] varies: the listener that observes selection changes. */
public data class ListBoxListenerParameters(
    val listSelectionListener: ListSelectionListener,
)

/**
 * A [ListBox] with the selection listener overload against a raw [JList] showing the same items. The items are the
 * ones of [ListBoxFixture.initial].
 */
public object ListBoxListenerFixture : ComposableFixture<ListBoxListenerParameters, JList<String>>(
    initial = ListBoxListenerParameters(listSelectionListener = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListBoxListenerParameters> =
        listOf(
            initial.copy(listSelectionListener = {}),
        )

    @Composable
    override fun Content(parameters: ListBoxListenerParameters): Unit = Content(parameters.listSelectionListener)

    /** Composes a [ListBox] showing the shared items, reporting selection changes to [listSelectionListener]. */
    @Composable
    public fun Content(listSelectionListener: ListSelectionListener) {
        ListBox(
            items = ListBoxFixture.initial.items,
            listSelectionListener = listSelectionListener,
        )
    }

    /** Builds a [JList] showing the shared items, before any of the fixture's values are applied. */
    override fun createRawSwing(): JList<String> = JList(ListBoxFixture.initial.items.toTypedArray())

    /**
     * Keeps the one selection listener this fixture added to [component] and has it pass events to the listener of
     * [parameters], leaving every other listener in place.
     */
    override fun updateRawSwing(
        component: JList<String>,
        parameters: ListBoxListenerParameters,
    ) {
        val adapter =
            component.listSelectionListeners.firstNotNullOfOrNull { it as? InstalledSelectionListener }
                ?: InstalledSelectionListener(parameters.listSelectionListener)
                    .also { component.addListSelectionListener(it) }
        adapter.delegate = parameters.listSelectionListener
    }
}
