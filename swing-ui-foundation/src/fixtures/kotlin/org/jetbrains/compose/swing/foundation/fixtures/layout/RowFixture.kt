// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [RowFixture] varies: the size of the row, how it arranges and aligns its children, and how many. */
public data class RowParameters(
    val width: Int,
    val height: Int,
    val horizontalArrangement: Arrangement.Horizontal,
    val verticalAlignment: Alignment.Vertical,
    val childCount: Int,
)

/** A [Row] of labels of a fixed size, against a raw [JPanel] with a grid-bag layout holding the same labels. */
public object RowFixture : ComposableFixture<RowParameters, JPanel>(
    initial =
        RowParameters(
            width = 180,
            height = 120,
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
            childCount = 2,
        ),
) {
    override val alternatives: List<RowParameters> =
        listOf(
            initial.copy(width = 200),
            initial.copy(height = 80),
            initial.copy(horizontalArrangement = Arrangement.End),
            initial.copy(horizontalArrangement = Arrangement.Center),
            initial.copy(verticalAlignment = Alignment.CenterVertically),
            initial.copy(verticalAlignment = Alignment.Bottom),
            initial.copy(childCount = 3),
        )

    @Composable
    override fun Content(parameters: RowParameters): Unit =
        Content(
            width = parameters.width,
            height = parameters.height,
            horizontalArrangement = parameters.horizontalArrangement,
            verticalAlignment = parameters.verticalAlignment,
            childCount = parameters.childCount,
        )

    /** Composes a [Row] of [width] by [height] holding [childCount] labels, arranged and aligned as given. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
        horizontalArrangement: Arrangement.Horizontal,
        verticalAlignment: Alignment.Vertical,
        childCount: Int,
    ) {
        Box {
            Row(
                modifier = SwingModifier.size(width, height),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment,
            ) {
                repeat(childCount) { index -> Label("Child $index") }
            }
        }
    }

    /** Builds the raw panel holding the sized panel with its grid-bag layout and no labels. */
    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            add(JPanel(GridBagLayout()).apply { isOpaque = false })
        }

    /**
     * Sizes the panel of [component] and places its labels in a grid-bag layout as [parameters] ask, keeping the labels
     * it already has and adding or removing only the difference.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: RowParameters,
    ) {
        val row = component.components.single() as JPanel
        val size = Dimension(parameters.width, parameters.height)
        // The outer box wraps its only child, so a hand-written program gives it the size of that child.
        component.preferredSize = size
        row.setBounds(0, 0, size.width, size.height)
        val anchor =
            when (parameters.verticalAlignment) {
                Alignment.Top -> GridBagConstraints.NORTHWEST
                Alignment.CenterVertically -> GridBagConstraints.WEST
                Alignment.Bottom -> GridBagConstraints.SOUTHWEST
                else -> error("Unsupported vertical alignment ${parameters.verticalAlignment}")
            }
        // The free columns around the labels take the space the row has left, so no spacer component joins the tree.
        val leadingColumns =
            when (parameters.horizontalArrangement) {
                Arrangement.Start -> 0
                Arrangement.End, Arrangement.Center -> 1
                else -> error("Unsupported horizontal arrangement ${parameters.horizontalArrangement}")
            }
        val columns = parameters.childCount + 2
        val weights = DoubleArray(columns)
        if (leadingColumns == 1) weights[0] = 1.0
        if (parameters.horizontalArrangement != Arrangement.End) weights[leadingColumns + parameters.childCount] = 1.0
        val layout = row.layout as GridBagLayout
        layout.columnWidths = IntArray(columns)
        layout.columnWeights = weights
        while (row.componentCount > parameters.childCount) row.remove(row.componentCount - 1)
        repeat(parameters.childCount) { index ->
            val constraints =
                GridBagConstraints().apply {
                    gridx = leadingColumns + index
                    gridy = 0
                    weighty = 1.0
                    this.anchor = anchor
                }
            if (index < row.componentCount) {
                layout.setConstraints(row.getComponent(index), constraints)
            } else {
                row.add(JLabel("Child $index"), constraints)
            }
        }
        component.revalidate()
    }
}
