// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.containerListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JPanel

/** The parameters [ContainerListenerDirectionalFixture] varies: the callbacks run on an add and on a removal. */
public data class ContainerListenerDirectionalParameters(
    val onComponentAdded: (ContainerEvent) -> Unit,
    val onComponentRemoved: (ContainerEvent) -> Unit,
)

/**
 * A [SwingNode] holding a [JPanel] with split [containerListener] callbacks against a raw [JPanel] with the same
 * callbacks.
 */
public object ContainerListenerDirectionalFixture : ComposableFixture<ContainerListenerDirectionalParameters, JPanel>(
    initial = ContainerListenerDirectionalParameters(onComponentAdded = {}, onComponentRemoved = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ContainerListenerDirectionalParameters> =
        listOf(
            initial.copy(onComponentAdded = {}),
            initial.copy(onComponentRemoved = {}),
        )

    @Composable
    override fun Content(parameters: ContainerListenerDirectionalParameters): Unit =
        Content(parameters.onComponentAdded, parameters.onComponentRemoved)

    /** Composes a panel host that runs [onComponentAdded] and [onComponentRemoved] on the matching child events. */
    @Composable
    public fun Content(
        onComponentAdded: (ContainerEvent) -> Unit,
        onComponentRemoved: (ContainerEvent) -> Unit,
    ) {
        SwingNode<JPanel>(
            factory = { JPanel() },
            modifier =
                SwingModifier.containerListener(
                    onComponentAdded = onComponentAdded,
                    onComponentRemoved = onComponentRemoved,
                ),
        )
    }

    /** Builds a bare [JPanel] before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel = JPanel()

    /** Adds the listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: ContainerListenerDirectionalParameters,
    ) {
        val existing = component.containerListeners.firstNotNullOfOrNull { it as? ContainerForwarder }
        if (existing == null) {
            component.addContainerListener(
                ContainerForwarder(parameters.onComponentAdded, parameters.onComponentRemoved),
            )
        } else {
            existing.onComponentAdded = parameters.onComponentAdded
            existing.onComponentRemoved = parameters.onComponentRemoved
        }
    }
}

/** The container listener the raw side of [ContainerListenerDirectionalFixture] adds, so it finds only its own. */
private class ContainerForwarder(
    var onComponentAdded: (ContainerEvent) -> Unit,
    var onComponentRemoved: (ContainerEvent) -> Unit,
) : ContainerAdapter() {
    override fun componentAdded(event: ContainerEvent) {
        onComponentAdded(event)
    }

    override fun componentRemoved(event: ContainerEvent) {
        onComponentRemoved(event)
    }
}
