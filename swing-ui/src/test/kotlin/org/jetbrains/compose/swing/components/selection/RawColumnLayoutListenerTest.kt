package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumnModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The raw-listener overloads of [Table] carry the column-layout channel through a
 * `TableColumnModelListener` of the caller's own, the same channel the `onColumnLayoutChange` overloads
 * express as a lambda: a reorder arrives as a column move and a resize as a margin change, each over the
 * columns the gesture left behind, and each only where the layout it left differs from the one the caller
 * and the table already agree on. A layout the user owned and a rebuild of the columns could not hold is
 * reported as what the surviving columns were left with.
 */
class RawColumnLayoutListenerTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    @Test
    fun aListenerThatFailsOnTheColumnLossReportLeavesLaterColumnsApplied() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        var failing by mutableStateOf(false)
        val selection = ListSelectionListener { }
        val listener = mockk<TableColumnModelListener>(relaxed = true)
        every { listener.columnMarginChanged(any()) } answers {
            if (failing) error("the column loss report fails")
        }
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        if (withAge) column("Age") { it.age }
                    },
                    listSelectionListener = selection,
                    tableColumnModelListener = listener,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 1, to = 0)
        awaitIdle()

        // Dropping a column the user's own layout covers is what makes the loss reach this listener.
        failing = true
        withAge = false
        awaitIdle()

        failing = false
        withAge = true
        awaitIdle()

        assertEquals(2, table.columnModel.columnCount, "a later declared column change must still reach the table")
        val failures = takeCallerFailures()
        assertTrue(
            failures.any { "the column loss report fails" in it.message.orEmpty() },
            "the contained failure should be the loss report's own, but was: $failures",
        )
    }

    /**
     * A raw column-model listener recording each event it is handed into [events], as the event's kind
     * paired with the layout the columns were in when it arrived.
     */
    private fun columnLayoutListener(events: MutableList<Pair<String, TableColumnLayout>>): TableColumnModelListener =
        object : TableColumnModelListener {
            override fun columnAdded(event: TableColumnModelEvent) = record("added", event.source)

            override fun columnRemoved(event: TableColumnModelEvent) = record("removed", event.source)

            override fun columnMoved(event: TableColumnModelEvent) = record("moved", event.source)

            override fun columnMarginChanged(event: ChangeEvent) = record("margin", event.source)

            override fun columnSelectionChanged(event: ListSelectionEvent) = record("selection", event.source)

            private fun record(
                kind: String,
                source: Any,
            ) {
                val columns = source as TableColumnModel
                events += kind to TableColumnLayout(columns.modelIndices(), columns.preferredWidths())
            }
        }

    @Test
    fun aReorderIsHandedToTheListenerAsAColumnMove() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    listSelectionListener = selection,
                    tableColumnModelListener = columnLayoutListener(events),
                )
            }
        }

        dragColumn(from = 1, to = 0)
        awaitIdle()

        assertEquals(listOf("moved"), events.map { it.first }, "a reorder should arrive as a column move")
        assertEquals(listOf(1, 0), events.last().second.modelIndices, "the reordered columns should be reported")
    }

    @Test
    fun aResizeIsHandedToTheListenerAsAMarginChange() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    listSelectionListener = selection,
                    tableColumnModelListener = columnLayoutListener(events),
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        dragColumnDivider(position = 0, dx = 100)
        awaitIdle()

        assertTrue(events.all { it.first == "margin" }, "a resize should arrive as margin changes only: $events")
        assertEquals(
            listOf(175, 75),
            events.last().second.preferredWidths,
            "the widths the drag left the columns at should be reported",
        )
        assertEquals(listOf(175, 75), table.columnModel.preferredWidths(), "the user's resize should stand")
        assertEquals(listOf(0, 1), events.last().second.modelIndices, "a resize should leave the order alone")
    }

    @Test
    fun aStructureChangeThatDropsAColumnReportsWhatIsLeftOfTheUsersLayout() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        if (withAge) column("Age") { it.age }
                    },
                    listSelectionListener = selection,
                    tableColumnModelListener = columnLayoutListener(events),
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 1, to = 0)
        awaitIdle()
        events.clear()

        withAge = false
        awaitIdle()

        assertEquals(listOf(0), table.columnModel.modelIndices(), "only the surviving column should be left")
        assertEquals(
            listOf(listOf(0)),
            events.map { it.second.modelIndices },
            "the layout the surviving columns were left holding should reach the listener once",
        )
    }

    @Test
    fun aReorderOfAModelDrivenTableIsHandedToTheListenerAsAColumnMove() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            HeaderPane {
                Table(
                    model = modelWithColumns("Name", "Age", "City"),
                    listSelectionListener = selection,
                    tableColumnModelListener = columnLayoutListener(events),
                )
            }
        }

        dragColumn(from = 2, to = 0)
        awaitIdle()

        assertEquals(
            listOf("moved", "moved"),
            events.map { it.first },
            "a reorder should arrive as one column move per position the drag crossed",
        )
        assertEquals(listOf(2, 0, 1), events.last().second.modelIndices, "the reordered columns should be reported")
    }

    @Test
    fun aColumnTheCallersOwnModelAddsReachesTheListenerAsNoLayoutChangeOfItsOwn() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        val model = modelWithColumns("Name", "Age", "City")
        setContent {
            HeaderPane {
                Table(
                    model = model,
                    listSelectionListener = selection,
                    tableColumnModelListener = columnLayoutListener(events),
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        events.clear()
        // The caller mutates the model it owns, so the table rebuilds its columns from it with no pass of the
        // composition around the rebuild. A column appearing or disappearing is never a gesture of the user's,
        // there being no header drag that adds or removes one.
        model.addColumn("Country")
        awaitIdle()

        assertEquals(4, table.columnModel.columnCount, "the column the caller added should reach the table")
        assertEquals(
            emptyList(),
            events.filter { it.first == "added" || it.first == "removed" },
            "columns the table builds from a model are no layout change of the user's",
        )
    }
}
