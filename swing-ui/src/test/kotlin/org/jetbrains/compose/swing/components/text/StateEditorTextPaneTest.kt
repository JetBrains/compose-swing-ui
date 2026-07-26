package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.html.HTMLEditorKit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the state-based [EditorPane] and [TextPane] overloads. Every assertion goes
 * through the public state API and the rendered Swing component, never a private field: the pane shares
 * the state's document, so an edit made through the state is what the pane displays and text typed into
 * the pane is what the state reports. The component-specific parameters (content type, editability) are
 * asserted on the live component.
 */
class StateEditorTextPaneTest {
    @Test
    fun editorPaneSharesTheStateDocument() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        assertSame(state.document, pane.document, "the pane must render the state's own document")
        assertEquals("seed", pane.text, "the pane must render the state's initial content")
    }

    @Test
    fun editingStateUpdatesEditorPane() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        assertEquals("ab", pane.text)

        state.edit { append("c") }
        awaitIdle()
        assertEquals("abc", pane.text, "an edit through the state must reach the pane")

        state.text = "xyz"
        awaitIdle()
        assertEquals("xyz", pane.text, "assigning text must reach the pane")
    }

    @Test
    fun typingIntoEditorPaneUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        pane.text = "typed"
        awaitIdle()

        assertEquals("typed", state.text.toString(), "text typed into the pane must reach the state")
    }

    @Test
    fun plainDocumentStateRendersEditorPaneAsPlainText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("plain")
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        assertEquals("text/plain", pane.contentType, "a plain-document state renders as plain text")
        assertSame(state.document, pane.document, "the pane renders the state's own document")
        assertEquals("plain", pane.text, "the pane renders the state's initial content")
    }

    @Test
    fun htmlDocumentStateRendersEditorPaneAsHtmlAndStaysAuthoritative() = runComposeSwingTest {
        val document = HTMLEditorKit().createDefaultDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        // The document type carries the content type: an HTML document makes the pane render as HTML.
        assertEquals("text/html", pane.contentType, "an HTML-document state renders as HTML")
        assertSame(document, pane.document, "the pane renders the state's own HTML document")

        state.edit { append("hello") }
        awaitIdle()
        assertTrue(
            state.text.toString().contains("hello"),
            "the state keeps driving the HTML document it renders",
        )
        assertTrue(pane.text.contains("hello"), "an edit through the state reaches the rendered pane")
    }

    @Test
    fun editorPaneRespectsEditableFlag() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("x")
            EditorPane(state = state, editable = editable)
        }

        val pane = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        assertTrue(pane.isEditable, "the pane should start editable")

        editable = false
        awaitIdle()
        assertFalse(pane.isEditable, "the pane should become read-only when editable is false")
    }

    @Test
    fun textPaneSharesTheStateStyledDocument() = runComposeSwingTest {
        val document = DefaultStyledDocument().apply { insertString(0, "seed", null) }
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        val pane = onNodeOfType<JTextPane>().fetch<JTextPane>()
        assertSame(document, pane.document, "the text pane must render the state's own styled document")
        assertEquals("seed", pane.text, "the text pane must render the state's initial content")
    }

    @Test
    fun editingStateUpdatesTextPane() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = DefaultStyledDocument().apply { insertString(0, "ab", null) })
            TextPane(state = state)
        }

        val pane = onNodeOfType<JTextPane>().fetch<JTextPane>()
        assertEquals("ab", pane.text)

        state.edit { append("c") }
        awaitIdle()
        assertEquals("abc", pane.text, "an edit through the state must reach the text pane")

        state.text = "xyz"
        awaitIdle()
        assertEquals("xyz", pane.text, "assigning text must reach the text pane")
    }

    @Test
    fun typingIntoTextPaneUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = DefaultStyledDocument())
            TextPane(state = state)
        }

        val pane = onNodeOfType<JTextPane>().fetch<JTextPane>()
        pane.text = "typed"
        awaitIdle()

        assertEquals("typed", state.text.toString(), "text typed into the text pane must reach the state")
    }

    @Test
    fun textPaneRespectsEditableFlag() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = DefaultStyledDocument().apply { insertString(0, "x", null) })
            TextPane(state = state, editable = editable)
        }

        val pane = onNodeOfType<JTextPane>().fetch<JTextPane>()
        assertTrue(pane.isEditable, "the text pane should start editable")

        editable = false
        awaitIdle()
        assertFalse(pane.isEditable, "the text pane should become read-only when editable is false")
    }
}
