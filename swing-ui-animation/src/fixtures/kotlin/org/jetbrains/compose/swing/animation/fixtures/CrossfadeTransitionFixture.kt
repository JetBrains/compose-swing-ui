// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.Crossfade
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.border.EmptyBorder

/** The parameters [CrossfadeTransitionFixture] varies: the name of the transition that the crossfade runs on. */
public data class CrossfadeTransitionParameters(
    val transitionLabel: String,
)

/**
 * A [Crossfade] on a [Transition][org.jetbrains.compose.swing.animation.core.Transition] settled on a target state,
 * showing a [Label] of that state, against a raw [JPanel] tree holding the same label at its end state.
 */
public object CrossfadeTransitionFixture : ComposableFixture<CrossfadeTransitionParameters, JPanel>(
    initial = CrossfadeTransitionParameters(transitionLabel = "Before"),
) {
    override val alternatives: List<CrossfadeTransitionParameters> =
        listOf(
            initial.copy(transitionLabel = "After"),
        )

    @Composable
    override fun Content(parameters: CrossfadeTransitionParameters): Unit =
        Content(transitionLabel = parameters.transitionLabel)

    /**
     * Composes a [Box] holding a [Crossfade] on the transition named [transitionLabel], settled on `false`,
     * padded by 2 and fading over 32 milliseconds.
     */
    @Composable
    public fun Content(transitionLabel: String) {
        val transition = updateTransition(targetState = false, label = transitionLabel)
        Box {
            transition.Crossfade(
                modifier = SwingModifier.padding(2),
                animationSpec = tween(32),
                contentKey = { it.toString() },
            ) {
                Label(contentText(state = it))
            }
        }
    }

    /**
     * Builds a raw panel tree holding one [JLabel] already at its end state, padded by 2 and showing the text of a
     * settled `false` state.
     */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            layout = OverlayLayout(this)
            add(
                JPanel().apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    layout = OverlayLayout(this)
                    border = EmptyBorder(2, 2, 2, 2)
                    add(JLabel(contentText(state = false)))
                },
            )
        }

    /**
     * Applies [parameters] to [component]. The transition name only labels the transition the Compose side animates,
     * and it settles on the same state, so nothing is applied.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: CrossfadeTransitionParameters,
    ): Unit = Unit

    private fun contentText(state: Boolean): String = "Before $state"
}
