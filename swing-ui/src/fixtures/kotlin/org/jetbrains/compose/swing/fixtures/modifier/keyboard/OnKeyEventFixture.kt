// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.keyboard

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.keyboard.onKeyEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton

/** The parameters [OnKeyEventFixture] varies: the callback that receives each key event and may consume it. */
public data class OnKeyEventParameters(
    val onKeyEvent: (KeyEvent) -> Boolean,
)

/** A [Button] with a key-event callback declared through `onKeyEvent`, against a raw [JButton] with a key listener. */
public object OnKeyEventFixture : ComposableFixture<OnKeyEventParameters, JButton>(
    initial = OnKeyEventParameters(onKeyEvent = { false }),
) {
    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<OnKeyEventParameters> =
        listOf(
            initial.copy(onKeyEvent = { true }),
        )

    @Composable
    override fun Content(parameters: OnKeyEventParameters): Unit = Content(parameters.onKeyEvent)

    /** Composes a [Button] whose key events are passed to [onKeyEvent]. */
    @Composable
    public fun Content(onKeyEvent: (KeyEvent) -> Boolean) {
        Button(
            text = "Event",
            onClick = onClick,
            modifier = SwingModifier.onKeyEvent(onKeyEvent = onKeyEvent),
        )
    }

    /** Builds a [JButton] showing the same text, with the click listener [Button] installs for its `onClick`. */
    override fun createRawSwing(): JButton = JButton("Event").apply { addActionListener { onClick() } }

    /** Adds the key listener on the first call; later calls only replace its callback with that of [parameters]. */
    override fun updateRawSwing(
        component: JButton,
        parameters: OnKeyEventParameters,
    ) {
        val existing = component.keyListeners.firstNotNullOfOrNull { it as? ConsumingKeyListener }
        if (existing == null) {
            component.addKeyListener(ConsumingKeyListener(parameters.onKeyEvent))
        } else {
            existing.onKeyEvent = parameters.onKeyEvent
        }
    }

    /** A key listener that consumes each event for which its callback returns true. */
    private class ConsumingKeyListener(
        var onKeyEvent: (KeyEvent) -> Boolean,
    ) : KeyAdapter() {
        override fun keyPressed(event: KeyEvent) = deliver(event)

        override fun keyReleased(event: KeyEvent) = deliver(event)

        override fun keyTyped(event: KeyEvent) = deliver(event)

        /** Consumes [event] when the callback returns true. */
        private fun deliver(event: KeyEvent) {
            if (onKeyEvent(event)) event.consume()
        }
    }
}
