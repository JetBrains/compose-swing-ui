// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures.core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [TransitionAnimateIntFixture] varies. */
public data class TransitionAnimateIntParameters(
    val targetState: Boolean,
    val transitionSpec: Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Int>,
    val label: String,
    val targetValueByState: (Boolean) -> Int,
)

/**
 * A [Transition.animateInt] shown as a [Label] against a raw [JLabel] showing the same end state.
 *
 * The animation has no Swing counterpart, so the raw side is compared on the value the animation settles at.
 */
public object TransitionAnimateIntFixture : ComposableFixture<TransitionAnimateIntParameters, JLabel>(
    initial =
        TransitionAnimateIntParameters(
            targetState = false,
            transitionSpec = { tween(32) },
            label = "Before",
            targetValueByState = { if (it) 80 else 20 },
        ),
) {
    override val alternatives: List<TransitionAnimateIntParameters> =
        listOf(
            initial.copy(targetState = true),
            initial.copy(transitionSpec = { tween(64) }),
            initial.copy(label = "After"),
            initial.copy(targetValueByState = { (if (it) 80 else 20) + 5 }),
        )

    @Composable
    override fun Content(parameters: TransitionAnimateIntParameters): Unit =
        Content(
            targetState = parameters.targetState,
            transitionSpec = parameters.transitionSpec,
            label = parameters.label,
            targetValueByState = parameters.targetValueByState,
        )

    /** Composes a [Transition] over [targetState] with a [Transition.animateInt] shown as a [Label]. */
    @Composable
    public fun Content(
        targetState: Boolean,
        transitionSpec: Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Int>,
        label: String,
        targetValueByState: (Boolean) -> Int,
    ) {
        val transition = updateTransition(targetState)
        val value =
            transition.animateInt(
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
        parameters: TransitionAnimateIntParameters,
    ) {
        component.text = parameters.targetValueByState(parameters.targetState).toString()
    }
}
