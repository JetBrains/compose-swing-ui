// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkListener

/** The parameters [EditorPaneListenerFixture] varies: the listener that hears the pane's link events. */
public data class EditorPaneListenerParameters(
    val hyperlinkListener: HyperlinkListener,
)

/**
 * An [EditorPane] given a [HyperlinkListener] against a raw [JEditorPane] with the same listener added by hand. The
 * markup, settings and base location are those of [EditorPaneFixture.initial].
 */
public object EditorPaneListenerFixture : ComposableFixture<EditorPaneListenerParameters, JEditorPane>(
    initial = EditorPaneListenerParameters(hyperlinkListener = { }),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<EditorPaneListenerParameters> =
        listOf(
            initial.copy(hyperlinkListener = { }),
        )

    @Composable
    override fun Content(parameters: EditorPaneListenerParameters): Unit = Content(parameters.hyperlinkListener)

    /** Composes an [EditorPane] reporting link events to [hyperlinkListener], with the settings of the primary. */
    @Composable
    public fun Content(hyperlinkListener: HyperlinkListener) {
        val settings = EditorPaneFixture.initial
        EditorPane(
            markup = settings.markup,
            hyperlinkListener = hyperlinkListener,
            style =
                SwingStyle {
                    enabled(settings.enabled)
                    foreground(settings.color)
                },
            contentType = settings.contentType,
            baseUrl = settings.baseUrl,
        )
    }

    /** Builds a [JEditorPane] in the shared state of [EditorPaneFixture.initial], before the listener is added. */
    override fun createRawSwing(): JEditorPane =
        JEditorPane().also { EditorPaneFixture.applySettings(it, EditorPaneFixture.initial) }

    override fun updateRawSwing(
        component: JEditorPane,
        parameters: EditorPaneListenerParameters,
    ) {
        if (parameters.hyperlinkListener !in component.hyperlinkListeners) {
            component.removeHyperlinkListener(initial.hyperlinkListener)
            alternatives.forEach { component.removeHyperlinkListener(it.hyperlinkListener) }
            component.addHyperlinkListener(parameters.hyperlinkListener)
        }
    }
}
