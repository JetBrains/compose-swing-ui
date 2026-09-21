// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultButtonModel
import javax.swing.JPanel
import javax.swing.JRadioButton

/** The parameters [RadioGroupFixture] varies: selection, selection callback, axis and the two option texts. */
public data class RadioGroupParameters(
    val selectedIndex: Int,
    val onSelectionChange: (Int) -> Unit,
    val axis: Int,
    val first: String,
    val second: String,
)

/** A [RadioGroup] with two options against a raw [JPanel] of two radio buttons in a button group. */
public object RadioGroupFixture : ComposableFixture<RadioGroupParameters, JPanel>(
    initial =
        RadioGroupParameters(
            selectedIndex = 0,
            onSelectionChange = {},
            axis = BoxLayout.Y_AXIS,
            first = "Alpha",
            second = "Beta",
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<RadioGroupParameters> =
        listOf(
            initial.copy(selectedIndex = 1),
            initial.copy(selectedIndex = -1),
            initial.copy(onSelectionChange = {}),
            initial.copy(axis = BoxLayout.X_AXIS),
            initial.copy(first = "Gamma"),
            initial.copy(second = "Delta"),
        )

    @Composable
    override fun Content(parameters: RadioGroupParameters): Unit =
        Content(
            parameters.selectedIndex,
            parameters.onSelectionChange,
            parameters.axis,
            parameters.first,
            parameters.second,
        )

    /** Composes a [RadioGroup] of two options labeled [first] and [second] along [axis]. */
    @Composable
    public fun Content(
        selectedIndex: Int,
        onSelectionChange: (Int) -> Unit,
        axis: Int,
        first: String,
        second: String,
    ) {
        RadioGroup(selectedIndex = selectedIndex, onSelectionChange = onSelectionChange, axis = axis) {
            option(first)
            option(second)
        }
    }

    /**
     * Builds a [JPanel] holding two unselected, unlabeled radio buttons of one button group, before the axis is
     * applied.
     */
    override fun createRawSwing(): JPanel {
        val panel = JPanel()
        val group = ButtonGroup()
        repeat(2) {
            val button = JRadioButton()
            group.add(button)
            panel.add(button)
        }
        return panel
    }

    /** Applies the texts, axis, selection and callback of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: RadioGroupParameters,
    ) {
        if ((component.layout as? BoxLayout)?.axis != parameters.axis) {
            component.layout = BoxLayout(component, parameters.axis)
            component.revalidate()
        }
        val buttons = component.components.map { it as JRadioButton }
        buttons[0].text = parameters.first
        buttons[1].text = parameters.second
        when (parameters.selectedIndex) {
            0 -> buttons[0].isSelected = true
            1 -> buttons[1].isSelected = true
            else -> (buttons[0].model as DefaultButtonModel).group.clearSelection()
        }
        buttons.forEachIndexed { index, button ->
            val listener =
                button.actionListeners.firstNotNullOfOrNull { it as? SelectionListener }
                    ?: SelectionListener(index).also { button.addActionListener(it) }
            listener.onSelectionChange = parameters.onSelectionChange
        }
    }

    /**
     * The listener attached once to the raw button at [index]: it reports a user's pick of that option to the
     * latest selection callback.
     */
    private class SelectionListener(
        private val index: Int,
    ) : ActionListener {
        var onSelectionChange: (Int) -> Unit = {}

        override fun actionPerformed(event: ActionEvent) {
            onSelectionChange(index)
        }
    }
}
