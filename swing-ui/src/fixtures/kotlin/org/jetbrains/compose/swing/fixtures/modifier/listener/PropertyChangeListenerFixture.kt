// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JLabel

/** The parameters [PropertyChangeListenerFixture] varies: the one callback run on every bound property change. */
public data class PropertyChangeListenerParameters(
    val onPropertyChange: (PropertyChangeEvent) -> Unit,
)

/**
 * A [Label] with one property change callback declared through `propertyChangeListener(onPropertyChange)`,
 * against a raw [JLabel] with the same callback attached as a [PropertyChangeListener].
 */
public object PropertyChangeListenerFixture : ComposableFixture<PropertyChangeListenerParameters, JLabel>(
    initial = PropertyChangeListenerParameters(onPropertyChange = {}),
) {
    /** The single-change copy of [initial], with a different callback. */
    override val alternatives: List<PropertyChangeListenerParameters> =
        listOf(
            initial.copy(onPropertyChange = {}),
        )

    @Composable
    override fun Content(parameters: PropertyChangeListenerParameters): Unit = Content(parameters.onPropertyChange)

    /** Composes a [Label] whose property change callback is [onPropertyChange]. */
    @Composable
    public fun Content(onPropertyChange: (PropertyChangeEvent) -> Unit) {
        Label(
            text = "",
            modifier = SwingModifier.propertyChangeListener(onPropertyChange = onPropertyChange),
        )
    }

    /** Builds a bare [JLabel] with no property change listener attached. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Adds the listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: PropertyChangeListenerParameters,
    ) {
        val existing =
            component.propertyChangeListeners.firstNotNullOfOrNull { it as? PropertyChangeListenerForwarder }
        if (existing == null) {
            component.addPropertyChangeListener(PropertyChangeListenerForwarder(parameters.onPropertyChange))
        } else {
            existing.onPropertyChange = parameters.onPropertyChange
        }
    }
}

/**
 * The property change listener [PropertyChangeListenerFixture] installs on its raw component,
 * so that it finds only its own.
 */
private class PropertyChangeListenerForwarder(
    var onPropertyChange: (PropertyChangeEvent) -> Unit,
) : PropertyChangeListener {
    override fun propertyChange(event: PropertyChangeEvent) {
        onPropertyChange(event)
    }
}
