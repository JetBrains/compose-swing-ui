package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A list node is recyclable: a parked [ReusableContentHost] child is reactivated onto the `JList` the node
 * already holds, and the node's factory does not run a second time.
 *
 * A reactivation re-applies every declared parameter onto a list that is already holding a selection, so it
 * is where the two owners of a selection pull hardest against each other. A declared selection is the
 * composition's state and is re-asserted; an undeclared one is the user's, and reaching the same
 * declarations by way of a reactivation is not a reason for it to disappear. Where the content the
 * reactivation brings really cannot hold it, what is left of it reaches the caller all the same - the
 * listeners a modifier installs are detached while a node is parked, so a loss reported through the widget's
 * own event would be lost with them.
 */
class ListBoxNodeReuseTest {
    private val colors = listOf("red", "green")

    /** A caller-owned model holding the first [size] of [colors]. */
    private fun listModel(size: Int = colors.size): DefaultListModel<String> =
        DefaultListModel<String>().apply { for (color in colors.take(size)) addElement(color) }

    @Test
    fun aReactivatedListStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = colors, selectedIndices = listOf(1))
            }
        }
        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(1), list.selectedIndices.toList(), "the controlled selection should be applied")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JList<*>>().fetch().selectedIndices.toList(),
            "a reactivated list should still hold the controlled selection",
        )
    }

    @Test
    fun aReactivatedModelDrivenListStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val model = listModel()
        setContent {
            ReusableContentHost(active = active) {
                ListBox(model = model, selectedIndices = listOf(1))
            }
        }
        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(1), list.selectedIndices.toList(), "the controlled selection should be applied")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JList<*>>().fetch().selectedIndices.toList(),
            "a reactivated model-driven list should still hold the controlled selection",
        )
    }

    @Test
    fun aReactivatedListKeepsTheSelectionTheUserMade() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = colors, onSelectionChange = { received += it })
            }
        }
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JList<*>>().fetch().selectedIndices.toList(),
            "an undeclared selection should survive a reactivation",
        )
        assertEquals(emptyList(), received, "a reactivation that keeps the selection has nothing to report")
    }

    @Test
    fun aReactivatedModelDrivenListKeepsTheSelectionTheUserMade() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val received = mutableListOf<List<Int>>()
        val model = listModel()
        setContent {
            ReusableContentHost(active = active) {
                ListBox(model = model, onSelectionChange = { received += it })
            }
        }
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(1),
            onNodeOfType<JList<*>>().fetch().selectedIndices.toList(),
            "an undeclared selection should survive a reactivation of a model-driven list",
        )
        assertEquals(emptyList(), received, "a reactivation that keeps the selection has nothing to report")
    }

    @Test
    fun aReactivationTooFewItemsToHoldTheUsersSelectionReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var items by mutableStateOf(colors)
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = items, onSelectionChange = { received += it })
            }
        }
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        items = colors.take(1)
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            emptyList(),
            onNodeOfType<JList<*>>().fetch().selectedIndices.toList(),
            "the row the new items cannot hold leaves the selection",
        )
        assertEquals(listOf(emptyList()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithAModelTooShortToHoldTheUsersSelectionReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var model by mutableStateOf(listModel())
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                ListBox(model = model, onSelectionChange = { received += it })
            }
        }
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(1, 1)
        received.clear()

        active = false
        awaitIdle()
        model = listModel(size = 1)
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            emptyList(),
            onNodeOfType<JList<*>>().fetch().selectedIndices.toList(),
            "the row the new model cannot hold leaves the selection",
        )
        assertEquals(listOf(emptyList()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithANarrowerModeReportsTheSelectionItLeaves() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val received = mutableListOf<List<Int>>()
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = colors, onSelectionChange = { received += it }, selectionMode = mode)
            }
        }
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(0, 1)
        received.clear()

        // A mode narrowed while the node is parked reaches the list on the reactivation pass, which applies
        // it before the modifier reinstalls the listeners the list reports through.
        active = false
        awaitIdle()
        mode = ListSelectionModel.SINGLE_SELECTION
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf(0),
            onNodeOfType<JList<*>>().fetch().selectedIndices.toList(),
            "the row the narrower mode still holds stays selected",
        )
        assertEquals(listOf(listOf(0)), received, "the selection the reactivation left should be reported once")
    }
}
