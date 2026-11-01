package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A parked [ReusableContentHost] child detaches its `JTable`, and reactivation builds a fresh one from
 * the node's own factory. The column-driven [Table] fills a model with the declared rows and columns on
 * every pass, so the model that composition drives has to be the one the fresh table renders.
 *
 * A controlled selection reaches the fresh table the same way it reaches any freshly composed one.
 *
 * A column's composable cell is owned by the composition too: the cell composition rendering it lives
 * only while the node is in the composition, so a table that outlives its own composable cell - parked
 * before it is torn down - paints that column through the renderer it picks by the column's class, and
 * the fresh table reactivation builds renders the composable cell again.
 */
class TableNodeReuseTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    /** A caller-owned model holding the first [rowCount] of [people], one column per field. */
    private fun tableModel(rowCount: Int = people.size): DefaultTableModel {
        val rows = people.take(rowCount).map { arrayOf<Any>(it.name, it.age) }
        return DefaultTableModel(rows.toTypedArray(), arrayOf<Any>("Name", "Age"))
    }

    /** Renders the cell at [row] of the first column through the renderer the table picks, as painting does. */
    private fun JTable.renderCell(row: Int): Component = getCellRenderer(row, 0)
        .getTableCellRendererComponent(this, getValueAt(row, 0), false, false, row, 0)

    @Test
    fun aReactivatedTableRendersRowsDeclaredAfterReactivation() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var rows by mutableStateOf(listOf(Person("Ada", 36)))
        setContent {
            ReusableContentHost(active = active) {
                Table(
                    rows = rows,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                )
            }
        }
        val table = onNodeOfType<JTable>()
        table.assertProperty(1, "the table should start on the declared row") { model.rowCount }

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        table.assertProperty(2, "the fresh table should present both columns") { model.columnCount }
        table.assertProperty("Ada", "the fresh table should render the declared row") { model.getValueAt(0, 0) }

        rows = rows + Person("Alan", 41)
        awaitIdle()

        table.assertProperty(2, "a row added after reactivation should reach the rendered model") { model.rowCount }
        table.assertProperty("Alan", "the added row should render through the declared columns") {
            model.getValueAt(1, 0)
        }
    }

    @Test
    fun aReactivatedTableStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                Table(
                    rows = people,
                    columns = { column("Name") { it.name } },
                    selectedRowIndices = setOf(1),
                )
            }
        }
        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "the controlled selection should be applied",
        ) { selectedRows.toList() }

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "the fresh table should hold the controlled selection",
        ) { selectedRows.toList() }
    }

    @Test
    fun aReactivatedModelDrivenTableStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val model = tableModel()
        setContent {
            ReusableContentHost(active = active) {
                Table(model = model, selectedRowIndices = setOf(1))
            }
        }
        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "the controlled selection should be applied",
        ) { selectedRows.toList() }

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "the fresh model-driven table should hold the controlled selection",
        ) { selectedRows.toList() }
    }

    @Test
    fun aParkedTableRendersItsOwnCellsAndTheFreshOneRendersTheComposableCellAgain() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                Table(
                    rows = people,
                    columns = { column(header = "Name", cellContent = { row -> Label("<${row.name}>") }) { it.name } },
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTable>().assertProperty(
            "<Ada>",
            "the composable cell should render row 0",
        ) { renderCell(row = 0).firstLabelText() }

        active = false
        awaitIdle()

        val parked = table.renderCell(row = 0)
        assertTrue(parked is JLabel, "a parked table should render a column through the renderer it picks by class")
        assertEquals("Ada", (parked as JLabel).text, "that renderer renders the cell value's toString")

        active = true
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            "<Alan>",
            "the fresh table should render the composable cell",
        ) { renderCell(row = 1).firstLabelText() }
    }
}
