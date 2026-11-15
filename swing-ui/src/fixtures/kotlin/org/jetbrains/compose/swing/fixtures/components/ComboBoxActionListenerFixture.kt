// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.event.ActionListener
import javax.swing.JComboBox

/** The parameters [ComboBoxActionListenerFixture] varies: what differs from [ComboBoxParameters]. */
public data class ComboBoxActionListenerParameters(
    val actionListener: ActionListener,
)

/** A [ComboBox] over a list of items driven by a raw action listener against a raw [JComboBox]. */
public object ComboBoxActionListenerFixture : ComposableFixture<ComboBoxActionListenerParameters, JComboBox<String>>(
    initial =
        ComboBoxActionListenerParameters(
            actionListener = {},
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ComboBoxActionListenerParameters> =
        listOf(initial.copy(actionListener = {}))

    @Composable
    override fun Content(parameters: ComboBoxActionListenerParameters): Unit = Content(parameters.actionListener)

    /**
     * Composes a [ComboBox] with fixed items, selection, name, color, editability and row count, notifying
     * [actionListener] and rendering items with the default renderer.
     */
    @Composable
    public fun Content(actionListener: ActionListener) {
        ComboBox(
            items = listOf("Before", "After"),
            selectedItem = "Before",
            actionListener = actionListener,
            modifier = SwingModifier.name("Before"),
            style = { foreground(Color.BLUE) },
            editable = false,
            maximumRowCount = 8,
        )
    }

    /**
     * Builds a [JComboBox] holding the fixed items, selection, name, foreground color, editability and maximum
     * row count that [Content] composes, before any action listener is attached.
     */
    override fun createRawSwing(): JComboBox<String> =
        JComboBox<String>().apply {
            addItem("Before")
            addItem("After")
            selectedItem = "Before"
            name = "Before"
            foreground = Color.BLUE
            isEditable = false
            maximumRowCount = 8
        }

    /**
     * Attaches the action listener of [parameters] to [component], replacing only the listener this fixture
     * attached before.
     */
    override fun updateRawSwing(
        component: JComboBox<String>,
        parameters: ComboBoxActionListenerParameters,
    ) {
        if (parameters.actionListener !in component.actionListeners) {
            component.removeActionListener(initial.actionListener)
            alternatives.forEach { component.removeActionListener(it.actionListener) }
            component.addActionListener(parameters.actionListener)
        }
    }
}
