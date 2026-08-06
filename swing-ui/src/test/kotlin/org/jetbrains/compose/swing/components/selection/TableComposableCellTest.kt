package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.TableCellRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for a [Table] column's composable `cellContent`. They prove the rubber-stamp
 * mechanism end to end: stamping a cell through the renderer installed on that column realizes the
 * composable cell into a real Swing subtree, the cell sees the cell it is being stamped for through its
 * [TableCellScope], and a column that declares no cell body renders through the renderer the table picks
 * by the column's class.
 *
 * Each column that declares a cell body gets an island of its own, and a column that stops declaring one
 * gives its island up: a cell body whose column is gone composes no more, and the renderer the table
 * captured stamps the empty cell a disposed island composes.
 *
 * The cell's component lives outside the composition root - it is what the renderer hands the table - so
 * these drive the renderer directly (as `JTable` does when it paints a cell) and inspect what it returns.
 */
class TableComposableCellTest {
    /** Renders the cell at [row]/[column] through the renderer the table picks for it, as painting does. */
    private fun JTable.stampCell(
        row: Int,
        column: Int,
        isSelected: Boolean = false,
        hasFocus: Boolean = false,
    ): Component = getCellRenderer(row, column)
        .getTableCellRendererComponent(this, getValueAt(row, column), isSelected, hasFocus, row, column)

    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    @Test
    fun cellContentRealizesAComposableCellPerCell() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Name") { it.name }
                column(
                    header = "Age",
                    cellContent = { row -> FlowPanel { Label("${row.name}: ${row.age}") } },
                ) { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            "Ada: 36",
            table.stampCell(row = 0, column = 1).firstLabelText(),
            "the composable cell should have realized a JLabel built from the row it belongs to",
        )
        // A single reused component per column restamps for every cell of that column the table paints.
        assertEquals(
            "Alan: 41",
            table.stampCell(row = 1, column = 1).firstLabelText(),
            "the reused cell should restamp the next row",
        )
    }

    @Test
    fun aColumnRendersThroughItsOwnCellsAlone() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Name", cellContent = { row -> Label("<${row.name}>") }) { it.name }
                column("Age", cellContent = { row -> Label("[${row.age}]") }) { it.age }
            }
        }

        // Two columns declaring cells hold an island each, so neither is rebuilt by the other's stamps.
        val table = onNodeOfType<JTable>().fetch()
        val name = table.stampCell(row = 0, column = 0)
        val age = table.stampCell(row = 0, column = 1)
        assertNotSame(name, age, "each column should stamp its cells through a component of its own")
        assertEquals("<Ada>", name.firstLabelText(), "the first column renders its own cell")
        assertEquals("[36]", age.firstLabelText(), "the second column renders its own cell")
        assertEquals(
            "<Ada>",
            table.stampCell(row = 0, column = 0).firstLabelText(),
            "restamping the first column should not have been disturbed by the second",
        )
    }

    @Test
    fun theCellScopeReflectsSelection() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Name", cellContent = { row -> Label(if (isSelected) "${row.name}*" else row.name) }) {
                    it.name
                }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            "Alan",
            table.stampCell(row = 1, column = 0, isSelected = false).firstLabelText(),
            "an unselected stamp should render the plain row",
        )
        assertEquals(
            "Alan*",
            table.stampCell(row = 1, column = 0, isSelected = true).firstLabelText(),
            "a selected stamp should observe isSelected through the TableCellScope",
        )
    }

    @Test
    fun theCellScopeNamesTheRowAndColumnTheTableWasDeclaredWith() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                sortable = true,
                sortKeys = listOf(SortKey(1, SortOrder.DESCENDING)),
            ) {
                column("Name") { it.name }
                column("Age", cellContent = { row -> Label("$rowIndex/$columnIndex ${row.name}") }) { it.age }
            }
        }

        // Sorted by age descending, the oldest row is drawn first; the cell is named by the row and the
        // column the table was declared with, which is what the row handed to the cell body is taken from.
        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            "1/1 Alan",
            table.stampCell(row = 0, column = 1).firstLabelText(),
            "the cell scope should name the declared row, not the row it is drawn at",
        )
    }

    @Test
    fun composableCellsWorkInsideAScrollPane() = runComposeSwingTest {
        // A composable cell island joins the enclosing composition, so it must not inherit the slot
        // attachment of the ScrollPane viewport that hosts the Table - otherwise the cell's own nodes
        // would try to install into that viewport as if the cell were its view.
        setContent {
            ScrollPane {
                content {
                    Table(rows = people, selectedRowIndices = setOf(0)) {
                        column("Name", cellContent = { row -> FlowPanel { Label(row.name) } }) { it.name }
                    }
                }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            "Ada",
            table.stampCell(row = 0, column = 0).firstLabelText(),
            "a composable cell inside a ScrollPane should realize its content, not leak the viewport slot",
        )
    }

    @Test
    fun aColumnWithoutACellBodyRendersThroughTheTablesOwnRenderer() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Name") { it.name }
                column("Age", cellContent = { row -> Label("${row.age}!") }) { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertNull(
            table.columnModel.getColumn(0).cellRenderer,
            "a column declaring no cell body must carry no renderer of its own",
        )
        val ownCell = table.stampCell(row = 0, column = 0)
        assertTrue(ownCell is JLabel, "the table's own renderer stamps a JLabel")
        assertEquals("Ada", (ownCell as JLabel).text, "the table's own renderer renders the cell's value")
    }

    @Test
    fun aCellBodyTakenAwayRendersThroughTheTablesOwnRenderer() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            Table(rows = people) {
                column(
                    header = "Name",
                    cellContent =
                        if (composableCells) {
                            { row -> FlowPanel { Label(row.name) } }
                        } else {
                            null
                        },
                ) { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals("Ada", table.stampCell(row = 0, column = 0).firstLabelText(), "the composable cell renders")

        composableCells = false
        awaitIdle()
        val ownCell = table.stampCell(row = 0, column = 0)
        assertTrue(ownCell is JLabel, "taking the cell body away should stamp the table's own renderer")
        assertEquals("Ada", (ownCell as JLabel).text, "the table's own renderer renders the cell's value")

        composableCells = true
        awaitIdle()
        assertEquals(
            "Alan",
            table.stampCell(row = 1, column = 0).firstLabelText(),
            "declaring a cell body again should stamp the composable cell",
        )
    }

    @Test
    fun aRemovedColumnsCellsComposeNoMore() = runComposeSwingTest {
        var badge by mutableStateOf("draft")
        var showBadge by mutableStateOf(true)
        val composed = mutableListOf<String>()
        setContent {
            Table(rows = people) {
                column("Name") { it.name }
                if (showBadge) {
                    column(
                        header = "Badge",
                        cellContent = { row ->
                            composed += "${row.name} $badge"
                            Label("${row.name} $badge")
                        },
                    ) { badge }
                }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals("Ada draft", table.stampCell(row = 0, column = 1).firstLabelText(), "the badge cell renders")
        val badgeRenderer: TableCellRenderer = table.columnModel.getColumn(1).cellRenderer

        // While the column stands, its cell body observes the state it reads: changing that state composes
        // the cell again without anything asking for a stamp.
        composed.clear()
        badge = "review"
        awaitIdle()
        assertTrue(composed.isNotEmpty(), "a live cell island should compose again when the state it reads moves")

        showBadge = false
        awaitIdle()
        composed.clear()
        badge = "final"
        awaitIdle()
        assertEquals(
            emptyList<String>(),
            composed.toList(),
            "a removed column's cell island must be disposed, so the state it read invalidates nothing",
        )

        val cellAfter =
            badgeRenderer.getTableCellRendererComponent(table, "final", false, false, 0, 0)
        assertNull(
            cellAfter.firstLabelText(),
            "a stamp on the disposed island must render an empty cell rather than a stale one",
        )
    }

    @Test
    fun takingTheCellsAwayLeavesTheCompositionIntact() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        var showTable by mutableStateOf(true)
        setContent {
            if (showTable) {
                Table(rows = people, selectedRowIndices = setOf(0)) {
                    column(
                        header = "Name",
                        cellContent =
                            if (composableCells) {
                                { row -> Label(row.name) }
                            } else {
                                null
                            },
                    ) { it.name }
                }
            }
        }

        composableCells = false
        awaitIdle()

        // Disposing a cell island must touch nothing of the composition it was mounted from. Discarding the
        // table afterwards rewrites the very slots the disposal ran from, and disposing the composition at
        // the end of the test rewrites all of them, so both fail outright if the disposal disturbed either.
        showTable = false
        awaitIdle()
        onAllNodesOfType<JTable>().assertCountEquals(0)
    }
}
