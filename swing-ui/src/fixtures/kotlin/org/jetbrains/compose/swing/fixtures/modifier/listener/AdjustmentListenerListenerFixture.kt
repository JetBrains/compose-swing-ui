// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.adjustmentListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.AdjustmentListener
import javax.swing.JScrollBar

/** The parameters [AdjustmentListenerListenerFixture] varies: the listener attached to the scrollbar. */
public data class AdjustmentListenerListenerParameters(
    val listener: AdjustmentListener,
)

/**
 * A [SwingNode] holding a [JScrollBar] with an [AdjustmentListener] attached, against a raw [JScrollBar]
 * with the same listener attached by hand.
 */
public object AdjustmentListenerListenerFixture :
    ComposableFixture<AdjustmentListenerListenerParameters, JScrollBar>(
        initial = AdjustmentListenerListenerParameters(listener = {}),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<AdjustmentListenerListenerParameters> =
        listOf(
            initial.copy(listener = {}),
        )

    @Composable
    override fun Content(parameters: AdjustmentListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a scrollbar host with [listener] attached to it. */
    @Composable
    public fun Content(listener: AdjustmentListener) {
        SwingNode<JScrollBar>(
            factory = { JScrollBar() },
            modifier = SwingModifier.adjustmentListener(listener = listener),
        )
    }

    /** Builds a bare [JScrollBar] before any of the fixture's values are applied. */
    override fun createRawSwing(): JScrollBar = JScrollBar()

    /** Replaces the fixture's earlier adjustment listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JScrollBar,
        parameters: AdjustmentListenerListenerParameters,
    ) {
        component.removeAdjustmentListener(initial.listener)
        alternatives.forEach { component.removeAdjustmentListener(it.listener) }
        component.addAdjustmentListener(parameters.listener)
    }
}
