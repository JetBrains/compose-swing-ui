// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.border.EmptyBorder

/** The parameters [AnimatedVisibilityStateFixture] varies. */
public data class AnimatedVisibilityStateParameters(
    val visible: Boolean,
)

/**
 * An [AnimatedVisibility] driven by a [MutableTransitionState] whose target is [Boolean], against a raw [JPanel]
 * showing the same content.
 */
public object AnimatedVisibilityStateFixture : ComposableFixture<AnimatedVisibilityStateParameters, JPanel>(
    initial = AnimatedVisibilityStateParameters(visible = true),
) {
    override val alternatives: List<AnimatedVisibilityStateParameters> =
        listOf(
            initial.copy(visible = false),
        )

    @Composable
    override fun Content(parameters: AnimatedVisibilityStateParameters): Unit = Content(visible = parameters.visible)

    /**
     * Composes a [Box] around an [AnimatedVisibility] over a remembered [MutableTransitionState] whose target is
     * [visible].
     */
    @Composable
    public fun Content(visible: Boolean) {
        val visibleState = remember { MutableTransitionState(visible) }
        visibleState.targetState = visible
        Box {
            AnimatedVisibility(
                visibleState = visibleState,
                modifier = SwingModifier.padding(2),
                enter = fadeIn(tween(32)),
                exit = fadeOut(tween(32)),
                label = "Before",
                content = { Label(BEFORE_TEXT) },
            )
        }
    }

    /**
     * Builds the empty raw panel; [updateRawSwing] adds the padded container panel holding a label while visible.
     */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            layout = OverlayLayout(this)
        }

    /**
     * Applies the end state of [parameters] to [component]: the container is present while visible and gone while
     * hidden, as the composed side emits no node for it.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AnimatedVisibilityStateParameters,
    ) {
        val container = component.components.singleOrNull { it is JPanel }
        if (parameters.visible && container == null) {
            component.add(newContainer())
            component.revalidate()
        } else if (!parameters.visible && container != null) {
            val bounds = container.bounds
            component.remove(container)
            component.revalidate()
            component.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    private fun newContainer(): JPanel =
        JPanel().apply {
            isOpaque = false
            layout = OverlayLayout(this)
            border = EmptyBorder(2, 2, 2, 2)
            add(JLabel(BEFORE_TEXT))
        }

    /** The label text of the initial content. */
    private const val BEFORE_TEXT = "Before: 80"
}
