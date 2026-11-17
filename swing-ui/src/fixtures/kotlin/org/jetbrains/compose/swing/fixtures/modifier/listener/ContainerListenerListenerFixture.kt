// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.containerListener
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerListener
import javax.swing.JPanel

/** The parameters [ContainerListenerListenerFixture] varies: the container listener attached to the panel. */
public data class ContainerListenerListenerParameters(
    val listener: ContainerListener,
)

/**
 * A [Panel] holding a [JPanel] with a [containerListener] listener against a raw [JPanel] with the same listener.
 */
public object ContainerListenerListenerFixture : ComposableFixture<ContainerListenerListenerParameters, JPanel>(
    initial = ContainerListenerListenerParameters(listener = object : ContainerAdapter() {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ContainerListenerListenerParameters> =
        listOf(
            initial.copy(listener = object : ContainerAdapter() {}),
        )

    @Composable
    override fun Content(parameters: ContainerListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a panel host with [listener] attached. */
    @Composable
    public fun Content(listener: ContainerListener) {
        Panel(modifier = SwingModifier.containerListener(listener)) {}
    }

    /** Builds a bare [JPanel] before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel = JPanel()

    /** Replaces the fixture's earlier container listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: ContainerListenerListenerParameters,
    ) {
        component.removeContainerListener(initial.listener)
        alternatives.forEach { component.removeContainerListener(it.listener) }
        component.addContainerListener(parameters.listener)
    }
}
