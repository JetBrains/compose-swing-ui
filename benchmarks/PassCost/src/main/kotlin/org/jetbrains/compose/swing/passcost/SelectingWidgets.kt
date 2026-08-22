package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.constants.SelectionMode

/**
 * [items] item texts, the last of them [lastText].
 *
 * Every call builds a list of its own, so a list an arm alternates onto is never the one the list box last
 * compared against by identity - what tells two of these apart is their elements.
 */
internal fun itemsOf(
    items: Int,
    lastText: String,
): List<String> = List(items) { index -> if (index == items - 1) lastText else "item $index" }

/**
 * A list box whose items are declared as data: the list [items] holds. Nothing else is declared onto it,
 * so what a pass reconciles is the content and the chain around it.
 */
@Composable
internal fun DeclaredListBox(
    items: State<List<String>>,
    onCompose: () -> Unit,
) {
    onCompose()
    ListBox(items = items.value)
}

/**
 * A list box over [items] that never change, whose selected rows are the set [selected] holds: what a pass
 * costs where the selection is the only thing that moved.
 *
 * The selection is declared through the overload that takes a lambda, which is the shape a caller reaches
 * for and the one a `ListState` delegates to, so the pass pays for the listener that overload adapts.
 */
@Composable
internal fun SelectingListBox(
    items: List<String>,
    selected: State<Set<Int>>,
    onCompose: () -> Unit,
) {
    onCompose()
    ListBox(items = items, selectedIndices = selected.value, onSelectionChange = {})
}

/**
 * A table of one column over [rows] that never change, whose selected rows are the set [selected] holds,
 * in selection mode [selectionMode]. The column is the one [DeclaredTable] declares, so the two read
 * against each other.
 */
@Composable
internal fun SelectingTable(
    rows: List<TableRow>,
    selected: State<Set<Int>>,
    @SelectionMode selectionMode: Int,
    onCompose: () -> Unit,
    onDeclareColumns: () -> Unit,
) {
    onCompose()
    Table(rows = rows, selectedRowIndices = selected.value, selectionMode = selectionMode) {
        onDeclareColumns()
        column(TABLE_COLUMN_HEADER) { row -> row.text }
    }
}

/**
 * A tree over a structure that never changes, whose selected nodes are the paths [selected] holds: what a
 * pass costs where the selection is the only thing that moved. The structure is the one [DeclaredTree]
 * declares, so the two read against each other.
 */
@Composable
internal fun SelectingTree(
    root: TreeValue,
    selected: State<Set<List<Int>>>,
    onCompose: () -> Unit,
) {
    onCompose()
    Tree(
        root = root,
        children = { it.children },
        label = { it.label },
        selectedPaths = selected.value,
        onSelectionChange = {},
    )
}
