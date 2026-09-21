// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JTextArea
import javax.swing.text.AbstractDocument

/**
 * The parameters [TextAreaFixture] varies: the text, the edit callback, the enabled state, the foreground color,
 * the rows and columns, and the editable, line wrap, word wrap and tab size settings.
 */
public data class TextAreaParameters(
    val value: String,
    val onValueChange: (String) -> Unit,
    val enabled: Boolean,
    val color: Color,
    val rows: Int,
    val columns: Int,
    val editable: Boolean,
    val lineWrap: Boolean,
    val wrapStyleWord: Boolean,
    val tabSize: Int,
)

/** A [TextArea] with the callback overload against a raw [JTextArea] holding the same text and settings. */
public object TextAreaFixture : ComposableFixture<TextAreaParameters, JTextArea>(
    initial =
        TextAreaParameters(
            value = "Before",
            onValueChange = { },
            enabled = true,
            color = Color.BLUE,
            rows = 2,
            columns = 8,
            editable = true,
            lineWrap = false,
            wrapStyleWord = true,
            tabSize = 8,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TextAreaParameters> =
        listOf(
            initial.copy(value = "After"),
            initial.copy(onValueChange = { }),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(rows = 3),
            initial.copy(columns = 12),
            initial.copy(editable = false),
            initial.copy(lineWrap = true),
            initial.copy(wrapStyleWord = false),
            initial.copy(tabSize = 4),
        )

    @Composable
    override fun Content(parameters: TextAreaParameters): Unit =
        Content(
            value = parameters.value,
            onValueChange = parameters.onValueChange,
            enabled = parameters.enabled,
            color = parameters.color,
            rows = parameters.rows,
            columns = parameters.columns,
            editable = parameters.editable,
            lineWrap = parameters.lineWrap,
            wrapStyleWord = parameters.wrapStyleWord,
            tabSize = parameters.tabSize,
        )

    /** Composes a [TextArea] showing [value], reporting edits to [onValueChange], with the given settings. */
    @Composable
    public fun Content(
        value: String,
        onValueChange: (String) -> Unit,
        enabled: Boolean,
        color: Color,
        rows: Int,
        columns: Int,
        editable: Boolean,
        lineWrap: Boolean,
        wrapStyleWord: Boolean,
        tabSize: Int,
    ) {
        TextArea(
            value = value,
            onValueChange = onValueChange,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
            rows = rows,
            columns = columns,
            editable = editable,
            lineWrap = lineWrap,
            wrapStyleWord = wrapStyleWord,
            tabSize = tabSize,
        )
    }

    /** Builds a bare [JTextArea] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTextArea = JTextArea()

    /**
     * Applies the values of [parameters] to [component] with their Swing setters. The edit listener this fixture
     * added earlier stays attached, calls the latest callback, and does not report the text applied here.
     */
    override fun updateRawSwing(
        component: JTextArea,
        parameters: TextAreaParameters,
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
        if (component.rows != parameters.rows) {
            component.rows = parameters.rows
            component.revalidate()
        }
        if (component.columns != parameters.columns) {
            component.columns = parameters.columns
            component.revalidate()
        }
        component.isEditable = parameters.editable
        component.lineWrap = parameters.lineWrap
        component.wrapStyleWord = parameters.wrapStyleWord
        component.tabSize = parameters.tabSize
    }
}
