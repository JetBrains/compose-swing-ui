package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.TracedTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a pass that moves a [Table]'s sorting costs.
 *
 * One pass: the pass that takes the sorter away writes the row order as the wrapper's own write, so the
 * order it lands on is not written back into the [TableState]. A write counted as the user's would change
 * the state, invalidate the scope that reads it, and buy a further pass to apply an order that already
 * stands.
 *
 * The frames are driven by the test, which is what makes the passes countable: each frame carries one, and
 * the idle gate publishes a declaration without sending a frame of its own, so the frame that follows is
 * the apply pass that carries it.
 *
 * The order the rows are left in says nothing about which route settled them - a table that answered a
 * successor pass would be left holding the same order - so what the passes cost is what is stated here.
 */
class TableSortingSettlesInOnePassTest : TracedTest() {
    @Test
    fun takingTheSorterAwayUnderAStatesOrderBuysNoSuccessorPass() = runComposeSwingTest {
        // One model instance across the test, so nothing but `sortable` moves: the sorter change runs
        // through the pass that declares it and through nothing else.
        val model =
            DefaultTableModel(
                arrayOf(arrayOf<Any?>("Ada"), arrayOf<Any?>("Alan"), arrayOf<Any?>("Grace")),
                arrayOf<Any?>("Name"),
            )
        var sortable by mutableStateOf(true)
        val state = TableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING)))
        // No row is selected, so the selection mirror has nothing to report.
        setContent { Table(model = model, state = state, sortable = sortable) }
        awaitIdle()
        mainClock.autoAdvance = false

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            listOf("Grace", "Alan", "Ada"),
            (0 until table.rowCount).map { table.getValueAt(it, 0) },
            "the state's order should reach the rows the table sorts",
        )

        sortable = false
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertNull(
            onNodeOfType<JTable>().fetch().rowSorter,
            "turning sorting off should leave the table without a sorter",
        )

        tracer.clear()
        mainClock.advanceTimeByFrame()

        assertEquals(
            emptyList(),
            tracer.passes(),
            "the pass that took the sorter away recorded the order it left the rows in inside its own " +
                "settlement, so no successor pass is bought to absorb the read-back: ${tracer.sections}",
        )
    }
}
