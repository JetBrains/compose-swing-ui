@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.GridLayout
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with GridLayout.
 *
 * A zero [rows] means as many rows as the children need, and a zero [cols] as many columns; one of
 * the two may be zero, never both.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param rows the number of rows, or 0 for as many as the children need
 * @param cols the number of columns, or 0 for as many as the children need
 * @param hgap the horizontal gap between components
 * @param vgap the vertical gap between components
 * @param content the composable content of the panel
 * @throws IllegalArgumentException if both [rows] and [cols] are zero
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
        factory = { JPanel(GridLayout(rows, cols, hgap, vgap)) },
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
 * Writes both grid dimensions, the non-zero one first. A `GridLayout` refuses a zero row count while
 * its column count is zero and vice versa, so the dimension being zeroed is always written against a
 * dimension that is already non-zero.
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
