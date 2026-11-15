// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.focusListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusListener
import javax.swing.JLabel

/** The parameters [FocusListenerListenerFixture] varies: the focus listener instance. */
public data class FocusListenerListenerParameters(
    val listener: FocusListener,
)

/**
 * A [Label] with a focus listener declared through `focusListener(listener)`, against a raw [JLabel] with the same
 * listener attached.
 */
public object FocusListenerListenerFixture : ComposableFixture<FocusListenerListenerParameters, JLabel>(
    initial = FocusListenerListenerParameters(listener = object : FocusAdapter() {}),
) {
    /** The single-change copies of [initial]: a different focus listener instance. */
    override val alternatives: List<FocusListenerListenerParameters> =
        listOf(
            initial.copy(listener = object : FocusAdapter() {}),
        )

    @Composable
    override fun Content(parameters: FocusListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [Label] with [listener] declared as its focus listener. */
    @Composable
    public fun Content(listener: FocusListener) {
        Label(
            text = "",
            modifier = SwingModifier.focusListener(listener),
        )
    }

    /** Builds a bare [JLabel] before any of the fixture's listeners are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Replaces the fixture's earlier focus listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: FocusListenerListenerParameters,
    ) {
        component.removeFocusListener(initial.listener)
        alternatives.forEach { component.removeFocusListener(it.listener) }
        component.addFocusListener(parameters.listener)
    }
}
