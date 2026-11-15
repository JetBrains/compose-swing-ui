// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures.core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.AnimationSpec
import org.jetbrains.compose.swing.animation.core.animateIntAsState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [AnimateIntAsStateFixture] varies. */
public data class AnimateIntAsStateParameters(
    val targetValue: Int,
    val animationSpec: AnimationSpec<Int>,
    val label: String,
    val finishedListener: ((Int) -> Unit)?,
)

/**
 * An [animateIntAsState] shown as a [Label] against a raw [JLabel] showing the same end state.
 *
 * The animation has no Swing counterpart, so the raw side is compared on the value the animation settles at.
 */
public object AnimateIntAsStateFixture : ComposableFixture<AnimateIntAsStateParameters, JLabel>(
    initial =
        AnimateIntAsStateParameters(
            targetValue = 20,
            animationSpec = tween(32),
            label = "Before",
            finishedListener = null,
        ),
) {
    override val alternatives: List<AnimateIntAsStateParameters> =
        listOf(
            initial.copy(targetValue = 80),
            initial.copy(animationSpec = tween(64)),
            initial.copy(label = "After"),
            initial.copy(finishedListener = { }),
        )

    @Composable
    override fun Content(parameters: AnimateIntAsStateParameters): Unit =
        Content(
            targetValue = parameters.targetValue,
            animationSpec = parameters.animationSpec,
            label = parameters.label,
            finishedListener = parameters.finishedListener,
        )

    /** Composes an [animateIntAsState] with the given arguments, shown as a [Label]. */
    @Composable
    public fun Content(
        targetValue: Int,
        animationSpec: AnimationSpec<Int>,
        label: String,
        finishedListener: ((Int) -> Unit)?,
    ) {
        val value =
            animateIntAsState(
                targetValue = targetValue,
                animationSpec = animationSpec,
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
        parameters: AnimateIntAsStateParameters,
    ) {
        component.text = "${parameters.targetValue}: "
    }
}
