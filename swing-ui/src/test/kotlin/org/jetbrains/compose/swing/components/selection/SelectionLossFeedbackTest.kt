package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A property the caller declares can leave a widget unable to hold the whole selection: a narrower
 * selection mode drops what no longer fits, and a hidden root takes the root itself out of the selection.
 * Who owns the selection decides what the wrapper does about it, so both sides of that split are pinned
 * here.
 *
 * Where the caller declared no selection it is the user's: what the widget destroys the wrapper reports,
 * so the caller learns of the loss exactly as it learns of the loss new content causes. Where the caller
 * declared one it is the composition's state, re-asserted on every pass: the widget's own narrowing is not
 * reported - a report would feed the caller's state holder a selection the user never chose, and the next
 * pass would apply it - and as much of the declaration as the widget can hold is left standing.
 *
 * Both sides are pinned on every variant of a component, the data-driven ones and the ones a caller-owned
 * model drives, and through the raw listener as well as the callback: each variant applies the property
 * itself, and the widget's own event reaches a raw listener directly.
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
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                onSelectionChange = { received += it },
                selectionMode = mode,
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(0, 2)
        received.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(listOf(0), list.selectedIndices.toList(), "the row the narrower mode still holds stays selected")
        assertEquals(listOf(setOf(0)), received, "the rows the user loses are reported once, as the new selection")
    }

    @Test
    fun aNarrowerTableSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = people, onSelectionChange = { received += it }, selectionMode = mode) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(0, 2)
        received.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(listOf(0), table.selectedRows.toList(), "the row the narrower mode still holds stays selected")
        assertEquals(listOf(setOf(0)), received, "the rows the user loses are reported once, as the new selection")
    }

    @Test
    fun aNarrowerModelTableSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val received = mutableListOf<Set<Int>>()
        val model = tableModel()
        setContent {
            Table(model = model, onSelectionChange = { received += it }, selectionMode = mode)
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(0, 2)
        received.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(listOf(0), table.selectedRows.toList(), "the row the narrower mode still holds stays selected")
        assertEquals(listOf(setOf(0)), received, "the rows the user loses are reported once, as the new selection")
    }

    @Test
    fun aNarrowerTreeSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel()
        setContent {
            Tree(model = model, onSelectionChange = { received += it }, selectionMode = mode)
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        received.clear()

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        assertEquals(1, tree.selectionCount, "the node the narrower mode still holds stays selected")
        assertEquals(listOf(setOf(listOf(0))), received, "the node the user loses is reported once")
    }

    @Test
    fun aNarrowerValueTreeSelectionModeReportsWhatTheUserKeeps() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                onSelectionChange = { received += it },
                selectionMode = mode,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        received.clear()

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        assertEquals(1, tree.selectionCount, "the node the narrower mode still holds stays selected")
        assertEquals(listOf(setOf(listOf(0))), received, "the node the user loses is reported once")
    }

    @Test
    fun hidingTheRootReportsTheSelectionItTakesAway() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel()
        setContent {
            Tree(model = model, onSelectionChange = { received += it }, rootVisible = rootVisible)
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo())
        received.clear()

        rootVisible = false
        awaitIdle()

        assertEquals(0, tree.selectionCount, "a hidden root cannot stay selected")
        assertEquals(listOf(emptySet()), received, "losing the whole selection is reported once")
    }

    @Test
    fun hidingTheRootOfAValueTreeReportsTheSelectionItTakesAway() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                onSelectionChange = { received += it },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo())
        received.clear()

        rootVisible = false
        awaitIdle()

        assertEquals(0, tree.selectionCount, "a hidden root cannot stay selected")
        assertEquals(listOf(emptySet()), received, "losing the whole selection is reported once")
    }

    @Test
    fun hidingTheRootDoesNotDestroyADeclaredSelection() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        var selection by mutableStateOf(setOf(emptyList<Int>()))
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel()
        setContent {
            Tree(
                model = model,
                selectedPaths = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(1, tree.selectionCount, "the declared root reaches the tree")
        received.clear()

        rootVisible = false
        awaitIdle()

        assertEquals(1, tree.selectionCount, "the declared selection should still stand")
        assertEquals(setOf(emptyList()), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "hiding the root reported the wrapper's own write")
    }

    @Test
    fun hidingTheRootOfAValueTreeDoesNotDestroyADeclaredSelection() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        var selection by mutableStateOf(setOf(emptyList<Int>()))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(1, tree.selectionCount, "the declared root reaches the tree")
        received.clear()

        rootVisible = false
        awaitIdle()

        assertEquals(1, tree.selectionCount, "the declared selection should still stand")
        assertEquals(setOf(emptyList()), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "hiding the root reported the wrapper's own write")
    }

    @Test
    fun narrowingTheTableModeDoesNotEmptyADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        var selection by mutableStateOf(setOf(0, 1, 2))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = people,
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
                selectionMode = mode,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the declared rows reach the table")
        received.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(1, table.selectedRows.size, "a single-selection table can still hold one declared row")
        assertEquals(setOf(0, 1, 2), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a narrower mode reported the wrapper's own write")
    }

    @Test
    fun narrowingTheModelTableModeDoesNotEmptyADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        var selection by mutableStateOf(setOf(0, 1, 2))
        val received = mutableListOf<Set<Int>>()
        val model = tableModel()
        setContent {
            Table(
                model = model,
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
                selectionMode = mode,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the declared rows reach the table")
        received.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(1, table.selectedRows.size, "a single-selection model-driven table still holds one declared row")
        assertEquals(setOf(0, 1, 2), selection, "the caller's state is left as it declared it")
        assertEquals(emptyList(), received, "a narrower mode reported the wrapper's own write")
    }

    @Test
    fun aNarrowerListSelectionModeLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val received = mutableListOf<Set<Int>>()
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                selectedIndices = setOf(0, 1, 2),
                onSelectionChange = { received += it },
                selectionMode = mode,
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(0, 1, 2), list.selectedIndices.toList(), "the declared rows reach the list")
        received.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(listOf(2), list.selectedIndices.toList(), "the declared row a single-selection list can hold")
        assertEquals(emptyList(), received, "a narrower mode reported the wrapper's own write")
    }

    @Test
    fun aNarrowerTableSelectionModeLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = people,
                selectedRowIndices = setOf(0, 1, 2),
                onSelectionChange = { received += it },
                selectionMode = mode,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the declared rows reach the table")
        received.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(listOf(2), table.selectedRows.toList(), "the declared row a single-selection table can hold")
        assertEquals(emptyList(), received, "a narrower mode reported the wrapper's own write")
    }

    @Test
    fun aNarrowerTreeSelectionModeLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel()
        setContent {
            Tree(
                model = model,
                selectedPaths = setOf(listOf(0), listOf(1)),
                onSelectionChange = { received += it },
                selectionMode = mode,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(2, tree.selectionCount, "both declared nodes reach the tree")
        received.clear()

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        assertEquals(1, tree.selectionCount, "the declared node a single-selection tree can hold")
        assertEquals(emptyList(), received, "a narrower mode reported the wrapper's own write")
    }

    @Test
    fun aNarrowerValueTreeSelectionModeLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = setOf(listOf(0), listOf(1)),
                onSelectionChange = { received += it },
                selectionMode = mode,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(2, tree.selectionCount, "both declared nodes reach the tree")
        received.clear()

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        assertEquals(1, tree.selectionCount, "the declared node a single-selection tree can hold")
        assertEquals(emptyList(), received, "a narrower mode reported the wrapper's own write")
    }

    @Test
    fun hidingTheRootLeavesADeclaredSelectionUnreported() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel()
        setContent {
            Tree(
                model = model,
                selectedPaths = setOf(emptyList()),
                onSelectionChange = { received += it },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(1, tree.selectionCount, "the declared root reaches the tree")
        received.clear()

        rootVisible = false
        awaitIdle()

        assertEquals(1, tree.selectionCount, "a declared root stays selected while it is hidden")
        assertEquals(emptyList(), received, "hiding the root reported the wrapper's own write")
    }

    @Test
    fun aNarrowerListSelectionModeReachesNoRawListenerOfADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                listSelectionListener = listener,
                selectedIndices = setOf(0, 1, 2),
                selectionMode = mode,
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(0, 1, 2), list.selectedIndices.toList(), "the declared rows reach the list")
        adjusting.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(
            listOf(2),
            list.selectedIndices.toList(),
            "the narrower mode moves the selection, so there is a change for the listener to be silent about",
        )
        assertEquals(emptyList(), adjusting, "a narrower mode reached the raw listener")
    }

    @Test
    fun aNarrowerTableSelectionModeReachesNoRawListenerOfADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            Table(
                rows = people,
                listSelectionListener = listener,
                selectedRowIndices = setOf(0, 1, 2),
                selectionMode = mode,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the declared rows reach the table")
        adjusting.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(
            listOf(2),
            table.selectedRows.toList(),
            "the narrower mode moves the selection, so there is a change for the listener to be silent about",
        )
        assertEquals(emptyList(), adjusting, "a narrower mode reached the raw listener")
    }

    @Test
    fun hidingTheRootReachesNoRawListenerOfADeclaredSelection() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        val paths = mutableListOf<Int>()
        val listener = TreeSelectionListener { event -> paths += event.paths.size }
        val model = treeModel()
        setContent {
            Tree(
                model = model,
                treeSelectionListener = listener,
                selectedPaths = setOf(emptyList()),
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(1, tree.selectionCount, "the declared root reaches the tree")
        paths.clear()

        rootVisible = false
        awaitIdle()

        assertEquals(1, tree.selectionCount, "a declared root stays selected while it is hidden")
        assertEquals(emptyList(), paths, "hiding the root reached the raw listener")
    }

    @Test
    fun aNarrowerModelTableSelectionModeReachesNoRawListenerOfADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        val model = tableModel()
        setContent {
            Table(
                model = model,
                listSelectionListener = listener,
                selectedRowIndices = setOf(0, 1, 2),
                selectionMode = mode,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the declared rows reach the table")
        adjusting.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(
            listOf(2),
            table.selectedRows.toList(),
            "the narrower mode moves the selection, so there is a change for the listener to be silent about",
        )
        assertEquals(emptyList(), adjusting, "a narrower mode reached the raw listener")
    }

    @Test
    fun aNarrowerValueTreeSelectionModeReachesNoRawListenerOfADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val paths = mutableListOf<Int>()
        val listener = TreeSelectionListener { event -> paths += event.paths.size }
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                treeSelectionListener = listener,
                label = { it.name },
                selectedPaths = setOf(listOf(0), listOf(1)),
                selectionMode = mode,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(2, tree.selectionCount, "both declared nodes reach the tree")
        paths.clear()

        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()

        assertEquals(1, tree.selectionCount, "the declared node a single-selection tree can hold")
        assertEquals(emptyList(), paths, "a narrower mode reached the raw listener")
    }

    @Test
    fun hidingTheRootOfAValueTreeReachesNoRawListenerOfADeclaredSelection() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        val paths = mutableListOf<Int>()
        val listener = TreeSelectionListener { event -> paths += event.paths.size }
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                treeSelectionListener = listener,
                label = { it.name },
                selectedPaths = setOf(emptyList()),
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(1, tree.selectionCount, "the declared root reaches the tree")
        paths.clear()

        rootVisible = false
        awaitIdle()

        assertEquals(1, tree.selectionCount, "a declared root stays selected while it is hidden")
        assertEquals(emptyList(), paths, "hiding the root reached the raw listener")
    }
}
