// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.rememberFormattedValueState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JFormattedTextField

/** The parameters [FormattedTextFieldStateFixture] varies: the value the state starts on. */
public data class FormattedTextFieldStateParameters(
    val initialValue: String,
)

/**
 * A [FormattedTextField] driven by a formatted value state against a raw [JFormattedTextField] holding the same
 * value.
 */
public object FormattedTextFieldStateFixture :
    ComposableFixture<FormattedTextFieldStateParameters, JFormattedTextField>(
        initial = FormattedTextFieldStateParameters(initialValue = "Before"),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<FormattedTextFieldStateParameters> =
        listOf(
            initial.copy(initialValue = "After"),
        )

    @Composable
    override fun Content(parameters: FormattedTextFieldStateParameters): Unit = Content(parameters.initialValue)

    /** Composes a [FormattedTextField] driven by a state that starts on [initialValue] and then follows it. */
    @Composable
    public fun Content(initialValue: String) {
        val state = rememberFormattedValueState(initialValue = initialValue)
        SideEffect { state.value = initialValue }
        FormattedTextField(state = state)
    }

    /** Builds a bare [JFormattedTextField] before any of the fixture's values are applied. */
    override fun createRawSwing(): JFormattedTextField = JFormattedTextField()

    /** Writes the initial value of [parameters] to [component] with its value setter, unless it holds it already. */
    override fun updateRawSwing(
        component: JFormattedTextField,
        parameters: FormattedTextFieldStateParameters,
    ) {
        if (component.value != parameters.initialValue) component.value = parameters.initialValue
    }
}
