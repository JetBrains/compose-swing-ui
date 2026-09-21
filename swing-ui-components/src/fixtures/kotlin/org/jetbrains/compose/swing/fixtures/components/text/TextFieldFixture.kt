// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JTextField
import javax.swing.text.AbstractDocument

/**
 * The parameters [TextFieldFixture] varies: the text, the edit callback, the enabled state, the foreground color,
 * the columns, and the editable setting.
 */
public data class TextFieldParameters(
    val value: String,
    val onValueChange: (String) -> Unit,
    val enabled: Boolean,
    val color: Color,
    val columns: Int,
    val editable: Boolean,
)

/** A [TextField] with the callback overload against a raw [JTextField] holding the same text. */
public object TextFieldFixture : ComposableFixture<TextFieldParameters, JTextField>(
    initial =
        TextFieldParameters(
            value = "Before",
            onValueChange = { },
            enabled = true,
            color = Color.BLUE,
            columns = 8,
            editable = true,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TextFieldParameters> =
        listOf(
            initial.copy(value = "After"),
            initial.copy(onValueChange = { }),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(columns = 12),
            initial.copy(editable = false),
        )

    @Composable
    override fun Content(parameters: TextFieldParameters): Unit =
        Content(
            value = parameters.value,
            onValueChange = parameters.onValueChange,
            enabled = parameters.enabled,
            color = parameters.color,
            columns = parameters.columns,
            editable = parameters.editable,
        )

    /** Composes a [TextField] showing [value], reporting edits to [onValueChange], with the given settings. */
    @Composable
    public fun Content(
        value: String,
        onValueChange: (String) -> Unit,
        enabled: Boolean,
        color: Color,
        columns: Int,
        editable: Boolean,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
            columns = columns,
            editable = editable,
        )
    }

    /** Builds a bare [JTextField] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTextField = JTextField()

    /**
     * Applies the values of [parameters] to [component] with their Swing setters. The edit listener this fixture
     * added earlier stays attached, calls the latest callback, and does not report the text applied here.
     */
    override fun updateRawSwing(
        component: JTextField,
        parameters: TextFieldParameters,
    ) {
        val document = component.document as AbstractDocument
        val listener =
            document.documentListeners.firstNotNullOfOrNull { it as? EditListener }
                ?: EditListener {}.also { document.addDocumentListener(it) }
        listener.onEdit = { parameters.onValueChange(component.text) }
        listener.writing = true
        try {
            if (component.text != parameters.value) component.text = parameters.value
        } finally {
            listener.writing = false
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
        if (component.columns != parameters.columns) {
            component.columns = parameters.columns
            component.revalidate()
        }
        component.isEditable = parameters.editable
    }
}
