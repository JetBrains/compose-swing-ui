// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.containerListener
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JPanel

/** The parameters [ContainerListenerFixture] varies: the callback run when a child is added or removed. */
public data class ContainerListenerParameters(
    val onChildrenChange: (ContainerEvent) -> Unit,
)

/**
 * A [SwingNode] holding a [JPanel] with a [containerListener] callback against a raw [JPanel] with the same callback.
 */
public object ContainerListenerFixture : ComposableFixture<ContainerListenerParameters, JPanel>(
    initial = ContainerListenerParameters(onChildrenChange = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ContainerListenerParameters> =
        listOf(
            initial.copy(onChildrenChange = {}),
        )

    @Composable
    override fun Content(parameters: ContainerListenerParameters): Unit = Content(parameters.onChildrenChange)

    /** Composes a panel host that runs [onChildrenChange] when a child is added or removed. */
    @Composable
    public fun Content(onChildrenChange: (ContainerEvent) -> Unit) {
        Panel(modifier = SwingModifier.containerListener(onChildrenChange)) { }
    }

    /** Builds a bare [JPanel] before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel = JPanel()

    /** Adds the listener on the first call; later calls only replace its callback with that of [parameters]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: ContainerListenerParameters,
    ) {
        val existing = component.containerListeners.firstNotNullOfOrNull { it as? ForwardingContainerListener }
        if (existing == null) {
            component.addContainerListener(ForwardingContainerListener(parameters.onChildrenChange))
        } else {
            existing.onChildrenChange = parameters.onChildrenChange
        }
    }
}

/** The container listener [ContainerListenerFixture] installs on its raw component, so that it finds only its own. */
private class ForwardingContainerListener(
    var onChildrenChange: (ContainerEvent) -> Unit,
) : ContainerAdapter() {
    override fun componentAdded(event: ContainerEvent) {
        onChildrenChange(event)
    }

    override fun componentRemoved(event: ContainerEvent) {
        onChildrenChange(event)
    }
}
