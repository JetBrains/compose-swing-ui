// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onAccept
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JTextField

/** The parameters [OnAcceptFixture] varies: the callback run when the text field accepts its value. */
public data class OnAcceptParameters(
    val onAccept: () -> Unit,
)

/**
 * A text field with an accept callback declared through `onAccept`, against a raw [JTextField] with the same callback.
 */
public object OnAcceptFixture : ComposableFixture<OnAcceptParameters, JTextField>(
    initial = OnAcceptParameters(onAccept = {}),
) {
    /** The single-change copies of [initial]: one with a different callback. */
    override val alternatives: List<OnAcceptParameters> =
        listOf(
            initial.copy(onAccept = {}),
        )

    @Composable
    override fun Content(parameters: OnAcceptParameters): Unit = Content(parameters.onAccept)

    /** Composes a text field that runs [onAccept] when it accepts its value, through [SwingModifier.onAccept]. */
    @Composable
    public fun Content(onAccept: () -> Unit) {
        SwingNode<JTextField>(
            factory = { JTextField("Text") },
            modifier = SwingModifier.onAccept(onAccept = onAccept),
        )
    }

    /** Builds a bare [JTextField] showing the same text, before any of the fixture's callbacks are applied. */
    override fun createRawSwing(): JTextField = JTextField("Text")

    /**
     * Adds the accept listener on the first call; later calls only replace its callback with the one in
     * [parameters].
     */
    override fun updateRawSwing(
        component: JTextField,
        parameters: OnAcceptParameters,
    ) {
        val existing = component.actionListeners.firstNotNullOfOrNull { it as? AcceptListener }
        if (existing == null) {
            component.addActionListener(AcceptListener(parameters.onAccept))
        } else {
            existing.onAccept = parameters.onAccept
        }
    }

    /** The listener the raw side installs, so that an update finds only listeners the fixture added. */
    private class AcceptListener(
        var onAccept: () -> Unit,
    ) : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            onAccept()
        }
    }
}
