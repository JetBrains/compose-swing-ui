// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.componentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JLabel

/** The parameters [ComponentListenerChangeFixture] varies: the one callback run on every component change. */
public data class ComponentListenerChangeParameters(
    val onComponentChange: (ComponentEvent) -> Unit,
)

/** A [Label] with one change callback declared through `componentListener(onComponentChange)`, against [JLabel]. */
public object ComponentListenerChangeFixture : ComposableFixture<ComponentListenerChangeParameters, JLabel>(
    initial = ComponentListenerChangeParameters(onComponentChange = { }),
) {
    /** The single-change copy of [initial], with a different callback. */
    override val alternatives: List<ComponentListenerChangeParameters> =
        listOf(
            initial.copy(onComponentChange = { }),
        )

    @Composable
    override fun Content(parameters: ComponentListenerChangeParameters): Unit = Content(parameters.onComponentChange)

    /** Composes a [Label] whose component callback is [onComponentChange]. */
    @Composable
    public fun Content(onComponentChange: (ComponentEvent) -> Unit) {
        Label(
            text = "",
            modifier = SwingModifier.componentListener(onComponentChange = onComponentChange),
        )
    }

    /** Builds a bare [JLabel] with no component listener attached. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Adds the listener on the first call; later calls only replace its callback with that of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: ComponentListenerChangeParameters,
    ) {
        val existing =
            component.componentListeners.firstNotNullOfOrNull { it as? ComponentListenerChangeForwardingListener }
        if (existing == null) {
            component.addComponentListener(ComponentListenerChangeForwardingListener(parameters.onComponentChange))
        } else {
            existing.onComponentChange = parameters.onComponentChange
        }
    }
}

/** The component listener this fixture installs on its raw component, so it finds only its own. */
private class ComponentListenerChangeForwardingListener(
    var onComponentChange: (ComponentEvent) -> Unit,
) : ComponentAdapter() {
    override fun componentResized(event: ComponentEvent) = onComponentChange(event)

    override fun componentMoved(event: ComponentEvent) = onComponentChange(event)

    override fun componentShown(event: ComponentEvent) = onComponentChange(event)

    override fun componentHidden(event: ComponentEvent) = onComponentChange(event)
}
