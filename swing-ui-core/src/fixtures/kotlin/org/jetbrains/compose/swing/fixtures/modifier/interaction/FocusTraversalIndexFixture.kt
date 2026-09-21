// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.focusTraversalIndex
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy

/** The parameters [FocusTraversalIndexFixture] varies: the traversal index of the first of two buttons. */
public data class FocusTraversalIndexParameters(
    val index: Int,
)

/**
 * Two buttons in a [Panel] with [SwingModifier.orderedFocusTraversal], the first carrying
 * [SwingModifier.focusTraversalIndex], against a raw [JPanel] that is a focus cycle root with its own traversal policy.
 */
public object FocusTraversalIndexFixture : ComposableFixture<FocusTraversalIndexParameters, JPanel>(
    initial = FocusTraversalIndexParameters(index = 0),
) {
    /** The click handler both sides run for each button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<FocusTraversalIndexParameters> =
        listOf(
            initial.copy(index = 2),
        )

    @Composable
    override fun Content(parameters: FocusTraversalIndexParameters): Unit = Content(parameters.index)

    /** Composes two buttons whose traversal order is the first one's [index] against the second one's `1`. */
    @Composable
    public fun Content(index: Int) {
        Panel(modifier = SwingModifier.orderedFocusTraversal()) {
            Button(text = "First", onClick = onClick, modifier = SwingModifier.focusTraversalIndex(index))
            Button(text = "Second", onClick = onClick, modifier = SwingModifier.focusTraversalIndex(1))
        }
    }

    /**
     * Builds a panel holding the two buttons, each with the click listener [Button] installs for its `onClick`,
     * before the fixture's value is applied.
     */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            add(JButton("First").apply { addActionListener { onClick() } })
            add(JButton("Second").apply { addActionListener { onClick() } })
        }

    /**
     * Makes [component] a focus cycle root with its own [LayoutFocusTraversalPolicy]. The traversal index lives in
     * a client property the library keeps private, so the raw side holds the stock policy the composed side's
     * policy extends and does not carry [parameters].
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: FocusTraversalIndexParameters,
    ) {
        component.isFocusCycleRoot = true
        component.isFocusTraversalPolicyProvider = true
        component.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    }
}
