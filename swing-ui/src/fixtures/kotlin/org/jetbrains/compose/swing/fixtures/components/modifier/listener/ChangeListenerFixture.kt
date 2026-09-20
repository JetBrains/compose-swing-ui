// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.changeListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JSlider
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/** The parameters [ChangeListenerFixture] varies. */
public data class ChangeListenerParameters(
    val onChange: (ChangeEvent) -> Unit,
)

/** A `changeListener` callback on a `JSlider` against a raw `JSlider` with the same change listener added by hand. */
public object ChangeListenerFixture : ComposableFixture<ChangeListenerParameters, JSlider>(
    initial = ChangeListenerParameters(onChange = {}),
) {
    /** The initial callback replaced by a fresh one, which the change listener then runs instead. */
    override val alternatives: List<ChangeListenerParameters> =
        listOf(
            initial.copy(onChange = {}),
        )

    @Composable
    override fun Content(parameters: ChangeListenerParameters): Unit = Content(parameters.onChange)

    /** Composes a `JSlider` whose change events run [onChange]. */
    @Composable
    public fun Content(onChange: (ChangeEvent) -> Unit) {
        SwingNode(
            factory = { JSlider() },
            modifier = SwingModifier.changeListener(onChange),
        )
    }

    /** Builds a bare `JSlider` with no change listener attached. */
    override fun createRawSwing(): JSlider = JSlider()

    /** Adds the change listener on the first call; later calls only replace its callback with that of [parameters]. */
    override fun updateRawSwing(
        component: JSlider,
        parameters: ChangeListenerParameters,
    ) {
        val existing = component.changeListeners.firstNotNullOfOrNull { it as? CallbackChangeListener }
        if (existing == null) {
            component.addChangeListener(CallbackChangeListener(parameters.onChange))
        } else {
            existing.onChange = parameters.onChange
        }
    }

    /** The raw listener that runs the callback, told apart from listeners the fixture did not add. */
    private class CallbackChangeListener(
        var onChange: (ChangeEvent) -> Unit,
    ) : ChangeListener {
        override fun stateChanged(event: ChangeEvent) = onChange(event)
    }
}
