// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures.core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.AnimationSpec
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [AnimateFloatAsStateFixture] varies. */
public data class AnimateFloatAsStateParameters(
    val targetValue: Float,
    val animationSpec: AnimationSpec<Float>,
    val visibilityThreshold: Float,
    val label: String,
    val finishedListener: ((Float) -> Unit)?,
)

/**
 * An [animateFloatAsState] shown as a [Label] against a raw [JLabel] showing the same end state.
 *
 * The animation has no Swing counterpart, so the raw side is compared on the value the animation settles at.
 */
public object AnimateFloatAsStateFixture : ComposableFixture<AnimateFloatAsStateParameters, JLabel>(
    initial =
        AnimateFloatAsStateParameters(
            targetValue = 20f,
            animationSpec = tween(32),
            visibilityThreshold = 0.01f,
            label = "Before",
            finishedListener = null,
        ),
) {
    override val alternatives: List<AnimateFloatAsStateParameters> =
        listOf(
            initial.copy(targetValue = 80f),
            initial.copy(animationSpec = tween(64)),
            initial.copy(visibilityThreshold = 0.1f),
            initial.copy(label = "After"),
            initial.copy(finishedListener = { }),
        )

    @Composable
    override fun Content(parameters: AnimateFloatAsStateParameters): Unit =
        Content(
            targetValue = parameters.targetValue,
            animationSpec = parameters.animationSpec,
            visibilityThreshold = parameters.visibilityThreshold,
            label = parameters.label,
            finishedListener = parameters.finishedListener,
        )

    /** Composes an [animateFloatAsState] with the given arguments, shown as a [Label]. */
    @Composable
    public fun Content(
        targetValue: Float,
        animationSpec: AnimationSpec<Float>,
        visibilityThreshold: Float,
        label: String,
        finishedListener: ((Float) -> Unit)?,
    ) {
        val value =
            animateFloatAsState(
                targetValue = targetValue,
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
        parameters: AnimateFloatAsStateParameters,
    ) {
        component.text = "${parameters.targetValue}: "
    }
}
