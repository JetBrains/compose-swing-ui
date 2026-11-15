// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import java.beans.PropertyChangeListener
import javax.swing.JLabel

/** The parameters [PropertyChangeListenerListenerFixture] varies: the listener attached to the component. */
public data class PropertyChangeListenerListenerParameters(
    val listener: PropertyChangeListener,
)

/**
 * A [Label] with a [PropertyChangeListener] declared through `propertyChangeListener(listener)`, against a raw
 * [JLabel] with the same listener attached by hand.
 */
public object PropertyChangeListenerListenerFixture :
    ComposableFixture<PropertyChangeListenerListenerParameters, JLabel>(
        initial = PropertyChangeListenerListenerParameters(listener = { }),
    ) {
    /** The single-change copy of [initial], with a different listener instance. */
    override val alternatives: List<PropertyChangeListenerListenerParameters> =
        listOf(
            initial.copy(listener = { }),
        )

    @Composable
    override fun Content(parameters: PropertyChangeListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [Label] with [listener] declared as its property change listener. */
    @Composable
    public fun Content(listener: PropertyChangeListener) {
        Label(
            text = "",
            modifier = SwingModifier.propertyChangeListener(listener = listener),
        )
    }

    /** Builds a bare [JLabel] with no property change listener attached. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Replaces the fixture's earlier property change listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: PropertyChangeListenerListenerParameters,
    ) {
        component.removePropertyChangeListener(initial.listener)
        alternatives.forEach { component.removePropertyChangeListener(it.listener) }
        component.addPropertyChangeListener(parameters.listener)
    }
}
