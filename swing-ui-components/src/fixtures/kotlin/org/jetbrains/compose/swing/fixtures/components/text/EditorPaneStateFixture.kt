// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import javax.swing.JEditorPane
import javax.swing.text.PlainDocument

/**
 * The parameters [EditorPaneStateFixture] varies: the text of the document the state adopts.
 */
public data class EditorPaneStateParameters(
    val text: String,
)

/**
 * An [EditorPane] driven by a document state against a raw [JEditorPane] adopting a fresh document of the same
 * text. The enabled state and foreground color are those of [EditorPaneFixture.initial].
 */
public object EditorPaneStateFixture : ComposableFixture<EditorPaneStateParameters, JEditorPane>(
    initial =
        EditorPaneStateParameters(
            text = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<EditorPaneStateParameters> =
        listOf(
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: EditorPaneStateParameters): Unit = Content(parameters.text)

    /** Composes an [EditorPane] rendering a document state over a document remembered for [text]. */
    @Composable
    public fun Content(text: String) {
        val settings = EditorPaneFixture.initial
        val document = remember(text) { PlainDocument().apply { insertString(0, text, null) } }
        val state = rememberDocumentState(document = document)
        EditorPane(
            state = state,
            style =
                SwingStyle {
                    enabled(settings.enabled)
                    foreground(settings.color)
                },
            editable = true,
        )
    }

    /** Builds a bare [JEditorPane] before any of the fixture's values are applied. */
    override fun createRawSwing(): JEditorPane = JEditorPane().apply { isEditable = true }

    /**
     * Applies the values of [parameters] to [component] with their Swing setters, the document after the content
     * type so that it replaces the default document of the plain text kit, and places the caret at the end of the
     * document as a state that adopts it does.
     */
    override fun updateRawSwing(
        component: JEditorPane,
        parameters: EditorPaneStateParameters,
    ) {
        component.contentType = "text/plain"
        component.document = PlainDocument().apply { insertString(0, parameters.text, null) }
        component.caretPosition = parameters.text.length
        component.isEnabled = EditorPaneFixture.initial.enabled
        component.foreground = EditorPaneFixture.initial.color
    }
}
