package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import javax.swing.ListSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A declared selection replaces the one the widget holds by the rows that separate the two: the rows that
 * leave it are deselected and the rows that join it are selected, and a row on both sides is not written at
 * all. What that leaves is the whole of what these tests assert - the declared rows selected, the anchor at
 * the start of the last run of adjacent rows the declaration names and the lead at its end - because a set
 * of rows names no order and no intermediate state, so the widget answers the same declaration the same way
 * whatever it held before.
 *
 * The lead and the anchor are the two the rows joining the selection would otherwise be left on, and they
 * are what a following shift-click extends the selection from, so where they sit is what the user gets next.
 *
 * [ListBox] and [Table] answer alike, both being the same selection model underneath.
 */
class SelectionDifferenceTest {
    private val people =
        listOf(
            Person("Ada", 36),
            Person("Alan", 41),
            Person("Grace", 50),
            Person("Edsger", 51),
            Person("Barbara", 45),
            Person("Donald", 60),
        )

    private val colors = listOf("red", "green", "blue", "amber", "violet", "teal")

    /** The rows [this] model holds, and the two rows a following shift-click would extend from. */
    private fun ListSelectionModel.state(): Triple<List<Int>, Int, Int> {
        val selected = (0 until ROW_COUNT).filter { isSelectedIndex(it) }
        return Triple(selected, anchorSelectionIndex, leadSelectionIndex)
    }

    @Test
    fun aTableSelectionDeclaredTwiceLeavesTheSameSelectionLeadAndAnchor() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(1, 2, 4))
        setContent {
            Table(rows = people, selectedRowIndices = selection) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val first = table.selectionModel.state()

        selection = setOf(0)
        awaitIdle()
        selection = setOf(1, 2, 4)
        awaitIdle()

        assertEquals(first, table.selectionModel.state(), "the same declaration leaves the table the same way")
        assertEquals(Triple(listOf(1, 2, 4), 4, 4), first, "the declared rows and the last run's ends")
    }

    @Test
    fun aListSelectionDeclaredTwiceLeavesTheSameSelectionLeadAndAnchor() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(1, 2, 4))
        setContent { ListBox(items = colors, selectedIndices = selection) }

        val list = onNodeOfType<JList<*>>().fetch()
        val first = list.selectionModel.state()

        selection = setOf(0)
        awaitIdle()
        selection = setOf(1, 2, 4)
        awaitIdle()

        assertEquals(first, list.selectionModel.state(), "the same declaration leaves the list the same way")
        assertEquals(Triple(listOf(1, 2, 4), 4, 4), first, "the declared rows and the last run's ends")
    }

    @Test
    fun aTableSelectionThatShrinksKeepsTheRowsItStillNames() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0, 1, 2, 3))
        setContent {
            Table(rows = people, selectedRowIndices = selection) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        selection = setOf(1, 2)
        awaitIdle()

        assertEquals(
            Triple(listOf(1, 2), 1, 2),
            table.selectionModel.state(),
            "the rows that left are deselected and the run that stayed carries the anchor and the lead",
        )
    }

    @Test
    fun aTableSelectionThatGainsAndLosesRowsHoldsExactlyWhatIsDeclared() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0, 1, 4))
        setContent {
            Table(rows = people, selectedRowIndices = selection) {
                column("Name") { it.name }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val table = onNodeOfType<JTable>().fetch()
        selection = setOf(1, 2, 5)
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            Triple(listOf(1, 2, 5), 5, 5),
            table.selectionModel.state(),
            "the one pass that declares the selection should already have taken row 0 and row 4 out, put " +
                "row 2 and row 5 in and left row 1 where it was",
        )
    }

    @Test
    fun aListSelectionThatGainsAndLosesRowsHoldsExactlyWhatIsDeclared() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0, 1, 4))
        setContent { ListBox(items = colors, selectedIndices = selection) }
        awaitIdle()
        mainClock.autoAdvance = false

        val list = onNodeOfType<JList<*>>().fetch()
        selection = setOf(1, 2, 5)
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            Triple(listOf(1, 2, 5), 5, 5),
            list.selectionModel.state(),
            "the one pass that declares the selection should already have taken row 0 and row 4 out, put " +
                "row 2 and row 5 in and left row 1 where it was",
        )
    }

    @Test
    fun theTableAnchorAndLeadFollowTheDeclarationAndNotTheRowsThatJoinedIt() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(4, 5))
        setContent {
            Table(rows = people, selectedRowIndices = selection) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        // Rows 0 and 1 join a selection that already holds the declaration's last run, so they are the rows
        // written and rows 4 and 5 are the rows the anchor and the lead belong on.
        selection = setOf(0, 1, 4, 5)
        awaitIdle()

        assertEquals(
            Triple(listOf(0, 1, 4, 5), 4, 5),
            table.selectionModel.state(),
            "the last declared run carries the anchor and the lead",
        )
    }

    @Test
    fun theListAnchorAndLeadFollowTheDeclarationAndNotTheRowsThatJoinedIt() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(4, 5))
        setContent { ListBox(items = colors, selectedIndices = selection) }

        val list = onNodeOfType<JList<*>>().fetch()
        selection = setOf(0, 1, 4, 5)
        awaitIdle()

        assertEquals(
            Triple(listOf(0, 1, 4, 5), 4, 5),
            list.selectionModel.state(),
            "the last declared run carries the anchor and the lead",
        )
    }

    @Test
    fun aTableWritesOnlyTheRowsThatChangedAndNotTheOnesItAlreadyHeld() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0, 1, 2, 3))
        setContent {
            Table(rows = people, selectedRowIndices = selection) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val settled = SettledSpan(table.selectionModel)

        selection = setOf(0, 1, 2, 3, 4)
        awaitIdle()

        assertEquals(
            3..4,
            settled.span,
            "rows 0 to 3 were already selected, so the change reaches the model as the row that " +
                "joined and the lead it moved",
        )
    }

    @Test
    fun aListWritesOnlyTheRowsThatChangedAndNotTheOnesItAlreadyHeld() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0, 1, 2, 3))
        setContent { ListBox(items = colors, selectedIndices = selection) }

        val list = onNodeOfType<JList<*>>().fetch()
        val settled = SettledSpan(list.selectionModel)

        selection = setOf(0, 1, 2, 3, 4)
        awaitIdle()

        assertEquals(
            3..4,
            settled.span,
            "rows 0 to 3 were already selected, so the change reaches the model as the row that " +
                "joined and the lead it moved",
        )
    }

    @Test
    fun aTableSelectionDeclaredEmptyLeavesNoRowSelected() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(1, 2))
        setContent {
            Table(rows = people, selectedRowIndices = selection) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        selection = emptySet()
        awaitIdle()

        assertEquals(
            Triple(emptyList<Int>(), 1, 2),
            table.selectionModel.state(),
            "every row the declaration dropped is deselected, and the anchor and the lead stay on the ends " +
                "of the run the last declaration named, which is where a shift-click extends from",
        )
    }

    @Test
    fun aListSelectionDeclaredEmptyLeavesNoRowSelected() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(1, 2))
        setContent { ListBox(items = colors, selectedIndices = selection) }

        val list = onNodeOfType<JList<*>>().fetch()
        selection = emptySet()
        awaitIdle()

        assertEquals(
            Triple(emptyList<Int>(), 1, 2),
            list.selectionModel.state(),
            "every row the declaration dropped is deselected, and the anchor and the lead stay on the ends " +
                "of the run the last declaration named, which is where a shift-click extends from",
        )
    }
}

/**
 * The rows a settled selection change covered, widened over every settled event a model published since
 * this was attached.
 *
 * A selection model names the rows it marked dirty in the event it settles on, and a repaint of exactly
 * those rows is what a declared change is meant to cost. Rows the model already held are outside it.
 */
private class SettledSpan(
    model: ListSelectionModel,
) {
    private var first = Int.MAX_VALUE
    private var last = Int.MIN_VALUE

    /** The rows covered, or an empty range where nothing settled. */
    val span: IntRange get() = if (first > last) IntRange.EMPTY else first..last

    init {
        model.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                first = minOf(first, event.firstIndex)
                last = maxOf(last, event.lastIndex)
            }
        }
    }
}

/** The rows both of this test's components hold, which is what a selection is read back over. */
private const val ROW_COUNT = 6
