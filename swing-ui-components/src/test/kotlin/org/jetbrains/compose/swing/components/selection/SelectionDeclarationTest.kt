// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.modifier.listener.treeSelectionListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * [Table], [ListBox] and [Tree] without a state leave selection to the user, which survives the writes the
 * library makes to its own widget while rendering new data. With a state, the user's selection is written
 * back to it and what is assigned to the state is applied to the widget. A [ComboBox] declares its
 * selection, so a change the caller declines to adopt is undone on the widget.
 *
 * A user's click reaches a widget as a selection write, so these tests select through the widget's own
 * selection API to stand in for one. The recomposition each test provokes changes a property
 * that has nothing to do with selection, which is precisely the pass that must not disturb it.
 *
 * A declaration names rows and not an order: whichever order it iterates in, the rows are applied
 * ascending, so every declaration of the same rows leaves the widget on the same selection and the same
 * lead row - the highest of them, left behind by the last row selected.
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

    private fun treeModel(rootLabel: String): DefaultTreeModel {
        val root = DefaultMutableTreeNode(rootLabel)
        for (leaf in listOf("apple", "pear")) root.add(DefaultMutableTreeNode(leaf))
        return DefaultTreeModel(root)
    }

    @Test
    fun anUndeclaredTableSelectionSurvivesARecomposition() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                modifier = SwingModifier.name(label),
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)

        label = "second"
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "an undeclared selection is the user's",
        ) { selectedRows.toList() }
    }

    @Test
    fun anUndeclaredTableSelectionSurvivesARowsRefresh() = runComposeSwingTest {
        val rows = mutableStateListOf(*people.toTypedArray())
        setContent {
            Table(
                rows = rows.toList(),
                columns = { column("Name") { it.name } },
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)

        rows[2] = Person("Grace Hopper", 50)
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "an undeclared selection survives new rows",
        ) { selectedRows.toList() }
    }

    @Test
    fun anUndeclaredTableSelectionSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        setContent {
            Table(model = model)
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)

        model = tableModel("Barbara", "Edsger", "Ada")
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "an undeclared selection survives a model swap",
        ) { selectedRows.toList() }
    }

    @Test
    fun aSwappedTableModelShowsTheNewModelsRows() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        setContent {
            Table(model = model, state = rememberTableState())
        }

        model = tableModel("Barbara", "Edsger")
        awaitIdle()

        val table = onNodeOfType<JTable>().fetch()
        assertSame(model, table.model, "the swapped-in model should be installed on the table")
        assertEquals(listOf("Barbara", "Edsger"), List(table.rowCount) { table.getValueAt(it, 0) })
    }

    @Test
    fun anUndeclaredListSelectionSurvivesAnItemsChange() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        setContent {
            ListBox(items = items.toList())
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(1, 1)

        items.add("violet")
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(
            listOf(1),
            "an undeclared selection survives new items",
        ) { selectedIndices.toList() }
    }

    @Test
    fun anUndeclaredListSelectionSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(listModel("red", "green"))
        setContent {
            ListBox(model = model)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(1, 1)

        model = listModel("cyan", "magenta")
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(
            listOf(1),
            "an undeclared selection survives a model swap",
        ) { selectedIndices.toList() }
    }

    @Test
    fun anUndeclaredTreeSelectionSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        var events = 0
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                label = { it.name },
                modifier = SwingModifier.treeSelectionListener { events++ },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.setSelectionRow(1)
        events = 0

        label = "trunk"
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            listOf("fruit"),
            "an undeclared selection survives a structure change",
        ) { selectedLabels() }
        assertEquals(0, events, "a structure change reported a selection change")
    }

    @Test
    fun anUndeclaredTreeSelectionSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(treeModel("root"))
        setContent {
            Tree(model = model)
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.setSelectionRow(1)

        model = treeModel("trunk")
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            listOf("apple"),
            "an undeclared selection survives a model swap",
        ) { selectedLabels() }
    }

    @Test
    fun aTableRowSelectionAssignedToTheStateIsAppliedAlongsideAModifierChange() = runComposeSwingTest {
        var label by mutableStateOf("first")
        val state = TableState()
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                modifier = SwingModifier.name(label),
                state = state,
            )
        }

        label = "second"
        state.selectedRowIndices = setOf(1)
        awaitIdle()

        onNodeWithName("second")
            .assertIsOfType<JTable>()
            .assertProperty(listOf(1), "the newly assigned selection is applied") { selectedRows.toList() }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["ListBox", "Table", "Tree"])
    fun aSelectionHeldByAStateBoundForTheFirstTimeIsAppliedAlongsideAModifierChange(widget: String) =
        runComposeSwingTest {
            var label by mutableStateOf("first")
            var listState by mutableStateOf<ListState?>(null)
            var tableState by mutableStateOf<TableState?>(null)
            var treeState by mutableStateOf<TreeState?>(null)
            setContent {
                val modifier = SwingModifier.name(label)
                when (widget) {
                    "ListBox" -> {
                        ListBox(items = listOf("red", "green", "blue"), modifier = modifier, state = listState)
                    }

                    "Table" -> {
                        Table(
                            rows = people,
                            columns = { column("Name") { it.name } },
                            modifier = modifier,
                            state = tableState,
                        )
                    }

                    else -> {
                        Tree(model = treeModel("root"), modifier = modifier, state = treeState)
                    }
                }
            }

            label = "second"
            listState = ListState(initialSelectedIndices = setOf(1))
            tableState = TableState(initialSelectedRowIndices = setOf(1))
            treeState = TreeState(initialSelectedPaths = setOf(listOf(1)))
            awaitIdle()

            val node = onNodeWithName("second")
            val message = "$widget shows the selection its state holds"
            when (widget) {
                "ListBox" -> {
                    node.assertIsOfType<JList<*>>().assertProperty(listOf(1), message) { selectedIndices.toList() }
                }

                "Table" -> {
                    node.assertIsOfType<JTable>().assertProperty(listOf(1), message) { selectedRows.toList() }
                }

                else -> {
                    node.assertIsOfType<JTree>().assertProperty(listOf("pear"), message) { selectedLabels() }
                }
            }
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["ListBox", "Table", "Tree"])
    fun anEmptySelectionAssignedToTheStateClearsWhatTheUserPicked(widget: String) = runComposeSwingTest {
        val listState = ListState()
        val tableState = TableState()
        val treeState = TreeState()
        setContent {
            when (widget) {
                "ListBox" -> ListBox(items = listOf("red", "green", "blue"), state = listState)
                "Table" -> Table(rows = people, columns = { column("Name") { it.name } }, state = tableState)
                else -> Tree(model = treeModel("root"), state = treeState)
            }
        }

        when (widget) {
            "ListBox" -> onNodeOfType<JList<*>>().performAddSelection(1)
            "Table" -> onNodeOfType<JTable>().performAddSelection(1)
            else -> onNodeOfType<JTree>().performAddSelection(0)
        }
        awaitIdle()

        listState.selectedIndices = emptySet()
        tableState.selectedRowIndices = emptySet()
        treeState.selectedPaths = emptySet()
        awaitIdle()

        val message = "$widget drops the pick"
        when (widget) {
            "ListBox" -> onNodeOfType<JList<*>>().assertProperty(emptyList(), message) { selectedIndices.toList() }
            "Table" -> onNodeOfType<JTable>().assertProperty(emptyList(), message) { selectedRows.toList() }
            else -> onNodeOfType<JTree>().assertProperty(emptyList(), message) { selectedLabels() }
        }
    }

    @Test
    fun aTableSelectionIsAppliedAscendingWhateverOrderItIsDeclaredIn() = runComposeSwingTest {
        val state = TableState(initialSelectedRowIndices = linkedSetOf(2, 0, 1))
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(0, 1, 2), "the declared rows reach the table") { selectedRows.toList() }
        table.assertProperty(2, "the highest declared row is selected last") { selectionModel.leadSelectionIndex }
    }

    @Test
    fun aListSelectionIsAppliedAscendingWhateverOrderItIsDeclaredIn() = runComposeSwingTest {
        val state = ListState(initialSelectedIndices = linkedSetOf(2, 0, 1))
        setContent {
            ListBox(items = listOf("red", "green", "blue"), state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(0, 1, 2), "the declared rows reach the list") { selectedIndices.toList() }
        list.assertProperty(2, "the highest declared row is selected last") { selectionModel.leadSelectionIndex }
    }

    @Test
    fun aRefusedComboBoxSelectionIsRestored() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = "red",
                onSelectionChange = {},
                modifier = SwingModifier.name(label),
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        combo.selectedIndex = 2
        onNodeOfType<JComboBox<*>>().assertProperty("blue", "the user's choice reaches the widget") { selectedItem }

        label = "second"
        awaitIdle()

        onNodeOfType<JComboBox<*>>().assertProperty("red", "the declared selection is re-applied") { selectedItem }
    }

    @Test
    fun aTreeSelectionMovedFromOneVisibleNodeToAnotherIsAppliedByThePassThatDeclaresIt() = runComposeSwingTest {
        val state = TreeState(initialSelectedPaths = setOf(listOf(0)))
        val model = treeModel("root")
        setContent {
            Tree(model = model, state = state)
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("apple"), "the tree opens on the node it first names") { selectedLabels() }

        state.selectedPaths = setOf(listOf(1))
        mainClock.advanceTimeByFrame()

        tree.assertProperty(
            listOf("pear"),
            "the one pass that declares the new node should already have moved the selection onto it",
        ) { selectedLabels() }
    }

    @Test
    fun everySelectionBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { listItemRenderer(DefaultListCellRenderer()) }
        assertDeclaredChainCarriedOnce { listCellSizing(null, 1, 1) }
        assertDeclaredChainCarriedOnce { listStateBinding(ListState()) }
        assertDeclaredChainCarriedOnce { tableStateBinding(TableState()) }
        assertDeclaredChainCarriedOnce { treeStateBinding(TreeState()) }
        assertDeclaredChainCarriedOnce {
            tableColumnModelListener(ColumnLayoutChannel(MirrorState(null), mutableStateOf(null)).listener)
        }
        assertDeclaredChainCarriedOnce {
            userSelectionListener(MirrorState(null), ListSelectionListener { })
        }
        assertDeclaredChainCarriedOnce {
            treeListeners(TreeSelectionListener { }, mockk<TreeExpansionListener>(relaxed = true), null)
        }
    }
}
