// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.documentListener
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The parameters [DocumentListenerFixture] varies. */
public data class DocumentListenerParameters(
    val onInsert: (DocumentEvent) -> Unit,
    val onRemove: (DocumentEvent) -> Unit,
    val onChange: (DocumentEvent) -> Unit,
)

/**
 * A [documentListener] with insert, remove and change callbacks against a raw [JTextField] that carries the same
 * callbacks.
 */
public object DocumentListenerFixture : ComposableFixture<DocumentListenerParameters, JTextField>(
    initial =
        DocumentListenerParameters(
            onInsert = {},
            onRemove = {},
            onChange = {},
        ),
) {
    override val alternatives: List<DocumentListenerParameters> =
        listOf(
            initial.copy(onInsert = {}),
            initial.copy(onRemove = {}),
            initial.copy(onChange = {}),
        )

    @Composable
    override fun Content(parameters: DocumentListenerParameters): Unit =
        Content(parameters.onInsert, parameters.onRemove, parameters.onChange)

    /** Composes a [JTextField] whose document reports to [onInsert], [onRemove] and [onChange]. */
    @Composable
    public fun Content(
        onInsert: (DocumentEvent) -> Unit,
        onRemove: (DocumentEvent) -> Unit,
        onChange: (DocumentEvent) -> Unit,
    ) {
        TextField(
            value = "",
            onValueChange = {},
            modifier =
                SwingModifier.documentListener(
                    onInsert = onInsert,
                    onRemove = onRemove,
                    onChange = onChange,
                ),
        )
    }

    /** Builds a [JTextField] with no document listener attached. */
    override fun createRawSwing(): JTextField = JTextField()

    /** Adds the document listener on the first call; later calls only replace its callbacks from [parameters]. */
    override fun updateRawSwing(
        component: JTextField,
        parameters: DocumentListenerParameters,
    ) {
        val key = "documentListener"
        val existing = component.getClientProperty(key)
        if (existing is DocumentListenerForwardingListener) {
            existing.onInsert = parameters.onInsert
            existing.onRemove = parameters.onRemove
            existing.onChange = parameters.onChange
        } else {
            val listener =
                DocumentListenerForwardingListener(parameters.onInsert, parameters.onRemove, parameters.onChange)
            component.putClientProperty(key, listener)
            component.document.addDocumentListener(listener)
        }
    }
}

/** The document listener [DocumentListenerFixture] installs on its raw component, so that it finds only its own. */
private class DocumentListenerForwardingListener(
    var onInsert: (DocumentEvent) -> Unit,
    var onRemove: (DocumentEvent) -> Unit,
    var onChange: (DocumentEvent) -> Unit,
) : DocumentListener {
    override fun insertUpdate(event: DocumentEvent) {
        onInsert(event)
    }

    override fun removeUpdate(event: DocumentEvent) {
        onRemove(event)
    }

    override fun changedUpdate(event: DocumentEvent) {
        onChange(event)
    }
}
