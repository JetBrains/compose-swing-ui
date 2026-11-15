// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.keyListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyListener
import javax.swing.JLabel

/** The parameters [KeyListenerListenerFixture] varies: the [KeyListener] instance attached to the component. */
public data class KeyListenerListenerParameters(
    val listener: KeyListener,
)

/**
 * A [Label] carrying the listener overload of `keyListener` against a raw [JLabel] that forwards key events to the same
 * listener.
 */
public object KeyListenerListenerFixture : ComposableFixture<KeyListenerListenerParameters, JLabel>(
    initial = KeyListenerListenerParameters(listener = object : KeyAdapter() {}),
) {
    private const val HOST_TEXT = "Key"

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<KeyListenerListenerParameters> =
        listOf(
            initial.copy(listener = object : KeyAdapter() {}),
        )

    @Composable
    override fun Content(parameters: KeyListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [Label] with [listener] attached through the modifier. */
    @Composable
    public fun Content(listener: KeyListener) {
        Label(text = HOST_TEXT, modifier = SwingModifier.keyListener(listener))
    }

    /** Builds the raw [JLabel] with the same text and no key listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Replaces the fixture's earlier key listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: KeyListenerListenerParameters,
    ) {
        component.removeKeyListener(initial.listener)
        alternatives.forEach { component.removeKeyListener(it.listener) }
        component.addKeyListener(parameters.listener)
    }
}
