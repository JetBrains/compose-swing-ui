// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.animateColor
import org.jetbrains.compose.swing.animation.core.InfiniteRepeatableSpec
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.Color
import javax.swing.JLabel

/** The parameters [InfiniteTransitionAnimateColorFixture] varies. */
public data class InfiniteTransitionAnimateColorParameters(
    val initialValue: Color,
    val targetValue: Color,
    val animationSpec: InfiniteRepeatableSpec<Color>,
    val label: String,
)

/** An infinite [animateColor] whose current color is shown in a [Label], against a raw [JLabel]. */
public object InfiniteTransitionAnimateColorFixture :
    ComposableFixture<InfiniteTransitionAnimateColorParameters, JLabel>(
        initial =
            InfiniteTransitionAnimateColorParameters(
                initialValue = Color.RED,
                targetValue = Color.BLUE,
                animationSpec = infiniteRepeatable(tween(32)),
                label = "Before",
            ),
    ) {
    override val alternatives: List<InfiniteTransitionAnimateColorParameters> =
        listOf(
            initial.copy(initialValue = Color.GREEN),
            initial.copy(targetValue = Color.YELLOW),
            initial.copy(animationSpec = infiniteRepeatable(tween(64))),
            initial.copy(label = "After"),
        )

    @Composable
    override fun Content(parameters: InfiniteTransitionAnimateColorParameters): Unit =
        Content(
            initialValue = parameters.initialValue,
            targetValue = parameters.targetValue,
            animationSpec = parameters.animationSpec,
            label = parameters.label,
        )

    /**
     * Composes an infinite color animation with these values and a [Label] showing the range it runs over,
     * so the text does not depend on the instant the color is read.
     */
    @Composable
    public fun Content(
        initialValue: Color,
        targetValue: Color,
        animationSpec: InfiniteRepeatableSpec<Color>,
        label: String,
    ) {
        val transition = rememberInfiniteTransition(label = "InfiniteTransition")
        val value =
            transition.animateColor(
                initialValue = initialValue,
                targetValue = targetValue,
                animationSpec = animationSpec,
                label = label,
            )
        Label(colorRangeText(initialValue, targetValue, value.value))
    }

    /** Creates a raw [JLabel] with no text, before the parameters are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Sets the raw label's text to the range the animation runs over. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: InfiniteTransitionAnimateColorParameters,
    ) {
        component.text = colorRangeText(parameters.initialValue, parameters.targetValue, parameters.initialValue)
    }
}

/** The range from [initial] to [target], whatever the instant [current] was read at, while [current] is a color. */
private fun colorRangeText(
    initial: Color,
    target: Color,
    current: Color,
): String = if (current.alpha in 0..255) "$initial..$target" else "not a color"
