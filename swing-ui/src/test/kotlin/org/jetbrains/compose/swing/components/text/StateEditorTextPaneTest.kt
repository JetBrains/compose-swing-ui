package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isEditable
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.Document
import javax.swing.text.PlainDocument
import javax.swing.text.StyledDocument
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

        // Sharing one document means an edit made through either side is what the other renders: a write
        // through the state reaches the pane, and typing in the pane reaches the state.
        state.edit { append(" grown") }
        awaitIdle()
        onNodeOfType<JEditorPane>().assertTextEquals("seed grown")

        onNodeOfType<JEditorPane>().performTextReplacement("typed")
        assertEquals("typed", state.text.toString())
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
        onNodeOfType<JEditorPane>().assertTextEquals("plain")

        state.edit { append(" grown") }
        awaitIdle()
        onNodeOfType<JEditorPane>().assertTextEquals("plain grown")
    }

    @Test
    fun htmlDocumentStateRendersEditorPaneAsHtmlAndStaysAuthoritative() = runComposeSwingTest {
        val kit = HTMLEditorKit()
        val document = kit.createDefaultDocument()
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document, kit = kit)
            EditorPane(state = state)
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        // The kit the state names is what renders its document, so the pane reports that kit's type.
        assertEquals("text/html", pane.contentType, "an HTML-kit state renders as HTML")
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
    fun anAdoptedDocumentWithoutAKitRendersAsPlainText() = runComposeSwingTest {
        // A state that names no kit leaves the choice to the pane, which renders any document handed to
        // it through its own kit - plain text - exactly as a JEditorPane given a document directly does.
        val document = HTMLEditorKit().createDefaultDocument()
        setContent {
            EditorPane(state = rememberDocumentState(document = document))
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertEquals("text/plain", pane.contentType, "an unnamed language renders through the pane's own kit")
        assertSame(document, pane.document, "the pane still renders the state's own document")
    }

    @Test
    fun swappingTheStateKitSwapsWhatRendersTheEditorPane() = runComposeSwingTest {
        // Swapping works in both directions: handing the pane a state with no kit after one with the HTML
        // kit must take that kit back out, or the pane would keep rendering a plain document through HTML
        // views and keep reporting text/html.
        val kit = HTMLEditorKit()
        val html = kit.createDefaultDocument()
        val plain = PlainDocument().apply { insertString(0, "plain", null) }
        var document: Document by mutableStateOf(html)
        setContent {
            EditorPane(state = rememberDocumentState(document, kit = kit.takeIf { document === html }))
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertEquals("text/html", pane.contentType, "an HTML-kit state renders as HTML")

        document = plain
        awaitIdle()

        assertEquals("text/plain", pane.contentType, "dropping the kit renders through the pane's own")
        assertSame(plain, pane.document, "the pane renders the newly adopted document")
        onNodeOfType<JEditorPane>().assertTextEquals("plain")

        document = html
        awaitIdle()

        assertEquals("text/html", pane.contentType, "naming the HTML kit again renders as HTML")
        assertSame(html, pane.document, "the pane renders the re-adopted HTML document")
    }

    @Test
    fun aContentTypeStateParsesItsInitialTextThroughThatKit() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("<h2>Title</h2><p>Body.</p>", contentType = "text/html")
            EditorPane(state = state, editable = false)
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertEquals("text/html", pane.contentType, "a text/html state renders as HTML")
        assertIs<HTMLDocument>(pane.document, "a text/html state holds an HTML document")
        // The markup was read by the kit, so what the document holds is the rendered text and not the
        // characters that spell the tags.
        val text = state.text.toString()
        assertTrue(text.contains("Title"), "the parsed markup keeps its text content")
        assertFalse(text.contains("<h2>"), "the markup was parsed, not held as characters")
    }

    @Test
    fun aContentTypeNamingNoRegisteredKitIsRejected() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    EditorPane(state = rememberDocumentState(contentType = "text/nonsense"))
                }
            }
        assertTrue(
            failure.message.orEmpty().contains("text/nonsense"),
            "the failure should name the content type nothing is registered for",
        )
    }

    @Test
    fun anRtfContentTypeStateBuildsTheStyledModelATextPaneDemands() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("""{\rtf1\ansi notes}""", contentType = "text/rtf")
            TextPane(state = state)
        }

        assertIs<StyledDocument>(
            onNodeOfType<JTextPane>().fetch().document,
            "the styled document the state built reaches the text pane",
        )
        assertTrue(state.text.toString().contains("notes"), "the kit read the RTF source into the document")
    }

    @Test
    fun sourceAKitCannotReadLeavesTheDocumentEmpty() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("notes", contentType = "text/rtf")
            TextPane(state = state)
        }

        // A kit recognizes nothing outside its own language, and what it makes of source it cannot read
        // is the same empty document as source that legitimately renders to nothing - so the seed simply
        // does not arrive, rather than being reported.
        assertEquals("", state.text.toString(), "a kit contributes nothing it cannot read")
    }

    @Test
    fun aStateBuiltFromAConfiguredKitRendersThroughThatKit() = runComposeSwingTest {
        val kit = HTMLEditorKit().apply { styleSheet.addRule("p { color: rgb(0, 128, 0); }") }
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(kit = kit, initialText = "<p>Green.</p>")
            EditorPane(state = state, editable = false)
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertSame(kit, pane.editorKit, "the pane renders through the kit the state was built with")
        assertTrue(state.text.toString().contains("Green."), "the kit read the markup into the document")
    }

    @Test
    fun aStateSurvivesARecompositionThatLeavesItsContentTypeAlone() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        lateinit var first: DocumentState
        lateinit var latest: DocumentState
        setContent {
            latest = rememberDocumentState("seed", contentType = "text/plain")
            EditorPane(state = latest, editable = editable)
        }
        first = latest

        latest.edit { append("ed") }
        awaitIdle()
        editable = false
        awaitIdle()

        assertSame(first, latest, "an unchanged content type must not rebuild the state")
        assertEquals("seeded", latest.text.toString(), "the edit made before the recomposition survives")
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
