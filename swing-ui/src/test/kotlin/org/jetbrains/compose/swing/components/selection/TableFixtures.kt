package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Point
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumnModel

/**
 * A scroll pane the host lays out at a fixed size, showing [content] as its viewport, so that the header of a
 * table in it has a size and takes the gestures a user makes on it.
 */
@Composable
internal inline fun HeaderPane(crossinline content: @Composable () -> Unit) {
    ScrollPane(modifier = SwingModifier.preferredSize(400, 200)) { Viewport { content() } }
}

internal fun JTable.columnHeaders(): List<Any?> = columnModel.columns.toList().map { it.headerValue }

/** The middle of the cell in the first column of [row], in the table's own coordinates. */
internal fun JTable.rowCenter(row: Int): Point =
    getCellRect(row, 0, true).let { Point(it.centerX.toInt(), it.centerY.toInt()) }

/** The model index of each of [this] model's view columns, left to right. */
internal fun TableColumnModel.modelIndices(): List<Int> = (0 until columnCount).map { getColumn(it).modelIndex }

/** The preferred width of each of [this] model's view columns, left to right. */
internal fun TableColumnModel.preferredWidths(): List<Int> = (0 until columnCount).map { getColumn(it).preferredWidth }

/** The names [this] table shows, top to bottom. */
internal fun JTable.shownNames(): List<Any?> = (0 until rowCount).map { getValueAt(it, 0) }

/** A one-row model with a column for each of [columns]. */
internal fun modelWithColumns(vararg columns: String): DefaultTableModel =
    DefaultTableModel(arrayOf(arrayOf<Any?>("a", "b", "c")), Array<Any?>(columns.size) { columns[it] })
