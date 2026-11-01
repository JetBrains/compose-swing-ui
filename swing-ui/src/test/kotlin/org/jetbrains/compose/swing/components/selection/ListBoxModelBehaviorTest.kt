package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * End-to-end tests for the model-driven [ListBox] overload over a real
 * [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier]. They assert observable behavior on
 * the rendered [JList]: the caller's [javax.swing.ListModel] is installed as-is, a settled
 * selection change fires `onSelectionChange`, the controlled selection survives a model swap
 * (which `setModel` clears), a programmatic selection set does not echo back, and the declared
 * selection mode and visible row count reach the list and follow a later change.
 */
class ListBoxModelBehaviorTest {
    @Test
    fun callerModelIsInstalledAsIs() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        setContent { ListBox(model = model) }

        val list = onNodeOfType<JList<*>>().fetch()
        assertSame(model, list.model, "the caller's model should be installed on the list as-is")
        assertEquals(3, list.model.size, "the model should hold all three items")
    }

    @Test
    fun settledSelectionChangeFiresOnSelectionChange() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        val events = mutableListOf<Set<Int>>()
        setContent { ListBox(model = model, onSelectionChange = { events += it }) }

        val list = onNodeOfType<JList<*>>().fetch()
        // An adjusting run must not fire interim callbacks.
        list.selectionModel.valueIsAdjusting = true
        onNodeOfType<JList<*>>().performAddSelection(1)
        assertEquals(emptyList(), events, "an adjusting selection must not fire onSelectionChange")

        // Settling the run delivers exactly one callback with the final selection.
        list.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(listOf(setOf(1)), events, "settling should fire exactly one callback with the final selection")
    }

    @Test
    fun selectedIndicesReAppliedAfterModelSwap() = runComposeSwingTest {
        val first = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        val second = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c", "d")) }
        var model by mutableStateOf(first)
        var selection by mutableStateOf(setOf(1))
        setContent {
            ListBox(
                model = model,
                selectedIndices = selection,
                onSelectionChange = { selection = it },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        onNodeOfType<JList<*>>().assertProperty(
            listOf(1),
            "the controlled selection should render initially",
        ) { selectedIndices.toList() }

        // setModel clears the JList selection; the wrapper must re-apply selectedIndices after the
        // model swap so the controlled selection survives.
        model = second
        awaitIdle()
        assertSame(second, list.model, "the swapped-in model should be installed on the list")
        onNodeOfType<JList<*>>().assertProperty(listOf(1), "selection lost on model swap") { selectedIndices.toList() }
    }

    @Test
    fun reApplyingTheSameControlledSelectionDoesNotEcho() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        val reported = mutableListOf<Set<Int>>()
        var trigger by mutableStateOf(0)
        setContent {
            // Recompose without changing selectedIndices: the echo-guard must skip re-setting an
            // unchanged selection so the programmatic set never re-enters the selection listener.
            trigger
            ListBox(
                model = model,
                selectedIndices = setOf(1),
                onSelectionChange = { reported += it },
            )
        }
        assertEquals(emptyList(), reported, "rendering the controlled selection must not fire onSelectionChange")

        trigger = 1
        awaitIdle()
        assertEquals(emptyList(), reported, "re-applying an unchanged controlled selection must not echo")
        onNodeOfType<JList<*>>().assertProperty(
            listOf(1),
            "the controlled selection should remain applied",
        ) { selectedIndices.toList() }
    }

    @Test
    fun aDeclaredSelectionModeLimitsWhatTheUserCanSelect() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        var selectionMode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        setContent { ListBox(model = model, selectionMode = selectionMode) }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(0, 2)
        onNodeOfType<JList<*>>().assertProperty(
            listOf(2),
            "a single-selection list holds one row at a time",
        ) { selectedIndices.toList() }

        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()
        list.selectionModel.setSelectionInterval(0, 2)
        onNodeOfType<JList<*>>().assertProperty(
            listOf(0, 1, 2),
            "the widened mode admits a whole range",
        ) { selectedIndices.toList() }
    }

    @Test
    fun aDeclaredVisibleRowCountIsAppliedAndUpdatedInPlace() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("a", "b", "c")) }
        var visibleRowCount by mutableStateOf(4)
        setContent { ListBox(model = model, visibleRowCount = visibleRowCount) }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(4, list.visibleRowCount, "the declared row count should reach the list")

        visibleRowCount = 2
        awaitIdle()
        assertEquals(2, list.visibleRowCount, "a later row count should update the list in place")
    }
}
