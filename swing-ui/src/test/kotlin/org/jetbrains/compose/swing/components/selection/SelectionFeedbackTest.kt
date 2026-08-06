package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The selection callbacks of [Table], [ListBox] and [Tree] report user interaction only. Re-rendering
 * declared data - new rows, new items, a new structure, a new model instance - is the library writing to
 * its own widget, so the selection the caller declared survives it and no callback fires. Data too small
 * to hold that declaration is no exception: the widget takes as much of it as the data allows, and the
 * declaration still stands, since nothing is reported for the caller to adopt.
 *
 * Each test drives the wrapper through the real applier and mirrors the callback back into the controlled
 * state, as a caller would. A callback that reports a change the user never made would corrupt that state,
 * which is what these tests catch.
 */
class SelectionFeedbackTest {
    private val leaves = listOf("apple", "pear")

    private fun treeModel(
        rootLabel: String,
        leafCount: Int = leaves.size,
    ): DefaultTreeModel {
        val root = DefaultMutableTreeNode(rootLabel)
        for (leaf in leaves.take(leafCount)) root.add(DefaultMutableTreeNode(leaf))
        return DefaultTreeModel(root)
    }

    private fun listModel(vararg elements: String): DefaultListModel<String> =
        DefaultListModel<String>().apply { for (element in elements) addElement(element) }

    private fun tableModel(vararg names: String): DefaultTableModel =
        DefaultTableModel(arrayOf<Any>("Name"), 0).apply { for (name in names) addRow(arrayOf<Any>(name)) }

    /** The labels of the nodes the tree has selected. */
    private fun JTree.selectedLabels(): List<String> = selectionPaths.orEmpty().map { it.lastPathComponent.toString() }

    @Test
    fun aRowsOnlyRefreshKeepsTheTableSelection() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))
        var selection by mutableStateOf(setOf(1))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = rows.toList(),
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1), table.selectedRows.toList(), "declared selection applied")
        received.clear()

        // A rows-only refresh: the column shape is unchanged, so only the data differs.
        rows[2] = Person("Grace Hopper", 50)
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "selection survives a rows-only refresh")
        assertEquals(setOf(1), selection, "controlled selection survives a rows-only refresh")
        assertEquals(emptyList(), received, "a rows-only refresh reported a selection change")
    }

    @Test
    fun aColumnStructureChangeKeepsTheTableSelection() = runComposeSwingTest {
        var withAge by mutableStateOf(false)
        var selection by mutableStateOf(setOf(2))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            ) {
                column("Name") { it.name }
                if (withAge) column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(2), table.selectedRows.toList(), "declared selection applied")
        received.clear()

        withAge = true
        awaitIdle()

        assertEquals(2, table.columnCount, "the declared column was added")
        assertEquals(listOf(2), table.selectedRows.toList(), "selection survives a structure change")
        assertEquals(emptyList(), received, "a structure change reported a selection change")
    }

    @Test
    fun aProgrammaticTableSelectionReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                selectedRowIndices = selection,
                onSelectionChange = { received += it },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        received.clear()

        selection = setOf(2)
        awaitIdle()

        assertEquals(listOf(2), table.selectedRows.toList(), "declared selection applied")
        assertEquals(emptyList(), received, "a declared selection change reported itself back")
    }

    @Test
    fun anItemsChangeKeepsTheListSelection() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        var selection by mutableStateOf(setOf(1))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(
                items = items.toList(),
                selectedIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(1), list.selectedIndices.toList(), "declared selection applied")
        received.clear()

        items.add("violet")
        awaitIdle()

        assertEquals(4, list.model.size, "the added item reached the list")
        assertEquals(listOf(1), list.selectedIndices.toList(), "selection survives an items change")
        assertEquals(setOf(1), selection, "controlled selection survives an items change")
        assertEquals(emptyList(), received, "an items change reported a selection change")
    }

    @Test
    fun aProgrammaticListSelectionReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(1))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                selectedIndices = selection,
                onSelectionChange = { received += it },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        received.clear()

        selection = setOf(2)
        awaitIdle()

        assertEquals(listOf(2), list.selectedIndices.toList(), "declared selection applied")
        assertEquals(emptyList(), received, "a declared selection change reported itself back")
    }

    @Test
    fun aModelSwapKeepsTheListSelection() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green"))
        var selection by mutableStateOf(setOf(1))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(
                model = model,
                selectedIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(1), list.selectedIndices.toList(), "declared selection applied")
        received.clear()

        model = listModel("cyan", "magenta")
        awaitIdle()

        assertEquals(listOf(1), list.selectedIndices.toList(), "selection survives a model swap")
        assertEquals(emptyList(), received, "a model swap reported a selection change")
    }

    @Test
    fun aStructureChangeKeepsTheTreeSelection() = runComposeSwingTest {
        var rootLabel by mutableStateOf("root")
        var selection by mutableStateOf(setOf(listOf(0)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = rootLabel,
                children = { if (it == rootLabel) leaves else emptyList() },
                selectedPaths = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("apple"), tree.selectedLabels(), "declared selection applied")
        received.clear()

        rootLabel = "trunk"
        awaitIdle()

        assertEquals(listOf("apple"), tree.selectedLabels(), "selection survives a structure change")
        assertEquals(setOf(listOf(0)), selection, "controlled selection survives a structure change")
        assertEquals(emptyList(), received, "a structure change reported a selection change")
    }

    @Test
    fun aTreeModelSwapKeepsTheSelection() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        var selection by mutableStateOf(setOf(listOf(1)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                model = model,
                selectedPaths = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("pear"), tree.selectedLabels(), "declared selection applied")
        received.clear()

        model = treeModel("trunk")
        awaitIdle()

        assertEquals(listOf("pear"), tree.selectedLabels(), "selection survives a model swap")
        assertEquals(setOf(listOf(1)), selection, "controlled selection survives a model swap")
        assertEquals(emptyList(), received, "a model swap reported a selection change")
    }

    @Test
    fun itemsTooFewForTheListSelectionReportTheNarrowing() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue", "violet"))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(items = items, onSelectionChange = { received += it })
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(1, 3)
        received.clear()

        items = listOf("red", "green")
        awaitIdle()

        assertEquals(listOf(1), list.selectedIndices.toList(), "the rows the new items still hold stay selected")
        assertEquals(listOf(setOf(1)), received, "the rows the user loses are reported once, as the new selection")
    }

    @Test
    fun itemsThatHoldNoneOfTheListSelectionReportTheLoss() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue"))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(items = items, onSelectionChange = { received += it })
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(2, 2)
        received.clear()

        items = listOf("red")
        awaitIdle()

        assertEquals(emptyList(), list.selectedIndices.toList(), "no row of the old selection is left to select")
        assertEquals(listOf(emptySet()), received, "losing the whole selection is reported once")
    }

    @Test
    fun aModelTooShortForTheListSelectionReportsTheNarrowing() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green", "blue"))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(model = model, onSelectionChange = { received += it })
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(1, 2)
        received.clear()

        model = listModel("cyan", "magenta")
        awaitIdle()

        assertEquals(listOf(1), list.selectedIndices.toList(), "the row the new model still holds stays selected")
        assertEquals(listOf(setOf(1)), received, "the rows the user loses are reported once, as the new selection")
    }

    @Test
    fun anItemsChangeThatDropsADeclaredListRowReportsNothing() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue"))
        var selection by mutableStateOf(setOf(2))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(
                items = items,
                selectedIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(2), list.selectedIndices.toList(), "the declared row reaches the list")
        received.clear()

        items = listOf("red", "green")
        awaitIdle()

        assertEquals(emptyList(), list.selectedIndices.toList(), "the new items hold none of the declared selection")
        assertEquals(setOf(2), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "an items change reported the wrapper's own write")
    }

    @Test
    fun aListModelSwapThatDropsADeclaredRowReportsNothing() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green", "blue"))
        var selection by mutableStateOf(setOf(2))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(
                model = model,
                selectedIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(2), list.selectedIndices.toList(), "the declared row reaches the list")
        received.clear()

        model = listModel("cyan", "magenta")
        awaitIdle()

        assertEquals(emptyList(), list.selectedIndices.toList(), "the new model holds none of the declared selection")
        assertEquals(setOf(2), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a model swap reported the wrapper's own write")
    }

    @Test
    fun rowsTooFewForTheTableSelectionReportTheNarrowing() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = rows, onSelectionChange = { received += it }) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(0, 2)
        received.clear()

        rows = listOf(Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        assertEquals(listOf(0, 1), table.selectedRows.toList(), "the rows the new data still holds stay selected")
        assertEquals(listOf(setOf(0, 1)), received, "the rows the user loses are reported once, as the new selection")
    }

    @Test
    fun rowsThatHoldNoneOfTheTableSelectionReportTheLoss() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = rows, onSelectionChange = { received += it }) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(2, 2)
        received.clear()

        rows = listOf(Person("Ada", 36))
        awaitIdle()

        assertEquals(emptyList(), table.selectedRows.toList(), "no row of the old selection is left to select")
        assertEquals(listOf(emptySet()), received, "losing the whole selection is reported once")
    }

    @Test
    fun aRowsChangeThatDropsADeclaredTableRowReportsNothing() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        var selection by mutableStateOf(setOf(2))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = rows,
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(2), table.selectedRows.toList(), "the declared row reaches the table")
        received.clear()

        rows = listOf(Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        assertEquals(emptyList(), table.selectedRows.toList(), "the new rows hold none of the declared selection")
        assertEquals(setOf(2), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a rows change reported the wrapper's own write")
    }

    @Test
    fun aTableModelSwapThatDropsADeclaredRowReportsNothing() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        var selection by mutableStateOf(setOf(2))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                model = model,
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(2), table.selectedRows.toList(), "the declared row reaches the table")
        received.clear()

        model = tableModel("Ada", "Alan")
        awaitIdle()

        assertEquals(emptyList(), table.selectedRows.toList(), "the new model holds none of the declared selection")
        assertEquals(setOf(2), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a model swap reported the wrapper's own write")
    }

    @Test
    fun aStructureThatDropsASelectedTreeNodeReportsTheNarrowing() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        val received = mutableListOf<Set<List<Int>>>()
        val expansions = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                // The root value carries the leaf count, so changing it changes declared data, not a
                // memoized lambda.
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                onSelectionChange = { received += it },
                onExpansionChange = { expansions += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        received.clear()
        expansions.clear()

        leafCount = 1
        awaitIdle()

        assertEquals(listOf("apple"), tree.selectedLabels(), "the node the new structure still holds stays selected")
        assertEquals(listOf(setOf(listOf(0))), received, "the node the user loses is reported once")
        assertEquals(emptyList(), expansions, "a structure change reported an expansion change")
    }

    @Test
    fun aStructureThatDropsEverySelectedTreeNodeReportsTheLoss() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                // The root value carries the leaf count, so changing it changes declared data, not a
                // memoized lambda.
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(1))
        received.clear()

        leafCount = 0
        awaitIdle()

        assertEquals(emptyList(), tree.selectedLabels(), "no node of the old selection is left to select")
        assertEquals(listOf(emptySet()), received, "losing the whole selection is reported once")
    }

    @Test
    fun aStructureThatDropsADeclaredTreeNodeReportsNothing() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        var selection by mutableStateOf(setOf(listOf(1)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                // The root value carries the leaf count, so changing it changes declared data, not a
                // memoized lambda.
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                selectedPaths = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("pear"), tree.selectedLabels(), "the declared node reaches the tree")
        received.clear()

        leafCount = 1
        awaitIdle()

        assertEquals(emptyList(), tree.selectedLabels(), "the new structure holds none of the declared selection")
        assertEquals(setOf(listOf(1)), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a structure change reported the wrapper's own write")
    }

    @Test
    fun aTreeModelSwapThatDropsADeclaredNodeReportsNothing() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        var selection by mutableStateOf(setOf(listOf(1)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                model = model,
                selectedPaths = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("pear"), tree.selectedLabels(), "the declared node reaches the tree")
        received.clear()

        model = treeModel("trunk", leafCount = 1)
        awaitIdle()

        assertEquals(emptyList(), tree.selectedLabels(), "the new model holds none of the declared selection")
        assertEquals(setOf(listOf(1)), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a model swap reported the wrapper's own write")
    }

    @Test
    fun aProgrammaticTreeSelectionReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(listOf(0)))
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel("root")
        setContent {
            Tree(
                model = model,
                selectedPaths = selection,
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("apple"), tree.selectedLabels(), "declared selection applied")
        received.clear()

        selection = setOf(listOf(1))
        awaitIdle()

        assertEquals(listOf("pear"), tree.selectedLabels(), "the new declared selection applied")
        assertEquals(emptyList(), received, "a declared selection change reported itself back")
    }

    @Test
    fun aListenerThatFailsOnTheListNarrowingReportLeavesLaterItemsApplied() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue"))
        var failing by mutableStateOf(false)
        val listener = ListSelectionListener { if (failing) error("the list narrowing report fails") }
        setContent {
            ListBox(items = items, listSelectionListener = listener)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(2, 2)

        failing = true
        // Shrinking below the selected row is what makes the narrowing reach this listener.
        items = listOf("red")
        awaitIdle()
        assertEquals(emptyList(), list.selectedIndices.toList(), "no row of the old selection is left to select")

        failing = false
        items = listOf("red", "green", "blue", "violet")
        awaitIdle()

        assertEquals(4, list.model.size, "a later declared items change must still reach the list")
        val failures = takeCallerFailures()
        assertTrue(failures.isNotEmpty(), "the narrowing report's failure is contained")
        assertTrue(
            failures.any { "the list narrowing report fails" in it.message.orEmpty() },
            "the contained failure should be the narrowing report's own, but was: $failures",
        )
    }

    @Test
    fun aListenerThatFailsOnTheTreeNarrowingReportLeavesLaterStructureApplied() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        var failing by mutableStateOf(false)
        val listener = TreeSelectionListener { if (failing) error("the tree narrowing report fails") }
        setContent {
            Tree(
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                treeSelectionListener = listener,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(1))

        failing = true
        // Dropping every leaf is what makes the narrowing reach this listener.
        leafCount = 0
        awaitIdle()
        assertEquals(emptyList(), tree.selectedLabels(), "no node of the old selection is left to select")

        failing = false
        leafCount = 2
        awaitIdle()

        assertEquals(
            2,
            tree.model.getChildCount(tree.model.root),
            "a later declared structure change must still reach the tree",
        )
        val failures = takeCallerFailures()
        assertTrue(failures.isNotEmpty(), "the narrowing report's failure is contained")
        assertTrue(
            failures.any { "the tree narrowing report fails" in it.message.orEmpty() },
            "the contained failure should be the narrowing report's own, but was: $failures",
        )
    }
}
