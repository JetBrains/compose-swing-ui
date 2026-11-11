// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumnModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

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
        val failure = IllegalStateException("the column loss report fails")
        every { listener.columnMarginChanged(any()) } answers {
            if (failing) throw failure
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
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> { awaitIdle() },
            "the reported failure is the loss report's own",
        )
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(0),
            "the declared column drop stands although its report failed",
        ) { columnModel.modelIndices() }

        failing = false
        withAge = true
        awaitIdle()

        assertEquals(2, table.columnModel.columnCount, "a later declared column change must still reach the table")
    }

    @Test
    fun aReorderIsHandedToTheListenerAsAColumnMove() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val listener = mockk<TableColumnModelListener>(relaxed = true)
        val reported = mutableListOf<List<Int>>()
        every { listener.columnMoved(any()) } answers {
            reported += assertIs<TableColumnModel>(firstArg<TableColumnModelEvent>().source).modelIndices()
        }
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    listSelectionListener = selection,
                    tableColumnModelListener = listener,
                )
            }
        }

        dragColumn(from = 1, to = 0)
        awaitIdle()

        verify(exactly = 1) { listener.columnMoved(any()) }
        verify(exactly = 0) { listener.columnAdded(any()) }
        verify(exactly = 0) { listener.columnRemoved(any()) }
        verify(exactly = 0) { listener.columnMarginChanged(any()) }
        verify(exactly = 0) { listener.columnSelectionChanged(any()) }
        assertEquals(listOf(1, 0), reported.last(), "the reordered columns should be reported")
        onNodeOfType<JTable>().assertProperty(listOf(1, 0), "the reordered columns stand") {
            columnModel.modelIndices()
        }
    }

    @Test
    fun aResizeIsHandedToTheListenerAsAMarginChange() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val listener = mockk<TableColumnModelListener>(relaxed = true)
        val reportedWidths = mutableListOf<List<Int>>()
        val reportedOrder = mutableListOf<List<Int>>()
        every { listener.columnMarginChanged(any()) } answers {
            val columns = assertIs<TableColumnModel>(firstArg<ChangeEvent>().source)
            reportedWidths += columns.preferredWidths()
            reportedOrder += columns.modelIndices()
        }
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    listSelectionListener = selection,
                    tableColumnModelListener = listener,
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }

        dragColumnDivider(position = 0, dx = 100)
        awaitIdle()

        verify(atLeast = 1) { listener.columnMarginChanged(any()) }
        verify(exactly = 0) { listener.columnMoved(any()) }
        verify(exactly = 0) { listener.columnAdded(any()) }
        verify(exactly = 0) { listener.columnRemoved(any()) }
        verify(exactly = 0) { listener.columnSelectionChanged(any()) }
        assertEquals(
            listOf(175, 75),
            reportedWidths.last(),
            "the widths the drag left the columns at should be reported",
        )
        assertEquals(listOf(0, 1), reportedOrder.last(), "a resize should leave the order alone")
        onNodeOfType<JTable>().assertProperty(
            listOf(175, 75),
            "the user's resize should stand",
        ) { columnModel.preferredWidths() }
        onNodeOfType<JTable>().assertProperty(listOf(0, 1), "a resize leaves the order alone") {
            columnModel.modelIndices()
        }
    }

    @Test
    fun aStructureChangeThatDropsAColumnReportsWhatIsLeftOfTheUsersLayout() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val selection = ListSelectionListener {}
        val listener = mockk<TableColumnModelListener>(relaxed = true)
        val reported = mutableListOf<List<Int>>()
        every { listener.columnMarginChanged(any()) } answers {
            reported += assertIs<TableColumnModel>(firstArg<ChangeEvent>().source).modelIndices()
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
        reported.clear()
        clearMocks(listener, answers = false)

        withAge = false
        awaitIdle()

        assertEquals(listOf(0), table.columnModel.modelIndices(), "only the surviving column should be left")
        assertEquals(
            listOf(listOf(0)),
            reported,
            "the layout the surviving columns were left holding should reach the listener once",
        )
        verify(exactly = 0) { listener.columnAdded(any()) }
        verify(exactly = 0) { listener.columnRemoved(any()) }
        verify(exactly = 0) { listener.columnMoved(any()) }
        verify(exactly = 0) { listener.columnSelectionChanged(any()) }
    }

    @Test
    fun aReorderOfAModelDrivenTableIsHandedToTheListenerAsAColumnMove() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val listener = mockk<TableColumnModelListener>(relaxed = true)
        val reported = mutableListOf<List<Int>>()
        every { listener.columnMoved(any()) } answers {
            reported += assertIs<TableColumnModel>(firstArg<TableColumnModelEvent>().source).modelIndices()
        }
        setContent {
            HeaderPane {
                Table(
                    model = modelWithColumns("Name", "Age", "City"),
                    listSelectionListener = selection,
                    tableColumnModelListener = listener,
                )
            }
        }

        dragColumn(from = 2, to = 0)
        awaitIdle()

        verify(exactly = 2) { listener.columnMoved(any()) }
        verify(exactly = 0) { listener.columnAdded(any()) }
        verify(exactly = 0) { listener.columnRemoved(any()) }
        verify(exactly = 0) { listener.columnMarginChanged(any()) }
        verify(exactly = 0) { listener.columnSelectionChanged(any()) }
        assertEquals(listOf(2, 0, 1), reported.last(), "the reordered columns should be reported")
        onNodeOfType<JTable>().assertProperty(listOf(2, 0, 1)) { columnModel.modelIndices() }
    }

    @Test
    fun aColumnTheCallersOwnModelAddsReachesTheListenerAsNoLayoutChangeOfItsOwn() = runComposeSwingTest {
        val selection = ListSelectionListener {}
        val listener = mockk<TableColumnModelListener>(relaxed = true)
        val model = modelWithColumns("Name", "Age", "City")
        setContent {
            HeaderPane {
                Table(
                    model = model,
                    listSelectionListener = selection,
                    tableColumnModelListener = listener,
                )
            }
        }

        clearMocks(listener, answers = false)
        // The caller mutates the model it owns, so the table rebuilds its columns from it with no pass of the
        // composition around the rebuild. A column appearing or disappearing is never a gesture of the user's,
        // there being no header drag that adds or removes one.
        model.addColumn("Country")
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            4,
            "the column the caller added should reach the table",
        ) { columnModel.columnCount }
        verify(exactly = 0) { listener.columnAdded(any()) }
        verify(exactly = 0) { listener.columnRemoved(any()) }
    }
}
