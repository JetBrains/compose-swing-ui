// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.buttonGroup
import javax.swing.ButtonGroup
import javax.swing.DefaultButtonModel
import javax.swing.JToggleButton

/** The parameters [ButtonGroupFixture] varies: which group the button joins, as a key for a group built per mount. */
public data class ButtonGroupParameters(
    val groupKey: Int,
)

/**
 * A [ToggleButton] enrolled with [SwingModifier.buttonGroup] against a raw [JToggleButton] joined to the
 * same group.
 */
public object ButtonGroupFixture : ComposableFixture<ButtonGroupParameters, JToggleButton>(
    initial = ButtonGroupParameters(groupKey = 0),
) {
    /** The selection handler both sides run for the toggle button's own action. */
    private val onSelectedChange: (Boolean) -> Unit = {}

    override val alternatives: List<ButtonGroupParameters> =
        listOf(
            initial.copy(groupKey = 1),
        )

    @Composable
    override fun Content(parameters: ButtonGroupParameters): Unit = Content(parameters.groupKey)

    /** Composes an unselected [ToggleButton] labelled "Grouped" that joins a group remembered for [groupKey]. */
    @Composable
    public fun Content(groupKey: Int) {
        val group = remember(groupKey) { ButtonGroup() }
        ToggleButton(
            text = "Grouped",
            selected = false,
            onSelectedChange = onSelectedChange,
            modifier = SwingModifier.buttonGroup(group),
        )
    }

    /**
     * Builds an unselected [JToggleButton] labelled "Grouped" that belongs to no group, with the action and item
     * listeners [ToggleButton] installs.
     */
    override fun createRawSwing(): JToggleButton =
        JToggleButton("Grouped").apply {
            addActionListener { onSelectedChange(isSelected) }
            addItemListener { onSelectedChange(isSelected) }
        }

    /** Moves [component] from its earlier group to a fresh group for [parameters]. */
    override fun updateRawSwing(
        component: JToggleButton,
        parameters: ButtonGroupParameters,
    ) {
        (component.model as DefaultButtonModel).group?.remove(component)
        ButtonGroup().add(component)
    }
}
