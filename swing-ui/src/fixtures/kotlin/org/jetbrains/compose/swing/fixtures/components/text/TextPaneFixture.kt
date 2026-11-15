// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JTextPane
import javax.swing.text.AbstractDocument

/**
 * The parameters [TextPaneFixture] varies: the text, the edit callback, the enabled state, the foreground color and
 * the editable setting.
 */
public data class TextPaneParameters(
    val value: String,
    val onValueChange: (String) -> Unit,
    val enabled: Boolean,
    val color: Color,
    val editable: Boolean,
)

/** A [TextPane] with the callback overload against a raw [JTextPane] holding the same text and settings. */
public object TextPaneFixture : ComposableFixture<TextPaneParameters, JTextPane>(
    initial =
        TextPaneParameters(
            value = "Before",
            onValueChange = { },
            enabled = true,
            color = Color.BLUE,
            editable = true,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TextPaneParameters> =
        listOf(
            initial.copy(value = "After"),
            initial.copy(onValueChange = { }),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(editable = false),
        )

    @Composable
    override fun Content(parameters: TextPaneParameters): Unit =
        Content(
            value = parameters.value,
            onValueChange = parameters.onValueChange,
            enabled = parameters.enabled,
            color = parameters.color,
            editable = parameters.editable,
        )

    /** Composes a [TextPane] showing [value], reporting edits to [onValueChange], with the given settings. */
    @Composable
    public fun Content(
        value: String,
        onValueChange: (String) -> Unit,
        enabled: Boolean,
        color: Color,
        editable: Boolean,
    ) {
        TextPane(
            value = value,
            onValueChange = onValueChange,
            style = {
                enabled(enabled)
                foreground(color)
            },
            editable = editable,
        )
    }

    /** Builds a bare [JTextPane] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTextPane = JTextPane()

    /**
     * Applies the values of [parameters] to [component] with their Swing setters. The edit listener this fixture
     * added earlier stays attached, calls the latest callback, and does not report the text applied here.
     */
    override fun updateRawSwing(
        component: JTextPane,
        parameters: TextPaneParameters,
    ) {
        val document = component.document as AbstractDocument
        val listener =
            document.documentListeners.firstNotNullOfOrNull { it as? EditListener }
                ?: EditListener {}.also { document.addDocumentListener(it) }
        listener.onEdit = { parameters.onValueChange(component.text) }
        listener.writing = true
        try {
            applySettings(component, parameters)
        } finally {
            listener.writing = false
        }
    }

    /** Applies everything of [parameters] to [component] except the edit callback. */
    internal fun applySettings(
        component: JTextPane,
        parameters: TextPaneParameters,
    ) {
        if (component.text != parameters.value) component.text = parameters.value
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
        component.isEditable = parameters.editable
    }
}
