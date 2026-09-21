// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JButton

/** The parameters [ListenerOnEventFixture] varies: the registration and the receiver-scoped handler. */
public data class ListenerOnEventParameters(
    val registration: CallbackRegistration<AbstractButton, (ActionEvent) -> Unit, ActionListener>,
    val onEvent: AbstractButton.(ActionEvent) -> Unit,
)

/**
 * The handler of [ListenerOnEventFixture.initial]; a distinct instance from [alternateOnEvent] so that swapping them
 * is a change.
 */
private val initialOnEvent: AbstractButton.(ActionEvent) -> Unit = {}

/** The handler that [ListenerOnEventFixture.alternatives] swaps in. */
private val alternateOnEvent: AbstractButton.(ActionEvent) -> Unit = {}

/** The registration of [ListenerOnEventFixture.initial], under the name "action". */
private val initialRegistration = callbackRegistration("action")

/**
 * The registration that [ListenerOnEventFixture.alternatives] swaps in, under a different name from
 * [initialRegistration].
 */
private val alternateRegistration = callbackRegistration("alternateAction")

/** A [Button] with a receiver-scoped action handler declared through [listener], against a raw [JButton] by hand. */
public object ListenerOnEventFixture : ComposableFixture<ListenerOnEventParameters, JButton>(
    initial =
        ListenerOnEventParameters(
            registration = initialRegistration,
            onEvent = initialOnEvent,
        ),
) {
    /** The text both sides show on the button. */
    private const val TEXT = "Listener"

    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListenerOnEventParameters> =
        listOf(
            initial.copy(registration = alternateRegistration),
            initial.copy(onEvent = alternateOnEvent),
        )

    @Composable
    override fun Content(parameters: ListenerOnEventParameters): Unit =
        Content(SwingModifier.listener(JButton::class, parameters.registration, parameters.onEvent))

    /** Composes a [Button] carrying [modifier], the listener modifier under test. */
    @Composable
    public fun Content(modifier: SwingModifier = SwingModifier) {
        Button(text = TEXT, onClick = onClick, modifier = modifier)
    }

    /** Builds a [JButton] showing the same text, with the click listener [Button] installs for its `onClick`. */
    override fun createRawSwing(): JButton = JButton(TEXT).apply { addActionListener { onClick() } }

    /**
     * Adds the listener on the first call. A later call with the same registration only replaces its handler with
     * that of [parameters]; a different registration replaces the listener.
     */
    override fun updateRawSwing(
        component: JButton,
        parameters: ListenerOnEventParameters,
    ) {
        val existing = component.actionListeners.firstNotNullOfOrNull { it as? ForwardingActionListener }
        if (existing?.registration == parameters.registration) {
            existing.onEvent = parameters.onEvent
            return
        }
        if (existing != null) component.removeActionListener(existing)
        component.addActionListener(ForwardingActionListener(component, parameters.registration, parameters.onEvent))
    }

    /** The listener this fixture installs on the raw side, so that it finds only what it added. */
    private class ForwardingActionListener(
        private val button: AbstractButton,
        val registration: CallbackRegistration<AbstractButton, (ActionEvent) -> Unit, ActionListener>,
        var onEvent: AbstractButton.(ActionEvent) -> Unit,
    ) : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            button.onEvent(e)
        }
    }
}
