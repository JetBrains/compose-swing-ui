package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the attributes a [DocumentState.edit] block writes text with. A styled
 * document - the model a [TextPane] renders and an editable [EditorPane]'s kit builds - keeps them on
 * the run they arrived with, which is what lets a caller author a bold word among plain ones. A document
 * that holds characters alone takes the text and nothing else.
 */
class DocumentEditAttributesTest {
    @Test
    fun anAppendedRunKeepsTheAttributesItWasWrittenWith() = runComposeSwingTest {
        val document = DefaultStyledDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        state.edit {
            append("plain ")
            append("bold", bold())
        }
        awaitIdle()

        assertEquals("plain bold", state.text.toString(), "both runs are appended, one after the other")
        assertFalse(document.isBoldAt(0), "the run written with no attributes stays plain")
        assertTrue(document.isBoldAt("plain ".length), "the attributed run keeps what it was written with")
    }

    @Test
    fun anInsertedRunCarriesItsAttributesIntoTheMiddleOfPlainText() = runComposeSwingTest {
        val document = DefaultStyledDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        state.edit {
            setText("ab")
            insert(1, "X", bold())
        }
        awaitIdle()

        assertEquals("aXb", state.text.toString(), "the insertion lands between the characters it split")
        assertFalse(document.isBoldAt(0), "the text the insertion split stays plain")
        assertTrue(document.isBoldAt(1), "the inserted run is the attributed one")
        assertFalse(document.isBoldAt(2), "and the text it shifted right stays plain")
    }

    @Test
    fun aReplacedSpanCarriesItsAttributes() = runComposeSwingTest {
        val document = DefaultStyledDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        state.edit {
            setText("abcd")
            replace(1, 3, "XY", bold())
        }
        awaitIdle()

        assertEquals("aXYd", state.text.toString(), "the replacement stands where the span it replaced was")
        assertFalse(document.isBoldAt(0), "the text before the replaced span stays plain")
        assertTrue(document.isBoldAt(1), "the replacement carries its attributes")
        assertTrue(document.isBoldAt(2), "across the whole of it")
        assertFalse(document.isBoldAt(3), "and the text after it stays plain")
    }

    @Test
    fun aWholeBufferReplacementCarriesItsAttributes() = runComposeSwingTest {
        val document = DefaultStyledDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        state.edit {
            setText("plain")
            setText("styled", bold())
        }
        awaitIdle()

        assertEquals("styled", state.text.toString(), "the replacement stands in place of the whole buffer")
        assertTrue(document.isBoldAt(0), "the buffer the replacement left holds the attributes it carried")
    }

    @Test
    fun aDocumentThatHoldsCharactersAloneTakesTheTextAndNothingElse() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextField(state = state)
        }

        state.edit { append("bold", bold()) }
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("bold")
    }
}

// A character-attribute set marking the run it is written with as bold.
private fun bold(): AttributeSet = SimpleAttributeSet().also { StyleConstants.setBold(it, true) }

// Whether the character at [offset] is bold in this styled document.
private fun StyledDocument.isBoldAt(offset: Int): Boolean =
    StyleConstants.isBold(getCharacterElement(offset).attributes)
