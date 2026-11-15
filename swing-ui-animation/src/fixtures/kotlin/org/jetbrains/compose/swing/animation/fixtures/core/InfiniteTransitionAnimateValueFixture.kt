// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures.core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.AnimationVector1D
import org.jetbrains.compose.swing.animation.core.InfiniteRepeatableSpec
import org.jetbrains.compose.swing.animation.core.TwoWayConverter
import org.jetbrains.compose.swing.animation.core.animateValue
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fixtures.rangeText
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [InfiniteTransitionAnimateValueFixture] varies. */
public data class InfiniteTransitionAnimateValueParameters(
    val initialValue: Float,
    val targetValue: Float,
    val typeConverter: TwoWayConverter<Float, AnimationVector1D>,
    val animationSpec: InfiniteRepeatableSpec<Float>,
    val label: String,
)

/**
 * An infinite [animateValue] whose current value is shown in a [Label], against a raw [JLabel].
 *
 * The animation has no Swing counterpart, so the raw side is compared on the range the animation runs over.
 */
public object InfiniteTransitionAnimateValueFixture :
    ComposableFixture<InfiniteTransitionAnimateValueParameters, JLabel>(
        initial =
            InfiniteTransitionAnimateValueParameters(
                initialValue = 0f,
                targetValue = 80f,
                typeConverter = TwoWayConverter({ AnimationVector1D(it) }, { it.value }),
                animationSpec = infiniteRepeatable(tween(32)),
                label = "Before",
            ),
    ) {
    /** The initial parameters, each with one value changed. */
    override val alternatives: List<InfiniteTransitionAnimateValueParameters> =
        listOf(
            initial.copy(initialValue = 10f),
            initial.copy(targetValue = 100f),
            initial.copy(
                typeConverter =
                    TwoWayConverter({ AnimationVector1D(it * 2f) }, { it.value / 2f }),
            ),
            initial.copy(animationSpec = infiniteRepeatable(tween(64))),
            initial.copy(label = "After"),
        )

    @Composable
    override fun Content(parameters: InfiniteTransitionAnimateValueParameters): Unit =
        Content(
            initialValue = parameters.initialValue,
            targetValue = parameters.targetValue,
            typeConverter = parameters.typeConverter,
            animationSpec = parameters.animationSpec,
            label = parameters.label,
        )

    /**
     * Composes an infinite value animation with these arguments, shown as a [Label] of the range it runs over,
     * so the text does not depend on the instant the value is read.
     */
    @Composable
    public fun Content(
        initialValue: Float,
        targetValue: Float,
        typeConverter: TwoWayConverter<Float, AnimationVector1D>,
        animationSpec: InfiniteRepeatableSpec<Float>,
        label: String,
    ) {
        val transition = rememberInfiniteTransition(label = "Before")
        val value =
            transition.animateValue(
                initialValue = initialValue,
                targetValue = targetValue,
                typeConverter = typeConverter,
                animationSpec = animationSpec,
                label = label,
            )
        Label(text = rangeText(initialValue, targetValue, value.value))
    }

    /** Creates a raw [JLabel] with no text, before the parameters are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Sets the raw label's text to the range the animation runs over for [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: InfiniteTransitionAnimateValueParameters,
    ) {
        component.text = rangeText(parameters.initialValue, parameters.targetValue, parameters.initialValue)
    }
}
