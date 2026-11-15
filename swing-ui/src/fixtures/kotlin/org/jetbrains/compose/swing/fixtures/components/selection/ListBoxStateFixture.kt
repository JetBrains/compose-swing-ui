// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.rememberListState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JList

/** The parameters [ListBoxStateFixture] varies: the rows the list state starts with selected. */
public data class ListBoxStateParameters(
    val selectedIndices: Set<Int>,
)

/**
 * A [ListBox] driven by a list state against a raw [JList] selecting the same rows. The items are the ones of
 * [ListBoxFixture.initial].
 */
public object ListBoxStateFixture : ComposableFixture<ListBoxStateParameters, JList<String>>(
    initial = ListBoxStateParameters(selectedIndices = setOf(0)),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListBoxStateParameters> =
        listOf(
            initial.copy(selectedIndices = setOf(1)),
        )

    @Composable
    override fun Content(parameters: ListBoxStateParameters): Unit = Content(parameters.selectedIndices)

    /** Composes a [ListBox] over the shared items with a state that starts with [selectedIndices] selected. */
    @Composable
    public fun Content(selectedIndices: Set<Int>) {
        val state = rememberListState(initialSelectedIndices = selectedIndices)
        SideEffect { state.selectedIndices = selectedIndices }
        ListBox(
            items = ListBoxFixture.initial.items,
            state = state,
        )
    }

    /** Builds a [JList] showing the shared items, before any of the fixture's values are applied. */
    override fun createRawSwing(): JList<String> = JList(ListBoxFixture.initial.items.toTypedArray())

    /** Selects the rows of [parameters] in [component], unless they are selected already. */
    override fun updateRawSwing(
        component: JList<String>,
        parameters: ListBoxStateParameters,
    ) {
        val rows = parameters.selectedIndices.toIntArray()
        if (!component.selectedIndices.contentEquals(rows)) component.selectedIndices = rows
    }
}
