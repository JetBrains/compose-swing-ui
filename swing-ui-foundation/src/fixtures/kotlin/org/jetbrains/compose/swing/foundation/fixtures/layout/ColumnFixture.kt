// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [ColumnFixture] varies: its size, how it arranges and aligns its children, and how many. */
public data class ColumnParameters(
    val width: Int,
    val height: Int,
    val verticalArrangement: Arrangement.Vertical,
    val horizontalAlignment: Alignment.Horizontal,
    val childCount: Int,
)

/** A [Column] of labels of a fixed size, against a raw [JPanel] with a grid-bag layout holding the same labels. */
public object ColumnFixture : ComposableFixture<ColumnParameters, JPanel>(
    initial =
        ColumnParameters(
            width = 180,
            height = 120,
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
            childCount = 2,
        ),
) {
    override val alternatives: List<ColumnParameters> =
        listOf(
            initial.copy(width = 200),
            initial.copy(height = 80),
            initial.copy(verticalArrangement = Arrangement.Bottom),
            initial.copy(verticalArrangement = Arrangement.Center),
            initial.copy(horizontalAlignment = Alignment.CenterHorizontally),
            initial.copy(horizontalAlignment = Alignment.End),
            initial.copy(childCount = 3),
        )

    @Composable
    override fun Content(parameters: ColumnParameters): Unit =
        Content(
            width = parameters.width,
            height = parameters.height,
            verticalArrangement = parameters.verticalArrangement,
            horizontalAlignment = parameters.horizontalAlignment,
            childCount = parameters.childCount,
        )

    /** Composes a [Column] of [width] by [height] holding [childCount] labels, arranged and aligned as given. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
        verticalArrangement: Arrangement.Vertical,
        horizontalAlignment: Alignment.Horizontal,
        childCount: Int,
    ) {
        Box {
            Column(
                modifier = SwingModifier.size(width, height),
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
            ) {
                repeat(childCount) { index -> Label("Child $index") }
            }
        }
    }

    /** Builds the raw panel holding the sized panel with its grid-bag layout and no labels. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JPanel(GridBagLayout()).apply { isOpaque = false })
        }

    /**
     * Sizes the panel of [component] and places its labels in a grid-bag layout as [parameters] ask, keeping the labels
     * it already has and adding or removing only the difference.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: ColumnParameters,
    ) {
        val column = component.components.single() as JPanel
        val size = Dimension(parameters.width, parameters.height)
        column.preferredSize = size
        val anchor =
            when (parameters.horizontalAlignment) {
                Alignment.Start -> GridBagConstraints.NORTHWEST
                Alignment.CenterHorizontally -> GridBagConstraints.NORTH
                Alignment.End -> GridBagConstraints.NORTHEAST
                else -> error("Unsupported horizontal alignment ${parameters.horizontalAlignment}")
            }
        // The free rows around the labels take the space the column has left, so no spacer component joins the tree.
        val leadingRows =
            when (parameters.verticalArrangement) {
                Arrangement.Top -> 0
                Arrangement.Bottom, Arrangement.Center -> 1
                else -> error("Unsupported vertical arrangement ${parameters.verticalArrangement}")
            }
        val rows = parameters.childCount + 2
        val weights = DoubleArray(rows)
        if (leadingRows == 1) weights[0] = 1.0
        if (parameters.verticalArrangement != Arrangement.Bottom) weights[leadingRows + parameters.childCount] = 1.0
        val layout = column.layout as GridBagLayout
        layout.rowHeights = IntArray(rows)
        layout.rowWeights = weights
        while (column.componentCount > parameters.childCount) column.remove(column.componentCount - 1)
        repeat(parameters.childCount) { index ->
            val constraints =
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = leadingRows + index
                    weightx = 1.0
                    this.anchor = anchor
                }
            if (index < column.componentCount) {
                layout.setConstraints(column.getComponent(index), constraints)
            } else {
                column.add(JLabel("Child $index"), constraints)
            }
        }
        component.revalidate()
    }
}
