// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JPasswordField
import javax.swing.text.AbstractDocument

/**
 * The parameters [PasswordFieldFixture] varies: the characters, the edit callback, the enabled state, the foreground
 * color, the echo character, the columns, and the editable setting.
 */
public data class PasswordFieldParameters(
    val value: CharArray,
    val onValueChange: (CharArray) -> Unit,
    val enabled: Boolean,
    val color: Color,
    val echoChar: Char,
    val columns: Int,
    val editable: Boolean,
)

/** A [PasswordField] with the callback overload against a raw [JPasswordField] holding the same characters. */
public object PasswordFieldFixture : ComposableFixture<PasswordFieldParameters, JPasswordField>(
    initial =
        PasswordFieldParameters(
            value = "Before".toCharArray(),
            onValueChange = { },
            enabled = true,
            color = Color.BLUE,
            echoChar = '*',
            columns = 8,
            editable = true,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<PasswordFieldParameters> =
        listOf(
            initial.copy(value = "After".toCharArray()),
            initial.copy(onValueChange = { }),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(echoChar = '#'),
            initial.copy(columns = 12),
            initial.copy(editable = false),
        )

    @Composable
    override fun Content(parameters: PasswordFieldParameters): Unit =
        Content(
            value = parameters.value,
            onValueChange = parameters.onValueChange,
            enabled = parameters.enabled,
            color = parameters.color,
            echoChar = parameters.echoChar,
            columns = parameters.columns,
            editable = parameters.editable,
        )

    /** Composes a [PasswordField] showing [value], reporting edits to [onValueChange], with the given settings. */
    @Composable
    public fun Content(
        value: CharArray,
        onValueChange: (CharArray) -> Unit,
        enabled: Boolean,
        color: Color,
        echoChar: Char,
        columns: Int,
        editable: Boolean,
    ) {
        PasswordField(
            value = value,
            onValueChange = onValueChange,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
            echoChar = echoChar,
            columns = columns,
            editable = editable,
        )
    }

    /** Builds a bare [JPasswordField] before any of the fixture's values are applied. */
    override fun createRawSwing(): JPasswordField = JPasswordField()

    /**
     * Applies the values of [parameters] to [component] with their Swing setters. The edit listener this fixture
     * added earlier stays attached, calls the latest callback, and does not report the characters applied here.
     */
    override fun updateRawSwing(
        component: JPasswordField,
        parameters: PasswordFieldParameters,
    ) {
        val document = component.document as AbstractDocument
        val listener =
            document.documentListeners.firstNotNullOfOrNull { it as? EditListener }
                ?: EditListener {}.also { document.addDocumentListener(it) }
        listener.onEdit = { parameters.onValueChange(component.password) }
        listener.writing = true
        try {
            if (!component.password.contentEquals(parameters.value)) component.text = String(parameters.value)
        } finally {
            listener.writing = false
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
        component.echoChar = parameters.echoChar
        component.columns = parameters.columns
        component.isEditable = parameters.editable
    }
}
