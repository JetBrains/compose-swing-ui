// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [TransitionAnimateFloatFixture] varies. */
public data class TransitionAnimateFloatParameters(
    val targetState: Boolean,
    val transitionSpec: Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
    val label: String,
    val targetValueByState: (Boolean) -> Float,
)

/**
 * A [Transition.animateFloat] shown as a [Label] against a raw [JLabel] showing the same end state.
 *
 * The animation has no Swing counterpart, so the raw side is compared on the value the animation settles at.
 */
public object TransitionAnimateFloatFixture : ComposableFixture<TransitionAnimateFloatParameters, JLabel>(
    initial =
        TransitionAnimateFloatParameters(
            targetState = false,
            transitionSpec = { tween(32) },
            label = "Before",
            targetValueByState = { if (it) 80f else 20f },
        ),
) {
    override val alternatives: List<TransitionAnimateFloatParameters> =
        listOf(
            initial.copy(targetState = true),
            initial.copy(transitionSpec = { tween(64) }),
            initial.copy(label = "After"),
            initial.copy(targetValueByState = { (if (it) 80f else 20f) + 5f }),
        )

    @Composable
    override fun Content(parameters: TransitionAnimateFloatParameters): Unit =
        Content(
            targetState = parameters.targetState,
            transitionSpec = parameters.transitionSpec,
            label = parameters.label,
            targetValueByState = parameters.targetValueByState,
        )

    /** Composes a [Transition] over [targetState] with a [Transition.animateFloat] shown as a [Label]. */
    @Composable
    public fun Content(
        targetState: Boolean,
        transitionSpec: Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
        label: String,
        targetValueByState: (Boolean) -> Float,
    ) {
        val transition = updateTransition(targetState)
        val value =
            transition.animateFloat(
                transitionSpec = { transitionSpec() },
                label = label,
                targetValueByState = { targetValueByState(it) },
            )
        Label(text = value.value.toString())
    }

    /** Builds an empty raw [JLabel]. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Shows the end state of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: TransitionAnimateFloatParameters,
    ) {
        component.text = parameters.targetValueByState(parameters.targetState).toString()
    }
}
