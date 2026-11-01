package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JList
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * What a [Table]'s `selectionMode` does to a selection the user made. A selection model narrows a selection
 * only as far as the new mode forces, so a mode that widens keeps the whole selection and a mode that holds
 * one row keeps the first row selected - and a [Table] and a [ListBox] declared alike answer the same mode
 * change alike, both being the same selection model underneath.
 */
class TableSelectionModeTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    private val colors = listOf("red", "green", "blue")

    private fun tableModel(): DefaultTableModel =
        DefaultTableModel(arrayOf<Any>("Name"), 0).apply { for (person in people) addRow(arrayOf<Any>(person.name)) }

    @Test
    fun theDeclaredModeReachesTheRowAndTheColumnSelectionModel() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, selectionMode = mode)
        }

        val table = onNodeOfType<JTable>()
        mode = ListSelectionModel.SINGLE_INTERVAL_SELECTION
        awaitIdle()

        table.assertProperty(
            ListSelectionModel.SINGLE_INTERVAL_SELECTION,
            "the rows should be in the declared selection mode",
        ) { selectionModel.selectionMode }
        table.assertProperty(
            ListSelectionModel.SINGLE_INTERVAL_SELECTION,
            "the columns should be in the declared selection mode",
        ) { columnModel.selectionModel.selectionMode }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tableDeclarations")
    fun aWiderSelectionModeKeepsTheSelectionTheUserMade(
        declare: @Composable TableSelectionModeTest.(Int, (Set<Int>) -> Unit) -> Unit,
    ) = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        val received = mutableListOf<Set<Int>>()
        setContent {
            declare(this@TableSelectionModeTest, mode) { received += it }
        }

        onNodeOfType<JTable>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        mode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "a wider mode takes no row out of the selection",
        ) { selectedRows.toList() }
        assertEquals(emptyList(), received, "a mode change that costs the user nothing has nothing to report")
    }

    @Test
    fun aWiderSelectionModeKeepsADeclaredSelection() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                selectedRowIndices = setOf(1),
                onSelectionChange = { received += it },
                selectionMode = mode,
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(1), "the declared row reaches the table") { selectedRows.toList() }
        val held = table.fetch()
        received.clear()

        mode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()

        assertSame(held, table.fetch(), "a wider mode updates the table in place")
        table.assertProperty(listOf(1), "a wider mode leaves the declared row selected") { selectedRows.toList() }
        assertEquals(emptyList(), received, "re-applying a declaration is the library's own write, not news")
    }

    @Test
    fun aTableAndAListNarrowTheUsersSelectionAlike() = runComposeSwingTest {
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val fromTable = mutableListOf<Set<Int>>()
        val fromList = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                onSelectionChange = { fromTable += it },
                selectionMode = mode,
            )
            ListBox(items = colors, onSelectionChange = { fromList += it }, selectionMode = mode)
        }

        val table = onNodeOfType<JTable>().fetch()
        val list = onNodeOfType<JList<*>>().fetch()
        table.selectionModel.setSelectionInterval(0, 2)
        list.selectionModel.setSelectionInterval(0, 2)
        fromTable.clear()
        fromList.clear()

        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            list.selectedIndices.toList(),
            "a table should keep the row a list keeps",
        ) { selectedRows.toList() }
        onNodeOfType<JTable>().assertProperty(
            listOf(0),
            "the first selected row is the one a single mode holds",
        ) { selectedRows.toList() }
        assertEquals(fromList, fromTable, "a table should report what a list reports")
    }

    companion object {
        @JvmStatic
        fun tableDeclarations(): List<Named<@Composable TableSelectionModeTest.(Int, (Set<Int>) -> Unit) -> Unit>> {
            val rows: @Composable TableSelectionModeTest.(Int, (Set<Int>) -> Unit) -> Unit = { mode, callback ->
                Table(
                    rows = people,
                    columns = { column("Name") { it.name } },
                    onSelectionChange = callback,
                    selectionMode = mode,
                )
            }
            val model: @Composable TableSelectionModeTest.(Int, (Set<Int>) -> Unit) -> Unit = { mode, callback ->
                Table(model = remember { tableModel() }, onSelectionChange = callback, selectionMode = mode)
            }
            return listOf(Named.of("declared rows", rows), Named.of("caller's model", model))
        }
    }
}
