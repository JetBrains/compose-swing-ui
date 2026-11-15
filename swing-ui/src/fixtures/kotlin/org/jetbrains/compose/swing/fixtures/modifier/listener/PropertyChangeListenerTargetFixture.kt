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
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.reflect.KClass

/**
 * The parameters [PropertyChangeListenerTargetFixture] varies: whether the callback's receiver type is `JLabel`
 * or `JComponent`.
 */
public data class PropertyChangeListenerTargetParameters(
    val targetType: KClass<out JComponent>,
)

/**
 * A [Label] whose bound property change callback is typed through `propertyChangeListener(name, targetType, ...)`,
 * against a raw [JLabel] with the same bound property listener attached by hand.
 */
public object PropertyChangeListenerTargetFixture : ComposableFixture<PropertyChangeListenerTargetParameters, JLabel>(
    initial = PropertyChangeListenerTargetParameters(targetType = JComponent::class),
) {
    /** The bound property the callback listens to, shared by the composed and the raw side. */
    private const val BOUND_PROPERTY = "background"

    /** The single-change copy of [initial], with the receiver type switched to `JLabel`. */
    override val alternatives: List<PropertyChangeListenerTargetParameters> =
        listOf(
            initial.copy(targetType = JLabel::class),
        )

    @Composable
    override fun Content(parameters: PropertyChangeListenerTargetParameters): Unit = Content(parameters.targetType)

    /** Composes a [Label] whose callback is typed by [targetType]. */
    @Composable
    public fun Content(targetType: KClass<out JComponent>) {
        Label(
            text = "",
            modifier =
                SwingModifier.propertyChangeListener(
                    name = BOUND_PROPERTY,
                    targetType = targetType,
                    onPropertyChange = { },
                ),
        )
    }

    /** Builds a bare [JLabel] with no property change listener attached. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Replaces the bound property listener this fixture added to [component] with a fresh one. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: PropertyChangeListenerTargetParameters,
    ) {
        for (listener in component.propertyChangeListeners) {
            if (listener is PropertyChangeListenerProxy && listener.listener is BoundProp) {
                component.removePropertyChangeListener(listener)
            }
        }
        component.addPropertyChangeListener(BOUND_PROPERTY, BoundProp())
    }
}

/** The listener this fixture installs on its raw component, so that it removes only its own. */
private class BoundProp : PropertyChangeListener {
    override fun propertyChange(event: PropertyChangeEvent) = Unit
}
