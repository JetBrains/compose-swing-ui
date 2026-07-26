package org.jetbrains.compose.swing.components.text

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for [DocumentState] driving a realized [TextField] over a real applier. Every
 * assertion goes through the public state API and the rendered [JTextField], never a private field: the
 * shared document is the single source of truth, so an edit on either side is observable on the other.
 */
class TextFieldStateTest {
    @Test
    fun typingIntoFieldUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        field.text = "typed"
        awaitIdle()

        assertEquals("typed", state.text.toString())
    }

    @Test
    fun editAppendUpdatesRealizedField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertEquals("ab", field.text)

        state.edit { append("c") }
        awaitIdle()
        assertEquals("abc", field.text)
    }

    @Test
    fun assigningTextReplacesFieldContentAtMinimalSpan() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        state.text = "help"
        awaitIdle()
        assertEquals("help", field.text)
        assertEquals("help", state.text.toString())
    }

    @Test
    fun settingSelectionMovesTheCaret() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello world")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        state.selection = TextRange(0, 5)
        awaitIdle()

        assertEquals(0, field.selectionStart)
        assertEquals(5, field.selectionEnd)
        assertEquals("hello", field.selectedText)
    }

    @Test
    fun movingCaretUpdatesStateSelection() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello world")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        field.select(6, 11)
        awaitIdle()

        assertEquals(TextRange(6, 11), state.selection)
    }

    @Test
    fun clearTextEmptiesTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("something")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        state.clearText()
        awaitIdle()

        assertEquals("", field.text)
        assertEquals("", state.text.toString())
    }

    @Test
    fun setTextAndPlaceCaretAtEndPlacesCaret() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        state.setTextAndPlaceCaretAtEnd("filled")
        awaitIdle()

        assertEquals("filled", field.text)
        assertEquals(6, field.caretPosition)
        assertEquals(TextRange(6, 6), state.selection)
    }

    @Test
    fun undoAndRedoRevertAndReapplyAnEdit() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("base")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertFalse(state.canUndo)

        state.edit { append("+more") }
        awaitIdle()
        assertEquals("base+more", field.text)
        assertTrue(state.canUndo)

        state.undo()
        awaitIdle()
        assertEquals("base", field.text)
        assertTrue(state.canRedo)

        state.redo()
        awaitIdle()
        assertEquals("base+more", field.text)
    }

    @Test
    fun assigningTextUndoesAsOneStep() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        // A single text assignment reaches the document as a remove followed by an insert; one undo
        // must revert the whole assignment rather than leave a torn intermediate string.
        state.text = "help"
        awaitIdle()
        assertEquals("help", field.text)

        state.undo()
        awaitIdle()
        assertEquals("hello", field.text, "assigning text must undo as a single step")
        assertFalse(state.canUndo, "the assignment was the only undoable edit")
    }

    @Test
    fun multiPrimitiveEditUndoesAsOneStep() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        // One edit block making several primitive edits must undo as a single compound step.
        state.edit {
            delete(0, 1)
            append("!")
            insert(0, "J")
        }
        awaitIdle()
        assertEquals("Jello!", field.text)

        state.undo()
        awaitIdle()
        assertEquals("hello", field.text, "the whole edit block should revert in one undo")
    }

    @Test
    fun editBufferSupportsAllPrimitivesAndCaretPlacement() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("0123456789")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        state.edit {
            assertEquals(10, length)
            replace(0, 2, "AB")
            insert(2, "-")
            delete(length - 1, length)
            append("!")
            placeCaretAtEnd()
        }
        awaitIdle()

        assertEquals("AB-2345678!", field.text)
        assertEquals(TextRange(11, 11), state.selection)
    }

    @Test
    fun editBufferSetTextReplacesWholeContent() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("old")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        state.edit { setText("brand new") }
        awaitIdle()

        assertEquals("brand new", field.text)
        assertEquals("brand new", state.text.toString())
    }

    @Test
    fun editSelectAllSelectsWholeDocument() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abcde")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        state.edit { selectAll() }
        awaitIdle()

        assertEquals(TextRange(0, 5), state.selection)
        assertEquals("abcde", field.selectedText)
    }

    @Test
    fun textAsFlowEmitsCurrentTextAndSubsequentEdits() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("one")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()

        // Collect the current value plus two edits with a bounded collector so the flow terminates.
        val collected = mutableListOf<CharSequence>()
        val scope = CoroutineScope(coroutineContext + Job())
        val collector =
            scope.launch {
                state.textAsFlow().take(3).toList(collected)
            }

        awaitIdle()
        field.text = "two"
        awaitIdle()
        state.setTextAndPlaceCaretAtEnd("three")
        awaitIdle()
        collector.join()

        assertEquals(listOf("one", "two", "three"), collected.map { it.toString() })
    }
}
