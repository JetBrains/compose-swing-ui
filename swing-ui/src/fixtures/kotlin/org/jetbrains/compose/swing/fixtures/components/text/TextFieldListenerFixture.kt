// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTextField
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument

/** The parameters [TextFieldListenerFixture] varies: the document listener that observes edits. */
public data class TextFieldListenerParameters(
    val documentListener: DocumentListener,
)

/**
 * A [TextField] with the document listener overload against a raw [JTextField] showing the same text. The text is
 * the one of [TextFieldFixture.initial].
 */
public object TextFieldListenerFixture : ComposableFixture<TextFieldListenerParameters, JTextField>(
    initial = TextFieldListenerParameters(documentListener = EditListener {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TextFieldListenerParameters> =
        listOf(
            initial.copy(documentListener = EditListener {}),
        )

    @Composable
    override fun Content(parameters: TextFieldListenerParameters): Unit = Content(parameters.documentListener)

    /** Composes a [TextField] showing the shared text, reporting document edits to [documentListener]. */
    @Composable
    public fun Content(documentListener: DocumentListener) {
        TextField(
            value = TextFieldFixture.initial.value,
            documentListener = documentListener,
        )
    }

    /** Builds a [JTextField] showing the shared text, before any of the fixture's values are applied. */
    override fun createRawSwing(): JTextField = JTextField(TextFieldFixture.initial.value)

    /**
     * Attaches the document listener of [parameters] to [component] unless it is already attached. Otherwise, the
     * listeners this fixture attached for [initial] and [alternatives] are removed first.
     */
    override fun updateRawSwing(
        component: JTextField,
        parameters: TextFieldListenerParameters,
    ) {
        val document = component.document
        val attached = (document as? AbstractDocument)?.documentListeners.orEmpty()
        if (parameters.documentListener !in attached) {
            document.removeDocumentListener(initial.documentListener)
            alternatives.forEach { document.removeDocumentListener(it.documentListener) }
            document.addDocumentListener(parameters.documentListener)
        }
    }
}
