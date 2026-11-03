// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.AbstractButton
import javax.swing.JCheckBox

/** The parameters [ItemListenerScopedFixture] varies: the receiver callback. */
public data class ItemListenerScopedParameters(
    val onItemStateChange: AbstractButton.(ItemEvent) -> Unit,
)

/**
 * A [SwingModifier.itemListener] whose callback runs with the component as receiver, against a raw [JCheckBox] with
 * the same callback added by hand.
 */
public object ItemListenerScopedFixture : ComposableFixture<ItemListenerScopedParameters, JCheckBox>(
    initial = ItemListenerScopedParameters(onItemStateChange = {}),
) {
    /** The listener the raw side installs, so that an update finds only what the fixture added. */
    private class ReceiverItemListener(
        private val component: JCheckBox,
        var callback: AbstractButton.(ItemEvent) -> Unit,
    ) : ItemListener {
        override fun itemStateChanged(e: ItemEvent) {
            component.callback(e)
        }
    }

    /** The single-change copies of [initial]: one with a different callback. */
    override val alternatives: List<ItemListenerScopedParameters> =
        listOf(
            initial.copy(onItemStateChange = {}),
        )

    @Composable
    override fun Content(parameters: ItemListenerScopedParameters): Unit =
        Content(
            SwingModifier.itemListener(
                targetType = AbstractButton::class,
                onItemStateChange = parameters.onItemStateChange,
            ),
        )

    /** Composes a check box carrying [modifier], the item listener modifier under test. */
    @Composable
    public fun Content(modifier: SwingModifier = SwingModifier) {
        SwingNode<JCheckBox>(
            factory = { JCheckBox("Select") },
            modifier = modifier,
        )
    }

    /** Builds a bare [JCheckBox] with the same text and no item callback attached yet. */
    override fun createRawSwing(): JCheckBox = JCheckBox("Select")

    /** Adds the item listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JCheckBox,
        parameters: ItemListenerScopedParameters,
    ) {
        val existing = component.itemListeners.firstNotNullOfOrNull { it as? ReceiverItemListener }
        if (existing == null) {
            component.addItemListener(ReceiverItemListener(component, parameters.onItemStateChange))
        } else {
            existing.callback = parameters.onItemStateChange
        }
    }
}
