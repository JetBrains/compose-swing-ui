// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.test.interaction.assertProperty
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
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for a [Table] column's composable `cellContent`. They prove the render
 * mechanism end to end: rendering a cell through the renderer installed on that column realizes the
 * composable cell into a real Swing subtree, the cell sees the cell it is being rendered for through its
 * [TableCellScope], and a column that declares no cell body renders through the renderer the table picks
 * by the column's class.
 *
 * Each column that declares a cell body gets a cell composition of its own, and a column that stops
 * declaring one gives that composition up: a cell body whose column is gone composes no more, and the
 * renderer the table captured renders the empty cell a disposed composition composes.
 *
 * The cell's component lives outside the composition root - it is what the renderer hands the table - so
 * these drive the renderer directly (as `JTable` does when it paints a cell) and inspect what it returns.
 */
class TableComposableCellTest {
    /** Renders the cell at [row]/[column] through the renderer the table picks for it, as painting does. */
    private fun JTable.renderCell(
        row: Int,
        column: Int,
        isSelected: Boolean = false,
        hasFocus: Boolean = false,
    ): Component = getCellRenderer(row, column)
        .getTableCellRendererComponent(this, getValueAt(row, column), isSelected, hasFocus, row, column)

    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    @Test
    fun aColumnRendersThroughItsOwnCellsAlone() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name", cellContent = { row -> Label("<${row.name}>") }) { it.name }
                    column("Age", cellContent = { row -> Label("[${row.age}]") }) { it.age }
                },
            )
        }

        // Two columns declaring cells hold a cell composition each, so neither is rebuilt by the other's renders.
        val table = onNodeOfType<JTable>()
        val name = table.fetch().renderCell(row = 0, column = 0)
        val age = table.fetch().renderCell(row = 0, column = 1)
        assertNotSame(name, age, "each column should render its cells through a component of its own")
        assertEquals("<Ada>", name.firstLabelText(), "the first column renders its own cell")
        assertEquals("[36]", age.firstLabelText(), "the second column renders its own cell")
        table.assertProperty(
            "<Ada>",
            "rerendering the first column should not have been disturbed by the second",
        ) { renderCell(row = 0, column = 0).firstLabelText() }
    }

    @Test
    fun theCellScopeReflectsSelection() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name", cellContent = { row -> Label(if (isSelected) "${row.name}*" else row.name) }) {
                        it.name
                    }
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            "Alan",
            "an unselected render should render the plain row",
        ) { renderCell(row = 1, column = 0, isSelected = false).firstLabelText() }
        table.assertProperty(
            "Alan*",
            "a selected render should observe isSelected through the TableCellScope",
        ) { renderCell(row = 1, column = 0, isSelected = true).firstLabelText() }
    }

    @Test
    fun theCellScopeNamesTheRowAndColumnTheTableWasDeclaredWith() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age", cellContent = { row -> Label("$rowIndex/$columnIndex ${row.name}") }) { it.age }
                },
                state = rememberTableState(initialSortKeys = listOf(SortKey(1, SortOrder.DESCENDING))),
                sortable = true,
            )
        }

        // Sorted by age descending, the oldest row is drawn first; the cell is named by the row and the
        // column the table was declared with, which is what the row handed to the cell body is taken from.
        onNodeOfType<JTable>().assertProperty(
            "1/1 Alan",
            "the cell scope should name the declared row, not the row it is drawn at",
        ) { renderCell(row = 0, column = 1).firstLabelText() }
    }

    @Test
    fun composableCellsWorkInsideAScrollPane() = runComposeSwingTest {
        // A cell composition joins the enclosing composition, and the cell's own nodes belong to the
        // renderer rather than to the pane the table is installed in: they render the cell, they do not
        // install themselves as the viewport's view.
        setContent {
            ScrollPane {
                Viewport {
                    Table(
                        rows = people,
                        columns = { column("Name", cellContent = { row -> Panel { Label(row.name) } }) { it.name } },
                        selectedRowIndices = setOf(0),
                    )
                }
            }
        }

        onNodeOfType<JTable>().assertProperty(
            "Ada",
            "a composable cell inside a ScrollPane should realize its content, not leak the viewport slot",
        ) { renderCell(row = 0, column = 0).firstLabelText() }
    }

    @Test
    fun aColumnWithoutACellBodyRendersThroughTheTablesOwnRenderer() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age", cellContent = { row -> Label("${row.age}!") }) { it.age }
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            null,
            "a column declaring no cell body must carry no renderer of its own",
        ) { columnModel.getColumn(0).cellRenderer }
        table.assertProperty(
            "Ada",
            "the table's own renderer renders the cell's value",
        ) {
            assertIs<JLabel>(
                renderCell(row = 0, column = 0),
                "the table's own renderer renders a JLabel",
            ).text
        }
    }

    @Test
    fun aCellBodyTakenAwayRendersThroughTheTablesOwnRenderer() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            Table(
                rows = people,
                columns = {
                    column(
                        header = "Name",
                        cellContent =
                            if (composableCells) {
                                { row -> Panel { Label("<${row.name}>") } }
                            } else {
                                null
                            },
                    ) { it.name }
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            "<Ada>",
            "the composable cell renders",
        ) { renderCell(row = 0, column = 0).firstLabelText() }

        composableCells = false
        awaitIdle()
        table.assertProperty(
            "Ada",
            "the table's own renderer renders the cell's value",
        ) {
            assertIs<JLabel>(
                renderCell(row = 0, column = 0),
                "taking the cell body away should render the table's own renderer",
            ).text
        }

        composableCells = true
        awaitIdle()
        table.assertProperty(
            "<Alan>",
            "declaring a cell body again should render the composable cell",
        ) { renderCell(row = 1, column = 0).firstLabelText() }
    }

    @Test
    fun aRemovedColumnsCellsComposeNoMore() = runComposeSwingTest {
        var badge by mutableStateOf("draft")
        var showBadge by mutableStateOf(true)
        val composed = mutableListOf<String>()
        setContent {
            Table(
                rows = people,
                columns = {
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
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty("Ada draft", "the badge cell renders") { renderCell(row = 0, column = 1).firstLabelText() }
        val badgeRenderer: TableCellRenderer =
            table
                .fetch()
                .columnModel
                .getColumn(1)
                .cellRenderer

        // While the column stands, its cell body observes the state it reads: changing that state composes
        // the cell again without anything asking for a render.
        composed.clear()
        badge = "review"
        awaitIdle()
        assertTrue(composed.isNotEmpty(), "a live cell composition should compose again when the state it reads moves")

        showBadge = false
        awaitIdle()
        composed.clear()
        badge = "final"
        awaitIdle()
        assertEquals(
            emptyList<String>(),
            composed.toList(),
            "a removed column's cell composition must be disposed, so the state it read invalidates nothing",
        )

        table.assertProperty(
            null,
            "a render on the disposed cell composition must render an empty cell rather than a stale one",
        ) { badgeRenderer.getTableCellRendererComponent(this, "final", false, false, 0, 0).firstLabelText() }
    }
}
