// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.clipToBounds
import org.jetbrains.compose.swing.foundation.layout.requiredSize
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel

/** The parameters [ClipToBoundsFixture] varies: the width and height of the clipping box. */
public data class ClipToBoundsParameters(
    val width: Int,
    val height: Int,
)

/** A [clipToBounds] box over a larger blue child against a raw [JPanel] clipping a larger blue child the same way. */
public object ClipToBoundsFixture : ComposableFixture<ClipToBoundsParameters, JPanel>(
    initial = ClipToBoundsParameters(width = 48, height = 32),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ClipToBoundsParameters> =
        listOf(
            initial.copy(width = 72),
            initial.copy(height = 56),
        )

    /** The size of the blue child, shared by the composed and the raw side so that they agree. */
    private const val CHILD_WIDTH = 96
    private const val CHILD_HEIGHT = 72

    @Composable
    override fun Content(parameters: ClipToBoundsParameters): Unit = Content(parameters.width, parameters.height)

    /** Composes a [Box] of [width] by [height] clipping a larger blue [Box] placed at its top start. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.clipToBounds().size(width, height)) {
                Box(
                    modifier =
                        SwingModifier
                            .requiredSize(CHILD_WIDTH, CHILD_HEIGHT)
                            .background(Color.BLUE, RectangleShape),
                )
            }
        }
    }

    /** Builds the raw host [JPanel] holding the clipping panel, which holds the blue child larger than its bounds. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    add(
                        JPanel(null).apply {
                            background = Color.BLUE
                            isOpaque = true
                            preferredSize = Dimension(CHILD_WIDTH, CHILD_HEIGHT)
                        },
                    )
                },
            )
        }

    /** Sizes the clipping panel of [component], which Swing clips its larger child to, to [parameters]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: ClipToBoundsParameters,
    ) {
        component.components.single().preferredSize = Dimension(parameters.width, parameters.height)
        component.revalidate()
        component.repaint()
    }
}
