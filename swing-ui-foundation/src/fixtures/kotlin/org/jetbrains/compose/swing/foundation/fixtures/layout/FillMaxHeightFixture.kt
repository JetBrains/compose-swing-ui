// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [FillMaxHeightFixture] varies: the fraction of the offered height the child occupies. */
public data class FillMaxHeightParameters(
    val fraction: Float,
)

/**
 * A [Box] filling a fraction of the bounded height its host offers with [fillMaxHeight], against a raw [JPanel] given
 * the same fraction of that host height by hand.
 */
public object FillMaxHeightFixture : ComposableFixture<FillMaxHeightParameters, JPanel>(
    initial = FillMaxHeightParameters(fraction = 0.5f),
) {
    override val alternatives: List<FillMaxHeightParameters> =
        listOf(
            initial.copy(fraction = 0.75f),
        )

    @Composable
    override fun Content(parameters: FillMaxHeightParameters): Unit = Content(parameters.fraction)

    /** Composes a [Box] of the bounded height that fills [fraction] of that height with a child of its own. */
    @Composable
    public fun Content(fraction: Float) {
        Box {
            Box(modifier = SwingModifier.size(WIDTH, HEIGHT)) {
                Box(modifier = SwingModifier.fillMaxHeight(fraction = fraction))
            }
        }
    }

    /** Builds the raw wrapper holding the fixed-size parent panel, a [GridBagLayout] splitting its height by weight. */
    override fun createRawSwing(): JPanel =
        overlayPanel().apply {
            add(
                JPanel(GridBagLayout()).apply {
                    (layout as GridBagLayout).rowHeights = intArrayOf(0, 0)
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    preferredSize = Dimension(WIDTH, HEIGHT)
                    maximumSize = Dimension(WIDTH, HEIGHT)
                    add(
                        JPanel().apply { isOpaque = false },
                        GridBagConstraints().apply {
                            fill = GridBagConstraints.VERTICAL
                            anchor = GridBagConstraints.NORTHWEST
                        },
                    )
                },
            )
        }

    /** Weights the rows of the parent panel in [component] so its child's row takes the [parameters] fraction. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: FillMaxHeightParameters,
    ) {
        val parent = component.components.single() as JPanel
        val grid = parent.layout as GridBagLayout
        grid.rowWeights = doubleArrayOf(parameters.fraction.toDouble(), 1.0 - parameters.fraction)
        parent.revalidate()
    }

    private fun overlayPanel(): JPanel =
        JPanel().apply {
            alignmentX = 0f
            alignmentY = 0f
            layout = OverlayLayout(this)
            isOpaque = false
        }

    private const val WIDTH = 180
    private const val HEIGHT = 120
}
