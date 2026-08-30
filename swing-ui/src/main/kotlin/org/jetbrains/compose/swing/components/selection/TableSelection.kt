package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.node.MirrorState
import javax.swing.JTable
import javax.swing.event.ListSelectionListener

/**
 * Gives the table new content through [install], keeping the rows [declared] names selected - or, where the
 * caller declared nothing, the rows the user had - and reporting to [target] the rows the new content is
 * too short to hold. See [installNarrowing].
 */
internal fun JTable.installContent(
    mirror: MirrorState<Set<Int>?>,
    declared: Set<Int>?,
    target: ListSelectionListener,
    install: () -> Unit,
): Unit =
    mirror.installNarrowing(
        declared = declared,
        selection = { selectedModelRows() },
        // A row event renumbers the table's own selection to follow the row it sits on, so where the
        // caller declared nothing the table has already answered and the rows read before the install
        // name the old numbering. They go back only where the install emptied the selection outright,
        // which is what a wholesale or structure change does and what leaves nothing to follow.
        apply = { indices -> if (declared != null || selectedRowCount == 0) applySelection(this, indices) },
        report = { lost -> reportLostRows(target, lost) },
        install = install,
    )

/**
 * Re-applies [indices] as the table's selected rows. The indices name rows of the model, so a row the model
 * no longer holds is dropped and a row the current filter hides has no screen row to select and is dropped
 * too; the rest are converted to the screen rows they sit on and selected there. A selection that already
 * matches is left alone, so a recomposition that changed nothing touches the table's selection model not at
 * all, and a `null` declaration leaves it alone entirely. See [selectExactly].
 */
internal fun applySelection(
    table: JTable,
    indices: Set<Int>?,
) {
    if (indices == null) return
    val rowCount = table.model.rowCount
    val valid =
        indices.mapNotNullTo(sortedSetOf()) { index ->
            if (index !in 0 until rowCount) null else table.convertRowIndexToView(index).takeIf { it >= 0 }
        }
    val standing = table.selectedRows
    if (standing.holdsSelection(valid)) return
    table.selectionModel.selectExactly(standing, valid)
}
