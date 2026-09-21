// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComboBox

/**
 * The parameters [ComboBoxFixture] varies: the items, the selected item, the selection and commit
 * callbacks, modifier name, foreground color, editability and maximum row count.
 */
public data class ComboBoxParameters(
    val items: List<String>,
    val selectedItem: String?,
    val onSelectionChange: (String?) -> Unit,
    val name: String,
    val color: Color,
    val editable: Boolean,
    val onValueCommit: (String) -> Unit,
    val maximumRowCount: Int?,
)

/** A [ComboBox] over a list of items with an `onSelectionChange` callback against a raw [JComboBox]. */
public object ComboBoxFixture : ComposableFixture<ComboBoxParameters, JComboBox<String>>(
    initial =
        ComboBoxParameters(
            items = listOf("Before", "After"),
            selectedItem = "Before",
            onSelectionChange = {},
            name = "Before",
            color = Color.BLUE,
            editable = false,
            onValueCommit = {},
            maximumRowCount = 8,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ComboBoxParameters> =
        listOf(
            initial.copy(items = listOf("Before", "After", "Extra")),
            initial.copy(selectedItem = "After"),
            initial.copy(onSelectionChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(editable = true),
            initial.copy(onValueCommit = {}),
            initial.copy(maximumRowCount = 4),
        )

    @Composable
    override fun Content(parameters: ComboBoxParameters): Unit =
        Content(
            parameters.items,
            parameters.selectedItem,
            parameters.onSelectionChange,
            parameters.name,
            parameters.color,
            parameters.editable,
            parameters.onValueCommit,
            parameters.maximumRowCount,
        )

    /**
     * Composes a [ComboBox] over [items] with [selectedItem] selected, reporting choices to
     * [onSelectionChange] and committed editor text to [onValueCommit], named [name], drawn in [color],
     * [editable] or not and showing at most [maximumRowCount] rows, rendering items with the default renderer.
     */
    @Composable
    public fun Content(
        items: List<String>,
        selectedItem: String?,
        onSelectionChange: (String?) -> Unit,
        name: String,
        color: Color,
        editable: Boolean,
        onValueCommit: (String) -> Unit,
        maximumRowCount: Int?,
    ) {
        ComboBox(
            items = items,
            selectedItem = selectedItem,
            onSelectionChange = onSelectionChange,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
            editable = editable,
            onValueCommit = onValueCommit,
            maximumRowCount = maximumRowCount,
        )
    }

    /** Builds a bare [JComboBox] before any of the fixture's values are applied. */
    override fun createRawSwing(): JComboBox<String> =
        JComboBox<String>().also { it.putClientProperty(MAXIMUM_ROW_COUNT, it.maximumRowCount) }

    /**
     * Applies the items, selection, name, foreground color, editability, maximum row count and callbacks
     * of [parameters] to [component], replacing only the listener this fixture attached before. Items it already
     * shows stay, and a maximum row count that is withdrawn goes back to the one the box was created with.
     */
    override fun updateRawSwing(
        component: JComboBox<String>,
        parameters: ComboBoxParameters,
    ) {
        val forwarder =
            component.actionListeners.firstNotNullOfOrNull { it as? SelectionForwarder }
                ?: SelectionForwarder(component).also { component.addActionListener(it) }
        forwarder.onSelectionChange = parameters.onSelectionChange
        forwarder.onValueCommit = parameters.onValueCommit
        forwarder.writing = true
        try {
            if (List(component.itemCount) { component.getItemAt(it) } != parameters.items) {
                component.removeAllItems()
                parameters.items.forEach { component.addItem(it) }
            }
            component.selectedItem = parameters.selectedItem
            component.name = parameters.name
            component.foreground = parameters.color
            component.isEditable = parameters.editable
            component.maximumRowCount = parameters.maximumRowCount ?: component.defaultOf<Int>(MAXIMUM_ROW_COUNT)
        } finally {
            forwarder.writing = false
        }
    }

    private const val MAXIMUM_ROW_COUNT = "fixtureMaximumRowCount"

    /**
     * The action listener attached once to the raw box: it forwards a user's choice to the latest selection
     * callback and an editor commit to the latest commit callback, and stays silent while [writing].
     */
    private class SelectionForwarder(
        private val box: JComboBox<String>,
    ) : ActionListener {
        var onSelectionChange: (String?) -> Unit = {}
        var onValueCommit: (String) -> Unit = {}
        var writing: Boolean = false

        override fun actionPerformed(event: ActionEvent) {
            if (writing) return
            val selected = box.selectedItem?.toString()
            if (event.actionCommand == "comboBoxEdited") {
                onValueCommit(selected.orEmpty())
            } else {
                onSelectionChange(selected)
            }
        }
    }
}
