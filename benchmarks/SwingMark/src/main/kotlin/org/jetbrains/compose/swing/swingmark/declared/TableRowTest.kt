package org.jetbrains.compose.swing.swingmark.declared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.ScrollState
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.swingmark.fixtures.Person
import org.jetbrains.compose.swing.swingmark.fixtures.swingMarkPeople
import org.jetbrains.compose.swing.swingmark.harness.change
import java.awt.Rectangle
import javax.swing.DefaultListSelectionModel
import javax.swing.JTable
import javax.swing.ListSelectionModel

/**
 * `TableRowTest`: every row removed one at a time, added back one at a time, then the rows selected under
 * each of the three selection modes.
 *
 * Rows and selection are state the test holds, and the library works out what moved. Each step also
 * scrolls a row into view through
 * [ScrollState.revealRect], which names the row in the content's coordinates - which is why the table
 * declares its row height instead of being asked for it.
 *
 * Every column holds `Any`, as the original's `DefaultTableModel` reports for all of its columns: a table
 * picks its renderer by column class, and a checkbox column paints differently from a text one.
 *
 * The selection phase declares a set, where the original adds an interval to what the table already
 * holds. What each pass leaves selected therefore depends on every pass before it, so [selectionSteps]
 * replays the original's own calls to work the sets out rather than restating them.
 */
internal class TableRowTest : DeclaredTest() {
    override val testName: String = "Table Rows"

    private val rows = PEOPLE.toMutableStateList()
    private var selection by mutableStateOf(emptySet<Int>())
    private var selectionMode by mutableIntStateOf(ListSelectionModel.SINGLE_SELECTION)
    private lateinit var scroll: ScrollState

    @Composable
    override fun Content() {
        scroll = rememberScrollState()
        FlowPanel {
            ScrollPane(state = scroll) {
                Table(
                    rows = rows,
                    modifier = SwingModifier.viewport(),
                    selectedRowIndices = selection,
                    selectionMode = selectionMode,
                    rowHeight = ROW_HEIGHT,
                ) {
                    column("First Name", Any::class.java) { it.first }
                    column("Last Name", Any::class.java) { it.last }
                    column("Favorite Color", Any::class.java) { it.color }
                    column("Favorite Number", Any::class.java) { it.number }
                    column("Vegetarian", Any::class.java) { it.vegetarian }
                }
            }
        }
    }

    override fun runTest() {
        val table = widget(JTable::class.java)
        removeRows(table)
        addRows(table)
        selectRows(table)
    }

    private fun removeRows(table: JTable) {
        repeat(PEOPLE.size) { removed ->
            val remaining = PEOPLE.size - removed - 1
            change(
                apply = {
                    rows.removeAt(0)
                    revealOnApply { reveal(cellRect(0, PEOPLE.size)) }
                },
                reached = { table.rowCount == remaining },
            )
        }
    }

    private fun addRows(table: JTable) {
        PEOPLE.forEachIndexed { index, person ->
            val expected = index + 1
            change(
                apply = {
                    rows.add(person)
                    revealOnApply { reveal(cellRect(expected - 1, PEOPLE.size)) }
                },
                reached = { table.rowCount == expected },
            )
        }
    }

    private fun selectRows(table: JTable) {
        for (mode in MODES) {
            change(
                apply = {
                    selectionMode = mode
                    selection = emptySet()
                },
                reached = { table.selectionModel.selectionMode == mode && table.selectedRowCount == 0 },
            )
            for (step in selectionSteps(mode, PEOPLE.size)) {
                change(
                    apply = {
                        selection = step.selection
                        revealOnApply { reveal(step.reveal) }
                    },
                    reached = { table.selectedRows.toSet() == step.selection },
                )
            }
        }
    }

    /** Scrolls [rect] into view, named in the table's own coordinates. */
    private fun reveal(rect: Rectangle) {
        check(scroll.revealRect(rect)) {
            "no scroll pane renders this test's scroll state, so $rect was never scrolled to"
        }
    }

    private companion object {
        val PEOPLE: List<Person> = swingMarkPeople()

        val MODES =
            listOf(
                ListSelectionModel.SINGLE_SELECTION,
                ListSelectionModel.SINGLE_INTERVAL_SELECTION,
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            )
    }
}

/** `JTable`'s own default row height, declared so a row's place is known without asking the table. */
private const val ROW_HEIGHT = 16

/**
 * The rectangle `JTable.getCellRect` reports for [row] of [rowCount], in the table's own coordinates.
 * A row past the last one has no cell: the table reports its own height and no height at all, which
 * scrolls to the bottom.
 */
private fun cellRect(
    row: Int,
    rowCount: Int,
): Rectangle =
    if (row < rowCount) {
        Rectangle(0, row * ROW_HEIGHT, 1, ROW_HEIGHT)
    } else {
        Rectangle(0, rowCount * ROW_HEIGHT, 1, 0)
    }

/** One selection step: the rows selected after it, and the rectangle it scrolls to. */
private class SelectionStep(
    val selection: Set<Int>,
    val reveal: Rectangle,
)

private const val INTERVAL_LENGTH = 5
private const val INTERVAL_REVEAL_AHEAD = 4
private const val MULTIPLE_LENGTH = 3
private const val MULTIPLE_STRIDE = 5
private const val MULTIPLE_REVEAL_AHEAD = 3

/**
 * The selection each of `TableScroller`'s passes leaves under [mode] over [rowCount] rows, and the
 * rectangle that pass scrolls to.
 *
 * `TableScroller` adds an interval to whatever the table already holds, so a pass's selection is the
 * accumulation of every pass before it, and under two of the three modes that accumulation saturates:
 * once the selection covers what a later pass would add, that pass selects what is already selected.
 * Which passes those are follows from how a selection model of each mode merges an added interval, so
 * they are replayed rather than restated - a [DefaultListSelectionModel] in the mode under test takes
 * `TableScroller`'s own calls, and each pass records the set it leaves and the row it scrolls to.
 *
 * Each mode walks `rowCount - 1` passes, which is what the original walks.
 */
private fun selectionSteps(
    mode: Int,
    rowCount: Int,
): List<SelectionStep> {
    val model = DefaultListSelectionModel().apply { selectionMode = mode }
    val max = rowCount - 1
    var current = 0
    return List(max) {
        val reveal =
            when (mode) {
                ListSelectionModel.SINGLE_SELECTION -> {
                    model.addSelectionInterval(current, current)
                    current++
                    current
                }

                ListSelectionModel.SINGLE_INTERVAL_SELECTION -> {
                    current = minOf(current, max)
                    model.addSelectionInterval(current, minOf(current + INTERVAL_LENGTH, max))
                    current++
                    model.anchorSelectionIndex + INTERVAL_REVEAL_AHEAD
                }

                else -> {
                    model.addSelectionInterval(
                        minOf(current, max),
                        minOf(current + MULTIPLE_LENGTH, max),
                    )
                    current += MULTIPLE_STRIDE
                    model.anchorSelectionIndex + MULTIPLE_REVEAL_AHEAD
                }
            }
        SelectionStep(
            selection = (0..max).filter(model::isSelectedIndex).toSet(),
            reveal = cellRect(reveal, rowCount),
        )
    }
}
