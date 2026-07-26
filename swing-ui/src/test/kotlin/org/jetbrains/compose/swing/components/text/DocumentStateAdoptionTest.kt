package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import javax.swing.text.Document
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Behavioral coverage for a [DocumentState] adopting a caller-supplied [Document]: the field renders the
 * document currently passed to [rememberDocumentState], and handing over a different one switches both
 * the rendered content and the state that drives it. Every assertion goes through the public state API
 * and the realized [JTextField].
 */
class DocumentStateAdoptionTest {
    @Test
    fun swappingTheAdoptedDocumentSwitchesTheRenderedContent() = runComposeSwingTest {
        val first = PlainDocument().apply { insertString(0, "first", null) }
        val second = PlainDocument().apply { insertString(0, "second", null) }
        var document: Document by mutableStateOf(first)
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document)
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>()
        field.assertTextEquals("first")
        assertSame(
            first,
            field.fetch().document,
            "the field renders the adopted document itself",
        )
        val firstState = state

        document = second
        awaitIdle()

        field.assertTextEquals("second")
        assertSame(
            second,
            field.fetch().document,
            "the field renders the newly adopted document itself",
        )
        assertSame(second, state.document, "the state adopts the newly passed document")
        assertNotSame(firstState, state, "a different document yields a new state")
    }

    @Test
    fun theStateOverTheSwappedDocumentDrivesTheField() = runComposeSwingTest {
        val first = PlainDocument().apply { insertString(0, "first", null) }
        val second = PlainDocument().apply { insertString(0, "second", null) }
        var document: Document by mutableStateOf(first)
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document)
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>()
        field.assertTextEquals("first")

        document = second
        awaitIdle()

        // The state built over the previous document is discarded with it, so nothing stays attached to a
        // document the caller may keep using: neither the released state's listeners nor the field's.
        assertEquals(0, first.documentListeners.size, "the released document keeps no document listener")
        assertEquals(0, first.undoableEditListeners.size, "the released document keeps no undo listener")

        state.edit { append("!") }
        awaitIdle()
        field.assertTextEquals("second!")

        // The abandoned document is no longer rendered, so editing it leaves the field untouched.
        first.insertString(first.length, " edited", null)
        awaitIdle()
        field.assertTextEquals("second!")

        field.performTextReplacement("typed")
        assertEquals("typed", state.text.toString(), "typing feeds the state over the adopted document")
        assertEquals("typed", second.getText(0, second.length), "the adopted document holds what the user typed")
    }
}
