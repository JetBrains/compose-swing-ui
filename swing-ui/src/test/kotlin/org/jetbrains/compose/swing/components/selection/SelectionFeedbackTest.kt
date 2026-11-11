// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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

    @Test
    fun aRowsOnlyRefreshKeepsTheTableSelection() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))
        var selection by mutableStateOf(setOf(1))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = rows.toList(),
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(1), "declared selection applied") { selectedRows.toList() }
        received.clear()

        // A rows-only refresh: the column shape is unchanged, so only the data differs.
        rows[2] = Person("Grace Hopper", 50)
        awaitIdle()

        table.assertProperty(listOf(1), "selection survives a rows-only refresh") { selectedRows.toList() }
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
                columns = {
                    column("Name") { it.name }
                    if (withAge) column("Age") { it.age }
                },
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
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
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                onSelectionChange = { received += it },
            )
        }

        received.clear()

        selection = setOf(2)
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(listOf(2), "declared selection applied") { selectedRows.toList() }
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

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1), "declared selection applied") { selectedIndices.toList() }
        received.clear()

        items.add("violet")
        awaitIdle()

        list.assertProperty(4, "the added item reached the list") { model.size }
        list.assertProperty(listOf(1), "selection survives an items change") { selectedIndices.toList() }
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

        received.clear()

        selection = setOf(2)
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(listOf(2), "declared selection applied") { selectedIndices.toList() }
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

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1), "declared selection applied") { selectedIndices.toList() }
        received.clear()

        model = listModel("cyan", "magenta")
        awaitIdle()

        list.assertProperty(listOf(1), "selection survives a model swap") { selectedIndices.toList() }
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

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("pear"), "declared selection applied") { selectedLabels() }
        received.clear()

        model = treeModel("trunk")
        awaitIdle()

        tree.assertProperty(listOf("pear"), "selection survives a model swap") { selectedLabels() }
        assertEquals(setOf(listOf(1)), selection, "controlled selection survives a model swap")
        assertEquals(emptyList(), received, "a model swap reported a selection change")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listSelectionLossCases")
    fun itemsThatLoseListRowsReportTheNewSelection(case: ListSelectionLossCase) = runComposeSwingTest {
        var items by mutableStateOf(case.initial)
        val received = mutableListOf<Set<Int>>()
        setContent { ListBox(items = items, onSelectionChange = { received += it }) }
        val list = onNodeOfType<JList<*>>()
        list.fetch().selectionModel.setSelectionInterval(case.selection.first, case.selection.last)
        received.clear()
        items = case.remaining
        awaitIdle()
        list.assertProperty(
            case.outcome.expected,
            "${case.name}: ${case.outcome.selectionMessage}",
        ) { selectedIndices.toList() }
        assertEquals(case.outcome.reported, received, "${case.name}: ${case.outcome.reportMessage}")
    }

    @Test
    fun aModelTooShortForTheListSelectionReportsTheNarrowing() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green", "blue"))
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(model = model, onSelectionChange = { received += it })
        }

        val list = onNodeOfType<JList<*>>()
        list.fetch().selectionModel.setSelectionInterval(1, 2)
        received.clear()

        model = listModel("cyan", "magenta")
        awaitIdle()

        list.assertProperty(listOf(1), "the row the new model still holds stays selected") { selectedIndices.toList() }
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

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(2), "the declared row reaches the list") { selectedIndices.toList() }
        received.clear()

        items = listOf("red", "green")
        awaitIdle()

        list.assertProperty(
            emptyList(),
            "the new items hold none of the declared selection",
        ) { selectedIndices.toList() }
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

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(2), "the declared row reaches the list") { selectedIndices.toList() }
        received.clear()

        model = listModel("cyan", "magenta")
        awaitIdle()

        list.assertProperty(
            emptyList(),
            "the new model holds none of the declared selection",
        ) { selectedIndices.toList() }
        assertEquals(setOf(2), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a model swap reported the wrapper's own write")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tableSelectionLossCases")
    fun rowsThatLoseTableRowsReportTheNewSelection(case: TableSelectionLossCase) = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } }, onSelectionChange = { received += it })
        }
        val table = onNodeOfType<JTable>()
        table.fetch().selectionModel.setSelectionInterval(case.first, 2)
        received.clear()
        rows = case.remaining
        awaitIdle()
        table.assertProperty(case.outcome.expected, case.outcome.selectionMessage) { selectedRows.toList() }
        assertEquals(case.outcome.reported, received, "${case.name}: ${case.outcome.reportMessage}")
    }

    @Test
    fun aModelTooSmallForTheTableSelectionReportsTheNarrowing() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(model = model, onSelectionChange = { received += it })
        }

        val table = onNodeOfType<JTable>()
        table.fetch().selectionModel.setSelectionInterval(0, 2)
        received.clear()

        model = tableModel("Ada", "Alan")
        awaitIdle()

        table.assertProperty(listOf(0, 1), "the rows the new model still holds stay selected") { selectedRows.toList() }
        assertEquals(listOf(setOf(0, 1)), received, "the rows the user loses are reported once, as the new selection")
    }

    @Test
    fun aRowsChangeThatDropsADeclaredTableRowReportsNothing() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        var selection by mutableStateOf(setOf(2))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = rows,
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(2), "the declared row reaches the table") { selectedRows.toList() }
        received.clear()

        rows = listOf(Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        table.assertProperty(emptyList(), "the new rows hold none of the declared selection") { selectedRows.toList() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(2), "the declared row reaches the table") { selectedRows.toList() }
        received.clear()

        model = tableModel("Ada", "Alan")
        awaitIdle()

        table.assertProperty(
            emptyList(),
            "the new model holds none of the declared selection",
        ) { selectedRows.toList() }
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
    fun aTreeModelThatDropsTheSelectionReportsTheNarrowing() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(model = model, onSelectionChange = { received += it })
        }

        val tree = onNodeOfType<JTree>()
        tree.fetch().selectionPaths = arrayOf(tree.fetch().pathTo(0), tree.fetch().pathTo(1))
        received.clear()

        model = treeModel("root", leafCount = 1)
        awaitIdle()

        tree.assertProperty(listOf("apple"), "the node the new model still holds stays selected") { selectedLabels() }
        assertEquals(listOf(setOf(listOf(0))), received, "the node the user loses is reported once")
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

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("pear"), "the declared node reaches the tree") { selectedLabels() }
        received.clear()

        model = treeModel("trunk", leafCount = 1)
        awaitIdle()

        tree.assertProperty(emptyList(), "the new model holds none of the declared selection") { selectedLabels() }
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

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("apple"), "declared selection applied") { selectedLabels() }
        received.clear()

        selection = setOf(listOf(1))
        awaitIdle()

        tree.assertProperty(listOf("pear"), "the new declared selection applied") { selectedLabels() }
        assertEquals(emptyList(), received, "a declared selection change reported itself back")
    }

    @Test
    fun aListenerThatFailsOnTheListNarrowingReportLeavesLaterItemsApplied() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue"))
        var failing by mutableStateOf(false)
        val failure = IllegalStateException("the list narrowing report fails")
        val listener = ListSelectionListener { if (failing) throw failure }
        setContent {
            ListBox(items = items, listSelectionListener = listener)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(2, 2)

        failing = true
        // Shrinking below the selected row is what makes the narrowing reach this listener.
        items = listOf("red")
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> { awaitIdle() },
            "the reported failure is the narrowing report's own",
        )
        awaitIdle()
        assertEquals(emptyList(), list.selectedIndices.toList(), "no row of the old selection is left to select")

        failing = false
        items = listOf("red", "green", "blue", "violet")
        awaitIdle()

        assertEquals(4, list.model.size, "a later declared items change must still reach the list")
    }

    @Test
    fun aListenerThatFailsOnTheTreeNarrowingReportLeavesLaterStructureApplied() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        var failing by mutableStateOf(false)
        val failure = IllegalStateException("the tree narrowing report fails")
        val listener = TreeSelectionListener { if (failing) throw failure }
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
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> { awaitIdle() },
            "the reported failure is the narrowing report's own",
        )
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
    }

    companion object {
        @JvmStatic
        fun listSelectionLossCases(): List<Arguments> = listOf(
            Arguments.of(
                Named.of(
                    "partial loss",
                    ListSelectionLossCase(
                        "partial loss",
                        listOf("red", "green", "blue", "violet"),
                        listOf("red", "green"),
                        1..3,
                        SelectionLossOutcome(
                            listOf(1),
                            listOf(setOf(1)),
                            "the rows the new items still hold stay selected",
                            "the rows the user loses are reported once, as the new selection",
                        ),
                    ),
                ),
            ),
            Arguments.of(
                Named.of(
                    "total loss",
                    ListSelectionLossCase(
                        "total loss",
                        listOf("red", "green", "blue"),
                        listOf("red"),
                        2..2,
                        SelectionLossOutcome(
                            emptyList<Int>(),
                            listOf(emptySet<Int>()),
                            "no row of the old selection is left to select",
                            "losing the whole selection is reported once",
                        ),
                    ),
                ),
            ),
        )

        @JvmStatic
        fun tableSelectionLossCases(): List<Arguments> = listOf(
            Arguments.of(
                Named.of(
                    "partial loss",
                    TableSelectionLossCase(
                        "partial loss",
                        listOf(Person("Ada", 36), Person("Alan", 41)),
                        0,
                        SelectionLossOutcome(
                            listOf(0, 1),
                            listOf(setOf(0, 1)),
                            "the rows the new data still holds stay selected",
                            "the rows the user loses are reported once, as the new selection",
                        ),
                    ),
                ),
            ),
            Arguments.of(
                Named.of(
                    "total loss",
                    TableSelectionLossCase(
                        "total loss",
                        listOf(Person("Ada", 36)),
                        2,
                        SelectionLossOutcome(
                            emptyList<Int>(),
                            listOf(emptySet<Int>()),
                            "no row of the old selection is left to select",
                            "losing the whole selection is reported once",
                        ),
                    ),
                ),
            ),
        )
    }
}

class ListSelectionLossCase(
    val name: String,
    val initial: List<String>,
    val remaining: List<String>,
    val selection: IntRange,
    val outcome: SelectionLossOutcome,
)

class TableSelectionLossCase internal constructor(
    val name: String,
    internal val remaining: List<Person>,
    val first: Int,
    val outcome: SelectionLossOutcome,
)

class SelectionLossOutcome(
    val expected: List<Int>,
    val reported: List<Set<Int>>,
    val selectionMessage: String,
    val reportMessage: String,
)
