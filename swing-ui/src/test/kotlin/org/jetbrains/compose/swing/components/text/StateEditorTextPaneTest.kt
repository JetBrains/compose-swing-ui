package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isEditable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.Document
import javax.swing.text.PlainDocument
import javax.swing.text.html.HTMLEditorKit
import kotlin.test.Test
import kotlin.test.assertEquals
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

        val pane = onNodeOfType<JEditorPane>()
        assertSame(
            state.document,
            pane.fetch().document,
            "the pane must render the state's own document",
        )
        pane.assertTextEquals("seed")
    }

    @Test
    fun editingStateUpdatesEditorPane() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>()
        pane.assertTextEquals("ab")

        state.edit { append("c") }
        awaitIdle()
        pane.assertTextEquals("abc")

        state.text = "xyz"
        awaitIdle()
        pane.assertTextEquals("xyz")
    }

    @Test
    fun typingIntoEditorPaneUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            EditorPane(state = state)
        }

        onNodeOfType<JEditorPane>().performTextReplacement("typed")

        assertEquals("typed", state.text.toString(), "text typed into the pane must reach the state")
    }

    @Test
    fun plainDocumentStateRendersEditorPaneAsPlainText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("plain")
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertEquals("text/plain", pane.contentType, "a plain-document state renders as plain text")
        assertSame(state.document, pane.document, "the pane renders the state's own document")
        onNodeOfType<JEditorPane>().assertTextEquals("plain")
    }

    @Test
    fun htmlDocumentStateRendersEditorPaneAsHtmlAndStaysAuthoritative() = runComposeSwingTest {
        val document = HTMLEditorKit().createDefaultDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
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
    fun swappingTheStateDocumentReDerivesTheEditorPaneContentType() = runComposeSwingTest {
        // The pane's content type is a function of the document it renders, in both directions: handing
        // the state a plain document after an HTML one must take the HTML kit back out, or the pane would
        // keep rendering a plain document through HTML views and keep reporting text/html.
        val html = HTMLEditorKit().createDefaultDocument()
        val plain = PlainDocument().apply { insertString(0, "plain", null) }
        var document: Document by mutableStateOf(html)
        setContent {
            EditorPane(state = rememberDocumentState(document))
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertEquals("text/html", pane.contentType, "an HTML-document state renders as HTML")

        document = plain
        awaitIdle()

        assertEquals("text/plain", pane.contentType, "swapping in a plain document renders as plain text")
        assertSame(plain, pane.document, "the pane renders the newly adopted document")
        onNodeOfType<JEditorPane>().assertTextEquals("plain")

        document = html
        awaitIdle()

        assertEquals("text/html", pane.contentType, "swapping back to an HTML document renders as HTML again")
        assertSame(html, pane.document, "the pane renders the re-adopted HTML document")
    }

    @Test
    fun editorPaneRespectsEditableFlag() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("x")
            EditorPane(state = state, editable = editable)
        }

        onNodeOfType<JEditorPane>().assert(isEditable())

        editable = false
        awaitIdle()
        onNodeOfType<JEditorPane>().assert(isEditable(false))
    }

    @Test
    fun textPaneSharesTheStateStyledDocument() = runComposeSwingTest {
        val document = DefaultStyledDocument().apply { insertString(0, "seed", null) }
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        val pane = onNodeOfType<JTextPane>()
        assertSame(
            document,
            pane.fetch().document,
            "the text pane must render the state's own styled document",
        )
        pane.assertTextEquals("seed")
    }

    @Test
    fun editingStateUpdatesTextPane() = runComposeSwingTest {
        val document = DefaultStyledDocument().apply { insertString(0, "ab", null) }
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        val pane = onNodeOfType<JTextPane>()
        pane.assertTextEquals("ab")

        state.edit { append("c") }
        awaitIdle()
        pane.assertTextEquals("abc")

        state.text = "xyz"
        awaitIdle()
        pane.assertTextEquals("xyz")
    }

    @Test
    fun typingIntoTextPaneUpdatesStateText() = runComposeSwingTest {
        val document = DefaultStyledDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state)
        }

        onNodeOfType<JTextPane>().performTextReplacement("typed")

        assertEquals("typed", state.text.toString(), "text typed into the text pane must reach the state")
    }

    @Test
    fun textPaneRespectsEditableFlag() = runComposeSwingTest {
        val document = DefaultStyledDocument().apply { insertString(0, "x", null) }
        var editable by mutableStateOf(true)
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextPane(state = state, editable = editable)
        }

        onNodeOfType<JTextPane>().assert(isEditable())

        editable = false
        awaitIdle()
        onNodeOfType<JTextPane>().assert(isEditable(false))
    }
}
