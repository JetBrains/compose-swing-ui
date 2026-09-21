// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.modifier.interaction.rememberFocusRequester
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [FocusRequesterFixture] varies: which of the two labels the requester is bound to. */
public data class FocusRequesterParameters(
    val secondLabel: Boolean,
)

/**
 * A [Panel] of two [Label]s, one carrying [SwingModifier.focusRequester], with a focus request made through the
 * requester, against a raw [JPanel] of two labels whose selected label is asked for focus directly.
 */
public object FocusRequesterFixture : ComposableFixture<FocusRequesterParameters, JPanel>(
    initial = FocusRequesterParameters(secondLabel = false),
) {
    /** The copy of [initial] that binds the requester to the second label. */
    override val alternatives: List<FocusRequesterParameters> =
        listOf(
            initial.copy(secondLabel = true),
        )

    @Composable
    override fun Content(parameters: FocusRequesterParameters): Unit = Content(parameters.secondLabel)

    /**
     * Composes a [Panel] of two labels and requests focus through a requester bound to the second one when
     * [secondLabel] is set.
     */
    @Composable
    public fun Content(secondLabel: Boolean) {
        val requester = rememberFocusRequester()
        Panel {
            Label("First", modifier = if (secondLabel) SwingModifier else SwingModifier.focusRequester(requester))
            Label("Second", modifier = if (secondLabel) SwingModifier.focusRequester(requester) else SwingModifier)
        }
        SideEffect { requester.requestFocus() }
    }

    /** Builds the raw panel with the two labels, before any focus is requested. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            add(JLabel("First"))
            add(JLabel("Second"))
        }

    /** Asks the raw label that [parameters] selects for focus in its window. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: FocusRequesterParameters,
    ) {
        component.getComponent(if (parameters.secondLabel) 1 else 0).requestFocusInWindow()
    }
}
