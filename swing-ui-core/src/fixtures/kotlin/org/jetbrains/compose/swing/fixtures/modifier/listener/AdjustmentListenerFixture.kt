// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.adjustmentListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import javax.swing.JScrollBar

/** The parameters [AdjustmentListenerFixture] varies: the callback run on each adjustment. */
public data class AdjustmentListenerParameters(
    val onAdjustment: (AdjustmentEvent) -> Unit,
)

/**
 * A [SwingNode] holding a [JScrollBar] with an [adjustmentListener] callback, against a raw [JScrollBar]
 * with the same callback attached by hand.
 */
public object AdjustmentListenerFixture : ComposableFixture<AdjustmentListenerParameters, JScrollBar>(
    initial = AdjustmentListenerParameters(onAdjustment = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<AdjustmentListenerParameters> =
        listOf(
            initial.copy(onAdjustment = {}),
        )

    @Composable
    override fun Content(parameters: AdjustmentListenerParameters): Unit = Content(parameters.onAdjustment)

    /** Composes a scrollbar host that runs [onAdjustment] on each adjustment. */
    @Composable
    public fun Content(onAdjustment: (AdjustmentEvent) -> Unit) {
        SwingNode<JScrollBar>(
            factory = { JScrollBar() },
            modifier = SwingModifier.adjustmentListener(onAdjustment = onAdjustment),
        )
    }

    /** Builds a bare [JScrollBar] before any of the fixture's values are applied. */
    override fun createRawSwing(): JScrollBar = JScrollBar()

    /** Adds the listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JScrollBar,
        parameters: AdjustmentListenerParameters,
    ) {
        val existing = component.adjustmentListeners.firstNotNullOfOrNull { it as? ForwardListener }
        if (existing == null) {
            component.addAdjustmentListener(ForwardListener(parameters.onAdjustment))
        } else {
            existing.onAdjustment = parameters.onAdjustment
        }
    }
}

/** The listener [AdjustmentListenerFixture] installs on its raw component, so that it finds only its own. */
private class ForwardListener(
    var onAdjustment: (AdjustmentEvent) -> Unit,
) : AdjustmentListener {
    override fun adjustmentValueChanged(event: AdjustmentEvent) {
        onAdjustment(event)
    }
}
