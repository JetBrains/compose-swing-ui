// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.modifier.listener.changeListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener

/**
 * The parameters [SpinnerModelFixture] varies: the value of the model the spinner renders and the raw
 * change listener attached to it.
 */
public data class SpinnerModelParameters(
    val value: Int,
    val changeListener: ChangeListener,
)

/**
 * A [Spinner] over a model remembered for a value, with a change listener attached through the modifier, against a
 * raw [JSpinner] over a fresh model of that value with the same listener.
 */
public object SpinnerModelFixture : ComposableFixture<SpinnerModelParameters, JSpinner>(
    initial =
        SpinnerModelParameters(
            value = 0,
            changeListener = {},
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SpinnerModelParameters> =
        listOf(
            initial.copy(value = 1),
            initial.copy(changeListener = ChangeListener {}),
        )

    @Composable
    override fun Content(parameters: SpinnerModelParameters): Unit =
        Content(parameters.value, parameters.changeListener)

    /** Composes a [Spinner] rendering a model remembered for [value] and notifying [changeListener] of its changes. */
    @Composable
    public fun Content(
        value: Int,
        changeListener: ChangeListener,
    ) {
        Spinner(
            model = remember(value) { SpinnerNumberModel().apply { this.value = value } },
            modifier = SwingModifier.changeListener(changeListener),
        )
    }

    /**
     * Builds a [JSpinner] over a fresh model of the value of [initial]. Handing the constructor the model, rather
     * than replacing the default one afterwards, leaves the editor the look and feel aligned.
     */
    override fun createRawSwing(): JSpinner = JSpinner(SpinnerNumberModel().apply { value = initial.value })

    /**
     * Gives [component] a fresh model of the value of [parameters] when its value differs, as the composable's
     * remembered model changes, and attaches its change listener in place of the previous one only when it is
     * another listener, leaving the change listeners the look and feel installed untouched.
     */
    override fun updateRawSwing(
        component: JSpinner,
        parameters: SpinnerModelParameters,
    ) {
        if (parameters.changeListener !in component.changeListeners) {
            component.removeChangeListener(initial.changeListener)
            alternatives.forEach { component.removeChangeListener(it.changeListener) }
            component.addChangeListener(parameters.changeListener)
        }
        if (component.value != parameters.value) {
            component.model = SpinnerNumberModel().apply { value = parameters.value }
        }
    }
}
