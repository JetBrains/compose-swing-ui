// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.animateColorAsState
import org.jetbrains.compose.swing.animation.core.AnimationSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.Color
import javax.swing.JLabel

/** The parameters [AnimateColorAsStateFixture] varies. */
public data class AnimateColorAsStateParameters(
    val targetValue: Color,
    val animationSpec: AnimationSpec<Color>,
    val label: String,
    val finishedListener: ((Color) -> Unit)?,
)

/** An [animateColorAsState] whose current color is shown in a [Label], against a raw [JLabel] showing the same text. */
public object AnimateColorAsStateFixture : ComposableFixture<AnimateColorAsStateParameters, JLabel>(
    initial =
        AnimateColorAsStateParameters(
            targetValue = Color.RED,
            animationSpec = tween(32),
            label = "Before",
            finishedListener = null,
        ),
) {
    override val alternatives: List<AnimateColorAsStateParameters> =
        listOf(
            initial.copy(targetValue = Color.BLUE),
            initial.copy(animationSpec = tween(64)),
            initial.copy(label = "After"),
            initial.copy(finishedListener = { }),
        )

    @Composable
    override fun Content(parameters: AnimateColorAsStateParameters): Unit =
        Content(
            targetValue = parameters.targetValue,
            animationSpec = parameters.animationSpec,
            label = parameters.label,
            finishedListener = parameters.finishedListener,
        )

    /** Composes an [animateColorAsState] with these values and a [Label] showing its current color. */
    @Composable
    public fun Content(
        targetValue: Color,
        animationSpec: AnimationSpec<Color>,
        label: String,
        finishedListener: ((Color) -> Unit)?,
    ) {
        val value =
            animateColorAsState(
                targetValue = targetValue,
                animationSpec = animationSpec,
                label = label,
                finishedListener = finishedListener,
            )
        Label("${value.value}: ")
    }

    /** Creates a raw [JLabel] with no text, before the parameters are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Sets the raw label's text to the target color, which is the end state the animation settles at. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: AnimateColorAsStateParameters,
    ) {
        component.text = "${parameters.targetValue}: "
    }
}
