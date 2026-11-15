// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [FillMaxWidthFixture] varies: the fraction of the offered width the child occupies. */
public data class FillMaxWidthParameters(
    val fraction: Float,
)

/**
 * A [Box] filling [FillMaxWidthParameters.fraction] of the bounded width its host offers, against a raw [JPanel]
 * given the same fraction by grid weights.
 */
public object FillMaxWidthFixture : ComposableFixture<FillMaxWidthParameters, JPanel>(
    initial = FillMaxWidthParameters(fraction = 0.5f),
) {
    /** The fraction changed from [initial] to a larger share of the host width. */
    override val alternatives: List<FillMaxWidthParameters> =
        listOf(
            initial.copy(fraction = 0.75f),
        )

    @Composable
    override fun Content(parameters: FillMaxWidthParameters): Unit = Content(fraction = parameters.fraction)

    /**
     * Composes a [Box] filling [fraction] of the width of a host [Box] of a fixed size.
     */
    @Composable
    public fun Content(fraction: Float) {
        Box {
            Box(modifier = SwingModifier.size(HOST_WIDTH, HOST_HEIGHT)) {
                Box(modifier = SwingModifier.fillMaxWidth(fraction = fraction))
            }
        }
    }

    /** Builds the raw wrapper holding the fixed-size host panel, a [GridBagLayout] splitting its width by weight. */
    override fun createRawSwing(): JPanel =
        overlayPanel().apply {
            add(
                JPanel(GridBagLayout()).apply {
                    (layout as GridBagLayout).columnWidths = intArrayOf(0, 0)
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    preferredSize = Dimension(HOST_WIDTH, HOST_HEIGHT)
                    maximumSize = Dimension(HOST_WIDTH, HOST_HEIGHT)
                    add(
                        JPanel().apply { isOpaque = false },
                        GridBagConstraints().apply {
                            fill = GridBagConstraints.HORIZONTAL
                            anchor = GridBagConstraints.NORTHWEST
                        },
                    )
                },
            )
        }

    /** Weights the columns of the host panel in [component] so its child's column takes the [parameters] fraction. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: FillMaxWidthParameters,
    ) {
        val host = component.components.single() as JPanel
        val grid = host.layout as GridBagLayout
        grid.columnWeights = doubleArrayOf(parameters.fraction.toDouble(), 1.0 - parameters.fraction)
        host.revalidate()
    }

    private fun overlayPanel(): JPanel =
        JPanel().apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            layout = OverlayLayout(this)
        }

    private const val HOST_WIDTH = 180
    private const val HOST_HEIGHT = 120
}
