// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.focusListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JLabel

/** The parameters [FocusListenerFixture] varies: the callbacks run when the component gains and loses the focus. */
public data class FocusListenerParameters(
    val onFocusGained: (FocusEvent) -> Unit,
    val onFocusLost: (FocusEvent) -> Unit,
)

/** A [Label] with focus callbacks declared through `focusListener`, against a raw [JLabel] with the same callbacks. */
public object FocusListenerFixture : ComposableFixture<FocusListenerParameters, JLabel>(
    initial = FocusListenerParameters(onFocusGained = {}, onFocusLost = {}),
) {
    /** The single-change copies of [initial], one for each callback. */
    override val alternatives: List<FocusListenerParameters> =
        listOf(
            initial.copy(onFocusGained = {}),
            initial.copy(onFocusLost = {}),
        )

    @Composable
    override fun Content(parameters: FocusListenerParameters): Unit =
        Content(parameters.onFocusGained, parameters.onFocusLost)

    /** Composes a [Label] whose focus callbacks are [onFocusGained] and [onFocusLost]. */
    @Composable
    public fun Content(
        onFocusGained: (FocusEvent) -> Unit,
        onFocusLost: (FocusEvent) -> Unit,
    ) {
        Label(
            text = "",
            modifier = SwingModifier.focusListener(onFocusGained = onFocusGained, onFocusLost = onFocusLost),
        )
    }

    /** Builds a bare [JLabel] before any of the fixture's callbacks are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Adds the focus listener on the first call; later calls only replace its callbacks with those in [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: FocusListenerParameters,
    ) {
        val existing = component.focusListeners.firstNotNullOfOrNull { it as? FocusListenerFixtureForwardingListener }
        if (existing == null) {
            component.addFocusListener(
                FocusListenerFixtureForwardingListener(parameters.onFocusGained, parameters.onFocusLost),
            )
        } else {
            existing.onFocusGained = parameters.onFocusGained
            existing.onFocusLost = parameters.onFocusLost
        }
    }
}

/** The focus listener [FocusListenerFixture] installs on its raw component, so that it finds only its own. */
private class FocusListenerFixtureForwardingListener(
    var onFocusGained: (FocusEvent) -> Unit,
    var onFocusLost: (FocusEvent) -> Unit,
) : FocusAdapter() {
    override fun focusGained(event: FocusEvent) {
        onFocusGained(event)
    }

    override fun focusLost(event: FocusEvent) {
        onFocusLost(event)
    }
}
