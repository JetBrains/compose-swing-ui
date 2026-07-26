package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.ListSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for [ListBox] over a real [SwingApplier]. They assert observable behavior on the
 * rendered [JList]: items render into the model, a settled selection change fires `onSelectionChange`,
 * the controlled selection is re-applied after an items change (which `setListData` clears), and the
 * declared selection mode and visible row count reach the list and follow a later change.
 */
class ListBoxBehaviorTest {
    @Test
    fun itemsRenderIntoTheModel() = runComposeSwingTest {
        setContent { ListBox(items = listOf("a", "b", "c")) }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(3, list.model.size, "the model should hold all three items")
        assertEquals(
            listOf("a", "b", "c"),
            (0 until list.model.size).map { list.model.getElementAt(it) },
            "the model elements should match the declared items in order",
        )
    }

    @Test
    fun settledSelectionChangeFiresOnSelectionChange() = runComposeSwingTest {
        val events = mutableListOf<List<Int>>()
        setContent { ListBox(items = listOf("a", "b", "c"), onSelectionChange = { events += it }) }

        val list = onNodeOfType<JList<*>>().fetch()
        // setValueIsAdjusting(true) marks the run as in-progress: those interim events must NOT fire.
        list.selectionModel.valueIsAdjusting = true
        list.selectedIndex = 1
        awaitIdle()
        assertEquals(emptyList(), events, "an adjusting selection must not fire onSelectionChange")

        // Settling the run delivers exactly one callback with the final selection.
        list.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(listOf(listOf(1)), events, "settling should fire exactly one callback with the final selection")
    }

    @Test
    fun selectedIndicesReAppliedAfterItemsChange() = runComposeSwingTest {
        var items by mutableStateOf(listOf("a", "b", "c"))
        var selection by mutableStateOf(listOf(1))
        setContent {
            ListBox(
                items = items,
                selectedIndices = selection,
                onSelectionChange = { selection = it },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(1), list.selectedIndices.toList(), "the controlled selection should render initially")

        // setListData clears the JList selection; the wrapper must re-apply selectedIndices after the
        // model swap so the controlled selection survives an items change.
        items = listOf("a", "b", "c", "d")
        awaitIdle()
        assertEquals(listOf(1), list.selectedIndices.toList(), "selection lost on items change")
    }

    @Test
    fun aDeclaredSelectionModeLimitsWhatTheUserCanSelect() = runComposeSwingTest {
        var selectionMode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        setContent { ListBox(items = listOf("a", "b", "c"), selectionMode = selectionMode) }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(0, 2)
        assertEquals(listOf(2), list.selectedIndices.toList(), "a single-selection list holds one row at a time")

        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()
        list.selectionModel.setSelectionInterval(0, 2)
        assertEquals(listOf(0, 1, 2), list.selectedIndices.toList(), "the widened mode admits a whole range")
    }

    @Test
    fun aDeclaredVisibleRowCountIsAppliedAndUpdatedInPlace() = runComposeSwingTest {
        var visibleRowCount by mutableStateOf(4)
        setContent { ListBox(items = listOf("a", "b", "c"), visibleRowCount = visibleRowCount) }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(4, list.visibleRowCount, "the declared row count should reach the list")

        visibleRowCount = 2
        awaitIdle()
        assertEquals(2, list.visibleRowCount, "a later row count should update the list in place")
    }

    @Test
    fun theLatestSelectionCallbackIsTheOneThatRuns() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        val first: (List<Int>) -> Unit = { reported += "first" }
        val second: (List<Int>) -> Unit = { reported += "second" }
        var useSecond by mutableStateOf(false)
        setContent {
            ListBox(items = listOf("a", "b", "c"), onSelectionChange = if (useSecond) second else first)
        }

        useSecond = true
        awaitIdle()

        onNodeOfType<JList<*>>().fetch().selectedIndex = 1
        awaitIdle()

        assertEquals(listOf("second"), reported, "a callback the recomposition replaced is not the one that runs")
    }
}
