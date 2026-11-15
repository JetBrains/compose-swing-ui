// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.animateContentSize
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [AnimateContentSizeFixture] varies. */
public data class AnimateContentSizeParameters(
    val animationSpec: FiniteAnimationSpec<Dimension>,
    val alignment: Alignment,
    val finishedListener: ((initialValue: Dimension, targetValue: Dimension) -> Unit)?,
)

/**
 * A [Box] carrying [animateContentSize] against a raw [JPanel] at the square size the box settles at.
 *
 * The animation parameters shape only how the size travels, so every alternative settles at the same size.
 */
public object AnimateContentSizeFixture : ComposableFixture<AnimateContentSizeParameters, JPanel>(
    initial =
        AnimateContentSizeParameters(
            animationSpec = tween(durationMillis = 32),
            alignment = Alignment.TopStart,
            finishedListener = null,
        ),
) {
    /** The side length, in pixels, of the square the box is sized to. */
    private const val SIZE = 40

    override val alternatives: List<AnimateContentSizeParameters> =
        listOf(
            initial.copy(animationSpec = tween(durationMillis = 64)),
            initial.copy(alignment = Alignment.Center),
            initial.copy(finishedListener = { _, _ -> }),
        )

    @Composable
    override fun Content(parameters: AnimateContentSizeParameters): Unit =
        Content(parameters.animationSpec, parameters.alignment, parameters.finishedListener)

    /** Composes a square [Box] that animates its size with [animationSpec], [alignment] and [finishedListener]. */
    @Composable
    public fun Content(
        animationSpec: FiniteAnimationSpec<Dimension>,
        alignment: Alignment,
        finishedListener: ((initialValue: Dimension, targetValue: Dimension) -> Unit)?,
    ) {
        Box {
            Box(
                SwingModifier
                    .animateContentSize(
                        animationSpec = animationSpec,
                        alignment = alignment,
                        finishedListener = finishedListener,
                    ).size(SIZE),
            )
        }
    }

    /**
     * Creates the outer panel that wraps its only child, the panel [updateRawSwing] gives the square preferred size.
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
                },
            )
        }

    /**
     * Gives the child of [component] the preferred size of the square the box settles at. The animation parameters
     * change only how the size travels, so none of them is applied to the component at rest.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AnimateContentSizeParameters,
    ) {
        val child = component.components.single()
        child.preferredSize = Dimension(SIZE, SIZE)
    }
}
