// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.AnimationVector1D
import org.jetbrains.compose.swing.animation.core.TwoWayConverter
import org.jetbrains.compose.swing.animation.core.createDeferredAnimation
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [CreateDeferredAnimationFixture] varies. */
public data class CreateDeferredAnimationParameters(
    val targetState: Boolean,
    val typeConverter: TwoWayConverter<Float, AnimationVector1D>,
    val label: String,
)

/**
 * A [createDeferredAnimation] whose current value is shown in a [Label], against a raw [JLabel] showing the value
 * the animation settles at.
 */
public object CreateDeferredAnimationFixture : ComposableFixture<CreateDeferredAnimationParameters, JLabel>(
    initial =
        CreateDeferredAnimationParameters(
            targetState = false,
            typeConverter = TwoWayConverter({ AnimationVector1D(it) }, { it.value }),
            label = "Before",
        ),
) {
    override val alternatives: List<CreateDeferredAnimationParameters> =
        listOf(
            initial.copy(targetState = true),
            initial.copy(
                typeConverter =
                    TwoWayConverter(
                        { AnimationVector1D(it * 2f) },
                        { it.value / 2f },
                    ),
            ),
            initial.copy(label = "After"),
        )

    @Composable
    override fun Content(parameters: CreateDeferredAnimationParameters): Unit =
        Content(
            targetState = parameters.targetState,
            typeConverter = parameters.typeConverter,
            label = parameters.label,
        )

    /**
     * Composes a transition to [targetState], a [createDeferredAnimation] with [typeConverter] and [label] on it, and a
     * [Label] showing the animated value.
     */
    @Composable
    public fun Content(
        targetState: Boolean,
        typeConverter: TwoWayConverter<Float, AnimationVector1D>,
        label: String,
    ) {
        val transition = updateTransition(targetState = targetState, label = "Transition")
        val animation = transition.createDeferredAnimation(typeConverter = typeConverter, label = label)
        val value = animation.animate(transitionSpec = { tween(32) }) { if (it) 80f else 20f }
        Label(value.value.toString())
    }

    /** Creates a raw [JLabel] with no text, before the parameters are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /**
     * Sets the raw label's text to the value the animation settles at for [parameters]. The converter and label do not
     * change that end value, so only the target state is reflected here.
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: CreateDeferredAnimationParameters,
    ) {
        component.text = if (parameters.targetState) "80.0" else "20.0"
    }
}
