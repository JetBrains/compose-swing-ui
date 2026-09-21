// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseListener
import javax.swing.JLabel

/** The parameters [MouseListenerListenerFixture] varies: the [MouseListener] instance attached to the component. */
public data class MouseListenerListenerParameters(
    val listener: MouseListener,
)

/**
 * A [Label] carrying the listener overload of `mouseListener` against a raw [JLabel] with the same listener added by
 * hand.
 */
public object MouseListenerListenerFixture : ComposableFixture<MouseListenerListenerParameters, JLabel>(
    initial = MouseListenerListenerParameters(listener = object : MouseAdapter() {}),
) {
    private const val HOST_TEXT = "Mouse"

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<MouseListenerListenerParameters> =
        listOf(
            initial.copy(listener = object : MouseAdapter() {}),
        )

    @Composable
    override fun Content(parameters: MouseListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [Label] with [listener] attached through the modifier. */
    @Composable
    public fun Content(listener: MouseListener) {
        Label(text = HOST_TEXT, modifier = SwingModifier.mouseListener(listener))
    }

    /** Builds the raw [JLabel] with the same text and no mouse listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Replaces the fixture's earlier mouse listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: MouseListenerListenerParameters,
    ) {
        component.removeMouseListener(initial.listener)
        alternatives.forEach { component.removeMouseListener(it.listener) }
        component.addMouseListener(parameters.listener)
    }
}
