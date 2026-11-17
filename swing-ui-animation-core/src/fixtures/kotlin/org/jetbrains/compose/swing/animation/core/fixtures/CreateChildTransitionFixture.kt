// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.ExperimentalTransitionApi
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.createChildTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [CreateChildTransitionFixture] varies. */
public data class CreateChildTransitionParameters(
    val targetState: Boolean,
    val label: String,
    val offset: Int,
)

/**
 * A child transition created with `createChildTransition` and shown as a [Label] against a raw [JLabel] showing
 * the same end state.
 *
 * The transition has no Swing counterpart, so the raw side is compared on the value the child settles at.
 */
public object CreateChildTransitionFixture : ComposableFixture<CreateChildTransitionParameters, JLabel>(
    initial =
        CreateChildTransitionParameters(
            targetState = false,
            label = "Before",
            offset = 0,
        ),
) {
    override val alternatives: List<CreateChildTransitionParameters> =
        listOf(
            initial.copy(targetState = true),
            initial.copy(label = "After"),
            initial.copy(offset = 5),
        )

    @Composable
    override fun Content(parameters: CreateChildTransitionParameters): Unit =
        Content(
            targetState = parameters.targetState,
            label = parameters.label,
            offset = parameters.offset,
        )

    /**
     * Composes a child transition over [targetState] whose animated value, plus [offset], is shown as a [Label].
     * A child follows its parent's target state, so [offset] is added after the animation rather than to the mapping.
     */
    @OptIn(ExperimentalTransitionApi::class)
    @Composable
    public fun Content(
        targetState: Boolean,
        label: String,
        offset: Int,
    ) {
        val transition = updateTransition(targetState)
        val child =
            transition.createChildTransition(label = label) {
                if (it) 80 else 20
            }
        val value = child.animateInt(transitionSpec = { tween(32) }) { it }
        Label(text = (value.value + offset).toString())
    }

    /** Builds an empty raw [JLabel]. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Shows the end state of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: CreateChildTransitionParameters,
    ) {
        component.text = ((if (parameters.targetState) 80 else 20) + parameters.offset).toString()
    }
}
