// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTextArea
import javax.swing.text.PlainDocument

/** The parameters [TextAreaStateFixture] varies: the text of the document the state adopts. */
public data class TextAreaStateParameters(
    val text: String,
)

/** A [TextArea] driven by a document state against a raw [JTextArea] adopting a fresh document of the same text. */
public object TextAreaStateFixture : ComposableFixture<TextAreaStateParameters, JTextArea>(
    initial = TextAreaStateParameters(text = "Before"),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TextAreaStateParameters> =
        listOf(
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: TextAreaStateParameters): Unit = Content(parameters.text)

    /** Composes a [TextArea] rendering a document state over a document remembered for [text]. */
    @Composable
    public fun Content(text: String) {
        val document = remember(text) { PlainDocument().apply { insertString(0, text, null) } }
        val state = rememberDocumentState(document = document)
        TextArea(state = state)
    }

    /** Builds a bare [JTextArea] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTextArea = JTextArea()

    /**
     * Replaces the document of [component] with a fresh document of the text of [parameters] and places the caret
     * at its end.
     */
    override fun updateRawSwing(
        component: JTextArea,
        parameters: TextAreaStateParameters,
    ) {
        component.document = PlainDocument().apply { insertString(0, parameters.text, null) }
        component.caretPosition = parameters.text.length
    }
}
