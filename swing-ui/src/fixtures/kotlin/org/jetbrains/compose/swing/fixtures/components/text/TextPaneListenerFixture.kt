// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import javax.swing.JTextPane
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument

/** The parameters [TextPaneListenerFixture] varies: the listener that hears the pane's document edits. */
public data class TextPaneListenerParameters(
    val documentListener: DocumentListener,
)

/**
 * A [TextPane] given a [DocumentListener] against a raw [JTextPane] with the same listener added by hand. The text,
 * enabled state, foreground color and editable setting are those of [TextPaneFixture.initial].
 */
public object TextPaneListenerFixture : ComposableFixture<TextPaneListenerParameters, JTextPane>(
    initial = TextPaneListenerParameters(documentListener = EditListener {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TextPaneListenerParameters> =
        listOf(
            initial.copy(documentListener = EditListener {}),
        )

    @Composable
    override fun Content(parameters: TextPaneListenerParameters): Unit = Content(parameters.documentListener)

    /** Composes a [TextPane] reporting document edits to [documentListener], with the settings of the primary. */
    @Composable
    public fun Content(documentListener: DocumentListener) {
        val settings = TextPaneFixture.initial
        TextPane(
            value = settings.value,
            documentListener = documentListener,
            style =
                SwingStyle {
                    enabled(settings.enabled)
                    foreground(settings.color)
                },
            editable = settings.editable,
        )
    }

    /** Builds a [JTextPane] in the shared state of [TextPaneFixture.initial], before the listener is added. */
    override fun createRawSwing(): JTextPane =
        JTextPane().also { TextPaneFixture.applySettings(it, TextPaneFixture.initial) }

    /**
     * Attaches the document listener of [parameters] to [component] unless it is already attached. Otherwise, the
     * listeners this fixture attached for [initial] and [alternatives] are removed first.
     */
    override fun updateRawSwing(
        component: JTextPane,
        parameters: TextPaneListenerParameters,
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
