package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
 * A composable cell is owned by the composition too: the cell composition rendering it lives only while
 * the node is in the composition, so a list that outlives its own composable cell - parked before it is
 * torn down - paints that row through the renderer it renders through before that cell, and the fresh
 * list reactivation builds renders the composable cell again.
 */
class ListBoxNodeReuseTest {
    @Test
    fun aParkedListRendersItsOwnCellsAndTheFreshOneRendersTheComposableCellAgain() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = colors) { item -> Label("<$item>") }
            }
        }
        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        assertEquals("<red>", list.renderCell(index = 0).firstLabelText(), "the composable cell should render row 0")

        active = false
        awaitIdle()

        // A parked list keeps painting through whatever renderer it carries once the composition rendering
        // its composable cell is gone: the renderer it rendered through before that cell is what has to
        // be back on it by then.
        val parked = list.renderCell(index = 0)
        assertTrue(parked is JLabel, "a parked list should render rows through the renderer of its own")
        assertEquals("red", (parked as JLabel).text, "the list's own renderer renders the item's toString")

        active = true
        awaitIdle()

        assertEquals(
            "<green>",
            onNodeOfType<JList<*>>().fetch<JList<String>>().renderCell(index = 1).firstLabelText(),
            "the fresh list should render the composable cell",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declarations")
    fun aReactivatedListStillHoldsItsControlledSelection(list: @Composable () -> Unit) = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent { ReusableContentHost(active = active) { list() } }
        onNodeOfType<JList<*>>().assertProperty(
            listOf(1),
            "the controlled selection should be applied",
        ) { selectedIndices.toList() }

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(
            listOf(1),
            "the fresh list should hold the controlled selection",
        ) { selectedIndices.toList() }
    }

    companion object {
        private val colors = listOf("red", "green")

        private fun listModel(size: Int = colors.size): DefaultListModel<String> =
            DefaultListModel<String>().apply { for (color in colors.take(size)) addElement(color) }

        @JvmStatic
        fun declarations(): List<Named<@Composable () -> Unit>> {
            val model = listModel()
            return listOf(
                Named.of("items", { ListBox(items = colors, selectedIndices = setOf(1)) }),
                Named.of("caller's model", { ListBox(model = model, selectedIndices = setOf(1)) }),
            )
        }
    }
}
