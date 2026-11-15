// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JButton
import kotlin.reflect.KClass

/** The parameters [ListenerFixture] varies: the target type, the listener instance and its registration. */
public data class ListenerParameters(
    val targetType: KClass<out AbstractButton>,
    val instance: ActionListener,
    val registration: ListenerRegistration<AbstractButton, ActionListener>,
)

/**
 * The listener instance of [ListenerFixture.initial]; distinct from [alternateInstance] so that swapping them is a
 * change.
 */
private val initialInstance = ActionListener {}

/** The listener instance that [ListenerFixture.alternatives] swaps in. */
private val alternateInstance = ActionListener {}

/** The registration of [ListenerFixture.initial], under the name "action". */
private val initialRegistration =
    ListenerRegistration(
        "action",
        AbstractButton::addActionListener,
        AbstractButton::removeActionListener,
    )

/** The registration that [ListenerFixture.alternatives] swaps in, under a different name from [initialRegistration]. */
private val alternateRegistration =
    ListenerRegistration(
        "alternateAction",
        AbstractButton::addActionListener,
        AbstractButton::removeActionListener,
    )

/**
 * A [Button] with an action listener instance declared through [listener], against a raw [JButton] listening by hand.
 */
public object ListenerFixture : ComposableFixture<ListenerParameters, JButton>(
    initial =
        ListenerParameters(
            targetType = JButton::class,
            instance = initialInstance,
            registration = initialRegistration,
        ),
) {
    /** The text both sides show on the button. */
    private const val TEXT = "Listener"

    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListenerParameters> =
        listOf(
            initial.copy(targetType = AbstractButton::class),
            initial.copy(instance = alternateInstance),
            initial.copy(registration = alternateRegistration),
        )

    @Composable
    override fun Content(parameters: ListenerParameters): Unit =
        Content(parameters.targetType, parameters.instance, parameters.registration)

    /** Composes a [Button] whose modifier attaches [instance] of [targetType] through [registration]. */
    @Composable
    public fun Content(
        targetType: KClass<out AbstractButton>,
        instance: ActionListener,
        registration: ListenerRegistration<AbstractButton, ActionListener>,
    ) {
        Button(
            text = TEXT,
            onClick = onClick,
            modifier = SwingModifier.listener(targetType, instance, registration),
        )
    }

    /** Builds a [JButton] showing the same text, with the click listener [Button] installs for its `onClick`. */
    override fun createRawSwing(): JButton = JButton(TEXT).apply { addActionListener { onClick() } }

    /** Replaces the listener this fixture added earlier with the instance of [parameters]. */
    override fun updateRawSwing(
        component: JButton,
        parameters: ListenerParameters,
    ) {
        component.removeActionListener(initialInstance)
        component.removeActionListener(alternateInstance)
        component.addActionListener(parameters.instance)
    }
}
