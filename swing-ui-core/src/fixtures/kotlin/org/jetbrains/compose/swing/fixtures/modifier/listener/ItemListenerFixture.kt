// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.JCheckBox

/** The parameters [ItemListenerFixture] varies: the callback run on each item event. */
public data class ItemListenerParameters(
    val onItemStateChange: (ItemEvent) -> Unit,
)

/**
 * A check box with an item callback declared through `itemListener`, against a raw [JCheckBox] with the same callback.
 */
public object ItemListenerFixture : ComposableFixture<ItemListenerParameters, JCheckBox>(
    initial = ItemListenerParameters(onItemStateChange = {}),
) {
    /** The listener the raw side installs, so that an update finds only what the fixture added. */
    private class CallbackItemListener(
        var callback: (ItemEvent) -> Unit,
    ) : ItemListener {
        override fun itemStateChanged(e: ItemEvent) {
            callback(e)
        }
    }

    /** The single-change copies of [initial]: one with a different callback. */
    override val alternatives: List<ItemListenerParameters> =
        listOf(
            initial.copy(onItemStateChange = {}),
        )

    @Composable
    override fun Content(parameters: ItemListenerParameters): Unit = Content(parameters.onItemStateChange)

    /** Composes a check box that runs [onItemStateChange] on each item event, through [SwingModifier.itemListener]. */
    @Composable
    public fun Content(onItemStateChange: (ItemEvent) -> Unit) {
        SwingNode<JCheckBox>(
            factory = { JCheckBox("Select") },
            modifier = SwingModifier.itemListener(onItemStateChange = onItemStateChange),
        )
    }

    /** Builds a bare [JCheckBox] before any of the fixture's callbacks are applied. */
    override fun createRawSwing(): JCheckBox = JCheckBox("Select")

    /** Adds the item listener on the first call; later calls only replace its callback with the one in [parameters]. */
    override fun updateRawSwing(
        component: JCheckBox,
        parameters: ItemListenerParameters,
    ) {
        val existing = component.itemListeners.firstNotNullOfOrNull { it as? CallbackItemListener }
        if (existing == null) {
            component.addItemListener(CallbackItemListener(parameters.onItemStateChange))
        } else {
            existing.callback = parameters.onItemStateChange
        }
    }
}
