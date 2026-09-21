// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeListenerProxy
import javax.swing.JLabel

/** The parameters [PropertyChangeListenerNameFixture] varies: the bound property the callback listens to. */
public data class PropertyChangeListenerNameParameters(
    val name: String,
)

/**
 * A [Label] whose property change callback is bound to one property through `propertyChangeListener(name, ...)`,
 * against a raw [JLabel] with the same property listener attached by hand.
 */
public object PropertyChangeListenerNameFixture : ComposableFixture<PropertyChangeListenerNameParameters, JLabel>(
    initial = PropertyChangeListenerNameParameters(name = "background"),
) {
    /** The single-change copy of [initial], bound to a different property. */
    override val alternatives: List<PropertyChangeListenerNameParameters> =
        listOf(
            initial.copy(name = "foreground"),
        )

    @Composable
    override fun Content(parameters: PropertyChangeListenerNameParameters): Unit = Content(parameters.name)

    /** Composes a [Label] whose property change callback listens to the property [name]. */
    @Composable
    public fun Content(name: String) {
        Label(
            text = "",
            modifier = SwingModifier.propertyChangeListener(name = name, onPropertyChange = { }),
        )
    }

    /** Builds a bare [JLabel] with no property change listener attached. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Replaces the property change listeners of [component] with one bound to the property in [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: PropertyChangeListenerNameParameters,
    ) {
        for (listener in component.propertyChangeListeners) {
            if (listener is PropertyChangeListenerProxy && listener.listener is FixturePropertyChangeListener) {
                component.removePropertyChangeListener(listener)
            }
        }
        component.addPropertyChangeListener(parameters.name, FixturePropertyChangeListener())
    }
}

/** The listener [PropertyChangeListenerNameFixture] adds to its raw component, so that it removes only its own. */
private class FixturePropertyChangeListener : PropertyChangeListener {
    override fun propertyChange(event: PropertyChangeEvent) = Unit
}
