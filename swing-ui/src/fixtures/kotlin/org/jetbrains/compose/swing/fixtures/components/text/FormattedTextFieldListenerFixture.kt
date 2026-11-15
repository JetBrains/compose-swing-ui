// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.beans.PropertyChangeListener
import javax.swing.JFormattedTextField

/** The parameters [FormattedTextFieldListenerFixture] varies: the property change listener that receives commits. */
public data class FormattedTextFieldListenerParameters(
    val valuePropertyChangeListener: PropertyChangeListener,
)

/**
 * A [FormattedTextField] with the property change listener overload against a raw [JFormattedTextField] holding the
 * same value, with the same listener added by hand.
 */
public object FormattedTextFieldListenerFixture :
    ComposableFixture<FormattedTextFieldListenerParameters, JFormattedTextField>(
        initial =
            FormattedTextFieldListenerParameters(
                valuePropertyChangeListener = { },
            ),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<FormattedTextFieldListenerParameters> =
        listOf(
            initial.copy(valuePropertyChangeListener = { }),
        )

    @Composable
    override fun Content(parameters: FormattedTextFieldListenerParameters): Unit =
        Content(valuePropertyChangeListener = parameters.valuePropertyChangeListener)

    /** Composes a [FormattedTextField] showing the fixed value, reporting commits to [valuePropertyChangeListener]. */
    @Composable
    public fun Content(valuePropertyChangeListener: PropertyChangeListener) {
        FormattedTextField(
            value = VALUE,
            valuePropertyChangeListener = valuePropertyChangeListener,
        )
    }

    /** Builds a [JFormattedTextField] already holding the fixed value, before the listener is added. */
    override fun createRawSwing(): JFormattedTextField = JFormattedTextField().apply { value = VALUE }

    /** Leaves [parameters]' listener as the only "value" listener this fixture manages on [component]. */
    override fun updateRawSwing(
        component: JFormattedTextField,
        parameters: FormattedTextFieldListenerParameters,
    ) {
        val valueKey = "value"
        if (parameters.valuePropertyChangeListener !in component.getPropertyChangeListeners(valueKey)) {
            component.removePropertyChangeListener(valueKey, initial.valuePropertyChangeListener)
            alternatives.forEach { component.removePropertyChangeListener(valueKey, it.valuePropertyChangeListener) }
            component.addPropertyChangeListener(valueKey, parameters.valuePropertyChangeListener)
        }
    }

    private const val VALUE = "Before"
}
