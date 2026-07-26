package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Selection is either the caller's state or the user's, and [Table], [ListBox] and [Tree] answer the
 * same way in every variant: a declared selection is owned by the composition and re-applied whenever
 * the composition runs, so a change the caller declines to adopt is undone on the widget; an undeclared
 * selection belongs to the user, is never imposed, and survives the writes the library makes to its own
 * widget while rendering new data. A [ComboBox] always has a declared selection, so it answers the first
 * half of that alike.
 *
 * A user's click reaches a widget as a selection write, so these tests select through the widget's own
 * selection API to stand in for one. The recomposition each test provokes changes a property
 * that has nothing to do with selection, which is precisely the pass that must not disturb it.
 */
class SelectionDeclarationTest {
    private data class Entry(
        val name: String,
        val children: List<Entry> = emptyList(),
    )

    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    private val sample =
        Entry(
            "root",
            listOf(
                Entry("fruit", listOf(Entry("apple"), Entry("pear"))),
                Entry("veg", listOf(Entry("carrot"))),
            ),
        )

    private fun listModel(vararg elements: String): DefaultListModel<String> =
        DefaultListModel<String>().apply { for (element in elements) addElement(element) }

    private fun tableModel(vararg names: String): DefaultTableModel =
        DefaultTableModel(arrayOf<Any>("Name"), 0).apply { for (name in names) addRow(arrayOf<Any>(name)) }

    /** The labels of the nodes the tree has selected. */
    private fun JTree.selectedLabels(): List<String> = selectionPaths.orEmpty().map { it.lastPathComponent.toString() }

    private fun treeModel(rootLabel: String): DefaultTreeModel {
        val root = DefaultMutableTreeNode(rootLabel)
        for (leaf in listOf("apple", "pear")) root.add(DefaultMutableTreeNode(leaf))
        return DefaultTreeModel(root)
    }

    @Test
    fun anUndeclaredTableSelectionSurvivesARecomposition() = runComposeSwingTest {
        var label by mutableStateOf("first")
        val received = mutableListOf<List<Int>>()
        setContent {
            Table(
                rows = people,
                modifier = SwingModifier.name(label),
                onSelectionChange = { received += it },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)
        received.clear()

        label = "second"
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "an undeclared selection is the user's")
        assertEquals(emptyList(), received, "a recomposition reported a selection change")
    }

    @Test
    fun anUndeclaredTableSelectionSurvivesARowsRefresh() = runComposeSwingTest {
        val rows = mutableStateListOf(*people.toTypedArray())
        val received = mutableListOf<List<Int>>()
        setContent {
            Table(rows = rows.toList(), onSelectionChange = { received += it }) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)
        received.clear()

        rows[2] = Person("Grace Hopper", 50)
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "an undeclared selection survives new rows")
        assertEquals(emptyList(), received, "a rows refresh reported a selection change")
    }

    @Test
    fun anUndeclaredTableSelectionSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val received = mutableListOf<List<Int>>()
        setContent {
            Table(model = model, onSelectionChange = { received += it })
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)
        received.clear()

        model = tableModel("Barbara", "Edsger", "Ada")
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "an undeclared selection survives a model swap")
        assertEquals(emptyList(), received, "a model swap reported a selection change")
    }

    @Test
    fun anUndeclaredListSelectionSurvivesAnItemsChange() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        val received = mutableListOf<List<Int>>()
        setContent {
            ListBox(items = items.toList(), onSelectionChange = { received += it })
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(1, 1)
        received.clear()

        items.add("violet")
        awaitIdle()

        assertEquals(listOf(1), list.selectedIndices.toList(), "an undeclared selection survives new items")
        assertEquals(emptyList(), received, "an items change reported a selection change")
    }

    @Test
    fun anUndeclaredListSelectionSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green"))
        val received = mutableListOf<List<Int>>()
        setContent {
            ListBox(model = model, onSelectionChange = { received += it })
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(1, 1)
        received.clear()

        model = listModel("cyan", "magenta")
        awaitIdle()

        assertEquals(listOf(1), list.selectedIndices.toList(), "an undeclared selection survives a model swap")
        assertEquals(emptyList(), received, "a model swap reported a selection change")
    }

    @Test
    fun anUndeclaredTreeSelectionSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        val received = mutableListOf<List<List<Int>>>()
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                label = { it.name },
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.setSelectionRow(1)
        received.clear()

        label = "trunk"
        awaitIdle()

        assertEquals(listOf("fruit"), tree.selectedLabels(), "an undeclared selection survives a structure change")
        assertEquals(emptyList(), received, "a structure change reported a selection change")
    }

    @Test
    fun anUndeclaredTreeSelectionSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        val received = mutableListOf<List<List<Int>>>()
        setContent {
            Tree(model = model, onSelectionChange = { received += it })
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.setSelectionRow(1)
        received.clear()

        model = treeModel("trunk")
        awaitIdle()

        assertEquals(listOf("apple"), tree.selectedLabels(), "an undeclared selection survives a model swap")
        assertEquals(emptyList(), received, "a model swap reported a selection change")
    }

    @Test
    fun aRefusedTableRowsSelectionIsRestored() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(
                rows = people,
                modifier = SwingModifier.name(label),
                selectedRowIndices = listOf(0),
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(2, 2)
        assertEquals(listOf(2), table.selectedRows.toList(), "the user's choice reaches the widget")

        label = "second"
        awaitIdle()

        assertEquals(listOf(0), table.selectedRows.toList(), "the declared selection is re-applied")
    }

    @Test
    fun aDeclaredEmptySelectionClearsWhatTheUserPicked() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(rows = people, modifier = SwingModifier.name(label), selectedRowIndices = emptyList()) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)

        label = "second"
        awaitIdle()

        assertEquals(emptyList(), table.selectedRows.toList(), "an empty selection is a declaration like any other")
    }

    @Test
    fun aRefusedTableModelSelectionIsRestored() = runComposeSwingTest {
        var label by mutableStateOf("first")
        val model = tableModel("Ada", "Alan", "Grace")
        setContent {
            Table(model = model, modifier = SwingModifier.name(label), selectedRowIndices = listOf(0))
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(2, 2)

        label = "second"
        awaitIdle()

        assertEquals(listOf(0), table.selectedRows.toList(), "the declared selection is re-applied")
    }

    @Test
    fun aTableRowSelectionDeclaredForTheFirstTimeIsAppliedAlongsideAModifierChange() = runComposeSwingTest {
        var label by mutableStateOf("first")
        var selection by mutableStateOf<List<Int>?>(null)
        setContent {
            Table(rows = people, modifier = SwingModifier.name(label), selectedRowIndices = selection) {
                column("Name") { it.name }
            }
        }

        label = "second"
        selection = listOf(1)
        awaitIdle()

        val table = onNodeWithName("second").fetch<JTable>()
        assertEquals(listOf(1), table.selectedRows.toList(), "the newly declared selection is applied")
    }

    @Test
    fun aRefusedListSelectionIsRestored() = runComposeSwingTest {
        var count by mutableStateOf(8)
        setContent {
            ListBox(items = listOf("red", "green", "blue"), selectedIndices = listOf(0), visibleRowCount = count)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(2, 2)
        assertEquals(listOf(2), list.selectedIndices.toList(), "the user's choice reaches the widget")

        count = 6
        awaitIdle()

        assertEquals(listOf(0), list.selectedIndices.toList(), "the declared selection is re-applied")
    }

    @Test
    fun aRefusedListModelSelectionIsRestored() = runComposeSwingTest {
        var count by mutableStateOf(8)
        val model = listModel("red", "green", "blue")
        setContent {
            ListBox(model = model, selectedIndices = listOf(0), visibleRowCount = count)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(2, 2)

        count = 6
        awaitIdle()

        assertEquals(listOf(0), list.selectedIndices.toList(), "the declared selection is re-applied")
    }

    @Test
    fun aRefusedComboBoxSelectionIsRestored() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                modifier = SwingModifier.name(label),
                selectedIndex = 0,
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        combo.selectedIndex = 2
        assertEquals(2, combo.selectedIndex, "the user's choice reaches the widget")

        label = "second"
        awaitIdle()

        assertEquals(0, combo.selectedIndex, "the declared selection is re-applied")
    }

    @Test
    fun aRefusedTreeSelectionIsRestored() = runComposeSwingTest {
        var label by mutableStateOf("first")
        val model = treeModel("root")
        setContent {
            Tree(model = model, modifier = SwingModifier.name(label), selectedPaths = listOf(listOf(0)))
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.setSelectionRow(2)
        assertEquals(listOf("pear"), tree.selectedLabels(), "the user's choice reaches the widget")

        label = "second"
        awaitIdle()

        assertEquals(listOf("apple"), tree.selectedLabels(), "the declared selection is re-applied")
    }
}
