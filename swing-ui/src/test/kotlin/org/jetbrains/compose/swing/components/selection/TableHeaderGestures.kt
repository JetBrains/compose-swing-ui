package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performMouseDrag
import org.jetbrains.compose.swing.test.onNodeOfType
import java.awt.Point
import javax.swing.JTable
import javax.swing.table.JTableHeader

/** The middle of the header cell for the column at [index], in the header's own coordinates. */
internal fun JTable.headerCenterOf(index: Int): Point {
    val x = (0 until index).sumOf { columnModel.getColumn(it).width }
    return Point(x + columnModel.getColumn(index).width / 2, tableHeader.height / 2)
}

/** The divider on the trailing edge of the column at [index], in the header's own coordinates. */
internal fun JTable.dividerAfter(index: Int): Point =
    Point((0..index).sumOf { columnModel.getColumn(it).width } - 1, tableHeader.height / 2)

internal fun Point.movedBy(dx: Int): Point = Point(x + dx, y)

/**
 * Reorders the columns by dragging the header cell of the column at [from] onto the one at [to]. The header
 * moves a dragged column one position per drag step, so the drag is made one cell at a time.
 */
internal suspend fun ComposeSwingTest.dragColumn(
    from: Int,
    to: Int,
) {
    val table = onNodeOfType<JTable>().fetch()
    val header = onNodeOfType<JTableHeader>()
    var position = from
    while (position != to) {
        val next = if (to < position) position - 1 else position + 1
        header.performMouseDrag(table.headerCenterOf(position), table.headerCenterOf(next))
        position = next
    }
}

/** Resizes the column at [position] by dragging the divider on its trailing edge [dx] pixels. */
internal suspend fun ComposeSwingTest.dragColumnDivider(
    position: Int,
    dx: Int,
) {
    val table = onNodeOfType<JTable>().fetch()
    val divider = table.dividerAfter(position)
    onNodeOfType<JTableHeader>().performMouseDrag(divider, divider.movedBy(dx))
}
