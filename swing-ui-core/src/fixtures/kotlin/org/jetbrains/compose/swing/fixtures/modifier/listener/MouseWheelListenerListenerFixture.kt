// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JLabel

/** A mouse wheel listener that ignores every event; each instance is distinct. */
private class NoOpWheelListener : MouseWheelListener {
    override fun mouseWheelMoved(e: MouseWheelEvent) = Unit
}

/** The parameters [MouseWheelListenerListenerFixture] varies: the mouse wheel listener instance. */
public data class MouseWheelListenerListenerParameters(
    val listener: MouseWheelListener,
)

/**
 * A [Label] with a mouse wheel listener declared through `mouseWheelListener(listener)`, against a raw [JLabel]
 * with the same listener attached.
 */
public object MouseWheelListenerListenerFixture : ComposableFixture<MouseWheelListenerListenerParameters, JLabel>(
    initial = MouseWheelListenerListenerParameters(listener = NoOpWheelListener()),
) {
    /** The single-change copies of [initial]: a different mouse wheel listener instance. */
    override val alternatives: List<MouseWheelListenerListenerParameters> =
        listOf(
            initial.copy(listener = NoOpWheelListener()),
        )

    @Composable
    override fun Content(parameters: MouseWheelListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [Label] with [listener] declared as its mouse wheel listener. */
    @Composable
    public fun Content(listener: MouseWheelListener) {
        Label(
            text = "",
            modifier = SwingModifier.mouseWheelListener(listener),
        )
    }

    /** Builds a bare [JLabel] before any of the fixture's listeners are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Replaces the fixture's earlier mouse wheel listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: MouseWheelListenerListenerParameters,
    ) {
        component.removeMouseWheelListener(initial.listener)
        alternatives.forEach { component.removeMouseWheelListener(it.listener) }
        component.addMouseWheelListener(parameters.listener)
    }
}
