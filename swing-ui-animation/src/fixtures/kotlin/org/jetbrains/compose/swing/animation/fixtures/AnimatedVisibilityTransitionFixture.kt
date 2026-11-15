// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.AnimatedVisibilityScope
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [AnimatedVisibilityTransitionFixture] varies. */
public data class AnimatedVisibilityTransitionParameters(
    val visibleAbove: Int,
)

/**
 * An [AnimatedVisibility] on a [androidx.compose.runtime.State]-driven transition settled at its target, against a raw
 * [JPanel] showing the same content.
 */
public object AnimatedVisibilityTransitionFixture : ComposableFixture<AnimatedVisibilityTransitionParameters, JPanel>(
    initial =
        AnimatedVisibilityTransitionParameters(
            visibleAbove = 50,
        ),
) {
    override val alternatives: List<AnimatedVisibilityTransitionParameters> =
        listOf(
            initial.copy(visibleAbove = 90),
        )

    @Composable
    override fun Content(parameters: AnimatedVisibilityTransitionParameters): Unit =
        Content(
            visibleAbove = parameters.visibleAbove,
        )

    /**
     * Composes a [Box] around an [AnimatedVisibility] on a transition targeting a fixed state, with this value for its
     * visibility threshold.
     */
    @Composable
    public fun Content(visibleAbove: Int) {
        val transition = updateTransition(TARGET_STATE)
        val before: @Composable AnimatedVisibilityScope.() -> Unit = { Label(BEFORE_TEXT) }
        Box {
            transition.AnimatedVisibility(
                visible = { it > visibleAbove },
                modifier = SwingModifier.padding(2),
                enter = fadeIn(tween(32)),
                exit = fadeOut(tween(32)),
                content = before,
            )
        }
    }

    /**
     * Builds the empty raw panel; [updateRawSwing] adds the container panel holding a label, inset by the fixed
     * padding that [Content] composes as layout padding, while visible.
     */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            layout = GridBagLayout()
        }

    /**
     * Applies the end state of [parameters] to [component]: the container is present while visible and gone while
     * hidden, as the composed side emits no node for it.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AnimatedVisibilityTransitionParameters,
    ) {
        val visible = TARGET_STATE > parameters.visibleAbove
        if (visible == (component.componentCount > 0)) return
        if (visible) {
            component.add(
                JPanel().apply {
                    isOpaque = false
                    layout = OverlayLayout(this)
                    add(JLabel(BEFORE_TEXT))
                },
                GridBagConstraints().apply { insets = Insets(2, 2, 2, 2) },
            )
            component.revalidate()
        } else {
            val bounds = component.getComponent(0).bounds
            component.removeAll()
            component.revalidate()
            component.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    private const val TARGET_STATE = 80
    private const val BEFORE_TEXT = "Before: 80"
}
