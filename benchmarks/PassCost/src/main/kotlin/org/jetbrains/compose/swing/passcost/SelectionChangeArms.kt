package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import org.jetbrains.compose.swing.constants.SelectionMode
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel

/** The two selections one arm alternates between, and the one its widget is composed on. */
private class Selections<T>(
    /** The two selections the passes alternate between, each of them one the widget's mode holds whole. */
    val alternates: List<Set<T>>,
) {
    /**
     * The selection the widget is composed on: none at all, which neither alternate names, so a widget a
     * declaration never reached holds a selection no expectation names.
     *
     * A widget of one row holds only that row or nothing, so its alternates already name the empty
     * selection and there is no third to compose on; it starts on a copy of the first alternate instead,
     * and the gate there bites on every batch whose last pass writes the second. The copy is what keeps
     * the first pass a change: an arm's state compares by identity, and the alternate itself would be
     * written over its own instance and invalidate nothing.
     */
    val start: Set<T> = if (alternates.any { it.isEmpty() }) LinkedHashSet(alternates[0]) else emptySet()
}

/**
 * A list box whose declared items change on every pass: the last item carries a different text. The two
 * lists are built ahead of the batch and alternated, so the driver allocates nothing, and the state starts
 * on a third list whose last item carries a text no pass writes, so a list left on what it was composed
 * with reads a text no expectation names. No selection is declared, so what a pass pays for is the content
 * alone.
 */
internal fun listItemsArm(): Arm =
    Arm(listOf(LIST_ITEMS_ARM)) { items, changing ->
        val itemSets = List(2) { index -> itemsOf(items, alternatingText(index)) }
        val declared = mutableStateOf(itemsOf(items, INITIAL_TEXT), referentialEqualityPolicy())
        val listRuns = IntArray(1)
        Run(
            content = { DeclaredListBox(declared) { listRuns[0]++ } },
            drive = { pass ->
                if (changing) declared.value = itemSets[pass % 2]
                LIST_ITEMS_ARM
            },
            verify = { composed, passes ->
                val model = singleOfType(composed, JList::class.java).model
                checkWidgets("list items", model.size, items)
                checkScopeRuns("the list's scope", listRuns[0], if (changing) 1 + passes else 1)
                val expected = if (changing) alternatingText(passes - 1) else INITIAL_TEXT
                val shown = model.getElementAt(model.size - 1)
                check(shown == expected) { "the last item reads '$shown', where '$expected' was declared last" }
            },
        )
    }

/**
 * A list box over items that never change, whose declared selection moves to another row on every pass:
 * what a selection change costs a list that also declares its items.
 *
 * Both selections name a single row, which every selection mode can hold; a list of one row has no second
 * row to move to, so there the selection alternates between that row and none. The list is composed on
 * [Selections.start].
 */
internal fun listSelectionArm(): Arm =
    Arm(listOf(LIST_SELECTION_ARM)) { items, changing ->
        val steadyItems = itemsOf(items, STEADY_TEXT)
        val selections = singleRowSelections(items)
        val declared = mutableStateOf(selections.start, referentialEqualityPolicy())
        val listRuns = IntArray(1)
        Run(
            content = { SelectingListBox(steadyItems, declared) { listRuns[0]++ } },
            drive = { pass ->
                if (changing) declared.value = selections.alternates[pass % 2]
                LIST_SELECTION_ARM
            },
            verify = { composed, passes ->
                val list = singleOfType(composed, JList::class.java)
                checkWidgets("list items", list.model.size, items)
                checkScopeRuns("the list's scope", listRuns[0], if (changing) 1 + passes else 1)
                checkSelection("the list", list.selectedIndices.toSet(), selections, changing, passes)
            },
        )
    }

/**
 * A tree over a structure that never changes, whose declared selection moves to another child on every
 * pass: what a selection change costs a tree that also declares its structure.
 *
 * A tree of one child has no second child to move to, so there the selection alternates between that
 * child and none at all. The tree is composed on [Selections.start].
 */
internal fun treeSelectionArm(): Arm =
    Arm(listOf(TREE_SELECTION_ARM)) { nodes, changing ->
        val steadyRoot = treeOf(nodes, STEADY_TEXT)
        val selections = childPathSelections(nodes)
        val declared = mutableStateOf(selections.start, referentialEqualityPolicy())
        val treeRuns = IntArray(1)
        Run(
            content = { SelectingTree(steadyRoot, declared) { treeRuns[0]++ } },
            drive = { pass ->
                if (changing) declared.value = selections.alternates[pass % 2]
                TREE_SELECTION_ARM
            },
            verify = { composed, passes ->
                val tree = singleOfType(composed, JTree::class.java)
                checkWidgets("tree nodes", treeNodeCount(tree), nodes + 1)
                checkScopeRuns("the tree's scope", treeRuns[0], if (changing) 1 + passes else 1)
                checkSelection("the tree", selectedChildIndices(tree), selections, changing, passes)
            },
        )
    }

/**
 * The children [tree] has selected, as the index paths a declaration names them by. Every arm here selects
 * children of the root, so a selected path is the one index of the child it ends on.
 */
private fun selectedChildIndices(tree: JTree): Set<List<Int>> {
    val model = tree.model
    return tree
        .selectionPaths
        .orEmpty()
        .map { path -> listOf(model.getIndexOfChild(model.root, path.getPathComponent(1))) }
        .toSet()
}

/**
 * Two selections of one child each. A tree of one child has no second to name, so there the pair is that
 * child and no selection at all.
 */
private fun childPathSelections(nodes: Int): Selections<List<Int>> =
    Selections(
        if (nodes > 1) listOf(setOf(listOf(0)), setOf(listOf(1))) else listOf(setOf(listOf(0)), emptySet()),
    )

/** The three selection modes a table's selection is measured under, each with the selections it holds. */
internal fun tableSelectionArms(): List<Arm> =
    listOf(
        tableSelectionArm(TABLE_SELECTION_SINGLE_ARM, ListSelectionModel.SINGLE_SELECTION, ::singleRowSelections),
        tableSelectionArm(
            TABLE_SELECTION_INTERVAL_ARM,
            ListSelectionModel.SINGLE_INTERVAL_SELECTION,
            ::intervalSelections,
        ),
        tableSelectionArm(
            TABLE_SELECTION_MULTIPLE_ARM,
            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            ::scatteredSelections,
        ),
    )

/**
 * A table over rows that never change, in selection mode [mode], whose declared selection alternates
 * between the two sets [selections] names for the row count: what a selection change costs a table that
 * also declares its rows and its columns.
 *
 * The sets a mode is measured on are ones that mode can hold whole. A mode that narrows the set it is
 * given would leave the widget on a selection the declaration never asked for, and every later pass would
 * write the same declaration again - a re-write loop rather than the change the arm names. The table is
 * composed on [Selections.start].
 *
 * The table's own column declarations are counted alongside the arm's content scope: they say how many
 * times the table itself was re-declared per pass, which is what tells a pass that took a second frame
 * apart from one that composed the table twice.
 */
private fun tableSelectionArm(
    name: String,
    @SelectionMode mode: Int,
    selections: (rows: Int) -> Selections<Int>,
): Arm =
    Arm(listOf(name)) { rows, changing ->
        val steadyRows = rowsOf(rows, STEADY_TEXT)
        val sets = selections(rows)
        val declared = mutableStateOf(sets.start, referentialEqualityPolicy())
        val tableRuns = IntArray(1)
        val declareRuns = IntArray(1)
        Run(
            content = {
                SelectingTable(
                    rows = steadyRows,
                    selected = declared,
                    selectionMode = mode,
                    onCompose = { tableRuns[0]++ },
                    onDeclareColumns = { declareRuns[0]++ },
                )
            },
            drive = { pass ->
                if (changing) declared.value = sets.alternates[pass % 2]
                name
            },
            verify = { composed, passes ->
                val table = singleOfType(composed, JTable::class.java)
                checkWidgets("table rows", table.model.rowCount, rows)
                checkScopeRuns("the arm's content scope", tableRuns[0], if (changing) 1 + passes else 1)
                checkScopeRuns("the table's column declarations", declareRuns[0], if (changing) 1 + passes else 1)
                val applied = table.selectionModel.selectionMode
                check(applied == mode) { "the table is in selection mode $applied, where $mode was declared" }
                checkSelection("the table", table.selectedRows.toSet(), sets, changing, passes)
            },
        )
    }

/**
 * Raises unless [shown] is the selection [selections] declared last - the set the last pass wrote for an
 * arm that changes, and the set the widget was composed on for its null variant.
 */
private fun checkSelection(
    what: String,
    shown: Set<*>,
    selections: Selections<*>,
    changing: Boolean,
    passes: Int,
) {
    val expected = if (changing) selections.alternates[(passes - 1) % 2] else selections.start
    check(shown == expected) { "$what has $shown selected, where $expected was declared last" }
}

/**
 * Two selections of one row each - the shape every selection mode holds whole. A widget of one row has no
 * second row to name, so there the pair is that row and no selection at all.
 */
private fun singleRowSelections(rows: Int): Selections<Int> =
    Selections(if (rows > 1) listOf(setOf(0), setOf(1)) else listOf(setOf(0), emptySet()))

/** Two selections of one run of [SELECTED_INTERVAL_ROWS] rows each - the shape a single interval holds. */
private fun intervalSelections(rows: Int): Selections<Int> =
    if (rows >= SELECTED_INTERVAL_ROWS * 2) {
        Selections(
            listOf(
                (0 until SELECTED_INTERVAL_ROWS).toSet(),
                (SELECTED_INTERVAL_ROWS until SELECTED_INTERVAL_ROWS * 2).toSet(),
            ),
        )
    } else {
        singleRowSelections(rows)
    }

/**
 * Two selections of rows [SELECTED_ROW_STRIDE] apart, covering the widget - the shape only a mode holding
 * several intervals keeps whole, and a selection wide enough that walking it is part of what a pass costs.
 */
private fun scatteredSelections(rows: Int): Selections<Int> =
    if (rows > SELECTED_ROW_STRIDE) {
        Selections(
            listOf(
                (0 until rows step SELECTED_ROW_STRIDE).toSet(),
                (1 until rows step SELECTED_ROW_STRIDE).toSet(),
            ),
        )
    } else {
        singleRowSelections(rows)
    }
