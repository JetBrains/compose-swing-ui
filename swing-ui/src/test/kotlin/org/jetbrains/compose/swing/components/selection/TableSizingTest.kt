package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

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
            Table(rows = people, columns = { column("Name") { it.name } })
        }

        val rows = people.map { arrayOf<Any?>(it.name) }.toTypedArray()
        onNodeOfType<JTable>().assertTreeMatches(JTable(DefaultTableModel(rows, arrayOf<Any>("Name"))))
    }

    @Test
    fun theSizingParametersAreReAppliedOnRecomposition() = runComposeSwingTest {
        var rowHeight: Int? by mutableStateOf(24)
        var autoResizeMode by mutableStateOf(JTable.AUTO_RESIZE_ALL_COLUMNS)
        var fillsViewportHeight by mutableStateOf(true)
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                rowHeight = rowHeight,
                autoResizeMode = autoResizeMode,
                fillsViewportHeight = fillsViewportHeight,
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(24, "the declared row height should reach the table") { this.rowHeight }
        table.assertProperty(JTable.AUTO_RESIZE_ALL_COLUMNS, "the declared auto-resize mode too") {
            this.autoResizeMode
        }
        table.assertProperty(true, "and the declared viewport fill") { this.fillsViewportHeight }

        rowHeight = 40
        autoResizeMode = JTable.AUTO_RESIZE_OFF
        fillsViewportHeight = false
        awaitIdle()
        table.assertProperty(40, "a changed row height should be re-applied") { this.rowHeight }
        table.assertProperty(JTable.AUTO_RESIZE_OFF, "a changed auto-resize mode should be re-applied") {
            this.autoResizeMode
        }
        table.assertProperty(false, "a changed viewport fill should be re-applied") { this.fillsViewportHeight }
    }

    @Test
    fun aWithdrawnRowHeightGoesBackToTheLookAndFeelsOwn() = runComposeSwingTest {
        var declared: Int? by mutableStateOf(null)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, rowHeight = declared)
        }

        // The height the look and feel gave this table, whatever the host's happens to be. Declaring a
        // different one is what lets the withdrawal tell the two apart, so the declared height is asserted
        // away from it: on a host whose own height matched, both assertions below would pass vacuously.
        val ownHeight = onNodeOfType<JTable>().fetch().rowHeight

        val declaredHeight = 31
        assertNotEquals(declaredHeight, ownHeight, "the declared height must differ from the look and feel's")
        declared = declaredHeight
        awaitIdle()
        onNodeOfType<JTable>().assertProperty(declaredHeight, "a declared row height should be applied") { rowHeight }

        declared = null
        awaitIdle()
        onNodeOfType<JTable>().assertProperty(
            ownHeight,
            "withdrawing the declaration should give the look and feel's own height back",
        ) { rowHeight }
    }

    @Test
    fun theDeclaredWidthsBoundTheirColumn() = runComposeSwingTest {
        var minWidth by mutableStateOf(80)
        var maxWidth by mutableStateOf(200)
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name", minWidth = minWidth, maxWidth = maxWidth) { it.name }
                    column("Age") { it.age }
                },
            )
        }

        val table = onNodeOfType<JTable>()
        val tableBefore = table.fetch()
        table.assertProperty(
            80,
            "the declared minimum width should reach the column",
        ) { columnModel.getColumn(0).minWidth }
        table.assertProperty(
            200,
            "the declared maximum width should reach the column",
        ) { columnModel.getColumn(0).maxWidth }

        // Both widths move at once, and a column holds each inside the other, so a pass that widens the
        // range has to leave the column on both declared widths rather than on one clamped by the other.
        minWidth = 300
        maxWidth = 400
        awaitIdle()
        table.assertProperty(
            300,
            "a changed minimum width should be re-applied",
        ) { columnModel.getColumn(0).minWidth }
        table.assertProperty(
            400,
            "a changed maximum width should be re-applied",
        ) { columnModel.getColumn(0).maxWidth }

        minWidth = 20
        maxWidth = 60
        awaitIdle()
        assertSame(tableBefore, table.fetch(), "the component is updated in place, not rebuilt")
        table.assertProperty(
            20,
            "a narrowed minimum width should be re-applied",
        ) { columnModel.getColumn(0).minWidth }
        table.assertProperty(
            60,
            "a narrowed maximum width should be re-applied",
        ) { columnModel.getColumn(0).maxWidth }
    }

    @Test
    fun aColumnIsNeverLeftOutsideItsDeclaredWidths() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = { column("Name", maxWidth = 120) { it.name } },
                state = rememberTableState(initialColumnLayout = TableColumnLayout(listOf(0), listOf(500))),
            )
        }

        // A state's layout is put back after the widths that bound the column, so the width it asks for is
        // the one the column can actually hold.
        onNodeOfType<JTable>().assertProperty(
            120,
            "a preferred width beyond the maximum",
        ) { columnModel.getColumn(0).preferredWidth }
    }
}
