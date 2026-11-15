// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import javax.swing.JButton

/** The parameters [DefaultButtonFixture] varies: whether the button is the root pane's default. */
public data class DefaultButtonParameters(
    val default: Boolean,
)

/** A [Button] with [SwingModifier.defaultButton] against a raw [JButton] made the default of its root pane. */
public object DefaultButtonFixture : ComposableFixture<DefaultButtonParameters, JButton>(
    initial = DefaultButtonParameters(default = true),
) {
    /** The text both sides show on the button. */
    private const val TEXT = "Default"

    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<DefaultButtonParameters> =
        listOf(
            initial.copy(default = false),
        )

    @Composable
    override fun Content(parameters: DefaultButtonParameters): Unit = Content(parameters.default)

    /** Composes a [Button] that is the default button of its window when [default] is `true`. */
    @Composable
    public fun Content(default: Boolean) {
        Button(text = TEXT, onClick = onClick, modifier = SwingModifier.defaultButton(default))
    }

    /** Builds a [JButton] with the click listener [Button] installs for its `onClick`. */
    override fun createRawSwing(): JButton = JButton(TEXT).apply { addActionListener { onClick() } }

    /**
     * Makes the button the default button of its root pane when [parameters] says so, and clears it otherwise. A
     * button mounted in a panel without a root pane has no default-button association to set.
     */
    override fun updateRawSwing(
        component: JButton,
        parameters: DefaultButtonParameters,
    ) {
        component.rootPane?.defaultButton = if (parameters.default) component else null
    }
}
