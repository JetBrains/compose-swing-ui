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

/** The parameters [DocumentListenerListenerFixture] varies. */
public data class DocumentListenerListenerParameters(
    val listener: DocumentListener,
)

/** A [documentListener] given a [DocumentListener] against a raw [JTextField] with the same listener added by hand. */
public object DocumentListenerListenerFixture : ComposableFixture<DocumentListenerListenerParameters, JTextField>(
    initial =
        DocumentListenerListenerParameters(
            listener =
                object : DocumentListener {
                    override fun insertUpdate(event: DocumentEvent) = Unit

                    override fun removeUpdate(event: DocumentEvent) = Unit

                    override fun changedUpdate(event: DocumentEvent) = Unit
                },
        ),
) {
    override val alternatives: List<DocumentListenerListenerParameters> =
        listOf(
            initial.copy(
                listener =
                    object : DocumentListener {
                        override fun insertUpdate(event: DocumentEvent) = Unit

                        override fun removeUpdate(event: DocumentEvent) = Unit

                        override fun changedUpdate(event: DocumentEvent) = Unit
                    },
            ),
        )

    @Composable
    override fun Content(parameters: DocumentListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [JTextField] whose document reports to [listener]. */
    @Composable
    public fun Content(listener: DocumentListener) {
        SwingNode(
            factory = { JTextField() },
            modifier = SwingModifier.documentListener(listener = listener),
        )
    }

    /** Builds a [JTextField] with no document listener attached. */
    override fun createRawSwing(): JTextField = JTextField()

    /** Replaces the document listener on [component] with [parameters]'s listener. */
    override fun updateRawSwing(
        component: JTextField,
        parameters: DocumentListenerListenerParameters,
    ) {
        val key = "documentListener"
        val previous = component.getClientProperty(key)
        if (previous is DocumentListener) component.document.removeDocumentListener(previous)
        component.putClientProperty(key, parameters.listener)
        component.document.addDocumentListener(parameters.listener)
    }
}
