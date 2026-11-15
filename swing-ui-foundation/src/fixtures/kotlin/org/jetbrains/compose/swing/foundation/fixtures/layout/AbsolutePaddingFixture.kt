// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.absolutePadding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/** The parameters [AbsolutePaddingFixture] varies. */
public data class AbsolutePaddingParameters(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * A [Box] with `absolutePadding` around a fixed-size child, against a raw [JPanel] that places its child the same
 * distance in from each edge.
 */
public object AbsolutePaddingFixture : ComposableFixture<AbsolutePaddingParameters, JPanel>(
    initial = AbsolutePaddingParameters(left = 4, top = 4, right = 4, bottom = 4),
) {
    private const val CONTENT_WIDTH = 240
    private const val CONTENT_HEIGHT = 180

    override val alternatives: List<AbsolutePaddingParameters> =
        listOf(
            initial.copy(left = 16),
            initial.copy(top = 16),
            initial.copy(right = 16),
            initial.copy(bottom = 16),
        )

    @Composable
    override fun Content(parameters: AbsolutePaddingParameters): Unit =
        Content(parameters.left, parameters.top, parameters.right, parameters.bottom)

    /**
     * Composes a [Box] that reserves [left], [top], [right] and [bottom] around a fixed-size child, inside a
     * wrapping [Box].
     */
    @Composable
    public fun Content(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.absolutePadding(left = left, top = top, right = right, bottom = bottom)) {
                Box(modifier = SwingModifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
    }

    /** Creates the raw outer [JPanel], which wraps the padded panel that holds the fixed-size child. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    add(
                        JPanel(null).apply {
                            isOpaque = false
                            alignmentX = 0f
                            alignmentY = 0f
                            preferredSize = Dimension(CONTENT_WIDTH, CONTENT_HEIGHT)
                        },
                        BorderLayout.CENTER,
                    )
                },
            )
        }

    /** Sets the border of the padded panel of [component] to the edges held by [parameters] and revalidates it. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AbsolutePaddingParameters,
    ) {
        val padded = component.components.single() as JPanel
        padded.border = EmptyBorder(parameters.top, parameters.left, parameters.bottom, parameters.right)
        padded.revalidate()
    }
}
