// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JLabel

/** The parameters [MouseWheelListenerFixture] varies: the callback run when the wheel turns over the component. */
public data class MouseWheelListenerParameters(
    val onMouseWheel: (MouseWheelEvent) -> Unit,
)

/**
 * A [Label] with a mouse wheel callback declared through `mouseWheelListener`, against a raw [JLabel]
 * with the same callback attached by hand.
 */
public object MouseWheelListenerFixture : ComposableFixture<MouseWheelListenerParameters, JLabel>(
    initial = MouseWheelListenerParameters(onMouseWheel = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<MouseWheelListenerParameters> =
        listOf(
            initial.copy(onMouseWheel = {}),
        )

    @Composable
    override fun Content(parameters: MouseWheelListenerParameters): Unit = Content(parameters.onMouseWheel)

    /** Composes a [Label] that runs [onMouseWheel] when the wheel turns over it. */
    @Composable
    public fun Content(onMouseWheel: (MouseWheelEvent) -> Unit) {
        Label(
            text = "",
            modifier = SwingModifier.mouseWheelListener(onMouseWheel = onMouseWheel),
        )
    }

    /** Builds a bare [JLabel] before any of the fixture's callbacks are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Adds the listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: MouseWheelListenerParameters,
    ) {
        val existing = component.mouseWheelListeners.firstNotNullOfOrNull { it as? ForwardingWheel }
        if (existing == null) {
            component.addMouseWheelListener(ForwardingWheel(parameters.onMouseWheel))
        } else {
            existing.onMouseWheel = parameters.onMouseWheel
        }
    }
}

/** The listener [MouseWheelListenerFixture] adds to its raw component, so that it finds only its own. */
private class ForwardingWheel(
    var onMouseWheel: (MouseWheelEvent) -> Unit,
) : MouseWheelListener {
    override fun mouseWheelMoved(event: MouseWheelEvent) {
        onMouseWheel(event)
    }
}
