// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.InfiniteRepeatableSpec
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [InfiniteTransitionAnimateFloatFixture] varies. */
public data class InfiniteTransitionAnimateFloatParameters(
    val initialValue: Float,
    val targetValue: Float,
    val animationSpec: InfiniteRepeatableSpec<Float>,
    val label: String,
)

/** An infinite [animateFloat] whose current value is shown in a [Label], against a raw [JLabel]. */
public object InfiniteTransitionAnimateFloatFixture :
    ComposableFixture<InfiniteTransitionAnimateFloatParameters, JLabel>(
        initial =
            InfiniteTransitionAnimateFloatParameters(
                initialValue = 0f,
                targetValue = 80f,
                animationSpec = infiniteRepeatable(tween(32)),
                label = "Before",
            ),
    ) {
    override val alternatives: List<InfiniteTransitionAnimateFloatParameters> =
        listOf(
            initial.copy(initialValue = 10f),
            initial.copy(targetValue = 100f),
            initial.copy(animationSpec = infiniteRepeatable(tween(64))),
            initial.copy(label = "After"),
        )

    @Composable
    override fun Content(parameters: InfiniteTransitionAnimateFloatParameters): Unit =
        Content(
            initialValue = parameters.initialValue,
            targetValue = parameters.targetValue,
            animationSpec = parameters.animationSpec,
            label = parameters.label,
        )

    /**
     * Composes an infinite float animation with these values and a [Label] showing the range it runs over,
     * so the text does not depend on the instant the value is read.
     */
    @Composable
    public fun Content(
        initialValue: Float,
        targetValue: Float,
        animationSpec: InfiniteRepeatableSpec<Float>,
        label: String,
    ) {
        val transition = rememberInfiniteTransition(label = "InfiniteTransition")
        val value =
            transition.animateFloat(
                initialValue = initialValue,
                targetValue = targetValue,
                animationSpec = animationSpec,
                label = label,
            )
        Label(rangeText(initialValue, targetValue, value.value))
    }

    /** Creates a raw [JLabel] with no text, before the parameters are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Sets the raw label's text to the range the animation runs over. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: InfiniteTransitionAnimateFloatParameters,
    ) {
        component.text = rangeText(parameters.initialValue, parameters.targetValue, parameters.initialValue)
    }
}

/**
 * The range from [initial] to [target], whatever the instant [current] was read at; `NaN` when the animation
 * produced no number.
 */
internal fun rangeText(
    initial: Float,
    target: Float,
    current: Float,
): String = if (current.isNaN()) "NaN" else "$initial..$target"
