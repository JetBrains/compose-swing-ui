// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import java.awt.event.HierarchyListener
import javax.swing.JLabel

/** The parameters [HierarchyListenerFixture] varies. */
public data class HierarchyListenerParameters(
    val listener: HierarchyListener,
)

/** A [Label] carrying `hierarchyListener(listener)` against a raw [JLabel] with the same listener added by hand. */
public object HierarchyListenerFixture : ComposableFixture<HierarchyListenerParameters, JLabel>(
    initial = HierarchyListenerParameters(listener = { }),
) {
    private const val HOST_TEXT = "Hierarchy"

    private val alternateListener = HierarchyListener { }

    /** The fixture's alternative values: the same host with a different listener instance attached. */
    override val alternatives: List<HierarchyListenerParameters> =
        listOf(
            initial.copy(listener = alternateListener),
        )

    @Composable
    override fun Content(parameters: HierarchyListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [Label] with [listener] attached through the modifier. */
    @Composable
    public fun Content(listener: HierarchyListener) {
        Label(text = HOST_TEXT, modifier = SwingModifier.hierarchyListener(listener))
    }

    /** Builds the raw [JLabel] with the same text and no listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /**
     * Installs [parameters]' listener on [component] in place of the fixture's earlier ones. Unrelated hierarchy
     * listeners remain.
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: HierarchyListenerParameters,
    ) {
        component.removeHierarchyListener(initial.listener)
        component.removeHierarchyListener(alternateListener)
        component.addHierarchyListener(parameters.listener)
    }
}
