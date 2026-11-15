// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.DefaultListModel
import javax.swing.JList

/** The parameters [ListBoxModelFixture] varies: the items of the model the list renders. */
public data class ListBoxModelParameters(
    val items: List<String>,
)

/** A [ListBox] over a model remembered for items against a raw [JList] given a fresh model of the same items. */
public object ListBoxModelFixture : ComposableFixture<ListBoxModelParameters, JList<String>>(
    initial = ListBoxModelParameters(items = listOf("Alpha", "Beta")),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListBoxModelParameters> =
        listOf(
            initial.copy(items = listOf("Delta", "Epsilon", "Zeta")),
        )

    @Composable
    override fun Content(parameters: ListBoxModelParameters): Unit = Content(parameters.items)

    /** Composes a [ListBox] rendering a model remembered for [items]. */
    @Composable
    public fun Content(items: List<String>) {
        ListBox(model = remember(items) { DefaultListModel<String>().apply { addAll(items) } })
    }

    /** Builds a bare [JList] before the fixture's model is applied. */
    override fun createRawSwing(): JList<String> = JList()

    /** Installs a fresh model of the items of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JList<String>,
        parameters: ListBoxModelParameters,
    ) {
        component.model = DefaultListModel<String>().apply { addAll(parameters.items) }
    }
}
