// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import javax.swing.JTextPane
import javax.swing.text.DefaultStyledDocument

/**
 * The parameters [TextPaneStateFixture] varies: the text of the document the state adopts.
 */
public data class TextPaneStateParameters(
    val text: String,
)

/**
 * A [TextPane] driven by a document state against a raw [JTextPane] adopting a fresh document of the same
 * text. The enabled state, foreground color and editable setting are those of [TextPaneFixture.initial].
 */
public object TextPaneStateFixture : ComposableFixture<TextPaneStateParameters, JTextPane>(
    initial =
        TextPaneStateParameters(
            text = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TextPaneStateParameters> =
        listOf(
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: TextPaneStateParameters): Unit = Content(parameters.text)

    /** Composes a [TextPane] rendering a document state over a document remembered for [text]. */
    @Composable
    public fun Content(text: String) {
        val settings = TextPaneFixture.initial
        val document = remember(text) { DefaultStyledDocument().apply { insertString(0, text, null) } }
        val state = rememberDocumentState(document = document)
        TextPane(
            state = state,
            style =
                SwingStyle {
                    enabled(settings.enabled)
                    foreground(settings.color)
                },
            editable = settings.editable,
        )
    }

    /** Builds a bare [JTextPane] with the editable setting of [TextPaneFixture.initial] already applied. */
    override fun createRawSwing(): JTextPane = JTextPane().apply { isEditable = TextPaneFixture.initial.editable }

    /**
     * Applies the values of [parameters] to [component] with their Swing setters, placing the caret at the end of the
     * document as a state that adopts it does.
     */
    override fun updateRawSwing(
        component: JTextPane,
        parameters: TextPaneStateParameters,
    ) {
        component.styledDocument = DefaultStyledDocument().apply { insertString(0, parameters.text, null) }
        component.caretPosition = parameters.text.length
        component.isEnabled = TextPaneFixture.initial.enabled
        component.foreground = TextPaneFixture.initial.color
    }
}
