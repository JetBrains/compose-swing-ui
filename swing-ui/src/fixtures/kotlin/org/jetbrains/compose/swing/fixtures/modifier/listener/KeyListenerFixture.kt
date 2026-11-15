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

/** The parameters [KeyListenerFixture] varies: the typed, pressed and released key callbacks. */
public data class KeyListenerParameters(
    val onKeyTyped: (KeyEvent) -> Unit,
    val onKeyPressed: (KeyEvent) -> Unit,
    val onKeyReleased: (KeyEvent) -> Unit,
)

/**
 * A [Label] carrying `keyListener` with three per-event callbacks against a raw [JLabel] that forwards the same three
 * events.
 */
public object KeyListenerFixture : ComposableFixture<KeyListenerParameters, JLabel>(
    initial = KeyListenerParameters(onKeyTyped = {}, onKeyPressed = {}, onKeyReleased = {}),
) {
    private const val HOST_TEXT = "Key"

    /** The single-change copies of [initial], one for each callback. */
    override val alternatives: List<KeyListenerParameters> =
        listOf(
            initial.copy(onKeyTyped = {}),
            initial.copy(onKeyPressed = {}),
            initial.copy(onKeyReleased = {}),
        )

    @Composable
    override fun Content(parameters: KeyListenerParameters): Unit =
        Content(
            onKeyTyped = parameters.onKeyTyped,
            onKeyPressed = parameters.onKeyPressed,
            onKeyReleased = parameters.onKeyReleased,
        )

    /** Composes a [Label] whose typed, pressed and released key events run the given callbacks through the modifier. */
    @Composable
    public fun Content(
        onKeyTyped: (KeyEvent) -> Unit,
        onKeyPressed: (KeyEvent) -> Unit,
        onKeyReleased: (KeyEvent) -> Unit,
    ) {
        Label(
            text = HOST_TEXT,
            modifier =
                SwingModifier.keyListener(
                    onKeyTyped = onKeyTyped,
                    onKeyPressed = onKeyPressed,
                    onKeyReleased = onKeyReleased,
                ),
        )
    }

    /** Builds the raw [JLabel] with the same text and no key listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Adds the key listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: KeyListenerParameters,
    ) {
        val existing = component.keyListeners.firstNotNullOfOrNull { it as? ForwardingKeyListener }
        if (existing == null) {
            component.addKeyListener(
                ForwardingKeyListener(parameters.onKeyTyped, parameters.onKeyPressed, parameters.onKeyReleased),
            )
        } else {
            existing.onKeyTyped = parameters.onKeyTyped
            existing.onKeyPressed = parameters.onKeyPressed
            existing.onKeyReleased = parameters.onKeyReleased
        }
    }
}

/** The key listener [KeyListenerFixture] installs on its raw component, so that it finds only its own. */
private class ForwardingKeyListener(
    var onKeyTyped: (KeyEvent) -> Unit,
    var onKeyPressed: (KeyEvent) -> Unit,
    var onKeyReleased: (KeyEvent) -> Unit,
) : KeyAdapter() {
    override fun keyTyped(event: KeyEvent) {
        onKeyTyped(event)
    }

    override fun keyPressed(event: KeyEvent) {
        onKeyPressed(event)
    }

    override fun keyReleased(event: KeyEvent) {
        onKeyReleased(event)
    }
}
