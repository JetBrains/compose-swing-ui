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

/** The parameters [ComponentListenerFixture] varies: one callback for each of the four component changes. */
public data class ComponentListenerParameters(
    val onComponentResized: (ComponentEvent) -> Unit,
    val onComponentMoved: (ComponentEvent) -> Unit,
    val onComponentShown: (ComponentEvent) -> Unit,
    val onComponentHidden: (ComponentEvent) -> Unit,
)

/** A [Label] with four callbacks declared through `componentListener(...)`, against a raw [JLabel]. */
public object ComponentListenerFixture : ComposableFixture<ComponentListenerParameters, JLabel>(
    initial =
        ComponentListenerParameters(
            onComponentResized = { },
            onComponentMoved = { },
            onComponentShown = { },
            onComponentHidden = { },
        ),
) {
    /** The single-change copies of [initial], one for each callback. */
    override val alternatives: List<ComponentListenerParameters> =
        listOf(
            initial.copy(onComponentResized = { }),
            initial.copy(onComponentMoved = { }),
            initial.copy(onComponentShown = { }),
            initial.copy(onComponentHidden = { }),
        )

    @Composable
    override fun Content(parameters: ComponentListenerParameters): Unit =
        Content(
            parameters.onComponentResized,
            parameters.onComponentMoved,
            parameters.onComponentShown,
            parameters.onComponentHidden,
        )

    /** Composes a [Label] whose component callbacks are the given lambdas. */
    @Composable
    public fun Content(
        onComponentResized: (ComponentEvent) -> Unit,
        onComponentMoved: (ComponentEvent) -> Unit,
        onComponentShown: (ComponentEvent) -> Unit,
        onComponentHidden: (ComponentEvent) -> Unit,
    ) {
        Label(
            text = "",
            modifier =
                SwingModifier.componentListener(
                    onComponentResized = onComponentResized,
                    onComponentMoved = onComponentMoved,
                    onComponentShown = onComponentShown,
                    onComponentHidden = onComponentHidden,
                ),
        )
    }

    /** Builds a bare [JLabel] with no component listener attached. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Adds the listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: ComponentListenerParameters,
    ) {
        val existing =
            component.componentListeners.firstNotNullOfOrNull { it as? ComponentListenerForwardingListener }
        if (existing == null) {
            component.addComponentListener(
                ComponentListenerForwardingListener(
                    parameters.onComponentResized,
                    parameters.onComponentMoved,
                    parameters.onComponentShown,
                    parameters.onComponentHidden,
                ),
            )
        } else {
            existing.onComponentResized = parameters.onComponentResized
            existing.onComponentMoved = parameters.onComponentMoved
            existing.onComponentShown = parameters.onComponentShown
            existing.onComponentHidden = parameters.onComponentHidden
        }
    }
}

/** The component listener [ComponentListenerFixture] installs on its raw component, so that it finds only its own. */
private class ComponentListenerForwardingListener(
    var onComponentResized: (ComponentEvent) -> Unit,
    var onComponentMoved: (ComponentEvent) -> Unit,
    var onComponentShown: (ComponentEvent) -> Unit,
    var onComponentHidden: (ComponentEvent) -> Unit,
) : ComponentAdapter() {
    override fun componentResized(event: ComponentEvent) {
        onComponentResized(event)
    }

    override fun componentMoved(event: ComponentEvent) {
        onComponentMoved(event)
    }

    override fun componentShown(event: ComponentEvent) {
        onComponentShown(event)
    }

    override fun componentHidden(event: ComponentEvent) {
        onComponentHidden(event)
    }
}
