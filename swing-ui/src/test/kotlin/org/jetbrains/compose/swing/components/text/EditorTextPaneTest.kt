package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isEditable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [EditorPane] and [TextPane]. Each test asserts the rendered Swing state
 * (text, content type, editability) and, for the interactive paths, the value the caller's
 * `onValueChange` receives - driven through the public API and read back from the live component.
 */
class EditorTextPaneTest {
    @Test
    fun editorPaneRendersValueAndContentType() = runComposeSwingTest {
        setContent {
            EditorPane(value = "hello", contentType = "text/plain")
        }
        val pane = onNodeOfType<JEditorPane>()
        pane.assertTextEquals("hello")
        assertEquals(
            "text/plain",
            pane.fetch().contentType,
            "the editor pane should render its content type",
        )
    }

    @Test
    fun editorPaneReportsEditsThroughOnValueChange() = runComposeSwingTest {
        var text by mutableStateOf("start")
        val reported = mutableListOf<String>()
        setContent {
            EditorPane(
                value = text,
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }

        val pane = onNodeOfType<JEditorPane>()
        pane.performTextReplacement("edited")
        assertEquals("edited", reported.last(), "onValueChange should report the edited text")
        pane.assertTextEquals("edited")
    }

    @Test
    fun editorPaneReflectsStateDrivenValue() = runComposeSwingTest {
        var text by mutableStateOf("before")
        setContent { EditorPane(value = text) }
        onNodeOfType<JEditorPane>().assertTextEquals("before")

        text = "after"
        awaitIdle()
        onNodeOfType<JEditorPane>().assertTextEquals("after")
    }

    @Test
    fun editorPaneSwitchesContentTypeAndKeepsReportingEdits() = runComposeSwingTest {
        var html by mutableStateOf(false)
        var text by mutableStateOf("plain")
        val reported = mutableListOf<String>()
        setContent {
            EditorPane(
                value = text,
                contentType = if (html) "text/html" else "text/plain",
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }
        val pane = onNodeOfType<JEditorPane>()
        assertEquals(
            "text/plain",
            pane.fetch().contentType,
            "the pane should start as plain text",
        )

        // Switching content type installs a fresh document; the edit binding must follow it.
        html = true
        awaitIdle()
        assertEquals(
            "text/html",
            pane.fetch().contentType,
            "the pane should switch to HTML content type",
        )

        pane.performTextReplacement("<html><body>typed</body></html>")
        assertTrue(reported.last().contains("typed"), "edits should still be reported after the content-type switch")
    }

    @Test
    fun editorPaneKeepsItsValueAcrossAContentTypeSwitch() = runComposeSwingTest {
        var html by mutableStateOf(false)
        setContent {
            EditorPane(
                value = "keep me",
                contentType = if (html) "text/html" else "text/plain",
            )
        }

        // A content type installs a fresh, empty document, so the value has to be rendered into it
        // again even though the value itself did not change.
        html = true
        awaitIdle()

        assertTrue(
            onNodeOfType<JEditorPane>().fetch().text.contains("keep me"),
            "the pane should render its value under the new content type",
        )
    }

    @Test
    fun editorPaneRawDocumentListenerFollowsAContentTypeSwitch() = runComposeSwingTest {
        var html by mutableStateOf(false)
        val seen = mutableListOf<String>()
        val listener =
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    seen += "insert"
                }

                override fun removeUpdate(e: DocumentEvent) {
                    seen += "remove"
                }

                override fun changedUpdate(e: DocumentEvent) {
                    seen += "changed"
                }
            }
        setContent {
            EditorPane(
                value = "plain",
                documentListener = listener,
                contentType = if (html) "text/html" else "text/plain",
            )
        }

        // Switching content type installs a fresh document; the listener observes the pane's document,
        // so it must move with it.
        html = true
        awaitIdle()
        seen.clear()

        val pane = onNodeOfType<JEditorPane>().fetch()
        pane.document.insertString(pane.document.length, "typed", null)
        assertTrue(seen.isNotEmpty(), "the listener should observe the document the pane holds now")
    }

    @Test
    fun anUnmountedEditorPaneStopsObservingTheDocumentItHeld() = runComposeSwingTest {
        var mounted by mutableStateOf(true)
        val seen = mutableListOf<String>()
        setContent {
            if (mounted) {
                EditorPane(value = "plain", onValueChange = { seen += it })
            }
        }
        // The document outlives the pane here: a caller holding it keeps it usable, and the binding the
        // composition installed must be gone from it once the pane leaves.
        val document = onNodeOfType<JEditorPane>().fetch().document

        mounted = false
        awaitIdle()
        document.insertString(document.length, "typed", null)

        assertTrue(seen.isEmpty(), "an unmounted pane reports nothing for the document it used to render")
    }

    @Test
    fun editorPaneRespectsEditableFlag() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        setContent {
            EditorPane(value = "x", editable = editable)
        }
        onNodeOfType<JEditorPane>().assert(isEditable())

        editable = false
        awaitIdle()
        onNodeOfType<JEditorPane>().assert(isEditable(false))
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
            TextPane(value = text, editable = editable)
        }
        onNodeOfType<JTextPane>().assertTextEquals("before").assert(isEditable())

        text = "after"
        editable = false
        awaitIdle()
        onNodeOfType<JTextPane>().assertTextEquals("after").assert(isEditable(false))
    }
}
