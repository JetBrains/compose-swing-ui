package org.jetbrains.compose.swing.swingmark.raw

import org.jetbrains.compose.swing.swingmark.fixtures.swingMarkTableData
import org.jetbrains.compose.swing.swingmark.harness.rest
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel

/**
 * `TableRowTest`: every row removed one at a time, added back one at a time, then the rows selected under
 * each of the three selection modes, every pass scrolling a row into view.
 */
internal class TableRowTest(
    private val blitScrolling: Boolean,
) : RawTest() {
    override val testName: String = "Table Rows"

    private lateinit var table: JTable
    private lateinit var dataModel: DefaultTableModel

    private val names = arrayOf("First Name", "Last Name", "Favorite Color", "Favorite Number", "Vegetarian")
    private val data: Array<Array<Any>> = swingMarkTableData()

    override fun testComponent(): JComponent {
        val panel = JPanel()
        dataModel = DefaultTableModel(data, names)
        table = CountTable(dataModel)
        val scrollPane = JScrollPane(table)
        if (blitScrolling) {
            scrollPane.viewport.putClientProperty(ENABLE_WINDOW_BLIT, true)
        }
        panel.add(scrollPane)
        return panel
    }

    override fun runTest() {
        testTable(table)
    }

    private fun testTable(currentTable: JTable) {
        val rowRemover = TableRowAdder(currentTable, data, add = false)
        while (dataModel.rowCount > 0) {
            post(rowRemover)
            rest()
        }

        val rowAdder = TableRowAdder(currentTable, data, add = true)
        while (dataModel.rowCount < data.size) {
            postAndWait(rowAdder)
        }

        for (mode in 0 until SELECTION_MODES) {
            val scroll = TableScroller(currentTable, mode)
            currentTable.clearSelection()
            repeat(currentTable.rowCount - 1) {
                postAndWait(scroll)
            }
        }
    }

    private inner class CountTable(
        model: TableModel,
    ) : JTable(model) {
        override fun paint(g: Graphics) {
            super.paint(g)
            paintCount++
        }
    }

    private companion object {
        /** Single row, single interval, multiple intervals - the modes the test walks, in its order. */
        const val SELECTION_MODES = 3
    }
}

private const val INTERVAL_LENGTH = 5
private const val INTERVAL_REVEAL_AHEAD = 4
private const val MULTIPLE_LENGTH = 3
private const val MULTIPLE_STRIDE = 5
private const val MULTIPLE_REVEAL_AHEAD = 3

/**
 * Adds an interval to the table's selection and scrolls a row into view, which each selection pass posts.
 *
 * The interval, the row it scrolls to and how far the next pass starts on all follow from the mode, which
 * this installs on the table as it is built.
 */
private class TableScroller(
    private val table: JTable,
    selectionMode: Int,
) : Runnable {
    private var currentRowSelection = 0

    init {
        table.setSelectionMode(selectionMode)
    }

    override fun run() {
        var ensureToSeeRow = 0

        when (table.selectionModel.selectionMode) {
            ListSelectionModel.SINGLE_SELECTION -> {
                table.addRowSelectionInterval(currentRowSelection, currentRowSelection)
                currentRowSelection++
                ensureToSeeRow = currentRowSelection
            }

            ListSelectionModel.SINGLE_INTERVAL_SELECTION -> {
                currentRowSelection = minOf(currentRowSelection, table.rowCount - 1)
                val maxRow = table.rowCount - 1
                table.addRowSelectionInterval(
                    currentRowSelection,
                    minOf(currentRowSelection + INTERVAL_LENGTH, maxRow),
                )
                currentRowSelection++
                ensureToSeeRow = table.selectionModel.anchorSelectionIndex + INTERVAL_REVEAL_AHEAD
            }

            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION -> {
                table.addRowSelectionInterval(
                    minOf(currentRowSelection, table.rowCount - 1),
                    minOf(currentRowSelection + MULTIPLE_LENGTH, table.rowCount - 1),
                )
                currentRowSelection += MULTIPLE_STRIDE
                ensureToSeeRow = table.selectionModel.anchorSelectionIndex + MULTIPLE_REVEAL_AHEAD
            }
        }

        val cellBound: Rectangle = table.getCellRect(ensureToSeeRow, 0, true)
        table.scrollRectToVisible(cellBound)
    }
}

/**
 * Adds the next row of the data, or removes the first row, and scrolls to the row it touched. Which of the
 * two it does is fixed when it is built, and one instance carries the whole phase.
 */
private class TableRowAdder(
    private val table: JTable,
    private val data: Array<Array<Any>>,
    private val add: Boolean,
) : Runnable {
    private var index = 0

    override fun run() {
        val model = table.model as DefaultTableModel
        val cellBound: Rectangle

        if (add) {
            model.addRow(data[index])
            index++
            cellBound = table.getCellRect(table.rowCount - 1, 0, true)
        } else {
            model.removeRow(0)
            cellBound = table.getCellRect(0, 0, true)
        }

        table.scrollRectToVisible(cellBound)
    }
}
