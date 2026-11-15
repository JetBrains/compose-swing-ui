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
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/** The parameters [ComboBoxModelActionListenerFixture] varies: what differs from [ComboBoxModelParameters]. */
public data class ComboBoxModelActionListenerParameters(
    val actionListener: ActionListener,
)

/** A [ComboBox] over a fixed model driven by a raw action listener against a raw [JComboBox]. */
public object ComboBoxModelActionListenerFixture :
    ComposableFixture<ComboBoxModelActionListenerParameters, JComboBox<String>>(
        initial =
            ComboBoxModelActionListenerParameters(
                actionListener = {},
            ),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ComboBoxModelActionListenerParameters> =
        listOf(
            initial.copy(actionListener = {}),
        )

    @Composable
    override fun Content(parameters: ComboBoxModelActionListenerParameters): Unit = Content(parameters.actionListener)

    /**
     * Composes a [ComboBox] over a remembered model, named "Before" and drawn in blue, not editable and showing at
     * most 8 rows, notifying [actionListener] and rendering items with the default renderer.
     */
    @Composable
    public fun Content(actionListener: ActionListener) {
        ComboBox(
            model = remember { DefaultComboBoxModel(arrayOf("Before", "After")) },
            actionListener = actionListener,
            modifier = SwingModifier.name("Before"),
            style = { foreground(Color.BLUE) },
            editable = false,
            maximumRowCount = 8,
        )
    }

    /** Builds a [JComboBox] carrying the same fixed model, name, color, editability and row count as [Content]. */
    override fun createRawSwing(): JComboBox<String> =
        JComboBox<String>().apply {
            model = DefaultComboBoxModel(arrayOf("Before", "After"))
            name = "Before"
            foreground = Color.BLUE
            isEditable = false
            maximumRowCount = 8
        }

    /**
     * Attaches the action listener of [parameters] to [component] in place of the one this fixture attached before.
     */
    override fun updateRawSwing(
        component: JComboBox<String>,
        parameters: ComboBoxModelActionListenerParameters,
    ) {
        if (parameters.actionListener !in component.actionListeners) {
            component.removeActionListener(initial.actionListener)
            alternatives.forEach { component.removeActionListener(it.actionListener) }
            component.addActionListener(parameters.actionListener)
        }
    }
}
