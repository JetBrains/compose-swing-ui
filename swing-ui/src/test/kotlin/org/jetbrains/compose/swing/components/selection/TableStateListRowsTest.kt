package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.TracedTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [Table] declared over a list the caller keeps and mutates, rather than one rebuilt each pass.
 *
 * A `JTable` repaints what a `TableModel` tells it changed. A model that answers a caller's list
 * directly reports the new contents to anything that asks, so a row count read back is no evidence
 * the table was told: these tests assert on the events the model fires.
 *
 * The events are asserted whole, since which rows a change names is what the table repaints and what its
 * selection is carried across: an event spanning every row costs a full repaint and empties the selection.
 *
 * The event is no evidence the widget was left alone either. A pass that swapped the model wholesale, or
 * rebuilt the table outright, would fire an equivalent event and read back the same rows - while losing
 * the table's selection, its column widths and its sort keys. That the row reaches the table through the
 * model is stated by the churn the applier reports: no component taken in or out. The settlements such a
 * pass reports are the table being reconciled with the properties the user can move too, which is
 * something every pass over a standing table does, so they are read past.
 */
class TableStateListRowsTest : TracedTest() {
    @Test
    fun addingToADeclaredStateListNotifiesTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        val events = recordEvents(table.fetch())
        // Building the table is churn, and reading it here is what says the recording below is a silence
        // rather than a channel that was never listening.
        val mountChurn = tracer.passes().flatten().filterNot { it == SETTLE }
        assertTrue(mountChurn.isNotEmpty(), "mounting the content should have taken the table into the tree")
        tracer.clear()

        rows.add(Person("Alan", 41))
        awaitIdle()

        table.assertProperty(2, "the model should hold both rows") { model.rowCount }
        assertEquals(listOf("insert 1..1"), events.describe(), "the appended row is what changed")
        assertEquals(
            emptyList(),
            tracer.passes().flatten().filterNot { it == SETTLE },
            "the row should reach the table through the model, with no pass rebuilding or re-adding the " +
                "table itself: ${tracer.sections}",
        )
    }

    @Test
    fun removingFromADeclaredStateListNotifiesTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        val tableBefore = table.fetch()
        val events = recordEvents(table.fetch())

        rows.removeAt(0)
        awaitIdle()
        assertSame(tableBefore, table.fetch(), "the component is updated in place, not rebuilt")

        table.assertProperty(1, "the model should hold the remaining row") { model.rowCount }
        assertEquals(listOf("delete 0..0"), events.describe(), "the removed row is what changed")
    }

    @Test
    fun replacingARowInADeclaredStateListNotifiesTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        val tableBefore = table.fetch()
        val events = recordEvents(table.fetch())

        rows[0] = Person("Alan", 41)
        awaitIdle()
        assertSame(tableBefore, table.fetch(), "the component is updated in place, not rebuilt")

        table.assertProperty(
            "Alan",
            "the model should hold the replacing row",
        ) { model.getValueAt(0, 0) }
        assertEquals(listOf("update 0..0"), events.describe(), "the replaced row is what changed")
    }

    @Test
    fun insertingIntoADeclaredStateListNamesTheInsertedRow() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Grace", 45))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        val tableBefore = table.fetch()
        val events = recordEvents(table.fetch())

        rows.add(1, Person("Alan", 41))
        awaitIdle()
        assertSame(tableBefore, table.fetch(), "the component is updated in place, not rebuilt")

        table.assertProperty(3, "the model should hold all three rows") { model.rowCount }
        assertEquals(listOf("insert 1..1"), events.describe(), "the inserted row is what changed")
    }

    @Test
    fun aWholesaleChangeOfADeclaredStateListNotifiesTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        val events = recordEvents(table.fetch())

        rows.clear()
        rows += listOf(Person("Marie", 66), Person("Rosalind", 37))
        awaitIdle()

        table.assertProperty(2, "the model should hold the new rows") { model.rowCount }
        assertEquals(
            listOf("update 0..${Int.MAX_VALUE}"),
            events.describe(),
            "a change no run of kept rows describes spans the whole table",
        )
    }

    @Test
    fun theModelAnswersOutOfTheRowsItReportedUntilAPassCarriesTheChange() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val table = onNodeOfType<JTable>()
        val tableBefore = table.fetch()
        table.assertProperty(2, "the table should start out showing both rows") { model.rowCount }

        // The caller drops a row, and no pass has carried that yet. A model reading the caller's list
        // would report one row here while the table still paints two, so a repaint arriving before the
        // pass reads past the end of what it was told.
        rows.removeAt(1)
        awaitIdle()

        table.assertProperty(
            2,
            "the model must report the rows the table was last told about, not the caller's list",
        ) { model.rowCount }
        table.assertProperty("Alan", "the dropped row must still be readable") { model.getValueAt(1, 0) }

        mainClock.advanceTimeByFrame()
        assertSame(tableBefore, table.fetch(), "the component is updated in place, not rebuilt")

        table.assertProperty(1, "the pass carrying the change should leave the model with one row") { model.rowCount }
    }

    @Test
    fun aPassThatChangesNoRowNotifiesNothing() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        var rowHeight by mutableStateOf(20)
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } }, rowHeight = rowHeight)
        }

        val table = onNodeOfType<JTable>().fetch()
        val events = recordEvents(table)

        rowHeight = 24
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(24, "the pass this asserts about has to have run") { this.rowHeight }
        assertTrue(events.isEmpty(), "a pass that changed no row should leave the table alone")
    }

    /** Collects every change [table]'s model reports from here on. */
    private fun recordEvents(table: JTable): List<TableModelEvent> {
        val events = mutableListOf<TableModelEvent>()
        table.model.addTableModelListener(TableModelListener { events += it })
        return events
    }

    /** Each recorded event as its type and the rows it names, in arrival order. */
    private fun List<TableModelEvent>.describe(): List<String> =
        map { "${it.typeName()} ${it.firstRow}..${it.lastRow}" }

    /** The kind of change [this] reports. */
    private fun TableModelEvent.typeName(): String = when (type) {
        TableModelEvent.INSERT -> "insert"
        TableModelEvent.DELETE -> "delete"
        else -> "update"
    }

    private companion object {
        /** The section reconciling a property the user can move too, which is not a component moving. */
        const val SETTLE = "settle"
    }
}
