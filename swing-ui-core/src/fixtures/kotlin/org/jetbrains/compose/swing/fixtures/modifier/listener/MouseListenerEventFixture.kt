// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel

/** The parameters [MouseListenerEventFixture] varies: the one callback run on every mouse event. */
public data class MouseListenerEventParameters(
    val onMouseEvent: (MouseEvent) -> Unit,
)

/**
 * A [Label] carrying the single-callback `mouseListener` against a raw [JLabel] that forwards every mouse event to the
 * same callback.
 */
public object MouseListenerEventFixture : ComposableFixture<MouseListenerEventParameters, JLabel>(
    initial = MouseListenerEventParameters(onMouseEvent = {}),
) {
    private const val HOST_TEXT = "Mouse"

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<MouseListenerEventParameters> =
        listOf(
            initial.copy(onMouseEvent = {}),
        )

    @Composable
    override fun Content(parameters: MouseListenerEventParameters): Unit = Content(parameters.onMouseEvent)

    /** Composes a [Label] whose every mouse event runs [onMouseEvent] through the modifier. */
    @Composable
    public fun Content(onMouseEvent: (MouseEvent) -> Unit) {
        Label(text = HOST_TEXT, modifier = SwingModifier.mouseListener(onMouseEvent = onMouseEvent))
    }

    /** Builds the raw [JLabel] with the same text and no mouse listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /**
     * Adds the mouse listener on the first call; later calls only replace its callback with the one of [parameters].
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: MouseListenerEventParameters,
    ) {
        val existing = component.mouseListeners.firstNotNullOfOrNull { it as? MouseListenerEventForwardingListener }
        if (existing == null) {
            component.addMouseListener(MouseListenerEventForwardingListener(parameters.onMouseEvent))
        } else {
            existing.onMouseEvent = parameters.onMouseEvent
        }
    }
}

/** The mouse listener [MouseListenerEventFixture] installs on its raw component, so that it finds only its own. */
private class MouseListenerEventForwardingListener(
    var onMouseEvent: (MouseEvent) -> Unit,
) : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent) {
        onMouseEvent(event)
    }

    override fun mousePressed(event: MouseEvent) {
        onMouseEvent(event)
    }

    override fun mouseReleased(event: MouseEvent) {
        onMouseEvent(event)
    }

    override fun mouseEntered(event: MouseEvent) {
        onMouseEvent(event)
    }

    override fun mouseExited(event: MouseEvent) {
        onMouseEvent(event)
    }
}
