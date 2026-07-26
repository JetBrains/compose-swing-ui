package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Re-applying a selection the widget already holds would rewrite it interval by interval, and that
 * rewrite moves the lead the user's drag left behind - the row a following shift-click extends from.
 * [ListBox] and [Table] leave a matching selection alone instead, so a recomposition that changed
 * nothing leaves the selection model exactly as the user left it.
 *
 * A user's drag reaches a widget as a selection write from the row it started on to the row it ended
 * on, so these tests select through the widget's own selection model to stand in for one; each
 * recomposition then changes a property that has nothing to do with selection.
 */
class RedundantSelectionWriteTest {
    @Test
    fun aRecompositionThatChangesNothingKeepsTheListLead() = runComposeSwingTest {
        var count by mutableStateOf(8)
        setContent {
            ListBox(items = listOf("red", "green", "blue"), selectedIndices = listOf(0, 1, 2), visibleRowCount = count)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        // A drag upwards from the last row: it ends - and so leaves the lead - on the first one.
        list.selectionModel.setSelectionInterval(2, 0)
        assertEquals(0, list.selectionModel.leadSelectionIndex, "the drag left the lead on the row it ended on")

        count = 6
        awaitIdle()

        assertEquals(listOf(0, 1, 2), list.selectedIndices.toList(), "the selection is the one already applied")
        assertEquals(0, list.selectionModel.leadSelectionIndex, "the lead the user left is where it was")
    }

    @Test
    fun aRecompositionThatChangesNothingKeepsTheTableLead() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                modifier = SwingModifier.name(label),
                selectedRowIndices = listOf(0, 1, 2),
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        // A drag upwards from the last row: it ends - and so leaves the lead - on the first one.
        table.selectionModel.setSelectionInterval(2, 0)
        assertEquals(0, table.selectionModel.leadSelectionIndex, "the drag left the lead on the row it ended on")

        label = "second"
        awaitIdle()

        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the selection is the one already applied")
        assertEquals(0, table.selectionModel.leadSelectionIndex, "the lead the user left is where it was")
    }
}
