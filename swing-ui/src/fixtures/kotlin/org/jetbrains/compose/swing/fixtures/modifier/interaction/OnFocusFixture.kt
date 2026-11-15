// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JLabel

/** The parameters [OnFocusFixture] varies: the actions that run when the component gains and loses the focus. */
public data class OnFocusParameters(
    val onGained: () -> Unit,
    val onLost: () -> Unit,
)

/** A [Label] with focus actions declared through `onFocus`, against a raw [JLabel] with the same focus actions. */
public object OnFocusFixture : ComposableFixture<OnFocusParameters, JLabel>(
    initial = OnFocusParameters(onGained = {}, onLost = {}),
) {
    /** The single-change copies of [initial], one for each action. */
    override val alternatives: List<OnFocusParameters> =
        listOf(
            initial.copy(onGained = {}),
            initial.copy(onLost = {}),
        )

    @Composable
    override fun Content(parameters: OnFocusParameters): Unit = Content(parameters.onGained, parameters.onLost)

    /** Composes a [Label] whose focus actions are [onGained] and [onLost]. */
    @Composable
    public fun Content(
        onGained: () -> Unit,
        onLost: () -> Unit,
    ) {
        Label(
            text = "",
            modifier = SwingModifier.onFocus(onGained = onGained, onLost = onLost),
        )
    }

    /** Builds a bare [JLabel] before any of the fixture's actions are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Adds the focus listener on the first call; later calls only replace its actions with those in [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: OnFocusParameters,
    ) {
        val existing = component.focusListeners.firstNotNullOfOrNull { it as? ForwardingFocusListener }
        if (existing == null) {
            component.addFocusListener(ForwardingFocusListener(parameters.onGained, parameters.onLost))
        } else {
            existing.onGained = parameters.onGained
            existing.onLost = parameters.onLost
        }
    }
}

/** The focus listener [OnFocusFixture] installs on its raw component, so that it finds only its own. */
private class ForwardingFocusListener(
    var onGained: () -> Unit,
    var onLost: () -> Unit,
) : FocusAdapter() {
    override fun focusGained(event: FocusEvent) {
        onGained()
    }

    override fun focusLost(event: FocusEvent) {
        onLost()
    }
}
