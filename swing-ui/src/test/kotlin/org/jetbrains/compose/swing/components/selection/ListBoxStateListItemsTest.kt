package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeListener
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ListBox] declared over a list the caller keeps and mutates, rather than one rebuilt each pass.
 *
 * Declared items reach the list as a model built around a copy of them, so a pass that adopts new items
 * swaps the model the list holds. These tests assert on that swap, because it is what a `JList` acts on:
 * the model it was holding is left behind entirely, and a model that was never swapped is still showing
 * the old items however the caller's own list reads back.
 */
class ListBoxStateListItemsTest {
    @Test
    fun addingToADeclaredStateListReachesTheList() = runComposeSwingTest {
        val items = mutableStateListOf("Ada")
        setContent { ListBox(items = items) }

        val list = onNodeOfType<JList<*>>().fetch()
        val swaps = list.recordModelSwaps()

        items.add("Alan")
        awaitIdle()

        assertTrue(swaps.isNotEmpty(), "the list was never given a model holding the new item")
        assertEquals(listOf("Ada", "Alan"), list.elements(), "the list should show both items")
    }

    @Test
    fun removingFromADeclaredStateListReachesTheList() = runComposeSwingTest {
        val items = mutableStateListOf("Ada", "Alan")
        setContent { ListBox(items = items) }

        val list = onNodeOfType<JList<*>>().fetch()
        val swaps = list.recordModelSwaps()

        items.removeAt(0)
        awaitIdle()

        assertTrue(swaps.isNotEmpty(), "the list was never given a model holding the remaining item")
        assertEquals(listOf("Alan"), list.elements(), "the list should show the remaining item")
    }

    @Test
    fun aPassThatChangesNoItemSwapsNoModel() = runComposeSwingTest {
        val items = mutableStateListOf("Ada")
        var visibleRowCount by mutableStateOf(4)
        setContent { ListBox(items = items, visibleRowCount = visibleRowCount) }

        val list = onNodeOfType<JList<*>>().fetch()
        val swaps = list.recordModelSwaps()

        visibleRowCount = 6
        awaitIdle()

        assertEquals(6, list.visibleRowCount, "the pass should have applied the new row count")
        assertTrue(swaps.isEmpty(), "a pass that changed no item should leave the list on the model it holds")
    }
}

/** Records every model the list is given from now on, newest last. */
private fun JList<*>.recordModelSwaps(): List<Any?> {
    val swaps = mutableListOf<Any?>()
    addPropertyChangeListener("model", PropertyChangeListener { swaps += it.newValue })
    return swaps
}

/** The items the list is currently showing, in order. */
private fun JList<*>.elements(): List<Any?> = (0 until model.size).map { model.getElementAt(it) }
