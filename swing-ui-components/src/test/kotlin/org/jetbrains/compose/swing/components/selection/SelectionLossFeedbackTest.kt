// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.components.modifier.listener.treeSelectionListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A property the caller declares can leave a widget unable to hold the whole selection: a narrower
 * selection mode drops what no longer fits, and a hidden root takes the root itself out of the selection.
 * The selection a state holds is the composition's own, re-asserted on every pass: the widget's own
 * narrowing leaves the state as it declared it, and as much of the declaration as the widget can hold is
 * left standing.
 *
 * This is pinned on every variant of a component, the data-driven ones and the ones a caller-owned model
 * drives: each variant applies the property itself.
 */
class SelectionLossFeedbackTest {
    private data class Entry(
        val name: String,
        val children: List<Entry> = emptyList(),
    )

    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    private val sample = Entry("root", listOf(Entry("apple"), Entry("pear")))

    private fun treeModel(): DefaultTreeModel {
        val root = DefaultMutableTreeNode("root")
        for (leaf in listOf("apple", "pear")) root.add(DefaultMutableTreeNode(leaf))
        return DefaultTreeModel(root)
    }

    private fun tableModel(): DefaultTableModel =
        DefaultTableModel(arrayOf<Any>("Name"), 0).apply { for (person in people) addRow(arrayOf<Any>(person.name)) }

    @Test
    fun aNarrowerListSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        var events = 0
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                modifier = SwingModifier.listSelectionListener { if (!it.valueIsAdjusting) events++ },
                selectionMode = mode,
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(0, 2)
        events = 0

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(
            listOf(0),
            "the row the narrower mode still holds stays selected",
        ) { selectedIndices.toList() }
        assertEquals(1, events, "the rows the user loses are reported as the new selection")
    }

    @Test
    fun aNarrowerModelTableSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val model = tableModel()
        setContent {
            Table(model = model, selectionMode = mode)
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(0, 2)

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(0),
            "the row the narrower mode still holds stays selected",
        ) { selectedRows.toList() }
    }

    @Test
    fun aNarrowerTreeSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        var events = 0
        val model = treeModel()
        setContent {
            Tree(
                model = model,
                modifier = SwingModifier.treeSelectionListener { events++ },
                selectionMode = mode,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        events = 0

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            1,
            "the node the narrower mode still holds stays selected",
        ) { selectionCount }
        assertEquals(1, events, "the node the user loses is reported")
    }

    @Test
    fun aNarrowerValueTreeSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        var events = 0
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                modifier = SwingModifier.treeSelectionListener { events++ },
                selectionMode = mode,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        events = 0

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            1,
            "the node the narrower mode still holds stays selected",
        ) { selectionCount }
        assertEquals(1, events, "the node the user loses is reported")
    }

    @Test
    fun hidingTheRootReportsTheSelectionItTakesAway() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        var events = 0
        val model = treeModel()
        setContent {
            Tree(
                model = model,
                modifier = SwingModifier.treeSelectionListener { events++ },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo())
        events = 0

        rootVisible = false
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(0, "a hidden root cannot stay selected") { selectionCount }
        assertEquals(1, events, "losing the whole selection is reported")
    }

    @Test
    fun hidingTheRootOfAValueTreeReportsTheSelectionItTakesAway() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        var events = 0
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                modifier = SwingModifier.treeSelectionListener { events++ },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo())
        events = 0

        rootVisible = false
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(0, "a hidden root cannot stay selected") { selectionCount }
        assertEquals(1, events, "losing the whole selection is reported")
    }

    @Test
    fun hidingTheRootDoesNotDestroyADeclaredSelection() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        val state = TreeState(initialSelectedPaths = setOf(emptyList()))
        val model = treeModel()
        setContent {
            Tree(model = model, state = state, rootVisible = rootVisible)
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(1, "the declared root reaches the tree") { selectionCount }

        rootVisible = false
        awaitIdle()

        tree.assertProperty(1, "the declared selection should still stand") { selectionCount }
        assertEquals(setOf(emptyList()), state.selectedPaths, "the caller's state is left as it declared it")
    }

    @Test
    fun hidingTheRootOfAValueTreeDoesNotDestroyADeclaredSelection() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        val state = TreeState(initialSelectedPaths = setOf(emptyList()))
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                state = state,
                label = { it.name },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(1, "the declared root reaches the tree") { selectionCount }

        rootVisible = false
        awaitIdle()

        tree.assertProperty(1, "the declared selection should still stand") { selectionCount }
        assertEquals(setOf(emptyList()), state.selectedPaths, "the caller's state is left as it declared it")
    }

    @Test
    fun narrowingTheTableModeDoesNotEmptyADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val state = TableState(initialSelectedRowIndices = setOf(0, 1, 2))
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                state = state,
                selectionMode = mode,
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(0, 1, 2), "the declared rows reach the table") { selectedRows.toList() }

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        table.assertProperty(listOf(2), "the declared row a single-selection table can hold") { selectedRows.toList() }
        assertEquals(setOf(0, 1, 2), state.selectedRowIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun narrowingTheModelTableModeDoesNotEmptyADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val state = TableState(initialSelectedRowIndices = setOf(0, 1, 2))
        val model = tableModel()
        setContent {
            Table(model = model, state = state, selectionMode = mode)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(0, 1, 2), "the declared rows reach the table") { selectedRows.toList() }

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        table.assertProperty(
            1,
            "a single-selection model-driven table still holds one declared row",
        ) { selectedRows.size }
        assertEquals(setOf(0, 1, 2), state.selectedRowIndices, "the caller's state is left as it declared it")
    }

    @Test
    fun aNarrowerListSelectionModeLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val state = ListState(initialSelectedIndices = setOf(0, 1, 2))
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                state = state,
                selectionMode = mode,
            )
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(0, 1, 2), "the declared rows reach the list") { selectedIndices.toList() }

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        list.assertProperty(listOf(2), "the declared row a single-selection list can hold") { selectedIndices.toList() }
        assertEquals(setOf(0, 1, 2), state.selectedIndices, "a narrower mode leaves the state as declared")
    }

    @Test
    fun aNarrowerTreeSelectionModeLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val state = TreeState(initialSelectedPaths = setOf(listOf(0), listOf(1)))
        val model = treeModel()
        setContent {
            Tree(model = model, state = state, selectionMode = mode)
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(2, "both declared nodes reach the tree") { selectionCount }

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        tree.assertProperty(1, "the declared node a single-selection tree can hold") { selectionCount }
        assertEquals(
            setOf(listOf(0), listOf(1)),
            state.selectedPaths,
            "a narrower mode leaves the state as declared",
        )
    }

    @Test
    fun aNarrowerValueTreeSelectionModeLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val state = TreeState(initialSelectedPaths = setOf(listOf(0), listOf(1)))
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                state = state,
                label = { it.name },
                selectionMode = mode,
            )
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(2, "both declared nodes reach the tree") { selectionCount }

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        tree.assertProperty(1, "the declared node a single-selection tree can hold") { selectionCount }
        assertEquals(
            setOf(listOf(0), listOf(1)),
            state.selectedPaths,
            "a narrower mode leaves the state as declared",
        )
    }
}
