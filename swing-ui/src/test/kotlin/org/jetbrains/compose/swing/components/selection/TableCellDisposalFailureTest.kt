package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JTable
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TableCellDisposalFailureTest {
    @TestFactory
    fun everyColumnIsDisposedWhenCellDisposalFails(): List<DynamicTest> = Retirement.entries.map { retirement ->
        DynamicTest.dynamicTest(retirement.name) { checkRetirement(retirement) }
    }

    private fun checkRetirement(retirement: Retirement) = runComposeSwingTest {
        var keepTable by mutableStateOf(true)
        var keepColumns by mutableStateOf(true)
        var keepCells by mutableStateOf(true)
        val failures = List(2) { object : IllegalStateException("cell-$it disposal failed") {} }
        val disposals = IntArray(failures.size)
        setContent {
            if (keepTable) {
                Table(
                    rows = listOf("row"),
                    columns = {
                        if (keepColumns) {
                            for (index in failures.indices) {
                                column(
                                    header = "column-$index",
                                    cellContent =
                                        if (keepCells) {
                                            { row ->
                                                Label(row)
                                                DisposableEffect(Unit) {
                                                    onDispose {
                                                        disposals[index]++
                                                        throw failures[index]
                                                    }
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                ) { it }
                            }
                        }
                    },
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        for (index in failures.indices) {
            table.prepareRenderer(table.getCellRenderer(0, index), 0, index)
        }
        when (retirement) {
            Retirement.Table -> keepTable = false
            Retirement.Columns -> keepColumns = false
            Retirement.CellBodies -> keepCells = false
        }
        val failure = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(failures.first(), failure)
        assertEquals(failures.drop(1), failure.suppressed.toList())
        assertContentEquals(intArrayOf(1, 1), disposals)
    }

    private enum class Retirement { Table, Columns, CellBodies }
}
