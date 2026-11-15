// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JSlider
import javax.swing.event.ChangeListener

/** The parameters [ChangeListenerListenerFixture] varies. */
public data class ChangeListenerListenerParameters(
    val listener: ChangeListener,
)

/**
 * A `changeListener` taking a `ChangeListener` on a `JSlider` against a raw `JSlider` with that listener added by hand.
 */
public object ChangeListenerListenerFixture : ComposableFixture<ChangeListenerListenerParameters, JSlider>(
    initial = ChangeListenerListenerParameters(listener = {}),
) {
    private val alternateListener = ChangeListener {}

    /** The initial listener replaced by a fresh instance, which the composition then attaches in its place. */
    override val alternatives: List<ChangeListenerListenerParameters> =
        listOf(
            initial.copy(listener = alternateListener),
        )

    @Composable
    override fun Content(parameters: ChangeListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a `JSlider` carrying [listener]. */
    @Composable
    public fun Content(listener: ChangeListener) {
        SwingNode(
            factory = { JSlider() },
            modifier = SwingModifier.changeListener(listener),
        )
    }

    /** Builds a bare `JSlider` with no change listener attached. */
    override fun createRawSwing(): JSlider = JSlider()

    /** Makes [component] carry the listener in [parameters], in place of any earlier one. */
    override fun updateRawSwing(
        component: JSlider,
        parameters: ChangeListenerListenerParameters,
    ) {
        component.removeChangeListener(initial.listener)
        component.removeChangeListener(alternateListener)
        component.addChangeListener(parameters.listener)
    }
}
