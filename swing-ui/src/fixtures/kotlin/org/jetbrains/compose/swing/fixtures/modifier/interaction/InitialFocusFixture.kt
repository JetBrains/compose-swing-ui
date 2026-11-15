// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.initialFocus
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JButton

/** The parameters [InitialFocusFixture] varies: whether the button asks for focus when it is first shown. */
public data class InitialFocusParameters(
    val applied: Boolean,
)

/** A [Button] with [SwingModifier.initialFocus] against a raw [JButton] that requests focus when first shown. */
public object InitialFocusFixture : ComposableFixture<InitialFocusParameters, JButton>(
    initial = InitialFocusParameters(applied = true),
) {
    private const val TEXT = "Focus"

    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<InitialFocusParameters> =
        listOf(
            initial.copy(applied = false),
        )

    @Composable
    override fun Content(parameters: InitialFocusParameters): Unit = Content(parameters.applied)

    /** Composes a [Button] that takes focus when first shown if [applied] is `true`. */
    @Composable
    public fun Content(applied: Boolean) {
        Button(
            text = TEXT,
            onClick = onClick,
            modifier = if (applied) SwingModifier.initialFocus() else SwingModifier,
        )
    }

    /**
     * Builds a [JButton] with the same text and the click listener [Button] installs for its `onClick`, and no focus
     * request installed yet.
     */
    override fun createRawSwing(): JButton = JButton(TEXT).apply { addActionListener { onClick() } }

    /**
     * Requests focus on [component] when [parameters] says so, at once if it is showing and on its first showing
     * otherwise, and removes a pending request otherwise.
     */
    override fun updateRawSwing(
        component: JButton,
        parameters: InitialFocusParameters,
    ) {
        for (listener in component.hierarchyListeners) {
            if (listener is FirstShowingFocus) component.removeHierarchyListener(listener)
        }
        if (!parameters.applied) return
        if (component.isShowing) {
            component.requestFocusInWindow()
        } else {
            component.addHierarchyListener(
                FirstShowingFocus(),
            )
        }
    }

    /** The raw listener the fixture installs, told apart from listeners it did not add. */
    private class FirstShowingFocus : HierarchyListener {
        override fun hierarchyChanged(event: HierarchyEvent) {
            val component = event.component
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && component.isShowing) {
                component.removeHierarchyListener(this)
                component.requestFocusInWindow()
            }
        }
    }
}
