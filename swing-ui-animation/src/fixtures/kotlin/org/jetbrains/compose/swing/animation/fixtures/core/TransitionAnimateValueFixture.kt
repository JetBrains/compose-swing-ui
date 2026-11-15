// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures.core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.AnimationVector1D
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.TwoWayConverter
import org.jetbrains.compose.swing.animation.core.animateValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [TransitionAnimateValueFixture] varies. */
public data class TransitionAnimateValueParameters(
    val targetState: Boolean,
    val typeConverter: TwoWayConverter<Float, AnimationVector1D>,
    val transitionSpec: Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
    val label: String,
    val targetOffset: Float,
)

/**
 * A [Transition.animateValue][org.jetbrains.compose.swing.animation.core.animateValue] shown as a [Label] against a
 * raw [JLabel] showing the same end state.
 *
 * The animation has no Swing counterpart, so the raw side is compared on the value the animation settles at.
 */
public object TransitionAnimateValueFixture : ComposableFixture<TransitionAnimateValueParameters, JLabel>(
    initial =
        TransitionAnimateValueParameters(
            targetState = false,
            typeConverter = DirectConverter,
            transitionSpec = { tween(32) },
            label = "Before",
            targetOffset = 0f,
        ),
) {
    private const val IDLE_VALUE = 20f
    private const val ACTIVE_VALUE = 80f

    /** The alternatives, each changing one value of [initial]. */
    override val alternatives: List<TransitionAnimateValueParameters> =
        listOf(
            initial.copy(targetState = true),
            initial.copy(typeConverter = ScaledConverter),
            initial.copy(transitionSpec = { tween(64) }),
            initial.copy(label = "After"),
            initial.copy(targetOffset = 5f),
        )

    @Composable
    override fun Content(parameters: TransitionAnimateValueParameters): Unit =
        Content(
            targetState = parameters.targetState,
            typeConverter = parameters.typeConverter,
            transitionSpec = parameters.transitionSpec,
            label = parameters.label,
            targetOffset = parameters.targetOffset,
        )

    /** Composes a transition-owned [animateValue] shown as a [Label]. */
    @Composable
    public fun Content(
        targetState: Boolean,
        typeConverter: TwoWayConverter<Float, AnimationVector1D>,
        transitionSpec: Transition.Segment<Boolean>.() -> FiniteAnimationSpec<Float>,
        label: String,
        targetOffset: Float,
    ) {
        val transition = updateTransition(targetState = targetState)
        val value =
            transition.animateValue(
                typeConverter = typeConverter,
                transitionSpec = { transitionSpec() },
                label = label,
            ) {
                (if (it) ACTIVE_VALUE else IDLE_VALUE) + targetOffset
            }
        Label(text = value.value.toString())
    }

    /** Builds an empty raw [JLabel]. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Shows the end state of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: TransitionAnimateValueParameters,
    ) {
        val end = (if (parameters.targetState) ACTIVE_VALUE else IDLE_VALUE) + parameters.targetOffset
        component.text = end.toString()
    }
}

/** Passes the value through as it is. */
private val DirectConverter = TwoWayConverter<Float, AnimationVector1D>({ AnimationVector1D(it) }, { it.value })

/** Animates twice the value. */
private val ScaledConverter =
    TwoWayConverter<Float, AnimationVector1D>({ AnimationVector1D(it * 2f) }, { it.value / 2f })
