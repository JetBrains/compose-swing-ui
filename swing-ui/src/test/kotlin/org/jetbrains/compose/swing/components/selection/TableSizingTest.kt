package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What decides how much room a [Table] gives its rows and its columns: the height every row is drawn at,
 * how the columns share out a change to the table's width, whether the table stretches to the viewport
 * showing it, and the widths each column may be left at.
 *
 * A table never measures a row by what its cells ask for, so these are what make room for a cell taller or
 * wider than the text a table sizes itself for. Each is declared state and is applied whenever the
 * declaration moves; an undeclared row height stays the look and feel's to choose.
 */
class TableSizingTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    @Test
    fun undeclaredSizingLeavesTheTablesOwnDefaults() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val own = JTable()
        assertEquals(own.rowHeight, table.rowHeight, "the row height should be a JTable's own")
        assertEquals(own.autoResizeMode, table.autoResizeMode, "the auto-resize mode should be a JTable's own")
        assertEquals(
            own.fillsViewportHeight,
            table.fillsViewportHeight,
            "the viewport fill should be a JTable's own",
        )
        val ownColumn = JTable(DefaultTableModel(1, 1)).columnModel.getColumn(0)
        assertEquals(ownColumn.minWidth, table.columnModel.getColumn(0).minWidth, "a column's own minimum width")
        assertEquals(ownColumn.maxWidth, table.columnModel.getColumn(0).maxWidth, "a column's own maximum width")
    }

    @Test
    fun theSizingParametersAreReAppliedOnRecomposition() = runComposeSwingTest {
        var rowHeight: Int? by mutableStateOf(24)
        var autoResizeMode by mutableStateOf(JTable.AUTO_RESIZE_ALL_COLUMNS)
        var fillsViewportHeight by mutableStateOf(true)
        setContent {
            Table(
                rows = people,
                rowHeight = rowHeight,
                autoResizeMode = autoResizeMode,
                fillsViewportHeight = fillsViewportHeight,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(24, table.rowHeight, "the declared row height should reach the table")
        assertEquals(JTable.AUTO_RESIZE_ALL_COLUMNS, table.autoResizeMode, "the declared auto-resize mode too")
        assertTrue(table.fillsViewportHeight, "and the declared viewport fill")

        rowHeight = 40
        autoResizeMode = JTable.AUTO_RESIZE_OFF
        fillsViewportHeight = false
        awaitIdle()
        assertEquals(40, table.rowHeight, "a changed row height should be re-applied")
        assertEquals(JTable.AUTO_RESIZE_OFF, table.autoResizeMode, "a changed auto-resize mode should be re-applied")
        assertFalse(table.fillsViewportHeight, "a changed viewport fill should be re-applied")
    }

    @Test
    fun aWithdrawnRowHeightGoesBackToTheLookAndFeelsOwn() = runComposeSwingTest {
        var declared: Int? by mutableStateOf(null)
        setContent {
            Table(rows = people, rowHeight = declared) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        // The height the look and feel gave this table, whatever the host's happens to be. Declaring a
        // different one is what lets the withdrawal tell the two apart: a fixed constant would match
        // the look and feel's own height on some host, and both assertions would pass vacuously.
        val ownHeight = table.rowHeight

        declared = ownHeight + 9
        awaitIdle()
        assertEquals(ownHeight + 9, table.rowHeight, "a declared row height should be applied")

        declared = null
        awaitIdle()
        assertEquals(
            ownHeight,
            table.rowHeight,
            "withdrawing the declaration should give the look and feel's own height back",
        )
    }

    @Test
    fun theDeclaredWidthsBoundTheirColumn() = runComposeSwingTest {
        var minWidth by mutableStateOf(80)
        var maxWidth by mutableStateOf(200)
        setContent {
            Table(rows = people) {
                column("Name", minWidth = minWidth, maxWidth = maxWidth) { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val column = table.columnModel.getColumn(0)
        assertEquals(80, column.minWidth, "the declared minimum width should reach the column")
        assertEquals(200, column.maxWidth, "the declared maximum width should reach the column")

        // Both widths move at once, and a column holds each inside the other, so a pass that widens the
        // range has to leave the column on both declared widths rather than on one clamped by the other.
        minWidth = 300
        maxWidth = 400
        awaitIdle()
        assertEquals(300, table.columnModel.getColumn(0).minWidth, "a changed minimum width should be re-applied")
        assertEquals(400, table.columnModel.getColumn(0).maxWidth, "a changed maximum width should be re-applied")

        minWidth = 20
        maxWidth = 60
        awaitIdle()
        assertEquals(20, table.columnModel.getColumn(0).minWidth, "a narrowed minimum width should be re-applied")
        assertEquals(60, table.columnModel.getColumn(0).maxWidth, "a narrowed maximum width should be re-applied")
    }

    @Test
    fun aColumnIsNeverLeftOutsideItsDeclaredWidths() = runComposeSwingTest {
        setContent {
            Table(rows = people, columnLayout = TableColumnLayout(listOf(0), listOf(500))) {
                column("Name", maxWidth = 120) { it.name }
            }
        }

        // A declared layout is put back after the widths that bound the column, so the width it asks for is
        // the one the column can actually hold.
        val table = onNodeOfType<JTable>().fetch()
        assertEquals(120, table.columnModel.getColumn(0).preferredWidth, "a preferred width beyond the maximum")
    }
}
