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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A parked [ReusableContentHost] child detaches its `JList`, and reactivation builds a fresh one from
 * the node's own factory: a controlled selection reaches the fresh list the same way it reaches any
 * freshly composed one.
 *
 * A composable cell is owned by the composition too: the island stamping it lives only while the node is
 * in the composition, so a list that outlives its own composable cell - parked before it is torn down -
 * paints that row through the renderer it renders through before that cell, and the fresh list
 * reactivation builds stamps the composable cell again.
 */
class ListBoxNodeReuseTest {
    private val colors = listOf("red", "green")

    /** A caller-owned model holding the first [size] of [colors]. */
    private fun listModel(size: Int = colors.size): DefaultListModel<String> =
        DefaultListModel<String>().apply { for (color in colors.take(size)) addElement(color) }

    @Test
    fun aParkedListRendersItsOwnCellsAndTheFreshOneStampsTheComposableCellAgain() = runComposeSwingTest {
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

        // A parked list keeps painting through whatever renderer it carries once the cell island behind
        // its composable cell is gone: the renderer it rendered through before that cell is what has to
        // be back on it by then.
        val parked = list.stampCell(index = 0)
        assertTrue(parked is JLabel, "a parked list should render rows through the renderer of its own")
        assertEquals("red", (parked as JLabel).text, "the list's own renderer renders the item's toString")

        active = true
        awaitIdle()

        assertEquals(
            "green",
            onNodeOfType<JList<*>>().fetch<JList<String>>().stampCell(index = 1).firstLabelText(),
            "the fresh list should stamp the composable cell",
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
            "the fresh list should hold the controlled selection",
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
            "the fresh model-driven list should hold the controlled selection",
        )
    }
}
