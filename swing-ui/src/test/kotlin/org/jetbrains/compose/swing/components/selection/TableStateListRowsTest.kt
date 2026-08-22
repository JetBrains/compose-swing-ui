package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Table] declared over a list the caller keeps and mutates, rather than one rebuilt each pass.
 *
 * A `JTable` repaints what a `TableModel` tells it changed. A model that answers a caller's list
 * directly reports the new contents to anything that asks, so a row count read back is no evidence
 * the table was told: these tests assert on the events the model fires.
 */
class TableStateListRowsTest {
    @Test
    fun addingToADeclaredStateListNotifiesTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        setContent {
            Table(rows = rows) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val events = mutableListOf<TableModelEvent>()
        table.model.addTableModelListener(TableModelListener { events += it })

        rows.add(Person("Alan", 41))
        awaitIdle()

        assertEquals(2, table.model.rowCount, "the model should hold both rows")
        assertTrue(
            events.isNotEmpty(),
            "the table was never told its rows changed, so it will not repaint them",
        )
    }

    @Test
    fun removingFromADeclaredStateListNotifiesTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(rows = rows) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val events = mutableListOf<TableModelEvent>()
        table.model.addTableModelListener(TableModelListener { events += it })

        rows.removeAt(0)
        awaitIdle()

        assertEquals(1, table.model.rowCount, "the model should hold the remaining row")
        assertTrue(
            events.isNotEmpty(),
            "the table was never told its rows changed, so it will not repaint them",
        )
    }

    @Test
    fun replacingARowInADeclaredStateListNotifiesTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        setContent {
            Table(rows = rows) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val events = mutableListOf<TableModelEvent>()
        table.model.addTableModelListener(TableModelListener { events += it })

        rows[0] = Person("Alan", 41)
        awaitIdle()

        assertEquals("Alan", table.model.getValueAt(0, 0), "the model should hold the replacing row")
        assertTrue(
            events.isNotEmpty(),
            "the table was never told its rows changed, so it will not repaint them",
        )
    }

    @Test
    fun theModelAnswersOutOfTheRowsItReportedUntilAPassCarriesTheChange() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(rows = rows) {
                column("Name") { it.name }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(2, table.model.rowCount, "the table should start out showing both rows")

        // The caller drops a row, and no pass has carried that yet. A model reading the caller's list
        // would report one row here while the table still paints two, so a repaint arriving before the
        // pass reads past the end of what it was told.
        rows.removeAt(1)
        awaitIdle()

        assertEquals(
            2,
            table.model.rowCount,
            "the model must report the rows the table was last told about, not the caller's list",
        )
        assertEquals("Alan", table.model.getValueAt(1, 0), "the dropped row must still be readable")

        mainClock.advanceTimeByFrame()

        assertEquals(1, table.model.rowCount, "the pass carrying the change should leave the model with one row")
    }

    @Test
    fun aPassThatChangesNoRowNotifiesNothing() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        var rowHeight by mutableStateOf(20)
        setContent {
            Table(rows = rows, rowHeight = rowHeight) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val events = mutableListOf<TableModelEvent>()
        table.model.addTableModelListener(TableModelListener { events += it })

        rowHeight = 24
        awaitIdle()

        assertEquals(24, table.rowHeight, "the pass this asserts about has to have run")
        assertTrue(events.isEmpty(), "a pass that changed no row should leave the table alone")
    }
}
