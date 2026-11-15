// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.net.URI
import java.net.URL
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.html.HTMLDocument

/**
 * The parameters [EditorPaneFixture] varies: the markup, the link callback, the enabled state, the foreground
 * color, the content type and the base location of the rendered markup.
 */
public data class EditorPaneParameters(
    val markup: String,
    val onLinkActivate: (String) -> Unit,
    val enabled: Boolean,
    val color: Color,
    val contentType: String,
    val baseUrl: URL?,
)

/**
 * An [EditorPane] with the link callback overload against a raw [JEditorPane] holding the same markup, settings and
 * base location, reporting link activations to the same callback.
 */
public object EditorPaneFixture : ComposableFixture<EditorPaneParameters, JEditorPane>(
    initial =
        EditorPaneParameters(
            markup = "<b>Before</b>",
            onLinkActivate = { },
            enabled = true,
            color = Color.BLUE,
            contentType = "text/html",
            baseUrl = URI("file:/before/").toURL(),
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<EditorPaneParameters> =
        listOf(
            initial.copy(markup = "<b>After</b>"),
            initial.copy(onLinkActivate = { }),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(contentType = "text/plain"),
            initial.copy(baseUrl = URI("file:/after/").toURL()),
        )

    @Composable
    override fun Content(parameters: EditorPaneParameters): Unit =
        Content(
            markup = parameters.markup,
            onLinkActivate = parameters.onLinkActivate,
            enabled = parameters.enabled,
            color = parameters.color,
            contentType = parameters.contentType,
            baseUrl = parameters.baseUrl,
        )

    /** Composes an [EditorPane] rendering [markup], reporting link activations to [onLinkActivate]. */
    @Composable
    public fun Content(
        markup: String,
        onLinkActivate: (String) -> Unit,
        enabled: Boolean,
        color: Color,
        contentType: String,
        baseUrl: URL?,
    ) {
        EditorPane(
            markup = markup,
            onLinkActivate = onLinkActivate,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
            contentType = contentType,
            baseUrl = baseUrl,
        )
    }

    /** Builds a bare [JEditorPane] before any of the fixture's values are applied. */
    override fun createRawSwing(): JEditorPane = JEditorPane()

    /**
     * Applies the values of [parameters] to [component] with their Swing setters. The link listener this fixture
     * added earlier stays attached and calls the latest callback.
     */
    override fun updateRawSwing(
        component: JEditorPane,
        parameters: EditorPaneParameters,
    ) {
        applySettings(component, parameters)
        val listener =
            component.hyperlinkListeners.firstNotNullOfOrNull { it as? LinkActivationListener }
                ?: LinkActivationListener().also { component.addHyperlinkListener(it) }
        listener.onLinkActivate = parameters.onLinkActivate
    }

    /**
     * Applies everything of [parameters] to [component] except the link callback. A document keeps the markup it was
     * given, so that unchanged markup is not parsed again, and a content type it already has is not installed again.
     */
    internal fun applySettings(
        component: JEditorPane,
        parameters: EditorPaneParameters,
    ) {
        val markupKey = "fixtureMarkup"
        component.isEditable = false
        if (component.contentType != parameters.contentType) component.contentType = parameters.contentType
        val document = component.document
        if (document is HTMLDocument && document.base != parameters.baseUrl) document.base = parameters.baseUrl
        if (document.getProperty(markupKey) != parameters.markup) {
            component.text = parameters.markup
            document.putProperty(markupKey, parameters.markup)
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
    }

    /** Reports the target of every activated link to [onLinkActivate]. */
    private class LinkActivationListener : HyperlinkListener {
        var onLinkActivate: (String) -> Unit = {}

        override fun hyperlinkUpdate(event: HyperlinkEvent) {
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) onLinkActivate(event.description)
        }
    }
}
