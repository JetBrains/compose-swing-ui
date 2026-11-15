// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.hyperlinkListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkListener

/** The parameters [HyperlinkListenerListenerFixture] varies: the [HyperlinkListener] instance attached to the pane. */
public data class HyperlinkListenerListenerParameters(
    val listener: HyperlinkListener,
)

/**
 * A [JEditorPane] host carrying the listener form of [hyperlinkListener], against the same pane with the
 * listener attached by hand.
 */
public object HyperlinkListenerListenerFixture :
    ComposableFixture<HyperlinkListenerListenerParameters, JEditorPane>(
        initial = HyperlinkListenerListenerParameters(listener = { }),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<HyperlinkListenerListenerParameters> =
        listOf(
            initial.copy(listener = { }),
        )

    @Composable
    override fun Content(parameters: HyperlinkListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [JEditorPane] host with [listener] attached to its hyperlink events. */
    @Composable
    public fun Content(listener: HyperlinkListener) {
        SwingNode(
            factory = { JEditorPane("text/html", "<a href='https://example.test'>Link</a>") },
            modifier = SwingModifier.hyperlinkListener(listener = listener),
        )
    }

    /** Builds a bare [JEditorPane] holding a single link, before any listener is attached. */
    override fun createRawSwing(): JEditorPane = JEditorPane("text/html", "<a href='https://example.test'>Link</a>")

    /** Replaces the fixture's earlier hyperlink listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JEditorPane,
        parameters: HyperlinkListenerListenerParameters,
    ) {
        component.removeHyperlinkListener(initial.listener)
        alternatives.forEach { component.removeHyperlinkListener(it.listener) }
        component.addHyperlinkListener(parameters.listener)
    }
}
