package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A table node is recyclable: a parked [ReusableContentHost] child is reactivated onto the `JTable` the
 * node already holds, and the node's factory does not run a second time. The column-driven [Table]
 * fills a model with the declared rows and columns on every pass, so the model that composition drives
 * has to be the one the live table renders - otherwise every declaration after a reactivation lands in
 * a model nothing displays.
 *
 * A reactivation re-applies every declared parameter onto a table that is already holding a selection, so
 * it is where the two owners of a selection pull hardest against each other. A declared selection is the
 * composition's state and is re-asserted; an undeclared one is the user's and reaching the same
 * declarations by way of a reactivation is not a reason for it to disappear. Where the content the
 * reactivation brings really cannot hold it, what is left of it reaches the caller all the same - the
 * listeners a modifier installs are detached while a node is parked, so a loss reported through the widget's
 * own event would be lost with them.
 */
class TableNodeReuseTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    /** A caller-owned model holding the first [rowCount] of [people], one column per field. */
    private fun tableModel(rowCount: Int = people.size): DefaultTableModel {
        val rows = people.take(rowCount).map { arrayOf<Any>(it.name, it.age) }
        return DefaultTableModel(rows.toTypedArray(), arrayOf<Any>("Name", "Age"))
    }

    /** The model index of each of [this] table's view columns, left to right. */
    private fun JTable.modelIndices(): List<Int> =
        (0 until columnModel.columnCount).map { columnModel.getColumn(it).modelIndex }

    /** Reorders the columns as a header drag does, by moving the column at [from] to [to]. */
    private fun JTable.dragColumn(
        from: Int,
        to: Int,
    ) = columnModel.moveColumn(from, to)

    @Test
    fun aReactivatedTableRendersRowsDeclaredAfterReactivation() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var rows by mutableStateOf(listOf(Person("Ada", 36)))
        setContent {
            ReusableContentHost(active = active) {
                Table(rows = rows) {
                    column("Name") { it.name }
                    column("Age") { it.age }
                }
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        assertEquals(1, table.model.rowCount, "the table should start on the declared row")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val reactivated = onNodeOfType<JTable>().fetch().model
        assertEquals(2, reactivated.columnCount, "the reactivated table should still present both columns")
        assertEquals("Ada", reactivated.getValueAt(0, 0), "the reactivated table should still render the row")

        rows = rows + Person("Alan", 41)
        awaitIdle()

        val updated = onNodeOfType<JTable>().fetch().model
        assertEquals(2, updated.rowCount, "a row added after reactivation should reach the rendered model")
        assertEquals("Alan", updated.getValueAt(1, 0), "the added row should render through the declared columns")
    }

    @Test
    fun aReactivatedTableStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                Table(
                    rows = people,
                    selectedRowIndices = listOf(1),
                ) {
                    column("Name") { it.name }
                }
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1), table.selectedRows.toList(), "the controlled selection should be applied")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JTable>().fetch().selectedRows.toList(),
            "a reactivated table should still hold the controlled selection",
        )
    }

    @Test
    fun aReactivatedModelDrivenTableStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val model = tableModel()
        setContent {
            ReusableContentHost(active = active) {
                Table(model = model, selectedRowIndices = listOf(1))
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1), table.selectedRows.toList(), "the controlled selection should be applied")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JTable>().fetch().selectedRows.toList(),
            "a reactivated model-driven table should still hold the controlled selection",
        )
    }

    @Test
    fun aReactivatedTableKeepsTheSelectionTheUserMade() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                Table(rows = people, onSelectionChange = { received += it }) {
                    column("Name") { it.name }
                }
            }
        }
        onNodeOfType<JTable>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JTable>().fetch().selectedRows.toList(),
            "an undeclared selection should survive a reactivation",
        )
        assertEquals(emptyList(), received, "a reactivation that keeps the selection has nothing to report")
    }

    @Test
    fun aReactivatedTableKeepsTheColumnOrderTheUserPutItIn() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                Table(rows = people) {
                    column("Name") { it.name }
                    column("Age") { it.age }
                }
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(0, 1)
        assertEquals(listOf(1, 0), table.modelIndices(), "the drag should reorder the columns")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1, 0),
            onNodeOfType<JTable>().fetch().modelIndices(),
            "an undeclared column order should survive a reactivation",
        )
    }

    @Test
    fun aReactivationTooFewRowsToHoldTheUsersSelectionReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var rows by mutableStateOf(people)
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                Table(rows = rows, onSelectionChange = { received += it }) {
                    column("Name") { it.name }
                }
            }
        }
        onNodeOfType<JTable>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        rows = people.take(1)
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            emptyList(),
            onNodeOfType<JTable>().fetch().selectedRows.toList(),
            "the row the new rows cannot hold leaves the selection",
        )
        assertEquals(listOf(emptyList()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithAModelTooSmallToHoldTheUsersSelectionReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var model by mutableStateOf(tableModel())
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                Table(model = model, onSelectionChange = { received += it })
            }
        }
        onNodeOfType<JTable>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        model = tableModel(rowCount = 1)
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            emptyList(),
            onNodeOfType<JTable>().fetch().selectedRows.toList(),
            "the row the new model cannot hold leaves the selection",
        )
        assertEquals(listOf(emptyList()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationThatSwapsTheModelKeepsTheColumnOrderTheUserPutItIn() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var model by mutableStateOf(tableModel())
        setContent {
            ReusableContentHost(active = active) {
                Table(model = model)
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(0, 1)
        assertEquals(listOf(1, 0), table.modelIndices(), "the drag should reorder the columns")

        active = false
        awaitIdle()
        // A fresh model instance is what rebuilds the columns, so the reactivation is the pass that has to
        // carry the user's order across the rebuild.
        model = tableModel()
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1, 0),
            onNodeOfType<JTable>().fetch().modelIndices(),
            "an undeclared column order should survive a reactivation that swaps the model",
        )
    }

    @Test
    fun aReactivatedModelDrivenTableKeepsTheSelectionTheUserMade() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val received = mutableListOf<List<Int>>()
        val model = tableModel()
        setContent {
            ReusableContentHost(active = active) {
                Table(model = model, onSelectionChange = { received += it })
            }
        }
        onNodeOfType<JTable>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JTable>().fetch().selectedRows.toList(),
            "an undeclared selection should survive a reactivation of a model-driven table",
        )
        assertEquals(emptyList(), received, "a reactivation that keeps the selection has nothing to report")
    }

    @Test
    fun aReactivationWithANarrowerModeReportsTheSelectionItLeaves() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                Table(rows = people, onSelectionChange = { received += it }, selectionMode = mode) {
                    column("Name") { it.name }
                }
            }
        }
        onNodeOfType<JTable>().fetch().selectionModel.setSelectionInterval(0, 1)
        received.clear()

        // A mode narrowed while the node is parked reaches the table on the reactivation pass, which applies
        // it before the modifier reinstalls the listeners the table reports through.
        active = false
        awaitIdle()
        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(0),
            onNodeOfType<JTable>().fetch().selectedRows.toList(),
            "the row the narrower mode still holds stays selected",
        )
        assertEquals(listOf(listOf(0)), received, "the selection the reactivation left should be reported once")
    }
}
