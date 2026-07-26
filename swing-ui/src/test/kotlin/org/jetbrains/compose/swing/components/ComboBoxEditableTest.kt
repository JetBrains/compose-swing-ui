package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for an editable [ComboBox] and for the size of its popup. An editable combo box
 * accepts a value the list does not contain: the index callback reports that no item is selected any
 * more, and the commit callback carries the value itself.
 */
class ComboBoxEditableTest {
    @Test
    fun editableTracksTheDeclaredValue() = runComposeSwingTest {
        // Declared `true` first: `false` is the combo box's own default, so a wrapper that never
        // applied the value at all would still satisfy an opening `isEditable(false)`.
        var editable by mutableStateOf(true)
        setContent {
            ComboBox(items = listOf("red", "green"), selectedIndex = -1, editable = editable)
        }

        onNodeOfType<JComboBox<*>>().assert(SwingMatcher.isEditable())

        editable = false
        awaitIdle()
        onNodeOfType<JComboBox<*>>().assert(SwingMatcher.isEditable(false))
    }

    @Test
    fun aTypedValueOutsideTheModelIsReportedAsACommit() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("red", "green"))
        val reported = mutableListOf<Int>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                model = model,
                onSelectionChange = { reported += it },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals(emptyList(), reported, "rendering the declared selection must not report a change")

        // Committing the editor is what the user's Enter key does: the typed text becomes the combo
        // box's selected item even though no item equals it.
        val editor = combo.editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(-1, combo.selectedIndex, "a value outside the model leaves the combo box with no selection")
        assertEquals(listOf(-1), reported, "no item is selected any more, reported once")
        assertEquals(listOf("purple"), committed, "the typed value should reach the commit callback")
        assertEquals("purple", model.selectedItem, "the typed value should land on the caller's model")
    }

    @Test
    fun aTypedValueOutsideTheItemsIsReportedAsACommit() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedIndex = 0,
                onSelectionChange = { reported += it },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val editor = onNodeOfType<JComboBox<*>>().fetch().editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(listOf("purple"), committed, "the typed value should reach the commit callback")
        assertEquals(listOf(-1), reported, "no item is selected any more, reported once")
    }

    @Test
    fun committingAnItemReportsBothItsIndexAndItsValue() = runComposeSwingTest {
        // The index a commit reports is adopted into state and fed back as the declaration, the way a
        // real caller wires a controlled ComboBox: a declaration that stayed behind would be reasserted
        // over the very commit this test is checking for.
        var selectedIndex by mutableStateOf(-1)
        val reported = mutableListOf<Int>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedIndex = selectedIndex,
                onSelectionChange = {
                    reported += it
                    selectedIndex = it
                },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        val editor = combo.editor.editorComponent as JTextField
        editor.text = "green"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(1, combo.selectedIndex, "a typed value that matches an item selects it")
        assertEquals(listOf(1), reported, "the selected index should be reported")
        assertEquals(listOf("green"), committed, "the committed text should be reported")
    }

    @Test
    fun maximumRowCountTracksTheDeclaredValue() = runComposeSwingTest {
        var rows by mutableStateOf(3)
        setContent {
            ComboBox(items = listOf("red", "green", "blue"), selectedIndex = -1, maximumRowCount = rows)
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals(3, combo.maximumRowCount, "the declared maximum row count should reach the combo box")

        rows = 5
        awaitIdle()
        assertEquals(5, combo.maximumRowCount, "changing the maximum row count should reach the combo box")
    }
}
