// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.fillMaxSize
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [FillMaxSizeFixture] varies: the fraction of the offered bounds the child occupies. */
public data class FillMaxSizeParameters(
    val fraction: Float,
)

/**
 * A [Box] filling fraction of a bounded parent against a raw [JPanel] given the same fraction by grid weights.
 */
public object FillMaxSizeFixture : ComposableFixture<FillMaxSizeParameters, JPanel>(
    initial = FillMaxSizeParameters(fraction = 0.5f),
) {
    override val alternatives: List<FillMaxSizeParameters> =
        listOf(
            initial.copy(fraction = 0.75f),
        )

    @Composable
    override fun Content(parameters: FillMaxSizeParameters): Unit = Content(fraction = parameters.fraction)

    /**
     * Composes a [Box] filling [fraction] of the bounds of a [Box] of the fixed size,
     * inside a host [Box] that supplies the constraint scope the size needs.
     */
    @Composable
    public fun Content(fraction: Float) {
        Box {
            Box(modifier = SwingModifier.size(PARENT_WIDTH, PARENT_HEIGHT)) {
                Box(modifier = SwingModifier.fillMaxSize(fraction = fraction))
            }
        }
    }

    /** Builds the raw wrapper around the fixed-size parent, a [GridBagLayout] splitting both extents by weight. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            layout = OverlayLayout(this)
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            add(
                JPanel(GridBagLayout()).apply {
                    (layout as GridBagLayout).apply {
                        columnWidths = intArrayOf(0, 0)
                        rowHeights = intArrayOf(0, 0)
                    }
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    preferredSize = Dimension(PARENT_WIDTH, PARENT_HEIGHT)
                    maximumSize = Dimension(PARENT_WIDTH, PARENT_HEIGHT)
                    add(
                        JPanel().apply { isOpaque = false },
                        GridBagConstraints().apply {
                            fill = GridBagConstraints.BOTH
                            anchor = GridBagConstraints.NORTHWEST
                        },
                    )
                },
            )
        }

    /**
     * Gives the child of the parent panel in [component] the [parameters] fraction as the weights of its column and
     * row.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: FillMaxSizeParameters,
    ) {
        val parent = component.components.single() as JPanel
        val grid = parent.layout as GridBagLayout
        val fraction = parameters.fraction.toDouble()
        grid.columnWeights = doubleArrayOf(fraction, 1.0 - fraction)
        grid.rowWeights = doubleArrayOf(fraction, 1.0 - fraction)
        parent.revalidate()
    }

    private const val PARENT_WIDTH = 180
    private const val PARENT_HEIGHT = 120
}
