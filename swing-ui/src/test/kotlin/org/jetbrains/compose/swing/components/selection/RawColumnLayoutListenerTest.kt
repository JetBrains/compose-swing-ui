package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumnModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The raw-listener overloads of [Table] carry the column-layout channel through a
 * `TableColumnModelListener` of the caller's own, the same channel the `onColumnLayoutChange` overloads
 * express as a lambda: a reorder arrives as a column move and a resize as a margin change, each over the
 * columns the gesture left behind, and each only where the layout it left differs from the one the caller
 * and the table already agree on. A layout the caller declared is re-asserted on every pass without
 * reaching the listener at all, while a layout the user owned and a rebuild of the columns could not hold
 * is reported as what the surviving columns were left with.
 *
 * Headless caveat: no native peer realizes, so a header drag is driven where the look and feel drives it -
 * a reorder through the column model's `moveColumn`, a resize through the header's resizing column and the
 * table's own layout pass, which is what turns a dragged width into the preferred width that outlives it.
 */
class RawColumnLayoutListenerTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    /** The model index of each of [this] model's view columns, left to right. */
    private fun TableColumnModel.modelIndices(): List<Int> = (0 until columnCount).map { getColumn(it).modelIndex }

    /** The preferred width of each of [this] model's view columns, left to right. */
    private fun TableColumnModel.preferredWidths(): List<Int> =
        (0 until columnCount).map { getColumn(it).preferredWidth }

    private fun tableModel(vararg columns: String): DefaultTableModel =
        DefaultTableModel(arrayOf(arrayOf<Any?>("a", "b", "c")), Array<Any?>(columns.size) { columns[it] })

    @Test
    fun aListenerThatFailsOnTheColumnLossReportLeavesLaterColumnsApplied() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        var failing by mutableStateOf(false)
        val selection = ListSelectionListener { }
        val listener =
            object : TableColumnModelListener {
                override fun columnAdded(event: TableColumnModelEvent) = Unit

                override fun columnRemoved(event: TableColumnModelEvent) = Unit

                override fun columnMoved(event: TableColumnModelEvent) = Unit

                override fun columnMarginChanged(event: ChangeEvent) {
                    if (failing) error("the column loss report fails")
                }

                override fun columnSelectionChanged(event: ListSelectionEvent) = Unit
            }
        setContent {
            Table(rows = people, listSelectionListener = selection, tableColumnModelListener = listener) {
                column("Name") { it.name }
                if (withAge) column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 1, to = 0)
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

    /** Reorders the columns as a header drag does, by moving the column at [from] to [to]. */
    private fun JTable.dragColumn(
        from: Int,
        to: Int,
    ) = columnModel.moveColumn(from, to)

    /**
     * Resizes the column at [position] to [width] the way a divider drag does: the header names the column
     * being resized and the table's layout pass then works out the preferred widths that produce the
     * layout the drag asked for.
     */
    private fun JTable.dragColumnDivider(
        position: Int,
        width: Int,
    ) {
        val column = columnModel.getColumn(position)
        tableHeader.resizingColumn = column
        column.width = width
        doLayout()
        tableHeader.resizingColumn = null
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
            Table(
                rows = people,
                listSelectionListener = selection,
                tableColumnModelListener = columnLayoutListener(events),
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        onNodeOfType<JTable>().fetch().dragColumn(from = 1, to = 0)
        awaitIdle()

        assertEquals(listOf("moved"), events.map { it.first }, "a reorder should arrive as a column move")
        assertEquals(listOf(1, 0), events.last().second.modelIndices, "the reordered columns should be reported")
    }

    @Test
    fun aResizeIsHandedToTheListenerAsAMarginChange() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            Table(
                rows = people,
                listSelectionListener = selection,
                tableColumnModelListener = columnLayoutListener(events),
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumnDivider(position = 0, width = 200)
        awaitIdle()

        // A resize drag re-lays out every column, and a column model publishes each width the pass changes as
        // a margin change of its own, so the drag arrives as one per view column and as nothing else.
        assertEquals(
            List(table.columnModel.columnCount) { "margin" },
            events.map { it.first },
            "a resize should arrive as one margin change per column the layout pass moved",
        )
        assertEquals(
            table.columnModel.preferredWidths(),
            events.last().second.preferredWidths,
            "the widths the drag left the columns at should be reported",
        )
        assertEquals(listOf(0, 1), events.last().second.modelIndices, "a resize should leave the order alone")
    }

    @Test
    fun aDeclaredLayoutPutBackReachesTheListenerNotAtAll() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            Table(
                rows = rows,
                listSelectionListener = selection,
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(80, 90)),
                tableColumnModelListener = columnLayoutListener(events),
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 1, to = 0)
        awaitIdle()
        assertEquals(
            listOf(listOf(1, 0)),
            events.map { it.second.modelIndices },
            "a reorder is the user's own gesture and reaches the listener whether or not a layout is declared",
        )
        events.clear()

        // The caller did not adopt the reorder, so the next pass puts the declared layout back.
        rows = people + Person("Grace", 50)
        awaitIdle()

        assertEquals(listOf(0, 1), table.columnModel.modelIndices(), "a declared layout outlives a reorder")
        assertEquals(emptyList(), events, "re-asserting a declared layout is the library's own write, not news")
    }

    @Test
    fun aStructureChangeThatDropsAColumnReportsWhatIsLeftOfTheUsersLayout() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            Table(
                rows = people,
                listSelectionListener = selection,
                tableColumnModelListener = columnLayoutListener(events),
            ) {
                column("Name") { it.name }
                if (withAge) column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 1, to = 0)
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
            Table(
                model = tableModel("Name", "Age", "City"),
                listSelectionListener = selection,
                tableColumnModelListener = columnLayoutListener(events),
            )
        }

        onNodeOfType<JTable>().fetch().dragColumn(from = 2, to = 0)
        awaitIdle()

        assertEquals(listOf("moved"), events.map { it.first }, "a reorder should arrive as a column move")
        assertEquals(listOf(2, 0, 1), events.last().second.modelIndices, "the reordered columns should be reported")
    }

    @Test
    fun aColumnTheCallersOwnModelAddsReachesTheListenerAsNoLayoutChangeOfItsOwn() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        val model = tableModel("Name", "Age", "City")
        setContent {
            Table(
                model = model,
                listSelectionListener = selection,
                tableColumnModelListener = columnLayoutListener(events),
            )
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

    @Test
    fun aModelDrivenTablesDeclaredLayoutPutBackReachesTheListenerNotAtAll() = runComposeSwingTest {
        // The model stays the same instance throughout, so the re-assert is the pass's own write and the
        // only column change there is to report.
        val model = tableModel("Name", "Age", "City")
        var tip by mutableStateOf("Names and ages")
        val selection = ListSelectionListener {}
        val events = mutableListOf<Pair<String, TableColumnLayout>>()
        setContent {
            Table(
                model = model,
                listSelectionListener = selection,
                modifier = SwingModifier.toolTip(tip),
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1, 2), preferredWidths = listOf(80, 90, 100)),
                tableColumnModelListener = columnLayoutListener(events),
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumnDivider(position = 0, width = 200)
        val draggedWidths = table.columnModel.preferredWidths()
        awaitIdle()
        assertEquals(
            List(table.columnModel.columnCount) { "margin" },
            events.map { it.first },
            "the resize the caller does not adopt should arrive as one margin change per column",
        )
        assertTrue(draggedWidths != listOf(80, 90, 100), "the drag should reach the columns")
        assertEquals(
            draggedWidths,
            events.last().second.preferredWidths,
            "the widths the drag left the columns at should be reported",
        )
        assertEquals(listOf(0, 1, 2), events.last().second.modelIndices, "a resize should leave the order alone")
        assertEquals(
            listOf(80, 90, 100),
            table.columnModel.preferredWidths(),
            "a resize the caller does not adopt does not stand against a declared layout",
        )
        events.clear()

        tip = "Who is who"
        awaitIdle()

        assertEquals("Who is who", table.toolTipText, "the recomposition should have reached the table")
        assertEquals(
            listOf(80, 90, 100),
            table.columnModel.preferredWidths(),
            "the declared widths should still stand",
        )
        assertEquals(emptyList(), events, "a pass that moves no column reports nothing")
    }
}
