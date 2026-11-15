// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.componentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentListener
import javax.swing.JLabel

/** The parameters [ComponentListenerListenerFixture] varies: the [ComponentListener] attached to the component. */
public data class ComponentListenerListenerParameters(
    val listener: ComponentListener,
)

/** A [Label] with a [ComponentListener] declared through `componentListener(listener)`, against a raw [JLabel]. */
public object ComponentListenerListenerFixture : ComposableFixture<ComponentListenerListenerParameters, JLabel>(
    initial = ComponentListenerListenerParameters(listener = object : ComponentAdapter() {}),
) {
    /** The single-change copy of [initial], with a different listener instance. */
    override val alternatives: List<ComponentListenerListenerParameters> =
        listOf(
            initial.copy(listener = object : ComponentAdapter() {}),
        )

    @Composable
    override fun Content(parameters: ComponentListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [Label] with [listener] attached to its component. */
    @Composable
    public fun Content(listener: ComponentListener) {
        Label(
            text = "",
            modifier = SwingModifier.componentListener(listener = listener),
        )
    }

    /** Builds a bare [JLabel] with no component listener attached. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Replaces the fixture's earlier component listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: ComponentListenerListenerParameters,
    ) {
        component.removeComponentListener(initial.listener)
        alternatives.forEach { component.removeComponentListener(it.listener) }
        component.addComponentListener(parameters.listener)
    }
}
