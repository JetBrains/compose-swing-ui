// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/** The parameters [ComboBoxModelFixture] varies: what differs from [ComboBoxParameters]. */
public data class ComboBoxModelParameters(
    val items: List<String>,
    val onSelectionChange: (Int) -> Unit,
)

/** A [ComboBox] over a model of the given items reporting the selected index against a raw [JComboBox]. */
public object ComboBoxModelFixture : ComposableFixture<ComboBoxModelParameters, JComboBox<String>>(
    initial =
        ComboBoxModelParameters(
            items = listOf("Before", "After"),
            onSelectionChange = {},
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ComboBoxModelParameters> =
        listOf(
            initial.copy(items = listOf("Before", "After", "Extra")),
            initial.copy(onSelectionChange = {}),
        )

    @Composable
    override fun Content(parameters: ComboBoxModelParameters): Unit =
        Content(
            parameters.items,
            parameters.onSelectionChange,
        )

    /**
     * Composes a [ComboBox] over a model remembered for [items] and reporting the selected index to
     * [onSelectionChange], rendering items with the default renderer.
     */
    @Composable
    public fun Content(
        items: List<String>,
        onSelectionChange: (Int) -> Unit,
    ) {
        ComboBox(
            model = remember(items) { DefaultComboBoxModel(items.toTypedArray()) },
            modifier = SwingModifier.name("Before"),
            style = { foreground(Color.BLUE) },
            onSelectionChange = onSelectionChange,
            editable = false,
            onValueCommit = {},
            maximumRowCount = 8,
        )
    }

    /** Builds a bare [JComboBox] carrying the fixed name, foreground color, editability and maximum row count. */
    override fun createRawSwing(): JComboBox<String> {
        val component = JComboBox<String>()
        component.name = "Before"
        component.foreground = Color.BLUE
        component.isEditable = false
        component.maximumRowCount = 8
        return component
    }

    /**
     * Gives [component] a fresh model when the items of [parameters] differ from those it holds, and points the
     * one listener this fixture attached at its selection callback.
     */
    override fun updateRawSwing(
        component: JComboBox<String>,
        parameters: ComboBoxModelParameters,
    ) {
        val model = component.model
        val items = parameters.items
        if (model.size != items.size || items.indices.any { model.getElementAt(it) != items[it] }) {
            component.model = DefaultComboBoxModel(items.toTypedArray())
        }
        val forwarder = component.actionListeners.firstNotNullOfOrNull { it as? IndexForwarder }
        if (forwarder == null) {
            component.addActionListener(IndexForwarder(component, parameters.onSelectionChange) {})
        } else {
            forwarder.onSelectionChange = parameters.onSelectionChange
        }
    }

    /** Forwards a user's choice as an index and an editor commit as its text. */
    private class IndexForwarder(
        private val box: JComboBox<String>,
        var onSelectionChange: (Int) -> Unit,
        private val onValueCommit: (String) -> Unit,
    ) : ActionListener {
        override fun actionPerformed(event: ActionEvent) {
            if (event.actionCommand == "comboBoxEdited") {
                onValueCommit(box.selectedItem?.toString().orEmpty())
            } else {
                onSelectionChange(box.selectedIndex)
            }
        }
    }
}
