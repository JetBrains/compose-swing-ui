// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JLabel

/** The parameters [HierarchyListenerCallbackFixture] varies. */
public data class HierarchyListenerCallbackParameters(
    val onHierarchyChange: (HierarchyEvent) -> Unit,
)

/** A [Label] carrying `hierarchyListener(onHierarchyChange)` against a raw [JLabel] with the same callback. */
public object HierarchyListenerCallbackFixture : ComposableFixture<HierarchyListenerCallbackParameters, JLabel>(
    initial = HierarchyListenerCallbackParameters(onHierarchyChange = { }),
) {
    private const val HOST_TEXT = "Hierarchy"

    /** The fixture's alternative values: the same host with a different callback. */
    override val alternatives: List<HierarchyListenerCallbackParameters> =
        listOf(
            initial.copy(onHierarchyChange = { }),
        )

    @Composable
    override fun Content(parameters: HierarchyListenerCallbackParameters): Unit = Content(parameters.onHierarchyChange)

    /** Composes a [Label] with [onHierarchyChange] attached through the modifier. */
    @Composable
    public fun Content(onHierarchyChange: (HierarchyEvent) -> Unit) {
        Label(text = HOST_TEXT, modifier = SwingModifier.hierarchyListener(onHierarchyChange))
    }

    /** Builds the raw [JLabel] with the same text and no listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Adds the listener on the first call; later calls only replace its callback with that of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: HierarchyListenerCallbackParameters,
    ) {
        val existing =
            component.hierarchyListeners.firstNotNullOfOrNull { it as? HierarchyListenerCallbackForwarder }
        if (existing == null) {
            component.addHierarchyListener(HierarchyListenerCallbackForwarder(parameters.onHierarchyChange))
        } else {
            existing.onHierarchyChange = parameters.onHierarchyChange
        }
    }
}

/** The hierarchy listener [HierarchyListenerCallbackFixture] adds to its raw component, so it finds only its own. */
private class HierarchyListenerCallbackForwarder(
    var onHierarchyChange: (HierarchyEvent) -> Unit,
) : HierarchyListener {
    override fun hierarchyChanged(event: HierarchyEvent) {
        onHierarchyChange(event)
    }
}
