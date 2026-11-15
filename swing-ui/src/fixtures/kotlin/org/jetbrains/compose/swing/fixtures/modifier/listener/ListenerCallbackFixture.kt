// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JButton

/** The parameters [ListenerCallbackFixture] varies: the callback and its registration. */
public data class ListenerCallbackParameters(
    val callback: (ActionEvent) -> Unit,
    val registration: CallbackRegistration<AbstractButton, (ActionEvent) -> Unit, ActionListener>,
)

/**
 * The callback of [ListenerCallbackFixture.initial]; a distinct instance from [alternateCallback] so that swapping
 * them is a change.
 */
private val initialCallback: (ActionEvent) -> Unit = {}

/** The callback that [ListenerCallbackFixture.alternatives] swaps in. */
private val alternateCallback: (ActionEvent) -> Unit = {}

/** The registration of an action listener under [name], shared by the listener fixtures. */
internal fun callbackRegistration(
    name: String,
): CallbackRegistration<AbstractButton, (ActionEvent) -> Unit, ActionListener> =
    CallbackRegistration(
        adapter = { declared -> ActionListener { event -> declared.report { it(event) } } },
        registration =
            ListenerRegistration(
                name,
                AbstractButton::addActionListener,
                AbstractButton::removeActionListener,
            ),
    )

/** The registration of [ListenerCallbackFixture.initial], under the name "action". */
private val initialRegistration = callbackRegistration("action")

/**
 * The registration that [ListenerCallbackFixture.alternatives] swaps in, under a different name from
 * [initialRegistration].
 */
private val alternateRegistration = callbackRegistration("alternateAction")

/** A [Button] with an action callback declared through [listener], against a raw [JButton] listening by hand. */
public object ListenerCallbackFixture : ComposableFixture<ListenerCallbackParameters, JButton>(
    initial =
        ListenerCallbackParameters(
            callback = initialCallback,
            registration = initialRegistration,
        ),
) {
    /** The text both sides show on the button. */
    private const val TEXT = "Listener"

    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListenerCallbackParameters> =
        listOf(
            initial.copy(callback = alternateCallback),
            initial.copy(registration = alternateRegistration),
        )

    @Composable
    override fun Content(parameters: ListenerCallbackParameters): Unit =
        Content(parameters.callback, parameters.registration)

    /** Composes a [Button] whose modifier attaches [callback] through [registration]. */
    @Composable
    public fun Content(
        callback: (ActionEvent) -> Unit,
        registration: CallbackRegistration<AbstractButton, (ActionEvent) -> Unit, ActionListener>,
    ) {
        Button(
            text = TEXT,
            onClick = onClick,
            modifier = SwingModifier.listener(JButton::class, callback, registration),
        )
    }

    /** Builds a [JButton] showing the same text, with the click listener [Button] installs for its `onClick`. */
    override fun createRawSwing(): JButton = JButton(TEXT).apply { addActionListener { onClick() } }

    /**
     * Adds the listener on the first call. A later call with the same registration only replaces its callback with
     * that of [parameters]; a different registration replaces the listener.
     */
    override fun updateRawSwing(
        component: JButton,
        parameters: ListenerCallbackParameters,
    ) {
        val existing = component.actionListeners.firstNotNullOfOrNull { it as? ForwardingActionListener }
        if (existing?.registration == parameters.registration) {
            existing.callback = parameters.callback
            return
        }
        if (existing != null) component.removeActionListener(existing)
        component.addActionListener(ForwardingActionListener(parameters.registration, parameters.callback))
    }

    /** The listener this fixture installs on the raw side, so that it finds only what it added. */
    private class ForwardingActionListener(
        val registration: CallbackRegistration<AbstractButton, (ActionEvent) -> Unit, ActionListener>,
        var callback: (ActionEvent) -> Unit,
    ) : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            callback(e)
        }
    }
}
