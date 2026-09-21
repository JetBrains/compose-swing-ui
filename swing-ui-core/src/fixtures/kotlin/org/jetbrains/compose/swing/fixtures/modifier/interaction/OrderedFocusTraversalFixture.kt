// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy

/** The parameters [OrderedFocusTraversalFixture] varies: whether the panel orders its focus traversal by index. */
public data class OrderedFocusTraversalParameters(
    val applied: Boolean,
)

/** A [Panel] with [SwingModifier.orderedFocusTraversal] against a raw [JPanel] that is a focus cycle root. */
public object OrderedFocusTraversalFixture : ComposableFixture<OrderedFocusTraversalParameters, JPanel>(
    initial = OrderedFocusTraversalParameters(applied = true),
) {
    /** The text both sides show on the child. */
    private const val TEXT = "Ordered"

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<OrderedFocusTraversalParameters> =
        listOf(
            initial.copy(applied = false),
        )

    @Composable
    override fun Content(parameters: OrderedFocusTraversalParameters): Unit = Content(parameters.applied)

    /** Composes a [Panel] holding a [Label], ordering its focus traversal when [applied] is `true`. */
    @Composable
    public fun Content(applied: Boolean) {
        Panel(modifier = if (applied) SwingModifier.orderedFocusTraversal() else SwingModifier) {
            Label(text = TEXT)
        }
    }

    /** Builds a [JPanel] holding a [JLabel], before the fixture's value is applied. */
    override fun createRawSwing(): JPanel = JPanel().also { it.add(JLabel(TEXT)) }

    /**
     * Makes the panel a focus cycle root with its own traversal policy when [parameters] says so, and restores the
     * defaults otherwise.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: OrderedFocusTraversalParameters,
    ) {
        component.isFocusCycleRoot = parameters.applied
        component.isFocusTraversalPolicyProvider = parameters.applied
        component.focusTraversalPolicy = if (parameters.applied) LayoutFocusTraversalPolicy() else null
    }
}
