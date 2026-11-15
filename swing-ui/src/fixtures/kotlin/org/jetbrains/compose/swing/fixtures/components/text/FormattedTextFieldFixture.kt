// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.FocusLostBehavior
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JFormattedTextField
import javax.swing.JFormattedTextField.AbstractFormatter
import javax.swing.JFormattedTextField.AbstractFormatterFactory
import javax.swing.text.DefaultFormatter

/**
 * The parameters [FormattedTextFieldFixture] varies: the value, the commit callback, the enabled state, the
 * foreground color, the formatter factory, the edit validity callback, the focus-lost behavior, the columns and
 * the editable setting.
 */
public data class FormattedTextFieldParameters(
    val value: Any?,
    val onValueChange: (Any?) -> Unit,
    val enabled: Boolean,
    val color: Color,
    val formatterFactory: AbstractFormatterFactory?,
    val onEditValidChange: (Boolean) -> Unit,
    @param:FocusLostBehavior val focusLostBehavior: Int,
    val columns: Int,
    val editable: Boolean,
)

/**
 * A [FormattedTextField] with the callback overload against a raw [JFormattedTextField] holding the same value and
 * settings, reporting commits and edit validity to the same callbacks.
 */
public object FormattedTextFieldFixture : ComposableFixture<FormattedTextFieldParameters, JFormattedTextField>(
    initial =
        FormattedTextFieldParameters(
            value = "Before",
            onValueChange = { },
            enabled = true,
            color = Color.BLUE,
            formatterFactory = FreshFormatterFactory(overwriteMode = true),
            onEditValidChange = { },
            focusLostBehavior = JFormattedTextField.COMMIT_OR_REVERT,
            columns = 8,
            editable = true,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<FormattedTextFieldParameters> =
        listOf(
            initial.copy(value = "After"),
            initial.copy(onValueChange = { }),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(formatterFactory = FreshFormatterFactory(overwriteMode = false)),
            initial.copy(onEditValidChange = { }),
            initial.copy(focusLostBehavior = JFormattedTextField.PERSIST),
            initial.copy(columns = 12),
            initial.copy(editable = false),
        )

    @Composable
    override fun Content(parameters: FormattedTextFieldParameters): Unit =
        Content(
            value = parameters.value,
            onValueChange = parameters.onValueChange,
            enabled = parameters.enabled,
            color = parameters.color,
            formatterFactory = parameters.formatterFactory,
            onEditValidChange = parameters.onEditValidChange,
            focusLostBehavior = parameters.focusLostBehavior,
            columns = parameters.columns,
            editable = parameters.editable,
        )

    /** Composes a [FormattedTextField] showing [value], reporting commits to [onValueChange], with these settings. */
    @Composable
    public fun Content(
        value: Any?,
        onValueChange: (Any?) -> Unit,
        enabled: Boolean,
        color: Color,
        formatterFactory: AbstractFormatterFactory?,
        onEditValidChange: (Boolean) -> Unit,
        @FocusLostBehavior focusLostBehavior: Int,
        columns: Int,
        editable: Boolean,
    ) {
        FormattedTextField(
            value = value,
            onValueChange = onValueChange,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
            formatterFactory = formatterFactory,
            onEditValidChange = onEditValidChange,
            focusLostBehavior = focusLostBehavior,
            columns = columns,
            editable = editable,
        )
    }

    /** Builds a bare [JFormattedTextField] before any of the fixture's values are applied. */
    override fun createRawSwing(): JFormattedTextField = JFormattedTextField()

    /**
     * Applies the values of [parameters] to [component] with their Swing setters. The commit and edit validity
     * listeners this fixture added earlier stay attached and call the latest callbacks, and the commit listener
     * does not report the value applied here. A formatter factory the field already has is not installed again,
     * as installing one renders the value through its formatter anew.
     */
    override fun updateRawSwing(
        component: JFormattedTextField,
        parameters: FormattedTextFieldParameters,
    ) {
        val valueKey = "value"
        val editValidKey = "editValid"
        val commit =
            component.getPropertyChangeListeners(valueKey).firstNotNullOfOrNull { it as? CommitListener }
                ?: CommitListener(component).also { component.addPropertyChangeListener(valueKey, it) }
        val editValidity =
            component.getPropertyChangeListeners(editValidKey).firstNotNullOfOrNull { it as? EditValidityListener }
                ?: EditValidityListener(component).also { component.addPropertyChangeListener(editValidKey, it) }
        commit.onValueChange = parameters.onValueChange
        editValidity.onEditValidChange = parameters.onEditValidChange
        if (component.formatterFactory !== parameters.formatterFactory) {
            component.formatterFactory = parameters.formatterFactory
        }
        commit.writing = true
        try {
            if (component.value != parameters.value) component.value = parameters.value
        } finally {
            commit.writing = false
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
        component.focusLostBehavior = parameters.focusLostBehavior
        if (component.columns != parameters.columns) {
            component.columns = parameters.columns
            component.revalidate()
        }
        component.isEditable = parameters.editable
    }

    /** Reports the value of [field] to [onValueChange] whenever a commit changes it, except while [writing]. */
    private class CommitListener(
        private val field: JFormattedTextField,
    ) : PropertyChangeListener {
        var onValueChange: (Any?) -> Unit = {}
        var writing: Boolean = false

        override fun propertyChange(event: PropertyChangeEvent) {
            if (!writing && event.oldValue != event.newValue) onValueChange(field.value)
        }
    }

    /** Reports the validity of the edit of [field] to [onEditValidChange] whenever it changes. */
    private class EditValidityListener(
        private val field: JFormattedTextField,
    ) : PropertyChangeListener {
        var onEditValidChange: (Boolean) -> Unit = {}

        override fun propertyChange(event: PropertyChangeEvent) {
            onEditValidChange(field.isEditValid)
        }
    }
}

/**
 * A formatter factory that gives every field it is installed on a formatter of its own, as a formatter serves one
 * field at a time and installing it on a second field uninstalls it from the first.
 */
private class FreshFormatterFactory(
    private val overwriteMode: Boolean,
) : AbstractFormatterFactory() {
    override fun getFormatter(field: JFormattedTextField): AbstractFormatter =
        DefaultFormatter().apply { overwriteMode = this@FreshFormatterFactory.overwriteMode }
}
