// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JPasswordField
import javax.swing.text.PlainDocument

/** The parameters [PasswordFieldStateFixture] varies: the text of the document the state adopts. */
public data class PasswordFieldStateParameters(
    val text: String,
)

/**
 * A [PasswordField] driven by a document state against a raw [JPasswordField] adopting a fresh document
 * of the same text.
 */
public object PasswordFieldStateFixture : ComposableFixture<PasswordFieldStateParameters, JPasswordField>(
    initial = PasswordFieldStateParameters(text = "Before"),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<PasswordFieldStateParameters> =
        listOf(
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: PasswordFieldStateParameters): Unit = Content(parameters.text)

    /** Composes a [PasswordField] rendering a document state over a document remembered for [text]. */
    @Composable
    public fun Content(text: String) {
        val document = remember(text) { PlainDocument().apply { insertString(0, text, null) } }
        val state = rememberDocumentState(document = document)
        PasswordField(state = state)
    }

    /** Builds a bare [JPasswordField] before any of the fixture's values are applied. */
    override fun createRawSwing(): JPasswordField = JPasswordField()

    /**
     * Replaces the document of [component] with a fresh document of the text of [parameters] and places the caret
     * at its end.
     */
    override fun updateRawSwing(
        component: JPasswordField,
        parameters: PasswordFieldStateParameters,
    ) {
        component.document = PlainDocument().apply { insertString(0, parameters.text, null) }
        component.caretPosition = parameters.text.length
    }
}
