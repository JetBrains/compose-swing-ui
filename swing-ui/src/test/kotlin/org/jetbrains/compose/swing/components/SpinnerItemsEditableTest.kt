package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSpinner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the field a [Spinner] over items shows an edit through: it must be editable,
 * the way a bare `JSpinner(SpinnerListModel(items))` is, and a committed edit must reach the model as
 * the item the text renders rather than as the text itself.
 */
class SpinnerItemsEditableTest {
    @Test
    fun theFieldAnItemsSpinnerShowsIsEditable() = runComposeSwingTest {
        setContent { Spinner(items = listOf("Ada", "Alan"), value = "Ada", onValueChange = {}) }

        val field = (onNodeOfType<JSpinner>().fetch().editor as JSpinner.DefaultEditor).textField
        assertTrue(field.isEditable, "an items spinner's field should be editable, the way a bare JSpinner's is")
    }

    @Test
    fun theFieldAnItemsSpinnerShowsRendersTheSelectedItem() = runComposeSwingTest {
        setContent { Spinner(items = Name.entries, value = Name.Alan, onValueChange = {}) }

        val field = (onNodeOfType<JSpinner>().fetch().editor as JSpinner.DefaultEditor).textField
        assertEquals("Alan", field.text, "the field shows the selected item as it renders")
    }

    @Test
    fun aCommittedEditLandingOnAnItemReportsTheItemNotItsText() = runComposeSwingTest {
        // Adopting the reported value into state is what a real controlled Spinner does; a declaration
        // left behind would reassert itself over the very commit this test is checking for.
        var value by mutableStateOf(Name.Ada)
        val changes = mutableListOf<Name>()
        setContent {
            Spinner(
                items = Name.entries,
                value = value,
                onValueChange = {
                    changes += it
                    value = it
                },
            )
        }

        val spinner = onNodeOfType<JSpinner>().fetch()
        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        field.text = "Alan"
        field.commitEdit()
        awaitIdle()

        assertEquals(
            listOf(Name.Alan),
            changes,
            "the committed edit should reach the change callback as the item, not as the text",
        )
        assertSame(Name.Alan, spinner.value, "the model should hold the item the text renders, not the text")
    }

    @Test
    fun aCommitAfterTheItemsChangedResolvesAgainstTheNewOnes() = runComposeSwingTest {
        var items by mutableStateOf(listOf("Ada", "Alan"))
        var value by mutableStateOf<String?>("Ada")
        val changes = mutableListOf<String>()
        setContent {
            Spinner(
                items = items,
                value = value,
                onValueChange = {
                    changes += it
                    value = it
                },
            )
        }
        awaitIdle()

        // The editor survives the swap, so the field it shows is one built around the items the spinner
        // no longer holds.
        items = listOf("Grace", "Barbara")
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        field.text = "Barbara"
        field.commitEdit()
        awaitIdle()

        assertEquals("Barbara", spinner.value, "the commit resolves against the items the spinner now holds")
        assertEquals("Barbara", changes.last(), "and reports that item")
    }

    @Test
    fun anEditNamingNoItemIsRejectedAndRevertsTheField() = runComposeSwingTest {
        val changes = mutableListOf<Name>()
        setContent { Spinner(items = Name.entries, value = Name.Ada, onValueChange = { changes += it }) }

        val spinner = onNodeOfType<JSpinner>().fetch()
        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        field.text = "Nope"

        field.commitEdit()
        awaitIdle()

        assertEquals("Ada", field.text, "an edit the items cannot resolve reverts to the value the spinner holds")
        assertEquals(emptyList(), changes, "an edit naming no item should never reach the change callback")
        assertSame(Name.Ada, spinner.value, "the model must not move for an edit it cannot resolve")
    }

    @Test
    fun typingAPrefixCompletesItToTheItemItNames() = runComposeSwingTest {
        var value by mutableStateOf("Ada")
        setContent {
            Spinner(
                items = listOf("Ada", "Alan", "Barbara", "Grace"),
                value = value,
                onValueChange = { value = it },
            )
        }

        val spinner = onNodeOfType<JSpinner>().fetch()
        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        // What DefaultEditorKit's key-typed action calls for a character, so the edit travels the
        // document filter exactly as a keystroke does. The harness's own typing gesture arrives later
        // on this branch, and this commit has to stand on its own.
        field.selectAll()
        field.replaceSelection("B")
        awaitIdle()

        assertEquals("Barbara", field.text, "a prefix must complete to the item that starts with it")
        assertEquals(1, field.selectionStart, "what the user typed must stay unselected")
        assertEquals(
            "Barbara".length,
            field.selectionEnd,
            "the completed tail must be selected, so the next keystroke replaces it",
        )

        field.commitEdit()
        awaitIdle()

        assertEquals("Barbara", spinner.value, "a completed prefix must commit as the item it completed to")
    }

    @Test
    fun typingAtTheEndWithNothingSelectedInsertsWhatNoItemCompletes() = runComposeSwingTest {
        var value by mutableStateOf("Ada")
        setContent {
            Spinner(
                items = listOf("Ada", "Alan", "Barbara", "Grace"),
                value = value,
                onValueChange = { value = it },
            )
        }

        val spinner = onNodeOfType<JSpinner>().fetch()
        val field = (spinner.editor as JSpinner.DefaultEditor).textField
        field.selectAll()
        field.replaceSelection("Barb")
        awaitIdle()

        // Sending the caret to the end drops the selection the completion left, so the next character
        // is an insertion rather than a replacement - the filter's other entry point.
        field.caretPosition = field.text.length
        field.replaceSelection("s")
        awaitIdle()

        assertEquals(
            "Barbaras",
            field.text,
            "a character no item's text carries on must reach the document as it stands",
        )
    }
}

/** An item type that is not its own rendering, so text resolved back to an item cannot be that text. */
private enum class Name { Ada, Alan }
