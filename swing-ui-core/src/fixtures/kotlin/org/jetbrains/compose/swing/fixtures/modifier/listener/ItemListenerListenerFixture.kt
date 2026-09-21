// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ItemListener
import javax.swing.JCheckBox

/** The parameters [ItemListenerListenerFixture] varies: the item listener instance. */
public data class ItemListenerListenerParameters(
    val listener: ItemListener,
)

/**
 * A check box with a listener declared through `itemListener(listener)`, against a raw [JCheckBox] with the same
 * listener attached.
 */
public object ItemListenerListenerFixture : ComposableFixture<ItemListenerListenerParameters, JCheckBox>(
    initial = ItemListenerListenerParameters(listener = {}),
) {
    /** The single-change copies of [initial]: one with a different listener instance. */
    override val alternatives: List<ItemListenerListenerParameters> =
        listOf(
            initial.copy(listener = {}),
        )

    @Composable
    override fun Content(parameters: ItemListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a check box that attaches [listener] through [SwingModifier.itemListener]. */
    @Composable
    public fun Content(listener: ItemListener) {
        SwingNode<JCheckBox>(
            factory = { JCheckBox("Select") },
            modifier = SwingModifier.itemListener(listener = listener),
        )
    }

    /** Builds a bare [JCheckBox] before any of the fixture's listeners are applied. */
    override fun createRawSwing(): JCheckBox = JCheckBox("Select")

    /** Replaces the item listener this fixture added to [component] with the listener in [parameters]. */
    override fun updateRawSwing(
        component: JCheckBox,
        parameters: ItemListenerListenerParameters,
    ) {
        component.removeItemListener(initial.listener)
        alternatives.forEach { component.removeItemListener(it.listener) }
        component.addItemListener(parameters.listener)
    }
}
