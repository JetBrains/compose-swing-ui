// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.hyperlinkListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/** The parameters [HyperlinkListenerFixture] varies: the callback that receives hyperlink events. */
public data class HyperlinkListenerParameters(
    val onHyperlinkUpdate: (HyperlinkEvent) -> Unit,
)

/**
 * A [JEditorPane] host carrying the callback form of [hyperlinkListener], against the same pane with a
 * [javax.swing.event.HyperlinkListener] wrapping the callback attached by hand.
 */
public object HyperlinkListenerFixture : ComposableFixture<HyperlinkListenerParameters, JEditorPane>(
    initial = HyperlinkListenerParameters(onHyperlinkUpdate = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<HyperlinkListenerParameters> =
        listOf(
            initial.copy(onHyperlinkUpdate = {}),
        )

    @Composable
    override fun Content(parameters: HyperlinkListenerParameters): Unit = Content(parameters.onHyperlinkUpdate)

    /** Composes a [JEditorPane] host whose hyperlink events are passed to [onHyperlinkUpdate]. */
    @Composable
    public fun Content(onHyperlinkUpdate: (HyperlinkEvent) -> Unit) {
        SwingNode(
            factory = { JEditorPane("text/html", "<a href='https://example.test'>Link</a>") },
            modifier = SwingModifier.hyperlinkListener(onHyperlinkUpdate = onHyperlinkUpdate),
        )
    }

    /** Builds a bare [JEditorPane] holding a single link, before any callback is attached. */
    override fun createRawSwing(): JEditorPane = JEditorPane("text/html", "<a href='https://example.test'>Link</a>")

    /** Adds the listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JEditorPane,
        parameters: HyperlinkListenerParameters,
    ) {
        val existing = component.hyperlinkListeners.firstNotNullOfOrNull { it as? HyperlinkCallbackForwarder }
        if (existing == null) {
            component.addHyperlinkListener(HyperlinkCallbackForwarder(parameters.onHyperlinkUpdate))
        } else {
            existing.onHyperlinkUpdate = parameters.onHyperlinkUpdate
        }
    }
}

/** The hyperlink listener [HyperlinkListenerFixture] installs on its raw component, so that it finds only its own. */
private class HyperlinkCallbackForwarder(
    var onHyperlinkUpdate: (HyperlinkEvent) -> Unit,
) : HyperlinkListener {
    override fun hyperlinkUpdate(event: HyperlinkEvent) {
        onHyperlinkUpdate(event)
    }
}
