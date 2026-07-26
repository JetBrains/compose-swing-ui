package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for [ListBox] over a real [SwingApplier]. They assert observable behavior on the
 * rendered [JList]: items render into the model, a settled selection change fires `onSelectionChange`,
 * and the controlled selection is re-applied after an items change (which `setListData` clears).
 */
class ListBoxBehaviorTest {
    @Test
    fun itemsRenderIntoTheModel() = runComposeSwingTest {
        setContent { ListBox(items = listOf("a", "b", "c")) }

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
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

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
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

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        assertEquals(listOf(1), list.selectedIndices.toList(), "the controlled selection should render initially")

        // setListData clears the JList selection; the wrapper must re-apply selectedIndices after the
        // model swap so the controlled selection survives an items change.
        items = listOf("a", "b", "c", "d")
        awaitIdle()
        assertEquals(listOf(1), list.selectedIndices.toList(), "selection lost on items change")
    }
}
