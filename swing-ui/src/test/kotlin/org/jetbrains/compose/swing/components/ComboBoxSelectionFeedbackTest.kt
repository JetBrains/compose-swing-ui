package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComboBox
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A [ComboBox]'s selection callback reports the user's choices only. The selection is declared with
 * `selectedItem`, so applying it is the wrapper writing the composition's own state to its widget - and a
 * `JComboBox` publishes an action event for such a write exactly as it does for a choice from the popup.
 *
 * In the two-way pattern the callbacks exist for (`selectedItem = state`, `onSelectionChange = { state = it }`)
 * a report of the wrapper's own write is indistinguishable from a choice the user made, so each test also
 * pins the other direction: the very next choice the user does make is still reported.
 *
 * Choosing an item from the popup reaches a `JComboBox` as `setSelectedIndex`, and committing its editor as
 * `postActionEvent` on the editor component, so the tests drive the widget those two ways.
 */
class ComboBoxSelectionFeedbackTest {
    @Test
    fun aDeclaredSelectionChangeReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf<String?>("red")
        val received = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()
        awaitIdle()
        mainClock.autoAdvance = false

        selection = "blue"
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals("blue", combo.selectedItem, "the pass declaring the selection should leave the combo box on it")
        assertEquals(emptyList(), received, "a declared selection change reported itself back")

        // The choice below is the user's own.
        mainClock.autoAdvance = true
        combo.selectedIndex = 1
        awaitIdle()

        assertEquals(listOf<String?>("green"), received, "the user's choice after a declared write should be reported")
        assertEquals("green", selection, "the user's choice reaches the caller's state")
    }

    @Test
    fun aChoiceTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        val selection = "red"
        setContent {
            ComboBox(items = listOf("red", "green", "blue"), selectedItem = selection, onSelectionChange = {})
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()

        // Choosing an item from the popup reaches the combo box the same way; with no
        // onSelectionChange to adopt it, the choice does not stand past the settle awaitIdle drives.
        combo.selectedIndex = 2
        awaitIdle()

        assertEquals("red", combo.selectedItem, "a choice the caller does not adopt does not stand")
    }

    @Test
    fun anItemsChangeReAppliesTheDeclaredSelectionSilently() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        val received = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = items.toList(),
                selectedItem = "green",
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        items += "violet"
        awaitIdle()

        assertEquals(4, combo.itemCount, "the added item reaches the combo box")
        assertEquals("green", combo.selectedItem, "the declared selection survives an items change")
        assertEquals(emptyList(), received, "an items change reported a selection change")
    }

    @Test
    fun equalItemsAreOneSelection() = runComposeSwingTest {
        val items = listOf("red", "green", "green")
        var selection by mutableStateOf<String?>("red")
        val received = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = items,
                selectedItem = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        // Declaring the later of two equal items settles on the selection the two of them share, which
        // the combo box holds at the first of them.
        selection = items[2]
        awaitIdle()

        assertEquals("green", combo.selectedItem, "the declared item reaches the combo box")
        assertEquals(1, combo.selectedIndex, "the combo box holds the declared item at the first equal one")
        assertEquals(emptyList(), received, "a declared selection change reported itself back")

        // Back to the first item, so that choosing one of the equal ones below is a change.
        selection = "red"
        awaitIdle()
        received.clear()

        // Choosing the later of two equal items is a choice of that item, whichever of them the combo
        // box holds it at, so adopting what is reported leaves the user's own choice standing.
        combo.selectedIndex = 2
        awaitIdle()

        assertEquals(listOf<String?>("green"), received, "the user's choice of a repeated item should report that item")
        assertEquals("green", selection, "the user's choice reaches the caller's state")
        assertEquals("green", combo.selectedItem, "adopting the reported item leaves the user's choice standing")
        // A `JComboBox` holds a selected object rather than a position and resolves it back to the first
        // equal item, so the index never tells two equal items apart.
        assertEquals(1, combo.selectedIndex, "the combo box holds the user's choice at the first equal item")
    }

    @Test
    fun reApplyingTheAdoptedSelectionKeepsAValueTheUserTyped() = runComposeSwingTest {
        var selection by mutableStateOf<String?>("red")
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = selection,
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

        // The caller adopted the `null` the commit reported, so the next pass declares the selection the
        // combo box already holds. What the user typed is not the composition's to replace.
        assertEquals(listOf("purple"), committed, "the commit reaches the caller")
        assertNull(selection, "the caller adopted the reported selection")
        assertEquals("purple", editor.text, "re-applying the adopted selection wiped the value the user typed")
    }

    @Test
    fun aDeclaredSelectionOfNullReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf<String?>("green")
        val received = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = selection,
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        // `null` names no selection at all, as against an item the items happen not to contain.
        selection = null
        awaitIdle()

        assertNull(combo.selectedItem, "declaring no selection reaches the combo box")
        assertEquals(emptyList(), received, "declaring no selection reported itself back")
    }

    @Test
    fun aDeclaredItemTheItemsDoNotContainReportsNothing() = runComposeSwingTest {
        var selection by mutableStateOf<String?>("green")
        val received = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = selection,
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        // The declared selection can only name one of the items; anything else names none of them.
        selection = "violet"
        awaitIdle()

        assertNull(combo.selectedItem, "an item the items do not contain selects nothing")
        assertEquals(emptyList(), received, "coercing a selection outside the items reported itself back")
    }

    @Test
    fun itemsLosingTheDeclaredSelectionReportsNothing() = runComposeSwingTest {
        val items = mutableStateListOf("red", "green", "blue")
        val received = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = items.toList(),
                selectedItem = "blue",
                onSelectionChange = { received += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        // The declared item is the last one; removing it leaves a declaration the items cannot hold.
        items.removeAt(items.lastIndex)
        awaitIdle()

        assertEquals(2, combo.itemCount, "the removed item leaves the combo box")
        assertNull(combo.selectedItem, "a selection the shrunk items no longer contain selects nothing")
        assertEquals(emptyList(), received, "the items change reported a selection change")
    }

    @Test
    fun theRawActionListenerHearsADeclaredSelectionChangeAndItsReassertion() = runComposeSwingTest {
        var selection by mutableStateOf<String?>("red")
        val received = mutableListOf<Any?>()
        val listener = ActionListener { event -> received += (event.source as JComboBox<*>).selectedItem }
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = selection,
                actionListener = listener,
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        received.clear()

        selection = "blue"
        awaitIdle()

        assertEquals("blue", combo.selectedItem, "the declared selection reaches the combo box")
        assertEquals(
            listOf<Any?>("blue"),
            received,
            "the raw listener hears the declared selection change like any other",
        )

        combo.selectedIndex = 1
        awaitIdle()

        // With no `onSelectionChange` to adopt it, the user's direct choice loses to the still-declared
        // item, which gets written back onto the combo box - a raw listener hears that write too.
        assertEquals(
            listOf<Any?>("blue", "green", "blue"),
            received,
            "the raw listener hears the user's choice and the wrapper's reassertion of the declared item",
        )
    }

    @Test
    fun aDeclaredSelectionChangeReportsNoEditorCommit() = runComposeSwingTest {
        var selection by mutableStateOf<String?>("red")
        val reported = mutableListOf<String?>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = selection,
                onSelectionChange = { reported += it },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        reported.clear()
        committed.clear()

        selection = "blue"
        awaitIdle()

        assertEquals("blue", combo.selectedItem, "the declared selection reaches the editable combo box")
        assertEquals(emptyList(), reported, "a declared selection change reported itself back")
        assertEquals(emptyList(), committed, "a declared selection change reported an editor commit")

        // What the user types is theirs alone: the items cannot express it, so it has to be reported.
        val editor = combo.editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(listOf("purple"), committed, "the user's commit after a declared write should be reported")
        assertEquals(listOf<String?>(null), reported, "the typed value matches no item, reported as no selection")
    }

    @Test
    fun theRawActionListenerHearsADeclaredSelectionChangeAsACommitAndItsReassertion() = runComposeSwingTest {
        var selection by mutableStateOf<String?>("red")
        val commands = mutableListOf<String>()
        val listener = ActionListener { event: ActionEvent -> commands += event.actionCommand }
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = selection,
                actionListener = listener,
                editable = true,
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        commands.clear()

        selection = "blue"
        awaitIdle()

        assertEquals(listOf("comboBoxChanged"), commands, "the raw listener hears the declared selection change")

        val editor = combo.editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        // A commit moves the selection and then reports itself, so one gesture reaches a raw listener as
        // both events a `JComboBox` publishes for it. With no `onSelectionChange` to adopt the commit,
        // the still-declared item then gets written back too, and the raw listener hears that as well.
        assertEquals(
            listOf("comboBoxChanged", "comboBoxChanged", "comboBoxEdited", "comboBoxChanged"),
            commands,
            "the user's commit and the wrapper's reassertion of the declared item both reach the raw listener",
        )
    }

    @Test
    fun aChoiceTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JComboBox::class.java,
            declared = "Ada",
            content = { report ->
                ComboBox(
                    items = listOf("Ada", "Alan", "Grace"),
                    selectedItem = "Ada",
                    onSelectionChange = { report() },
                )
            },
            change = { it.selectedIndex = 2 },
            read = { it.selectedItem },
        )
    }
}
