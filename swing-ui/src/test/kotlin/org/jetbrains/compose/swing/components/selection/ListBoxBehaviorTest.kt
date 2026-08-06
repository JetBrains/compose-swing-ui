package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for [ListBox] over a real [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier].
 * They assert observable behavior on the rendered [JList].
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
        val events = mutableListOf<Set<Int>>()
        setContent { ListBox(items = listOf("a", "b", "c"), onSelectionChange = { events += it }) }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.valueIsAdjusting = true
        list.selectedIndex = 1
        awaitIdle()
        assertEquals(emptyList(), events, "an adjusting selection must not fire onSelectionChange")

        list.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(listOf(setOf(1)), events, "settling should fire exactly one callback with the final selection")
    }

    @Test
    fun selectedIndicesReAppliedAfterItemsChange() = runComposeSwingTest {
        var items by mutableStateOf(listOf("a", "b", "c"))
        var selection by mutableStateOf(setOf(1))
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
    fun aSelectionTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        setContent { ListBox(items = listOf("a", "b", "c"), selectedIndices = setOf(0)) }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectedIndex = 1
        awaitIdle()

        // The list is already settled back onto the declared selection by the time the move's own
        // recomposition finishes - not just once some later, unrelated recomposition happens to run.
        assertEquals(listOf(0), list.selectedIndices.toList(), "an unadopted selection change does not stand")
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
        val first: (Set<Int>) -> Unit = { reported += "first" }
        val second: (Set<Int>) -> Unit = { reported += "second" }
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

    @Test
    fun aRawListenerReadsTheAnchorAndLeadOfTheSelectionOffTheEvent() = runComposeSwingTest {
        val positions = mutableListOf<Pair<Int, Int>>()
        val listener =
            ListSelectionListener { event ->
                val list = event.source as JList<*>
                if (!event.valueIsAdjusting) positions += list.anchorSelectionIndex to list.leadSelectionIndex
            }
        setContent { ListBox(items = listOf("a", "b", "c"), listSelectionListener = listener) }

        // A range is anchored where it began and led by where it reached, and a list's selection event is
        // sourced at the list itself - so both positions are read back off it along with the selection.
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(2, 0)
        awaitIdle()

        assertEquals(listOf(2 to 0), positions, "the anchor and lead of the range should reach the listener")
    }
}
