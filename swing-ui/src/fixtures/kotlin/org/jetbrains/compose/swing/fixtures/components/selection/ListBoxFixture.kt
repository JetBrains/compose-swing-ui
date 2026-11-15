// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/** Reports the settled selection of [list] to [onSelectionChange], except while [writing]. */
private class SelectionCallbackListener(
    private val list: JList<*>,
    var onSelectionChange: (Set<Int>) -> Unit,
) : ListSelectionListener {
    var writing: Boolean = false

    override fun valueChanged(event: ListSelectionEvent) {
        if (!writing && !event.valueIsAdjusting) onSelectionChange(list.selectedIndices.toSet())
    }
}

/** The parameters [ListBoxFixture] varies. */
public data class ListBoxParameters(
    val items: List<String>,
    val selectedIndices: Set<Int>?,
    val onSelectionChange: (Set<Int>) -> Unit,
    val selectionMode: Int,
    val visibleRowCount: Int,
    val layoutOrientation: Int,
    val prototypeCellValue: String?,
    val fixedCellWidth: Int,
    val fixedCellHeight: Int,
)

/** A [ListBox] over a list of items with a selection callback against a raw [JList] showing the same items. */
public object ListBoxFixture : ComposableFixture<ListBoxParameters, JList<String>>(
    initial =
        ListBoxParameters(
            items = listOf("Alpha", "Beta", "Gamma"),
            selectedIndices = null,
            onSelectionChange = {},
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            visibleRowCount = 3,
            layoutOrientation = JList.VERTICAL,
            prototypeCellValue = null,
            fixedCellWidth = -1,
            fixedCellHeight = -1,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListBoxParameters> =
        listOf(
            initial.copy(items = listOf("Delta", "Epsilon", "Zeta")),
            initial.copy(selectedIndices = setOf(1)),
            initial.copy(onSelectionChange = {}),
            initial.copy(selectionMode = ListSelectionModel.SINGLE_SELECTION),
            initial.copy(visibleRowCount = 4),
            initial.copy(layoutOrientation = JList.HORIZONTAL_WRAP),
            initial.copy(prototypeCellValue = "Long prototype"),
            initial.copy(fixedCellWidth = 120),
            initial.copy(fixedCellHeight = 24),
        )

    @Composable
    override fun Content(parameters: ListBoxParameters): Unit =
        Content(
            parameters.items,
            parameters.selectedIndices,
            parameters.onSelectionChange,
            parameters.selectionMode,
            parameters.visibleRowCount,
            parameters.layoutOrientation,
            parameters.prototypeCellValue,
            parameters.fixedCellWidth,
            parameters.fixedCellHeight,
        )

    /** Composes a [ListBox] over [items], reporting selection changes to [onSelectionChange]. */
    @Composable
    public fun Content(
        items: List<String>,
        selectedIndices: Set<Int>?,
        onSelectionChange: (Set<Int>) -> Unit,
        selectionMode: Int,
        visibleRowCount: Int,
        layoutOrientation: Int,
        prototypeCellValue: String?,
        fixedCellWidth: Int,
        fixedCellHeight: Int,
    ) {
        ListBox(
            items = items,
            selectedIndices = selectedIndices,
            onSelectionChange = onSelectionChange,
            selectionMode = selectionMode,
            visibleRowCount = visibleRowCount,
            layoutOrientation = layoutOrientation,
            prototypeCellValue = prototypeCellValue,
            fixedCellWidth = fixedCellWidth,
            fixedCellHeight = fixedCellHeight,
        )
    }

    /** Builds a bare [JList] before any of the fixture's values are applied. */
    override fun createRawSwing(): JList<String> = JList()

    /**
     * Applies the values of [parameters] to [component]. The selection listener it installed before stays attached
     * and hands its callback the latest one without reporting the values applied here; every other listener stays
     * in place.
     */
    override fun updateRawSwing(
        component: JList<String>,
        parameters: ListBoxParameters,
    ) {
        val listener =
            component.listSelectionListeners.firstNotNullOfOrNull { it as? SelectionCallbackListener }
                ?: SelectionCallbackListener(component) {}.also { component.addListSelectionListener(it) }
        listener.onSelectionChange = parameters.onSelectionChange
        listener.writing = true
        try {
            writeValues(component, parameters)
        } finally {
            listener.writing = false
        }
    }

    private fun writeValues(
        component: JList<String>,
        parameters: ListBoxParameters,
    ) {
        // Replacing the data clears the selection, so a program that keeps the same items leaves it alone.
        val model = component.model
        val shown = List(model.size) { model.getElementAt(it) }
        if (shown != parameters.items) component.setListData(parameters.items.toTypedArray())
        parameters.selectedIndices?.let { component.selectedIndices = it.toIntArray() }
        component.selectionMode = parameters.selectionMode
        component.visibleRowCount = parameters.visibleRowCount
        component.layoutOrientation = parameters.layoutOrientation
        // A list measures its prototype into the fixed cell size, so a stated size follows it and -1 is left unwritten.
        component.prototypeCellValue = null
        component.prototypeCellValue = parameters.prototypeCellValue
        if (parameters.prototypeCellValue == null || parameters.fixedCellWidth != -1) {
            component.fixedCellWidth = parameters.fixedCellWidth
        }
        if (parameters.prototypeCellValue == null || parameters.fixedCellHeight != -1) {
            component.fixedCellHeight = parameters.fixedCellHeight
        }
    }
}
