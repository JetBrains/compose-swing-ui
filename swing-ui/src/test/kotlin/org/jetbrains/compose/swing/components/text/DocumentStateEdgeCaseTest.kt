package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import javax.swing.JTextField
import javax.swing.text.BadLocationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

/**
 * Behavioral coverage for [DocumentState] at its edges: calls that have nothing to do, a state driving
 * no component, ownership handed from one component to another, and out-of-range offsets handed to the
 * [DocumentEditScope]. Everything is asserted through the public state API and the rendered field.
 */
class DocumentStateEdgeCaseTest {
    @Test
    fun undoAndRedoAreNoOpsWithNothingRecorded() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("start")
            TextField(state = state)
        }

        assertFalse(state.canUndo, "a freshly seeded state has nothing to undo")
        assertFalse(state.canRedo, "a freshly seeded state has nothing to redo")

        state.undo()
        state.redo()
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("start")
    }

    @Test
    fun assigningTheSameTextRecordsNoUndoableStep() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("same")
            TextField(state = state)
        }

        state.text = "same"
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("same")
        assertFalse(state.canUndo, "assigning the text the document already holds is not an edit")
    }

    @Test
    fun anEditBlockThatChangesNothingRecordsNoUndoableStep() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("body")
            TextField(state = state)
        }

        // The block places the caret but makes no document change, so it records nothing undoable.
        state.edit { placeCaretAtEnd() }
        awaitIdle()

        assertFalse(state.canUndo, "an edit block that mutates nothing must record no undoable step")
        assertEquals(TextRange(4, 4), state.selection, "the block's caret placement still applies")
    }

    @Test
    fun selectionSetWhileUnmountedAppliesWhenAComponentBinds() = runComposeSwingTest {
        lateinit var state: DocumentState
        var mounted by mutableStateOf(false)
        setContent {
            state = rememberDocumentState("hello world")
            if (mounted) TextField(state = state)
        }

        // No component renders the state yet; the assignment is stored on the state itself.
        state.selection = TextRange(0, 5)
        assertEquals(TextRange(0, 5), state.selection, "an unmounted state still reports the assigned selection")

        mounted = true
        awaitIdle()

        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(0, field.selectionStart, "the stored selection should reach the caret on bind")
        assertEquals(5, field.selectionEnd, "the stored selection should reach the caret on bind")
    }

    @Test
    fun selectionBeyondTheDocumentSettlesToARangeTheFieldHas() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abc")
            TextField(state = state)
        }

        // A field cannot hold a caret past its last character, so the assignment settles at the
        // document's end and that settled range is the selection the state reports.
        state.selection = TextRange(99, 99)
        awaitIdle()

        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(3, field.caretPosition, "the field settles a caret past the end at the document's end")
        assertEquals(TextRange(3, 3), state.selection, "the state reports the range the field settled on")
    }

    @Test
    fun selectionLeftOutsideAShrunkDocumentSettlesWhenTheFieldMounts() = runComposeSwingTest {
        lateinit var state: DocumentState
        var mounted by mutableStateOf(false)
        setContent {
            state = rememberDocumentState("hello world")
            if (mounted) TextField(state = state)
        }

        // The selection is in range when it is assigned, and the text shrinks past it while no
        // component renders the state, so nothing settles it until a field binds.
        state.selection = TextRange(11, 11)
        state.text = "abc"

        mounted = true
        awaitIdle()

        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(3, field.caretPosition, "the caret lands at the end of the document the field renders")
        assertEquals(TextRange(3, 3), state.selection, "the state reports the range the field settled on at bind")
    }

    @Test
    fun bindingASecondComponentTakesOverFromTheFirst() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("shared text")
            TextField(state = state)
            TextField(state = state)
        }

        val fields = onAllNodesOfType<JTextField>().fetchAll()
        val first = fields[0]
        val second = fields[1]
        assertSame(first.document, second.document, "both fields render the same document")

        // A state drives at most one component: the later bind took ownership, so only that field's
        // caret feeds the state's selection.
        val selectionTheOwningFieldLeft = state.selection
        first.caret.dot = 2
        awaitIdle()
        assertEquals(
            selectionTheOwningFieldLeft,
            state.selection,
            "the unbound field's caret must not drive the state",
        )

        second.caret.dot = 7
        awaitIdle()
        assertEquals(TextRange(7, 7), state.selection, "the owning field's caret drives the state's selection")
    }

    @Test
    fun editOffsetsOutsideTheDocumentAreRejected() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abc")
            TextField(state = state)
        }

        // Offsets address the live document, so an offset past its end is refused by the document
        // itself rather than silently clamped.
        assertFailsWith<BadLocationException> { state.edit { insert(99, "x") } }
        assertFailsWith<BadLocationException> { state.edit { delete(1, 99) } }
        assertFailsWith<BadLocationException> { state.edit { replace(1, 99, "x") } }

        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("abc")
        assertFalse(state.canUndo, "a rejected edit must record no undoable step")
    }

    @Test
    fun invertedEditRangesLeaveTheDocumentUnchanged() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abcdef")
            TextField(state = state)
        }

        // An end before its start spans no characters: the deletion removes nothing.
        state.edit { delete(4, 1) }
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("abcdef")

        // An inverted replace range likewise removes nothing, and inserts at its start offset.
        state.edit { replace(4, 1, "X") }
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("abcdXef")
    }
}
