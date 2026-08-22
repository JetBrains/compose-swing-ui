package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import javax.swing.JComboBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ComboBox] declared over a list the caller keeps and mutates, rather than one rebuilt each pass.
 *
 * Declared items reach the combo box as a model built around a copy of them, so a pass that adopts new
 * items swaps the model the combo box holds. These tests assert on that swap, because it is what a
 * `JComboBox` acts on: a model that was never swapped is still offering the old items however the
 * caller's own list reads back.
 */
class ComboBoxStateListItemsTest {
    @Test
    fun addingToADeclaredStateListReachesTheComboBox() = runComposeSwingTest {
        val items = mutableStateListOf("Ada")
        setContent { ComboBox(items = items, selectedItem = "Ada", onSelectionChange = {}) }

        val comboBox = onNodeOfType<JComboBox<*>>().fetch()
        val swaps = comboBox.recordModelSwaps()

        items.add("Alan")
        awaitIdle()

        assertTrue(swaps.isNotEmpty(), "the combo box was never given a model holding the new item")
        assertEquals(listOf("Ada", "Alan"), comboBox.elements(), "the combo box should offer both items")
    }

    @Test
    fun removingFromADeclaredStateListReachesTheComboBox() = runComposeSwingTest {
        val items = mutableStateListOf("Ada", "Alan")
        setContent { ComboBox(items = items, selectedItem = "Ada", onSelectionChange = {}) }

        val comboBox = onNodeOfType<JComboBox<*>>().fetch()
        val swaps = comboBox.recordModelSwaps()

        items.removeAt(1)
        awaitIdle()

        assertTrue(swaps.isNotEmpty(), "the combo box was never given a model holding the remaining item")
        assertEquals(listOf("Ada"), comboBox.elements(), "the combo box should offer the remaining item")
    }

    @Test
    fun addingToADeclaredStateListReachesARawListenerComboBox() = runComposeSwingTest {
        val items = mutableStateListOf("Ada")
        setContent {
            ComboBox(items = items, selectedItem = "Ada", actionListener = ActionListener { })
        }

        val comboBox = onNodeOfType<JComboBox<*>>().fetch()
        val swaps = comboBox.recordModelSwaps()

        items.add("Alan")
        awaitIdle()

        assertTrue(swaps.isNotEmpty(), "the combo box was never given a model holding the new item")
        assertEquals(listOf("Ada", "Alan"), comboBox.elements(), "the combo box should offer both items")
    }

    @Test
    fun aPassThatChangesNoItemSwapsNoModel() = runComposeSwingTest {
        val items = mutableStateListOf("Ada")
        var maximumRowCount by mutableStateOf(4)
        setContent {
            ComboBox(
                items = items,
                selectedItem = "Ada",
                onSelectionChange = {},
                maximumRowCount = maximumRowCount,
            )
        }

        val comboBox = onNodeOfType<JComboBox<*>>().fetch()
        val swaps = comboBox.recordModelSwaps()

        maximumRowCount = 6
        awaitIdle()

        assertEquals(6, comboBox.maximumRowCount, "the pass should have applied the new row count")
        assertTrue(
            swaps.isEmpty(),
            "a pass that changed no item should leave the combo box on the model it holds",
        )
    }
}

/** Records every model the combo box is given from now on, newest last. */
private fun JComboBox<*>.recordModelSwaps(): List<Any?> {
    val swaps = mutableListOf<Any?>()
    addPropertyChangeListener("model", PropertyChangeListener { swaps += it.newValue })
    return swaps
}

/** The items the combo box is currently offering, in order. */
private fun JComboBox<*>.elements(): List<Any?> = (0 until model.size).map { model.getElementAt(it) }
