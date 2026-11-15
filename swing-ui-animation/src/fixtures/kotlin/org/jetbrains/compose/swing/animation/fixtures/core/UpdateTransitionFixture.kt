// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures.core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [UpdateTransitionFixture] varies. */
public data class UpdateTransitionParameters(
    val targetState: Boolean,
    val label: String,
)

/**
 * An [updateTransition] driving an animated int shown as a [Label], against a raw [JLabel] showing the same end state.
 *
 * The transition has no Swing counterpart, so the raw side is compared on the value the animation settles at.
 * The [UpdateTransitionParameters.label] only names the transition and has no visible effect, so the raw side
 * does not change for it.
 */
public object UpdateTransitionFixture : ComposableFixture<UpdateTransitionParameters, JLabel>(
    initial = UpdateTransitionParameters(targetState = false, label = "Before"),
) {
    /** The changed values: the target state flipped, and the transition label renamed. */
    override val alternatives: List<UpdateTransitionParameters> =
        listOf(
            initial.copy(targetState = true),
            initial.copy(label = "After"),
        )

    @Composable
    override fun Content(parameters: UpdateTransitionParameters): Unit =
        Content(
            targetState = parameters.targetState,
            label = parameters.label,
        )

    /** Composes an [updateTransition] with the given arguments, shown as a [Label]. */
    @Composable
    public fun Content(
        targetState: Boolean,
        label: String,
    ) {
        val transition = updateTransition(targetState = targetState, label = label)
        val value = transition.animateInt(transitionSpec = { tween(32) }) { if (it) 80 else 20 }
        Label(text = value.value.toString())
    }

    /** Builds an empty raw [JLabel] before the initial state is applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Shows the end state of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: UpdateTransitionParameters,
    ) {
        component.text = if (parameters.targetState) "80" else "20"
    }
}
