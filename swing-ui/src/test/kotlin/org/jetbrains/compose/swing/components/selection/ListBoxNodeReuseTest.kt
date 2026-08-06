package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A list node is recyclable: a parked [ReusableContentHost] child reactivates onto the `JList` the node
 * already holds, and the node's factory does not run again.
 *
 * A reactivation re-applies every declared parameter onto a list that already holds a selection, so this
 * is where the two owners of a selection pull hardest against each other. A declared selection is the
 * composition's state, so it is re-asserted. An undeclared selection is the user's; reaching the same
 * declaration through a reactivation is not a reason for it to disappear. When the reactivated content
 * cannot hold the selection, what is left of it still reaches the caller: the listeners a modifier
 * installs are detached while a node is parked, so a loss reported only through the widget's own event
 * would otherwise be lost with them.
 *
 * A composable cell is owned the same way: the island stamping it lives only while the node is in the
 * composition, so a reactivation stamps the composable cell again.
 */
class ListBoxNodeReuseTest {
    private val colors = listOf("red", "green")

    /** A caller-owned model holding the first [size] of [colors]. */
    private fun listModel(size: Int = colors.size): DefaultListModel<String> =
        DefaultListModel<String>().apply { for (color in colors.take(size)) addElement(color) }

    @Test
    fun aParkedListRendersItsOwnCellsAndStampsTheComposableOneAgainOnReactivation() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = colors) { item -> Label(item) }
            }
        }
        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        assertEquals("red", list.stampCell(index = 0).firstLabelText(), "the composable cell should render row 0")

        active = false
        awaitIdle()

        // A parked list keeps its place in the Swing tree and goes on painting, while the cell island
        // behind its composable cell is gone, so the renderer it used before that cell has to be back
        // on it by then.
        val parked = list.stampCell(index = 0)
        assertTrue(parked is JLabel, "a parked list should render rows through the renderer of its own")
        assertEquals("red", (parked as JLabel).text, "the list's own renderer renders the item's toString")

        active = true
        awaitIdle()

        assertEquals(
            "green",
            onNodeOfType<JList<*>>().fetch<JList<String>>().stampCell(index = 1).firstLabelText(),
            "a reactivated list should stamp the composable cell again",
        )
    }

    @Test
    fun aReactivatedListStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = colors, selectedIndices = setOf(1))
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
                ListBox(model = model, selectedIndices = setOf(1))
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
        val received = mutableListOf<Set<Int>>()
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
        val received = mutableListOf<Set<Int>>()
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
        val received = mutableListOf<Set<Int>>()
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
        assertEquals(listOf(emptySet()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithAModelTooShortToHoldTheUsersSelectionReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var model by mutableStateOf(listModel())
        val received = mutableListOf<Set<Int>>()
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
        assertEquals(listOf(emptySet()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithANarrowerModeReportsTheSelectionItLeaves() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var mode by mutableStateOf(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        val received = mutableListOf<Set<Int>>()
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
        assertEquals(listOf(setOf(0)), received, "the selection the reactivation left should be reported once")
    }
}
