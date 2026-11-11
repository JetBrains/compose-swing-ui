// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.performTextInput
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
    fun anUndeclaredTextFieldIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { TextField(value = "", onValueChange = {}) }
        onNodeOfType<JTextField>().assertTreeMatches(JTextField())
    }

    @Test
    fun typingIntoFieldUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextField(state = state)
        }

        onNodeOfType<JTextField>().performTextReplacement("typed")

        assertEquals("typed", state.text.toString())
    }

    @Test
    fun editAppendUpdatesRealizedField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            TextField(state = state)
        }

        onNodeOfType<JTextField>().assertTextEquals("ab")

        state.edit { append("c") }
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("abc")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("textAssignments")
    fun assigningTheTextReplacesWhatTheFieldShows(
        seed: String,
        assigned: String,
    ) = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(seed)
            TextField(state = state)
        }
        state.text = assigned
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals(assigned)
    }

    companion object {
        @JvmStatic
        fun textAssignments(): List<Arguments> = listOf(
            Arguments.of(Named.of("new text", "hello"), "help"),
            Arguments.of(Named.of("empty text", "something"), ""),
        )
    }

    @Test
    fun assigningTextReplacesOnlyTheChangedSpan() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextField(state = state)
        }
        val field = onNodeOfType<JTextField>().fetch()
        state.selection = TextRange(2, 2)
        awaitIdle()
        val edits = ArrayList<String>()
        field.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    edits += "insert ${e.length} at ${e.offset}"
                }

                override fun removeUpdate(e: DocumentEvent) {
                    edits += "remove ${e.length} at ${e.offset}"
                }

                override fun changedUpdate(e: DocumentEvent) = Unit
            },
        )

        state.text = "help"
        awaitIdle()

        assertEquals(listOf("remove 2 at 3", "insert 1 at 3"), edits)
        assertEquals(TextRange(2, 2), state.selection, "the text before the changed span keeps the caret")
        assertEquals(2, field.caretPosition)
    }

    @Test
    fun settingSelectionMovesTheCaret() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello world")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>()
        state.selection = TextRange(0, 5)
        awaitIdle()

        field.assertProperty(0) { selectionStart }
        field.assertProperty(5) { selectionEnd }
        field.assertProperty("hello") { selectedText }
    }

    @Test
    fun movingCaretUpdatesStateSelection() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello world")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch()
        field.select(6, 11)
        awaitIdle()

        assertEquals(TextRange(6, 11), state.selection)
    }

    @Test
    fun writingTheTextAndThenTheSelectionPlacesTheCaret() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextField(state = state)
        }

        state.text = "filled"
        state.selection = TextRange(6, 6)
        awaitIdle()

        val field = onNodeOfType<JTextField>()
        field.assertTextEquals("filled")
        field.assertProperty(6) { caretPosition }
        assertEquals(TextRange(6, 6), state.selection)
    }

    @Test
    fun undoAndRedoRevertAndReapplyAnEdit() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("base")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>()
        assertFalse(state.canUndo)

        state.edit { append("+more") }
        awaitIdle()
        field.assertTextEquals("base+more")
        assertTrue(state.canUndo)

        state.undo()
        awaitIdle()
        field.assertTextEquals("base")
        assertTrue(state.canRedo)

        state.redo()
        awaitIdle()
        field.assertTextEquals("base+more")
    }

    @Test
    fun assigningTextUndoesAsOneStep() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextField(state = state)
        }

        // A single text assignment reaches the document as a remove followed by an insert; one undo
        // must revert the whole assignment rather than leave a torn intermediate string.
        state.text = "help"
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("help")

        state.undo()
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("hello")
        assertFalse(state.canUndo, "the assignment was the only undoable edit")
    }

    @Test
    fun multiPrimitiveEditUndoesAsOneStep() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextField(state = state)
        }

        // One edit block making several primitive edits must undo as a single compound step.
        state.edit {
            delete(0, 1)
            append("!")
            insert(0, "J")
        }
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("Jello!")

        state.undo()
        awaitIdle()
        onNodeOfType<JTextField>().assertTextEquals("hello")
    }

    @Test
    fun editBufferSupportsAllPrimitivesAndCaretPlacement() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("0123456789")
            TextField(state = state)
        }

        state.edit {
            assertEquals(10, length)
            replace(0, 2, "AB")
            insert(2, "-")
            delete(length - 1, length)
            append("!")
            placeCaretAtEnd()
        }
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("AB-2345678!")
        assertEquals(TextRange(11, 11), state.selection)
    }

    @Test
    fun editBufferSetTextReplacesWholeContent() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("old")
            TextField(state = state)
        }

        state.edit { setText("brand new") }
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("brand new")
        assertEquals("brand new", state.text.toString())
    }

    @Test
    fun editSelectAllSelectsWholeDocument() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abcde")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>()
        state.edit { selectAll() }
        awaitIdle()

        assertEquals(TextRange(0, 5), state.selection)
        field.assertProperty("abcde") { selectedText }
    }

    @Test
    fun placeCaretAtEndBeforeALaterAppendLandsAfterTheAppendedText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            TextField(state = state)
        }

        state.edit {
            placeCaretAtEnd()
            append(" more")
        }
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("ab more")
        assertEquals(TextRange(7, 7), state.selection)
    }

    @Test
    fun selectAllBeforeALaterAppendCoversTheAppendedTextToo() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>()
        state.edit {
            selectAll()
            append("cd")
        }
        awaitIdle()

        assertEquals(TextRange(0, 4), state.selection)
        field.assertProperty("abcd") { selectedText }
    }

    @Test
    fun theTextIsObservableAsAFlowOfTheCurrentValueAndSubsequentEdits() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("one")
            TextField(state = state)
        }

        // Collect the current value plus two edits with a bounded collector so the flow terminates.
        val collected = mutableListOf<CharSequence>()
        val scope = CoroutineScope(coroutineContext + Job())
        val collector =
            scope.launch {
                snapshotFlow { state.text.toString() }.take(3).toList(collected)
            }

        awaitIdle()
        // One character, so the edit the user makes is one edit: typing emits per keystroke, because a
        // keystroke is what the document takes.
        onNodeOfType<JTextField>().performTextInput("!")
        state.text = "three"
        awaitIdle()
        collector.join()

        assertEquals(listOf("one", "one!", "three"), collected.map { it.toString() })
    }
}
