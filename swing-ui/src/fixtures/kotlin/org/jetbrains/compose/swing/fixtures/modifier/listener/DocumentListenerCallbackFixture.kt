// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The parameters [DocumentListenerCallbackFixture] varies. */
public data class DocumentListenerCallbackParameters(
    val onDocumentChange: (DocumentEvent) -> Unit,
)

/** A [documentListener] given one document callback against a raw [JTextField] with the same callback added by hand. */
public object DocumentListenerCallbackFixture : ComposableFixture<DocumentListenerCallbackParameters, JTextField>(
    initial = DocumentListenerCallbackParameters(onDocumentChange = {}),
) {
    override val alternatives: List<DocumentListenerCallbackParameters> =
        listOf(
            initial.copy(onDocumentChange = {}),
        )

    @Composable
    override fun Content(parameters: DocumentListenerCallbackParameters): Unit = Content(parameters.onDocumentChange)

    /** Composes a [JTextField] whose document reports every change to [onDocumentChange]. */
    @Composable
    public fun Content(onDocumentChange: (DocumentEvent) -> Unit) {
        SwingNode(
            factory = { JTextField() },
            modifier = SwingModifier.documentListener(onDocumentChange = onDocumentChange),
        )
    }

    /** Builds a [JTextField] with no document listener attached. */
    override fun createRawSwing(): JTextField = JTextField()

    /** Adds the document listener on the first call; later calls only replace its callback from [parameters]. */
    override fun updateRawSwing(
        component: JTextField,
        parameters: DocumentListenerCallbackParameters,
    ) {
        val key = "documentListener"
        val existing = component.getClientProperty(key)
        if (existing is DocumentListenerCallbackForwardingListener) {
            existing.onDocumentChange = parameters.onDocumentChange
        } else {
            val listener = DocumentListenerCallbackForwardingListener(parameters.onDocumentChange)
            component.putClientProperty(key, listener)
            component.document.addDocumentListener(listener)
        }
    }
}

/** The document listener [DocumentListenerCallbackFixture] installs on its raw component, so it finds only its own. */
private class DocumentListenerCallbackForwardingListener(
    var onDocumentChange: (DocumentEvent) -> Unit,
) : DocumentListener {
    override fun insertUpdate(event: DocumentEvent) {
        onDocumentChange(event)
    }

    override fun removeUpdate(event: DocumentEvent) {
        onDocumentChange(event)
    }

    override fun changedUpdate(event: DocumentEvent) {
        onDocumentChange(event)
    }
}
