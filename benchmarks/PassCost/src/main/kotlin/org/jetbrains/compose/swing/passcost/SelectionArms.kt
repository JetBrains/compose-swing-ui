package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.tree.TreeModel

/**
 * A tree declared from data, whose last node carries a different label on every pass: what a changed value
 * costs a tree that describes its whole structure as data.
 *
 * The two trees are built ahead of the batch and alternated, so the driver allocates nothing and every
 * pass is a real change. The state compares by identity, so the only comparison a pass pays for is the
 * one the tree itself makes, and it starts on the tree the first pass does not write.
 */
internal fun treeValueArm(): Arm =
    Arm(listOf(TREE_VALUE_ARM)) { nodes, changing ->
        val roots = List(2) { index -> treeOf(nodes, alternatingText(index)) }
        val root = mutableStateOf(roots[1], referentialEqualityPolicy())
        val treeRuns = IntArray(1)
        Run(
            content = { DeclaredTree(root) { treeRuns[0]++ } },
            drive = { pass ->
                if (changing) root.value = roots[pass % 2]
                TREE_VALUE_ARM
            },
            verify = { composed, passes ->
                val tree = singleOfType(composed, JTree::class.java)
                checkWidgets("tree nodes", treeNodeCount(tree), nodes + 1)
                checkScopeRuns("the tree's scope", treeRuns[0], if (changing) 1 + passes else 1)
                val expected = alternatingText(if (changing) passes - 1 else 1)
                val shown = lastNodeLabel(tree)
                check(shown == expected) { "the last node reads '$shown', where '$expected' was declared last" }
            },
        )
    }

/**
 * One node appearing and disappearing at the end of the same declared tree, reported as two series the way
 * the structural arm reports its own. Nothing but the node count separates the two trees, so a comparison
 * tells them apart at the child count and the pass pays for the rebuild alone.
 */
internal fun treeSizeArm(): Arm =
    Arm(listOf(TREE_GROW_SERIES, TREE_SHRINK_SERIES)) { nodes, changing ->
        val grownRoot = treeOf(nodes + 1, STEADY_TEXT)
        val shrunkRoot = treeOf(nodes, STEADY_TEXT)
        val root = mutableStateOf(shrunkRoot, referentialEqualityPolicy())
        val treeRuns = IntArray(1)
        Run(
            content = { DeclaredTree(root) { treeRuns[0]++ } },
            drive = { pass ->
                val growing = pass % 2 == 0
                if (changing) root.value = if (growing) grownRoot else shrunkRoot
                if (growing) TREE_GROW_SERIES else TREE_SHRINK_SERIES
            },
            verify = { composed, passes ->
                val grown = changing && (passes - 1) % 2 == 0
                val tree = singleOfType(composed, JTree::class.java)
                checkWidgets("tree nodes", treeNodeCount(tree), nodes + 1 + if (grown) 1 else 0)
                checkScopeRuns("the tree's scope", treeRuns[0], if (changing) 1 + passes else 1)
            },
        )
    }

/**
 * A table declared from rows, whose last row carries a different cell value on every pass. The two row
 * lists are built ahead of the batch, and the state starts on the list the first pass does not write.
 */
internal fun tableValueArm(): Arm =
    Arm(listOf(TABLE_VALUE_ARM)) { rows, changing ->
        val rowSets = List(2) { index -> rowsOf(rows, alternatingText(index)) }
        val declared = mutableStateOf(rowSets[1], referentialEqualityPolicy())
        val tableRuns = IntArray(1)
        Run(
            content = { DeclaredTable(declared) { tableRuns[0]++ } },
            drive = { pass ->
                if (changing) declared.value = rowSets[pass % 2]
                TABLE_VALUE_ARM
            },
            verify = { composed, passes ->
                val model = singleOfType(composed, JTable::class.java).model
                checkWidgets("table rows", model.rowCount, rows)
                checkScopeRuns("the table's scope", tableRuns[0], if (changing) 1 + passes else 1)
                val expected = alternatingText(if (changing) passes - 1 else 1)
                val shown = model.getValueAt(model.rowCount - 1, 0)
                check(shown == expected) { "the last row reads '$shown', where '$expected' was declared last" }
            },
        )
    }

/** One row appearing and disappearing at the end of the same declared table, reported as two series. */
internal fun tableSizeArm(): Arm =
    Arm(listOf(TABLE_GROW_SERIES, TABLE_SHRINK_SERIES)) { rows, changing ->
        val grownRows = rowsOf(rows + 1, STEADY_TEXT)
        val shrunkRows = rowsOf(rows, STEADY_TEXT)
        val declared = mutableStateOf(shrunkRows, referentialEqualityPolicy())
        val tableRuns = IntArray(1)
        Run(
            content = { DeclaredTable(declared) { tableRuns[0]++ } },
            drive = { pass ->
                val growing = pass % 2 == 0
                if (changing) declared.value = if (growing) grownRows else shrunkRows
                if (growing) TABLE_GROW_SERIES else TABLE_SHRINK_SERIES
            },
            verify = { composed, passes ->
                val grown = changing && (passes - 1) % 2 == 0
                val model = singleOfType(composed, JTable::class.java).model
                checkWidgets("table rows", model.rowCount, rows + if (grown) 1 else 0)
                checkScopeRuns("the table's scope", tableRuns[0], if (changing) 1 + passes else 1)
            },
        )
    }

/** How many nodes the model [tree] shows holds, its root included. */
internal fun treeNodeCount(tree: JTree): Int = nodeCount(tree.model, tree.model.root)

/** The text the last child of [tree]'s root renders as - a `JTree` shows a node as its own text. */
private fun lastNodeLabel(tree: JTree): String {
    val model = tree.model
    val root = model.root
    return model.getChild(root, model.getChildCount(root) - 1).toString()
}

private fun nodeCount(
    model: TreeModel,
    node: Any,
): Int {
    var total = 1
    for (index in 0 until model.getChildCount(node)) total += nodeCount(model, model.getChild(node, index))
    return total
}
