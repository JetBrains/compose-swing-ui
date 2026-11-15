// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.animateColor
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.Color
import javax.swing.JLabel

/** The parameters [TransitionAnimateColorFixture] varies. */
public data class TransitionAnimateColorParameters(
    val inputTargetState: Boolean,
    val transitionLabel: String,
    val transitionSpec: FiniteAnimationSpec<Color>,
    val label: String,
    val targetValueByState: (Boolean) -> Color,
)

/**
 * A [Transition][org.jetbrains.compose.swing.animation.core.Transition] carrying [animateColor], with its current
 * color shown in a [Label], against a raw [JLabel] showing the color the animation settles at.
 */
public object TransitionAnimateColorFixture : ComposableFixture<TransitionAnimateColorParameters, JLabel>(
    initial =
        TransitionAnimateColorParameters(
            inputTargetState = false,
            transitionLabel = "Before",
            transitionSpec = tween(durationMillis = 32),
            label = "Before",
            targetValueByState = { if (it) Color.BLUE else Color.RED },
        ),
) {
    override val alternatives: List<TransitionAnimateColorParameters> =
        listOf(
            initial.copy(inputTargetState = true),
            initial.copy(transitionLabel = "After"),
            initial.copy(transitionSpec = tween(durationMillis = 64)),
            initial.copy(label = "After"),
            initial.copy(targetValueByState = { if (it) Color.GREEN else Color.YELLOW }),
        )

    @Composable
    override fun Content(parameters: TransitionAnimateColorParameters): Unit =
        Content(
            inputTargetState = parameters.inputTargetState,
            transitionLabel = parameters.transitionLabel,
            transitionSpec = parameters.transitionSpec,
            label = parameters.label,
            targetValueByState = parameters.targetValueByState,
        )

    /**
     * Composes a [Label] showing the color of an [animateColor] on a transition named [transitionLabel] and fed
     * [inputTargetState], with the given [transitionSpec], [label] and [targetValueByState].
     */
    @Composable
    public fun Content(
        inputTargetState: Boolean,
        transitionLabel: String,
        transitionSpec: FiniteAnimationSpec<Color>,
        label: String,
        targetValueByState: (Boolean) -> Color,
    ) {
        val transition = updateTransition(inputTargetState, label = transitionLabel)
        val value =
            transition.animateColor(
                transitionSpec = { transitionSpec },
                label = label,
            ) { targetState -> targetValueByState(targetState) }
        Label(value.value.toString())
    }

    /** Creates a raw [JLabel] with no text, before the parameters are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /**
     * Sets the raw label's text to the color the transition settles at for [parameters]. The transition
     * name and the animation spec change only how the color travels, so they do not change the text.
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: TransitionAnimateColorParameters,
    ) {
        component.text = parameters.targetValueByState(parameters.inputTargetState).toString()
    }
}
