@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.GridLayout

/**
 * A `JPanel` under a `GridLayout`: the panel is divided into equally sized cells and the children fill
 * them one per cell, row by row in the panel's reading order.
 *
 * A zero [rows] means as many rows as the children need, and a zero [cols] as many columns; one of
 * the two may be zero, never both.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param rows the number of rows, or 0 for as many as the children need; the default `1` puts every
 *   child in one row
 * @param cols the number of columns, or 0 for as many as the children need; a non-zero [rows] takes
 *   precedence, and the column count then follows from the row count and the number of children
 * @param hgap the horizontal gap between components; `0` by default, so columns touch
 * @param vgap the vertical gap between components; `0` by default, so rows touch
 * @param content the composable content of the panel; empty by default
 * @throws IllegalArgumentException if both [rows] and [cols] are zero
 * @see java.awt.GridLayout
 */
@Composable
public fun GridPanel(
    modifier: SwingModifier = SwingModifier,
    rows: Int = 1,
    cols: Int = 0,
    hgap: Int = 0,
    vgap: Int = 0,
    content: @Composable () -> Unit = {},
) {
    SwingNode(
        factory = { ScrollablePanel(GridLayout(rows, cols, hgap, vgap)) },
        update = {
            updateLayout<GridLayout, _>(rows) { applyDimensions(it, cols) }
            updateLayout<GridLayout, _>(cols) { applyDimensions(rows, it) }
            updateLayout<GridLayout, _>(hgap) { this.hgap = it }
            updateLayout<GridLayout, _>(vgap) { this.vgap = it }
            applyModifier(modifier)
        },
        content = content,
    )
}

/**
 * Writes both dimensions, the non-zero one first: `GridLayout` refuses a zero row count while its
 * column count is also zero, and vice versa.
 */
private fun GridLayout.applyDimensions(
    rows: Int,
    cols: Int,
) {
    if (rows != 0) {
        this.rows = rows
        this.columns = cols
    } else {
        this.columns = cols
        this.rows = rows
    }
}
