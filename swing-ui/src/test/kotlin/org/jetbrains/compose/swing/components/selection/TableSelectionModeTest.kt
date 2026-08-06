package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a [Table]'s `selectionMode` does to a selection the user made. A selection model narrows a selection
 * only as far as the new mode forces, so a mode that widens keeps the whole selection and a mode that holds
 * one row keeps the first row selected - and a [Table] and a [ListBox] declared alike answer the same mode
 * change alike, both being the same selection model underneath.
 */
class TableSelectionModeTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    private val colours = listOf("red", "green", "blue")

    private fun tableModel(): DefaultTableModel =
        DefaultTableModel(arrayOf<Any>("Name"), 0).apply { for (person in people) addRow(arrayOf<Any>(person.name)) }

    @Test
    fun theDeclaredModeReachesTheRowAndTheColumnSelectionModel() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        setContent {
            Table(rows = people, selectionMode = mode) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        mode = ListSelectionModel.SINGLE_INTERVAL_SELECTION
        awaitIdle()

        assertEquals(
            ListSelectionModel.SINGLE_INTERVAL_SELECTION,
            table.selectionModel.selectionMode,
            "the rows should be in the declared selection mode",
        )
        assertEquals(
            ListSelectionModel.SINGLE_INTERVAL_SELECTION,
            table.columnModel.selectionModel.selectionMode,
            "the columns should be in the declared selection mode",
        )
    }

    @Test
    fun aWiderSelectionModeKeepsTheSelectionTheUserMade() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = people, onSelectionChange = { received += it }, selectionMode = mode) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)
        received.clear()

        mode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "a wider mode takes no row out of the selection")
        assertEquals(emptyList(), received, "a mode change that costs the user nothing has nothing to report")
    }

    @Test
    fun aWiderSelectionModeKeepsTheSelectionTheUserMadeOnAModelDrivenTable() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        val received = mutableListOf<Set<Int>>()
        val model = tableModel()
        setContent {
            Table(model = model, onSelectionChange = { received += it }, selectionMode = mode)
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 1)
        received.clear()

        mode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "a wider mode takes no row out of the selection")
        assertEquals(emptyList(), received, "a mode change that costs the user nothing has nothing to report")
    }

    @Test
    fun aWiderSelectionModeKeepsADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = people,
                selectedRowIndices = setOf(1),
                onSelectionChange = { received += it },
                selectionMode = mode,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1), table.selectedRows.toList(), "the declared row reaches the table")
        received.clear()

        mode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "a wider mode leaves the declared row selected")
        assertEquals(emptyList(), received, "re-applying a declaration is the library's own write, not news")
    }

    @Test
    fun aTableAndAListNarrowTheUsersSelectionAlike() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val fromTable = mutableListOf<Set<Int>>()
        val fromList = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = people, onSelectionChange = { fromTable += it }, selectionMode = mode) {
                column("Name") { it.name }
            }
            ListBox(items = colours, onSelectionChange = { fromList += it }, selectionMode = mode)
        }

        val table = onNodeOfType<JTable>().fetch()
        val list = onNodeOfType<JList<*>>().fetch()
        table.selectionModel.setSelectionInterval(0, 2)
        list.selectionModel.setSelectionInterval(0, 2)
        fromTable.clear()
        fromList.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        assertEquals(
            list.selectedIndices.toList(),
            table.selectedRows.toList(),
            "a table should keep the row a list keeps",
        )
        assertEquals(listOf(0), table.selectedRows.toList(), "the first selected row is the one a single mode holds")
        assertEquals(fromList, fromTable, "a table should report what a list reports")
    }
}
