// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.keyListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JLabel

/** The parameters [KeyListenerEventFixture] varies: the one callback run on every key event. */
public data class KeyListenerEventParameters(
    val onKeyEvent: (KeyEvent) -> Unit,
)

/**
 * A [Label] carrying the single-callback `keyListener` against a raw [JLabel] that forwards every key event to the same
 * callback.
 */
public object KeyListenerEventFixture : ComposableFixture<KeyListenerEventParameters, JLabel>(
    initial = KeyListenerEventParameters(onKeyEvent = {}),
) {
    private const val HOST_TEXT = "Key"

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<KeyListenerEventParameters> =
        listOf(
            initial.copy(onKeyEvent = {}),
        )

    @Composable
    override fun Content(parameters: KeyListenerEventParameters): Unit = Content(parameters.onKeyEvent)

    /** Composes a [Label] whose every key event runs [onKeyEvent] through the modifier. */
    @Composable
    public fun Content(onKeyEvent: (KeyEvent) -> Unit) {
        Label(text = HOST_TEXT, modifier = SwingModifier.keyListener(onKeyEvent = onKeyEvent))
    }

    /** Builds the raw [JLabel] with the same text and no key listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Adds the key listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: KeyListenerEventParameters,
    ) {
        val existing = component.keyListeners.firstNotNullOfOrNull { it as? EventKeyListener }
        if (existing == null) {
            component.addKeyListener(EventKeyListener(parameters.onKeyEvent))
        } else {
            existing.onKeyEvent = parameters.onKeyEvent
        }
    }
}

/** The key listener [KeyListenerEventFixture] installs on its raw component, so that it finds only its own. */
private class EventKeyListener(
    var onKeyEvent: (KeyEvent) -> Unit,
) : KeyAdapter() {
    override fun keyTyped(event: KeyEvent) {
        onKeyEvent(event)
    }

    override fun keyPressed(event: KeyEvent) {
        onKeyEvent(event)
    }

    override fun keyReleased(event: KeyEvent) {
        onKeyEvent(event)
    }
}
