// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.modifier.listener.treeSelectionListener
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
import kotlin.test.assertTrue

/**
 * The [ListState], [TableState] and [TreeState] of [ListBox], [Table] and [Tree] hold the selection the
 * caller declared. Re-rendering declared data - new rows, new items, a new structure, a new model
 * instance - is the library writing to its own widget, so the selection the state holds survives it and
 * is never rewritten by it. Data too small to hold that selection is no exception: the widget takes as
 * much of it as the data allows, and the state still names what was declared.
 *
 * A call that declares no state leaves selection to Swing: the widget narrows its own selection when the
 * data shrinks, and a listener modifier on the widget observes that as it does any selection change.
 *
 * Each test drives the wrapper through the real applier. A write-back that reports a change the user
 * never made would corrupt the state, which is what these tests catch.
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
        val state = TableState(initialSelectedRowIndices = setOf(1))
        setContent {
            Table(
                rows = rows.toList(),
                columns = { column("Name") { it.name } },
                state = state,
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(1), "declared selection applied") { selectedRows.toList() }

        // A rows-only refresh: the column shape is unchanged, so only the data differs.
        rows[2] = Person("Grace Hopper", 50)
        awaitIdle()

        table.assertProperty(listOf(1), "selection survives a rows-only refresh") { selectedRows.toList() }
        assertEquals(setOf(1), state.selectedRowIndices, "state selection survives a rows-only refresh")
    }

    @Test
    fun aColumnStructureChangeKeepsTheTableSelection() = runComposeSwingTest {
        var withAge by mutableStateOf(false)
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                columns = {
                    column("Name") { it.name }
                    if (withAge) column("Age") { it.age }
                },
                state = state,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(2), table.selectedRows.toList(), "declared selection applied")

        withAge = true
        awaitIdle()

        assertEquals(2, table.columnCount, "the declared column was added")
        assertEquals(listOf(2), table.selectedRows.toList(), "selection survives a structure change")
        assertEquals(setOf(2), state.selectedRowIndices, "state selection survives a structure change")
    }

    @Test
    fun aProgrammaticTableSelectionIsAppliedToTheTable() = runComposeSwingTest {
        val state = TableState(initialSelectedRowIndices = setOf(0))
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                columns = { column("Name") { it.name } },
                state = state,
            )
        }

        state.selectedRowIndices = setOf(2)
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(listOf(2), "declared selection applied") { selectedRows.toList() }
        assertEquals(setOf(2), state.selectedRowIndices, "the declared selection was not written over")
    }

    @Test
    fun anItemsChangeKeepsTheListSelection() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        val state = ListState(initialSelectedIndices = setOf(1))
        setContent {
            ListBox(items = items.toList(), state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1), "declared selection applied") { selectedIndices.toList() }

        items.add("violet")
        awaitIdle()

        list.assertProperty(4, "the added item reached the list") { model.size }
        list.assertProperty(listOf(1), "selection survives an items change") { selectedIndices.toList() }
        assertEquals(setOf(1), state.selectedIndices, "state selection survives an items change")
    }

    @Test
    fun aProgrammaticListSelectionIsAppliedToTheList() = runComposeSwingTest {
        val state = ListState(initialSelectedIndices = setOf(1))
        setContent {
            ListBox(items = listOf("red", "green", "blue"), state = state)
        }

        state.selectedIndices = setOf(2)
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(listOf(2), "declared selection applied") { selectedIndices.toList() }
        assertEquals(setOf(2), state.selectedIndices, "the declared selection was not written over")
    }

    @Test
    fun aModelSwapKeepsTheListSelection() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green"))
        val state = ListState(initialSelectedIndices = setOf(1))
        setContent {
            ListBox(model = model, state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1), "declared selection applied") { selectedIndices.toList() }

        model = listModel("cyan", "magenta")
        awaitIdle()

        list.assertProperty(listOf(1), "selection survives a model swap") { selectedIndices.toList() }
        assertEquals(setOf(1), state.selectedIndices, "state selection survives a model swap")
    }

    @Test
    fun aStructureChangeKeepsTheTreeSelection() = runComposeSwingTest {
        var rootLabel by mutableStateOf("root")
        val state = TreeState(initialSelectedPaths = setOf(listOf(0)))
        setContent {
            Tree(
                root = rootLabel,
                children = { if (it == rootLabel) leaves else emptyList() },
                state = state,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("apple"), tree.selectedLabels(), "declared selection applied")

        rootLabel = "trunk"
        awaitIdle()

        assertEquals(listOf("apple"), tree.selectedLabels(), "selection survives a structure change")
        assertEquals(setOf(listOf(0)), state.selectedPaths, "state selection survives a structure change")
    }

    @Test
    fun aTreeModelSwapKeepsTheSelection() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        val state = TreeState(initialSelectedPaths = setOf(listOf(1)))
        setContent {
            Tree(model = model, state = state)
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("pear"), "declared selection applied") { selectedLabels() }

        model = treeModel("trunk")
        awaitIdle()

        tree.assertProperty(listOf("pear"), "selection survives a model swap") { selectedLabels() }
        assertEquals(setOf(listOf(1)), state.selectedPaths, "state selection survives a model swap")
    }

    @Test
    fun anItemsChangeThatDropsADeclaredListRowLeavesTheStateAlone() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue"))
        val state = ListState(initialSelectedIndices = setOf(2))
        setContent {
            ListBox(items = items, state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(2), "the declared row reaches the list") { selectedIndices.toList() }

        items = listOf("red", "green")
        awaitIdle()

        list.assertProperty(
            emptyList(),
            "the new items hold none of the declared selection",
        ) { selectedIndices.toList() }
        assertEquals(setOf(2), state.selectedIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aListModelSwapThatDropsADeclaredRowLeavesTheStateAlone() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green", "blue"))
        val state = ListState(initialSelectedIndices = setOf(2))
        setContent {
            ListBox(model = model, state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(2), "the declared row reaches the list") { selectedIndices.toList() }

        model = listModel("cyan", "magenta")
        awaitIdle()

        list.assertProperty(
            emptyList(),
            "the new model holds none of the declared selection",
        ) { selectedIndices.toList() }
        assertEquals(setOf(2), state.selectedIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aRowsChangeThatDropsADeclaredTableRowLeavesTheStateAlone() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } }, state = state)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(2), "the declared row reaches the table") { selectedRows.toList() }

        rows = listOf(Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        table.assertProperty(emptyList(), "the new rows hold none of the declared selection") { selectedRows.toList() }
        assertEquals(setOf(2), state.selectedRowIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aTableModelSwapThatDropsADeclaredRowLeavesTheStateAlone() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            Table(model = model, state = state)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(2), "the declared row reaches the table") { selectedRows.toList() }

        model = tableModel("Ada", "Alan")
        awaitIdle()

        table.assertProperty(
            emptyList(),
            "the new model holds none of the declared selection",
        ) { selectedRows.toList() }
        assertEquals(setOf(2), state.selectedRowIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aStructureThatDropsADeclaredTreeNodeLeavesTheStateAlone() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        val state = TreeState(initialSelectedPaths = setOf(listOf(1)))
        setContent {
            Tree(
                // The root value carries the leaf count, so changing it changes declared data, not a
                // memoized lambda.
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                state = state,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("pear"), tree.selectedLabels(), "the declared node reaches the tree")

        leafCount = 1
        awaitIdle()

        assertEquals(emptyList(), tree.selectedLabels(), "the new structure holds none of the declared selection")
        assertEquals(setOf(listOf(1)), state.selectedPaths, "the caller's state is left as it declared it")
    }

    @Test
    fun aTreeModelSwapThatDropsADeclaredNodeLeavesTheStateAlone() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        val state = TreeState(initialSelectedPaths = setOf(listOf(1)))
        setContent {
            Tree(model = model, state = state)
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("pear"), "the declared node reaches the tree") { selectedLabels() }

        model = treeModel("trunk", leafCount = 1)
        awaitIdle()

        tree.assertProperty(emptyList(), "the new model holds none of the declared selection") { selectedLabels() }
        assertEquals(setOf(listOf(1)), state.selectedPaths, "the caller's state is left as it declared it")
    }

    @Test
    fun itemsThatLoseSomeDeclaredListRowsKeepTheRowsTheyStillHold() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue", "violet"))
        val state = ListState(initialSelectedIndices = setOf(1, 2, 3))
        setContent {
            ListBox(items = items, state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1, 2, 3), "the declared rows reach the list") { selectedIndices.toList() }

        items = listOf("red", "green")
        awaitIdle()

        list.assertProperty(listOf(1), "the rows the new items still hold stay selected") { selectedIndices.toList() }
        assertEquals(setOf(1, 2, 3), state.selectedIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aModelTooShortForTheDeclaredListSelectionKeepsTheRowsItStillHolds() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green", "blue"))
        val state = ListState(initialSelectedIndices = setOf(1, 2))
        setContent {
            ListBox(model = model, state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1, 2), "the declared rows reach the list") { selectedIndices.toList() }

        model = listModel("cyan", "magenta")
        awaitIdle()

        list.assertProperty(listOf(1), "the row the new model still holds stays selected") { selectedIndices.toList() }
        assertEquals(setOf(1, 2), state.selectedIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun rowsThatLoseSomeDeclaredTableRowsKeepTheRowsTheyStillHold() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        val state = TableState(initialSelectedRowIndices = setOf(0, 1, 2))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } }, state = state)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(0, 1, 2), "the declared rows reach the table") { selectedRows.toList() }

        rows = listOf(Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        table.assertProperty(listOf(0, 1), "the rows the new data still holds stay selected") { selectedRows.toList() }
        assertEquals(setOf(0, 1, 2), state.selectedRowIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aModelTooSmallForTheDeclaredTableSelectionKeepsTheRowsItStillHolds() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val state = TableState(initialSelectedRowIndices = setOf(0, 1, 2))
        setContent {
            Table(model = model, state = state)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(0, 1, 2), "the declared rows reach the table") { selectedRows.toList() }

        model = tableModel("Ada", "Alan")
        awaitIdle()

        table.assertProperty(listOf(0, 1), "the rows the new model still holds stay selected") { selectedRows.toList() }
        assertEquals(setOf(0, 1, 2), state.selectedRowIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aStructureThatDropsSomeDeclaredTreeNodesKeepsTheNodesItStillHolds() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        val state = TreeState(initialSelectedPaths = setOf(listOf(0), listOf(1)))
        setContent {
            Tree(
                // The root value carries the leaf count, so changing it changes declared data, not a
                // memoized lambda.
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                state = state,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("apple", "pear"), tree.selectedLabels(), "the declared nodes reach the tree")
        val expanded = state.expandedPaths

        leafCount = 1
        awaitIdle()

        assertEquals(listOf("apple"), tree.selectedLabels(), "the node the new structure still holds stays selected")
        assertEquals(
            setOf(listOf(0), listOf(1)),
            state.selectedPaths,
            "the caller's state is left as it declared it",
        )
        assertEquals(expanded, state.expandedPaths, "a structure change rewrote the declared expansion")
    }

    @Test
    fun aTreeModelSwapThatDropsSomeDeclaredNodesKeepsTheNodesItStillHolds() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        val state = TreeState(initialSelectedPaths = setOf(listOf(0), listOf(1)))
        setContent {
            Tree(model = model, state = state)
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("apple", "pear"), "the declared nodes reach the tree") { selectedLabels() }

        model = treeModel("root", leafCount = 1)
        awaitIdle()

        tree.assertProperty(listOf("apple"), "the node the new model still holds stays selected") { selectedLabels() }
        assertEquals(
            setOf(listOf(0), listOf(1)),
            state.selectedPaths,
            "the caller's state is left as it declared it",
        )
    }

    @Test
    fun aProgrammaticTreeSelectionIsAppliedToTheTree() = runComposeSwingTest {
        val state = TreeState(initialSelectedPaths = setOf(listOf(0)))
        val model = treeModel("root")
        setContent {
            Tree(model = model, state = state)
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("apple"), "declared selection applied") { selectedLabels() }

        state.selectedPaths = setOf(listOf(1))
        awaitIdle()

        tree.assertProperty(listOf("pear"), "the new declared selection applied") { selectedLabels() }
        assertEquals(setOf(listOf(1)), state.selectedPaths, "the declared selection was not written over")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listSelectionLossCases")
    fun itemsThatLoseListRowsReportTheNewSelection(case: ListSelectionLossCase) = runComposeSwingTest {
        var items by mutableStateOf(case.initial)
        var events = 0
        setContent {
            ListBox(
                items = items,
                modifier = SwingModifier.listSelectionListener { if (!it.valueIsAdjusting) events++ },
            )
        }
        val list = onNodeOfType<JList<*>>()
        list.fetch().selectionModel.setSelectionInterval(case.selection.first, case.selection.last)
        events = 0
        items = case.remaining
        awaitIdle()
        list.assertProperty(
            case.outcome.expected,
            "${case.name}: ${case.outcome.selectionMessage}",
        ) { selectedIndices.toList() }
        assertTrue(events > 0, "${case.name}: ${case.outcome.reportMessage}")
    }

    @Test
    fun aModelTooShortForTheListSelectionReportsTheNarrowing() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green", "blue"))
        var events = 0
        setContent {
            ListBox(
                model = model,
                modifier = SwingModifier.listSelectionListener { if (!it.valueIsAdjusting) events++ },
            )
        }

        val list = onNodeOfType<JList<*>>()
        list.fetch().selectionModel.setSelectionInterval(1, 2)
        events = 0

        model = listModel("cyan", "magenta")
        awaitIdle()

        list.assertProperty(listOf(1), "the row the new model still holds stays selected") { selectedIndices.toList() }
        assertEquals(2, events, "the rows the user loses are reported as the new selection")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tableSelectionLossCases")
    fun rowsThatLoseTableRowsReportTheNewSelection(case: TableSelectionLossCase) = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }
        val table = onNodeOfType<JTable>()
        table.fetch().selectionModel.setSelectionInterval(case.first, 2)
        rows = case.remaining
        awaitIdle()
        table.assertProperty(case.expected, "${case.name}: ${case.selectionMessage}") { selectedRows.toList() }
    }

    @Test
    fun aModelTooSmallForTheTableSelectionReportsTheNarrowing() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        setContent {
            Table(model = model)
        }

        val table = onNodeOfType<JTable>()
        table.fetch().selectionModel.setSelectionInterval(0, 2)

        model = tableModel("Ada", "Alan")
        awaitIdle()

        table.assertProperty(listOf(0, 1), "the rows the new model still holds stay selected") { selectedRows.toList() }
    }

    @Test
    fun aStructureThatDropsASelectedTreeNodeReportsTheNarrowing() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        var events = 0
        val expansions = mutableListOf<Any>()
        setContent {
            Tree(
                // The root value carries the leaf count, so changing it changes declared data, not a
                // memoized lambda.
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                modifier =
                    SwingModifier
                        .treeSelectionListener { events++ }
                        .treeExpansionListener { expansions += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        events = 0
        expansions.clear()

        leafCount = 1
        awaitIdle()

        assertEquals(listOf("apple"), tree.selectedLabels(), "the node the new structure still holds stays selected")
        assertEquals(1, events, "the node the user loses is reported as the new selection")
        assertEquals(emptyList(), expansions, "a structure change reported an expansion change")
    }

    @Test
    fun aStructureThatDropsEverySelectedTreeNodeReportsTheLoss() = runComposeSwingTest {
        var leafCount by mutableStateOf(2)
        var events = 0
        setContent {
            Tree(
                // The root value carries the leaf count, so changing it changes declared data, not a
                // memoized lambda.
                root = "root of $leafCount",
                children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                modifier = SwingModifier.treeSelectionListener { events++ },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(1))
        events = 0

        leafCount = 0
        awaitIdle()

        assertEquals(emptyList(), tree.selectedLabels(), "no node of the old selection is left to select")
        assertEquals(1, events, "losing the whole selection is reported")
    }

    @Test
    fun aTreeModelThatDropsTheSelectionReportsTheNarrowing() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        var events = 0
        setContent {
            Tree(
                model = model,
                modifier = SwingModifier.treeSelectionListener { events++ },
            )
        }

        val tree = onNodeOfType<JTree>()
        tree.fetch().selectionPaths = arrayOf(tree.fetch().pathTo(0), tree.fetch().pathTo(1))
        events = 0

        model = treeModel("root", leafCount = 1)
        awaitIdle()

        tree.assertProperty(listOf("apple"), "the node the new model still holds stays selected") { selectedLabels() }
        assertEquals(2, events, "the node the user loses is reported as the new selection")
    }

    @Test
    fun aListenerThatFailsOnTheListNarrowingReportLeavesLaterItemsApplied() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue"))
        var failing by mutableStateOf(false)
        val failure = IllegalStateException("the list narrowing report fails")
        val listener = ListSelectionListener { if (failing) throw failure }
        setContent {
            ListBox(items = items, modifier = SwingModifier.listSelectionListener(listener))
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
                modifier = SwingModifier.treeSelectionListener(listener),
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
                            "the rows the new items still hold stay selected",
                            "the rows the user loses are reported as the new selection",
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
                            "no row of the old selection is left to select",
                            "losing the whole selection is reported",
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
                        listOf(0, 1),
                        "the rows the new data still holds stay selected",
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
                        emptyList<Int>(),
                        "no row of the old selection is left to select",
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
    val expected: List<Int>,
    val selectionMessage: String,
)

class SelectionLossOutcome(
    val expected: List<Int>,
    val selectionMessage: String,
    val reportMessage: String,
)
