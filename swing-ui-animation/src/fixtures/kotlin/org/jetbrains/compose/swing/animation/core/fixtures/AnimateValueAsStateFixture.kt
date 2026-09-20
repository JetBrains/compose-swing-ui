// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.AnimationSpec
import org.jetbrains.compose.swing.animation.core.AnimationVector1D
import org.jetbrains.compose.swing.animation.core.TwoWayConverter
import org.jetbrains.compose.swing.animation.core.animateValueAsState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [AnimateValueAsStateFixture] varies. */
public data class AnimateValueAsStateParameters(
    val targetValue: Float,
    val typeConverter: TwoWayConverter<Float, AnimationVector1D>,
    val animationSpec: AnimationSpec<Float>,
    val visibilityThreshold: Float?,
    val label: String,
    val finishedListener: ((Float) -> Unit)?,
)

/**
 * An [animateValueAsState] shown as a [Label] against a raw [JLabel] showing the same end state.
 *
 * The animation has no Swing counterpart, so the raw side is compared on the value the animation settles at.
 */
public object AnimateValueAsStateFixture : ComposableFixture<AnimateValueAsStateParameters, JLabel>(
    initial =
        AnimateValueAsStateParameters(
            targetValue = 20f,
            typeConverter = TwoWayConverter({ AnimationVector1D(it) }, { it.value }),
            animationSpec = tween(32),
            visibilityThreshold = 0.01f,
            label = "Before",
            finishedListener = null,
        ),
) {
    /** The alternatives, each changing one value of [initial]. */
    override val alternatives: List<AnimateValueAsStateParameters> =
        listOf(
            initial.copy(targetValue = 80f),
            initial.copy(typeConverter = TwoWayConverter({ AnimationVector1D(it * 2f) }, { it.value / 2f })),
            initial.copy(animationSpec = tween(64)),
            initial.copy(visibilityThreshold = 0.1f),
            initial.copy(label = "After"),
            initial.copy(finishedListener = { }),
        )

    @Composable
    override fun Content(parameters: AnimateValueAsStateParameters): Unit =
        Content(
            targetValue = parameters.targetValue,
            typeConverter = parameters.typeConverter,
            animationSpec = parameters.animationSpec,
            visibilityThreshold = parameters.visibilityThreshold,
            label = parameters.label,
            finishedListener = parameters.finishedListener,
        )

    /** Composes an [animateValueAsState] with the given arguments, shown as a [Label]. */
    @Composable
    public fun Content(
        targetValue: Float,
        typeConverter: TwoWayConverter<Float, AnimationVector1D>,
        animationSpec: AnimationSpec<Float>,
        visibilityThreshold: Float?,
        label: String,
        finishedListener: ((Float) -> Unit)?,
    ) {
        val value =
            animateValueAsState(
                targetValue = targetValue,
                typeConverter = typeConverter,
                animationSpec = animationSpec,
                visibilityThreshold = visibilityThreshold,
                label = label,
                finishedListener = finishedListener,
            )
        Label(text = "${value.value}: ")
    }

    /** Builds an empty raw [JLabel]. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Shows the end state of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: AnimateValueAsStateParameters,
    ) {
        component.text = "${parameters.targetValue}: "
    }
}
