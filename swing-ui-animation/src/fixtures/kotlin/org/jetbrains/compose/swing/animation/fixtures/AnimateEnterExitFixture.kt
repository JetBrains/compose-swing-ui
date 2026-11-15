// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [AnimateEnterExitFixture] varies. */
public data class AnimateEnterExitParameters(
    val visible: Boolean,
    val enterDurationMillis: Int,
    val exitDurationMillis: Int,
    val label: String,
)

/**
 * A child that declares its own enter and exit inside an [AnimatedVisibility], settled at its end state, against a raw
 * [JPanel] showing the same child.
 */
public object AnimateEnterExitFixture : ComposableFixture<AnimateEnterExitParameters, JPanel>(
    initial =
        AnimateEnterExitParameters(
            visible = true,
            enterDurationMillis = 32,
            exitDurationMillis = 32,
            label = "Before",
        ),
) {
    override val alternatives: List<AnimateEnterExitParameters> =
        listOf(
            initial.copy(visible = false),
            initial.copy(enterDurationMillis = 64),
            initial.copy(exitDurationMillis = 64),
            initial.copy(label = "After"),
        )

    @Composable
    override fun Content(parameters: AnimateEnterExitParameters): Unit =
        Content(
            visible = parameters.visible,
            enterDurationMillis = parameters.enterDurationMillis,
            exitDurationMillis = parameters.exitDurationMillis,
            label = parameters.label,
        )

    /**
     * Composes a [Box] around an [AnimatedVisibility] around a [Box] whose enter, exit and transition name come from
     * these values.
     */
    @Composable
    public fun Content(
        visible: Boolean,
        enterDurationMillis: Int,
        exitDurationMillis: Int,
        label: String,
    ) {
        Box {
            AnimatedVisibility(visible = visible) {
                Box(
                    modifier =
                        SwingModifier.animateEnterExit(
                            enter = fadeIn(tween(enterDurationMillis)),
                            exit = fadeOut(tween(exitDurationMillis)),
                            label = label,
                        ),
                ) {
                    Label(CHILD_TEXT)
                }
            }
        }
    }

    /** Builds the raw panel that holds the animated child once [updateRawSwing] shows it. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            layout = OverlayLayout(this)
        }

    /**
     * Applies the end state of [parameters] to [component]: a settled-hidden container leaves the tree, so the child is
     * removed from the panel and added again when shown. The durations and the transition name shape only the way the
     * end state is reached, so they set nothing.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AnimateEnterExitParameters,
    ) {
        val visible = parameters.visible
        if (visible == (component.componentCount > 0)) return
        if (visible) {
            component.add(
                JPanel().apply {
                    isOpaque = false
                    layout = OverlayLayout(this)
                    add(
                        JPanel().apply {
                            isOpaque = false
                            layout = OverlayLayout(this)
                            add(JLabel(CHILD_TEXT))
                        },
                    )
                },
            )
            component.revalidate()
        } else {
            val bounds = component.getComponent(0).bounds
            component.removeAll()
            component.revalidate()
            component.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    /** The text of the child label. */
    private const val CHILD_TEXT = "Animated child"
}
