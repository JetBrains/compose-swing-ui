package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListModel
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * End-to-end tests for the model-driven [ListBox] overload over a real [SwingApplier]. They assert
 * observable behavior on the rendered [JList]: the caller's [javax.swing.ListModel] is installed
 * as-is, a settled selection change fires `onSelectionChange`, the controlled selection survives a
 * model swap (which `setModel` clears), and a programmatic selection set does not echo back.
 */
class ListBoxModelBehaviorTest {
    @Test
    fun callerModelIsInstalledAsIs() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        setContent { ListBox(model = model) }

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        assertSame(model, list.model, "the caller's model should be installed on the list as-is")
        assertEquals(3, list.model.size, "the model should hold all three items")
    }

    @Test
    fun settledSelectionChangeFiresOnSelectionChange() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        val events = mutableListOf<List<Int>>()
        setContent { ListBox(model = model, onSelectionChange = { events += it }) }

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        // An adjusting run must not fire interim callbacks.
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
    fun selectedIndicesReAppliedAfterModelSwap() = runComposeSwingTest {
        val first = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        val second = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c", "d")) }
        var model by mutableStateOf(first)
        var selection by mutableStateOf(listOf(1))
        setContent {
            ListBox(
                model = model,
                selectedIndices = selection,
                onSelectionChange = { selection = it },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        assertEquals(listOf(1), list.selectedIndices.toList(), "the controlled selection should render initially")

        // setModel clears the JList selection; the wrapper must re-apply selectedIndices after the
        // model swap so the controlled selection survives.
        model = second
        awaitIdle()
        assertSame(second, list.model, "the swapped-in model should be installed on the list")
        assertEquals(listOf(1), list.selectedIndices.toList(), "selection lost on model swap")
    }

    @Test
    fun reApplyingTheSameControlledSelectionDoesNotEcho() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        val reported = mutableListOf<List<Int>>()
        var trigger by mutableStateOf(0)
        setContent {
            // Recompose without changing selectedIndices: the echo-guard must skip re-setting an
            // unchanged selection so the programmatic set never re-enters the selection listener.
            trigger
            ListBox(
                model = model,
                selectedIndices = listOf(1),
                onSelectionChange = { reported += it },
            )
        }
        assertEquals(emptyList(), reported, "rendering the controlled selection must not fire onSelectionChange")

        trigger = 1
        awaitIdle()
        assertEquals(emptyList(), reported, "re-applying an unchanged controlled selection must not echo")
        assertEquals(
            listOf(1),
            onNodeOfType<JList<*>>().fetch<JList<*>>().selectedIndices.toList(),
            "the controlled selection should remain applied",
        )
    }
}
