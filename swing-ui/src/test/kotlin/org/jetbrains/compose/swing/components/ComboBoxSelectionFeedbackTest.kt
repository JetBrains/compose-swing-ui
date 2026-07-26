package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComboBox
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A [ComboBox]'s selection callback reports the user's choices only. The selection is declared with
 * `selectedIndex`, so applying it is the wrapper writing the composition's own state to its widget - and a
 * `JComboBox` publishes an action event for such a write exactly as it does for a choice from the popup.
 *
 * In the two-way pattern the callbacks exist for (`selectedIndex = state`, `onSelectionChange = { state = it }`)
 * a report of the wrapper's own write is indistinguishable from a choice the user made, so each test also
 * pins the other direction: the very next choice the user does make is still reported.
 *
 * Choosing an item from the popup reaches a `JComboBox` as `setSelectedIndex`, and committing its editor as
 * `postActionEvent` on the editor component, so the tests drive the widget those two ways.
 */
class ComboBoxSelectionFeedbackTest {
    @Test
    fun aDeclaredSelectionChangeReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf(0)
        val received = mutableListOf<Int>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedIndex = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        selection = 2
        awaitIdle()

        assertEquals(2, combo.selectedIndex, "the declared selection reaches the combo box")
        assertEquals(emptyList(), received, "a declared selection change reported itself back")

        // The latched write must not outlive itself: the user's next choice is news either way.
        combo.selectedIndex = 1
        awaitIdle()

        assertEquals(listOf(1), received, "the user's choice after a declared write should be reported")
        assertEquals(1, selection, "the user's choice reaches the caller's state")
    }

    @Test
    fun aChoiceTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        val selection = 0
        setContent {
            ComboBox(items = listOf("red", "green", "blue"), selectedIndex = selection)
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()

        // Choosing an item from the popup reaches the combo box the same way; with no
        // onSelectionChange to adopt it, the choice does not stand past the settle awaitIdle drives.
        combo.selectedIndex = 2
        awaitIdle()

        assertEquals(0, combo.selectedIndex, "a choice the caller does not adopt does not stand")
    }

    @Test
    fun anItemsChangeReAppliesTheDeclaredSelectionSilently() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        val received = mutableListOf<Int>()
        setContent {
            ComboBox(
                items = items.toList(),
                selectedIndex = 1,
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        items += "violet"
        awaitIdle()

        assertEquals(4, combo.itemCount, "the added item reaches the combo box")
        assertEquals(1, combo.selectedIndex, "the declared selection survives an items change")
        assertEquals(emptyList(), received, "an items change reported a selection change")
    }

    @Test
    fun reApplyingTheAdoptedIndexKeepsAValueTheUserTyped() = runComposeSwingTest {
        var selection by mutableStateOf(0)
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedIndex = selection,
                onSelectionChange = { selection = it },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        val editor = combo.editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        // The caller adopted the `-1` the commit reported, so the next pass declares an index the combo
        // box already holds. What the user typed is not the composition's to replace.
        assertEquals(listOf("purple"), committed, "the commit reaches the caller")
        assertEquals(-1, selection, "the caller adopted the reported index")
        assertEquals("purple", editor.text, "re-applying the adopted index wiped the value the user typed")
    }

    @Test
    fun aDeclaredSelectionOfMinusOneReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf(1)
        val received = mutableListOf<Int>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedIndex = selection,
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        // `-1` is JSwing's own sentinel for no selection, not an index the items fail to cover.
        selection = -1
        awaitIdle()

        assertEquals(-1, combo.selectedIndex, "declaring no selection reaches the combo box")
        assertEquals(emptyList(), received, "declaring no selection reported itself back")
    }

    @Test
    fun aDeclaredSelectionPastTheLastItemReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf(1)
        val received = mutableListOf<Int>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedIndex = selection,
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        // Three items cover indices 0..2; declaring the index one past the last one is out of range.
        selection = 3
        awaitIdle()

        assertEquals(-1, combo.selectedIndex, "a selection past the last item selects nothing")
        assertEquals(emptyList(), received, "coercing an out-of-range selection reported itself back")
    }

    @Test
    fun itemsShrinkingBelowTheDeclaredSelectionReportsNothing() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        val received = mutableListOf<Int>()
        setContent {
            ComboBox(
                items = items.toList(),
                selectedIndex = 2,
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        // The declared index (2) named the last item; removing it leaves the declaration out of range.
        items.removeAt(items.lastIndex)
        awaitIdle()

        assertEquals(2, combo.itemCount, "the removed item leaves the combo box")
        assertEquals(-1, combo.selectedIndex, "a selection the shrunk items no longer cover selects nothing")
        assertEquals(emptyList(), received, "the items change reported a selection change")
    }

    @Test
    fun theRawActionListenerHearsADeclaredSelectionChangeAndItsReassertion() = runComposeSwingTest {
        var selection by mutableStateOf(0)
        val received = mutableListOf<Int>()
        val listener = ActionListener { event -> received += (event.source as JComboBox<*>).selectedIndex }
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                actionListener = listener,
                selectedIndex = selection,
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        selection = 2
        awaitIdle()

        assertEquals(2, combo.selectedIndex, "the declared selection reaches the combo box")
        assertEquals(listOf(2), received, "the raw listener hears the declared selection change like any other")

        combo.selectedIndex = 1
        awaitIdle()

        // With no `onSelectionChange` to adopt it, the user's direct choice loses to the still-declared
        // index, which gets written back onto the combo box - a raw listener hears that write too.
        assertEquals(
            listOf(2, 1, 2),
            received,
            "the raw listener hears the user's choice and the wrapper's reassertion of the declared index",
        )
    }

    @Test
    fun aDeclaredSelectionChangeReportsNoEditorCommit() = runComposeSwingTest {
        var selection by mutableStateOf(0)
        val reported = mutableListOf<Int>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedIndex = selection,
                onSelectionChange = { reported += it },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        reported.clear()
        committed.clear()

        selection = 2
        awaitIdle()

        assertEquals(2, combo.selectedIndex, "the declared selection reaches the editable combo box")
        assertEquals(emptyList(), reported, "a declared selection change reported itself back")
        assertEquals(emptyList(), committed, "a declared selection change reported an editor commit")

        // What the user types is theirs alone: the items cannot express it, so it has to be reported.
        val editor = combo.editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(listOf("purple"), committed, "the user's commit after a declared write should be reported")
        assertEquals(listOf(-1), reported, "the typed value matches no item, reported as no selection")
    }

    @Test
    fun theRawActionListenerHearsADeclaredSelectionChangeAsACommitAndItsReassertion() = runComposeSwingTest {
        var selection by mutableStateOf(0)
        val commands = mutableListOf<String>()
        val listener = ActionListener { event: ActionEvent -> commands += event.actionCommand }
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                actionListener = listener,
                selectedIndex = selection,
                editable = true,
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        commands.clear()

        selection = 2
        awaitIdle()

        assertEquals(listOf("comboBoxChanged"), commands, "the raw listener hears the declared selection change")

        val editor = combo.editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        // A commit moves the selection and then reports itself, so one gesture reaches a raw listener as
        // both events a `JComboBox` publishes for it. With no `onSelectionChange` to adopt the commit,
        // the still-declared index then gets written back too, and the raw listener hears that as well.
        assertEquals(
            listOf("comboBoxChanged", "comboBoxChanged", "comboBoxEdited", "comboBoxChanged"),
            commands,
            "the user's commit and the wrapper's reassertion of the declared index both reach the raw listener",
        )
    }
}
