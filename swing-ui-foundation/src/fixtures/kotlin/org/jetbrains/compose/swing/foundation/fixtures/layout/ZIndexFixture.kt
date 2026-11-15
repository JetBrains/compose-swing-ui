// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.zIndex
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JPanel

/** The parameters [ZIndexFixture] varies. */
public data class ZIndexParameters(
    val zIndex: Float,
)

/**
 * A larger box declaring a `zIndex` beside a smaller box declared after it, against a raw [JPanel] stacking the
 * same two boxes.
 */
public object ZIndexFixture : ComposableFixture<ZIndexParameters, JPanel>(
    initial = ZIndexParameters(zIndex = -1f),
) {
    override val alternatives: List<ZIndexParameters> =
        listOf(
            initial.copy(zIndex = 1f),
        )

    @Composable
    override fun Content(parameters: ZIndexParameters): Unit = Content(parameters.zIndex)

    /** Composes a larger box at [zIndex] among its siblings, with a smaller box declared after it. */
    @Composable
    public fun Content(zIndex: Float) {
        Box {
            Box(
                modifier =
                    SwingModifier
                        .size(BLUE_WIDTH, BLUE_HEIGHT)
                        .zIndex(zIndex),
            )
            Box(modifier = SwingModifier.size(RED_WIDTH, RED_HEIGHT))
        }
    }

    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            preferredSize = Dimension(BLUE_WIDTH, BLUE_HEIGHT)
            add(
                JPanel(null).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    setBounds(0, 0, RED_WIDTH, RED_HEIGHT)
                },
            )
            add(
                JPanel(null).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    setBounds(0, 0, BLUE_WIDTH, BLUE_HEIGHT)
                },
            )
        }

    override fun updateRawSwing(
        component: JPanel,
        parameters: ZIndexParameters,
    ) {
        val blue = component.components.first { it.width == BLUE_WIDTH }
        component.setComponentZOrder(blue, if (parameters.zIndex > 0f) 0 else 1)
        component.revalidate()
        component.repaint()
    }

    private const val BLUE_WIDTH = 72
    private const val BLUE_HEIGHT = 48
    private const val RED_WIDTH = 48
    private const val RED_HEIGHT = 32
}
