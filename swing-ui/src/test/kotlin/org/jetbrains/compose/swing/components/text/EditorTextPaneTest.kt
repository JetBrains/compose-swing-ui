package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isEditable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.net.URI
import java.net.URL
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.Document
import javax.swing.text.html.HTMLDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the markup-rendering [EditorPane] and the value-based [TextPane]. Each test
 * asserts the rendered Swing state - the document the declared source was parsed into, the content
 * type, editability - and the link events the pane publishes, driven through the public API and read
 * back from the live component.
 */
class EditorTextPaneTest {
    @Test
    fun editorPaneRendersMarkupThroughTheKitItsContentTypeNames() = runComposeSwingTest {
        setContent {
            EditorPane(markup = "<h2>Title</h2><p>Body.</p>", onLinkActivate = {}, contentType = "text/html")
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertEquals("text/html", pane.contentType, "the pane renders through the kit its content type names")
        assertIs<HTMLDocument>(pane.document, "text/html renders into an HTML document")
        val rendered = pane.document.getText(0, pane.document.length)
        assertTrue(rendered.contains("Title"), "the parsed markup keeps its text content")
        assertFalse(rendered.contains("<h2>"), "the markup was parsed, not held as characters")
    }

    @Test
    fun plainTextMarkupIsRenderedAsTheCharactersItIs() = runComposeSwingTest {
        setContent {
            EditorPane(markup = "<h2>Title</h2>", onLinkActivate = {}, contentType = "text/plain")
        }

        val pane = onNodeOfType<JEditorPane>()
        assertEquals("text/plain", pane.fetch().contentType, "the pane renders its content type")
        pane.assertTextEquals("<h2>Title</h2>")
    }

    @Test
    fun editorPaneReRendersWhenTheMarkupChanges() = runComposeSwingTest {
        var markup by mutableStateOf("<p>before</p>")
        setContent { EditorPane(markup = markup, onLinkActivate = {}, contentType = "text/html") }

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertTrue(pane.document.textContains("before"), "the pane renders the declared markup")

        markup = "<p>after</p>"
        awaitIdle()

        assertTrue(pane.document.textContains("after"), "a changed declaration is rendered")
        assertFalse(pane.document.textContains("before"), "the previous content is replaced")
    }

    @Test
    fun editorPaneReRendersItsMarkupUnderANewContentType() = runComposeSwingTest {
        var html by mutableStateOf(false)
        setContent {
            EditorPane(
                markup = "<b>keep me</b>",
                onLinkActivate = {},
                contentType = if (html) "text/html" else "text/plain",
            )
        }

        val plain = onNodeOfType<JEditorPane>().fetch().document.getText(0, 14)
        assertEquals("<b>keep me</b>", plain, "plain text holds the markup as characters")

        // A content type brings the kit's own empty document with it, so the markup has to be rendered
        // into that document again even though the declaration itself did not change.
        html = true
        awaitIdle()

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertEquals("text/html", pane.contentType, "the pane switches to the new content type")
        assertTrue(pane.document.textContains("keep me"), "the markup is rendered under the new content type")
    }

    @Test
    fun aContentTypeNoKitIsRegisteredForIsReported() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        EditorPane(markup = "<h2>Title</h2>", onLinkActivate = {}, contentType = "text/x-nothing")
                    }
                }
            }

        // Falling back to plain text would render the markup as the characters that spell it, silently.
        assertTrue(
            failure.message.orEmpty().contains("text/x-nothing"),
            "the refusal names the content type nothing is registered for: ${failure.message}",
        )
    }

    @Test
    fun aContentTypeCarryingParametersNamesTheKitOfItsMediaType() = runComposeSwingTest {
        setContent {
            EditorPane(markup = "<h2>Title</h2>", onLinkActivate = {}, contentType = "text/html; charset=UTF-8")
        }

        // The kit registry is keyed by media type alone, so the parameters are dropped before the lookup.
        val pane = onNodeOfType<JEditorPane>().fetch()
        assertIs<HTMLDocument>(pane.document, "the parameters name a charset, not another language")
        assertTrue(pane.document.textContains("Title"), "the markup is rendered through the HTML kit")
    }

    @Test
    fun aMarkupChangeKeepsTheDocumentOfAKitRegisteredUnderAnAliasedMediaType() = runComposeSwingTest {
        // A kit answers with the media type it calls itself, which need not be the one it is registered
        // under: application/rtf yields the kit that reports text/rtf. Installing the kit the pane
        // already holds would bring a fresh document with it and drop what is rendered, so the pane is
        // held to the source having changed and the kit not.
        var markup by mutableStateOf("{\\rtf1 before}")
        setContent { EditorPane(markup = markup, onLinkActivate = {}, contentType = "application/rtf") }

        val before = onNodeOfType<JEditorPane>().fetch().document

        markup = "{\\rtf1 after}"
        awaitIdle()

        val pane = onNodeOfType<JEditorPane>().fetch()
        assertSame(before, pane.document, "only the source changed, so the document it is read into stands")
        assertTrue(pane.document.textContains("after"), "the changed markup is rendered")
    }

    @Test
    fun aRenderedEditorPaneIsNotEditable() = runComposeSwingTest {
        setContent { EditorPane(markup = "read me", onLinkActivate = {}) }

        // A rendered pane holds only what is declared.
        onNodeOfType<JEditorPane>().assert(isEditable(false))
    }

    @Test
    fun activatingALinkReportsItsRawHref() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent {
            EditorPane(
                markup = "<p><a href=\"/q3/details\">details</a></p>",
                onLinkActivate = { reported += it },
                contentType = "text/html",
            )
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        pane.fireHyperlinkUpdate(hyperlinkEvent(pane, HyperlinkEvent.EventType.ACTIVATED, "/q3/details"))

        assertEquals(listOf("/q3/details"), reported, "an activated link is reported once, as its raw href")
    }

    @Test
    fun hoveringALinkIsNotAnActivation() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent {
            EditorPane(
                markup = "<p><a href=\"/q3\">details</a></p>",
                onLinkActivate = { reported += it },
                contentType = "text/html",
            )
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        pane.fireHyperlinkUpdate(hyperlinkEvent(pane, HyperlinkEvent.EventType.ENTERED, "/q3"))
        pane.fireHyperlinkUpdate(hyperlinkEvent(pane, HyperlinkEvent.EventType.EXITED, "/q3"))

        assertEquals(emptyList(), reported, "entering and leaving a link is not activating it")
    }

    @Test
    fun aRawHyperlinkListenerHearsEveryLinkEvent() = runComposeSwingTest {
        val seen = mutableListOf<HyperlinkEvent.EventType>()
        val listener = HyperlinkListener { seen += it.eventType }
        setContent {
            EditorPane(
                markup = "<p><a href=\"/q3\">details</a></p>",
                hyperlinkListener = listener,
                contentType = "text/html",
            )
        }

        val pane = onNodeOfType<JEditorPane>().fetch()
        pane.fireHyperlinkUpdate(hyperlinkEvent(pane, HyperlinkEvent.EventType.ENTERED, "/q3"))
        pane.fireHyperlinkUpdate(hyperlinkEvent(pane, HyperlinkEvent.EventType.ACTIVATED, "/q3"))

        assertEquals(
            listOf(HyperlinkEvent.EventType.ENTERED, HyperlinkEvent.EventType.ACTIVATED),
            seen,
            "the raw listener hears hover as well as activation",
        )
    }

    @Test
    fun aBaseUrlIsWhatRelativeReferencesResolveAgainst() = runComposeSwingTest {
        var baseUrl: URL? by mutableStateOf(null)
        setContent {
            EditorPane(
                markup = "<p><a href=\"details\">details</a></p>",
                onLinkActivate = {},
                contentType = "text/html",
                baseUrl = baseUrl,
            )
        }

        val document = onNodeOfType<JEditorPane>().fetch().document as HTMLDocument
        assertNull(document.base, "without a base there is nothing for a relative href to resolve against")

        baseUrl = URI("https://example.org/reports/").toURL()
        awaitIdle()

        val based = onNodeOfType<JEditorPane>().fetch().document as HTMLDocument
        assertEquals(
            "https://example.org/reports/",
            based.base.toString(),
            "the declared base is what the document resolves against",
        )
        assertTrue(based.textContains("details"), "the markup is rendered again under the new base")
    }

    @Test
    fun aRecompositionWithAnEqualBaseUrlLeavesTheDocumentAloneButAChangedOneReRenders() = runComposeSwingTest {
        // never-resolved.invalid never answers a DNS lookup. If RenderedSource ever compared baseUrl
        // itself rather than its string form, java.net.URL.equals would resolve this host on the event
        // dispatch thread and this test would hang instead of finishing. neverEqualPolicy forces the
        // reassignments below to be seen even where the new URL is equal in string form to the old one,
        // without the state itself calling URL.equals to find that out.
        var baseUrl by mutableStateOf(URI("http://never-resolved.invalid/docs/").toURL(), neverEqualPolicy())
        setContent {
            EditorPane(markup = "<p>hello</p>", onLinkActivate = {}, contentType = "text/html", baseUrl = baseUrl)
        }

        val document = onNodeOfType<JEditorPane>().fetch().document
        var edits = 0
        document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    edits++
                }

                override fun removeUpdate(e: DocumentEvent) {
                    edits++
                }

                override fun changedUpdate(e: DocumentEvent) = Unit
            },
        )

        baseUrl = URI("http://never-resolved.invalid/docs/").toURL()
        awaitIdle()
        assertEquals(0, edits, "a recomposition with a baseUrl equal in string form leaves the document alone")

        baseUrl = URI("http://never-resolved.invalid/other/").toURL()
        awaitIdle()
        assertTrue(edits > 0, "changing only the baseUrl re-renders the document")
        assertEquals(
            "http://never-resolved.invalid/other/",
            (onNodeOfType<JEditorPane>().fetch().document as HTMLDocument).base.toString(),
            "the new base is written into the document",
        )
    }

    @Test
    fun textPaneRendersValueAndReportsEdits() = runComposeSwingTest {
        var text by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent {
            TextPane(
                value = text,
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }
        val pane = onNodeOfType<JTextPane>()
        pane.assertTextEquals("hello")

        pane.performTextReplacement("world")
        assertEquals("world", reported.last(), "onValueChange should report the edited text")
        pane.assertTextEquals("world")
    }

    @Test
    fun textPaneReflectsStateAndRespectsEditableFlag() = runComposeSwingTest {
        var text by mutableStateOf("before")
        var editable by mutableStateOf(true)
        setContent {
            TextPane(value = text, onValueChange = {}, editable = editable)
        }
        onNodeOfType<JTextPane>().assertTextEquals("before").assert(isEditable())

        text = "after"
        editable = false
        awaitIdle()
        onNodeOfType<JTextPane>().assertTextEquals("after").assert(isEditable(false))
    }

    private fun Document.textContains(text: String): Boolean = getText(0, length).contains(text)

    // A link event as the HTML kit publishes one: the raw href as the description, and the URL it
    // resolves to against the document's base - null where the two do not make one.
    private fun hyperlinkEvent(
        pane: JEditorPane,
        type: HyperlinkEvent.EventType,
        href: String,
    ): HyperlinkEvent {
        val base = (pane.document as? HTMLDocument)?.base
        return HyperlinkEvent(pane, type, base?.let { URI(it.toString()).resolve(href).toURL() }, href)
    }
}
