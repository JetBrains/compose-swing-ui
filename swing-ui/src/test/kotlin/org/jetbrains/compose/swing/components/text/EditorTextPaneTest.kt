package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import javax.swing.JTextPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [EditorPane] and [TextPane]. Each test asserts the rendered Swing state
 * (text, content type, editability) and, for the interactive paths, the value the caller's
 * `onValueChange` receives — driven through the public API and read back from the live component.
 */
class EditorTextPaneTest {
    @Test
    fun editorPaneRendersValueAndContentType() = runComposeSwingTest {
        setContent {
            EditorPane(
                value = "hello",
                modifier = SwingModifier.testTag("ep"),
                contentType = "text/plain",
            )
        }
        val pane = onNodeWithTag("ep").fetch<JEditorPane>()
        assertEquals("hello", pane.text, "the editor pane should render its value")
        assertEquals("text/plain", pane.contentType, "the editor pane should render its content type")
    }

    @Test
    fun editorPaneReportsEditsThroughOnValueChange() = runComposeSwingTest {
        var text by mutableStateOf("start")
        val reported = mutableListOf<String>()
        setContent {
            EditorPane(
                value = text,
                modifier = SwingModifier.testTag("ep"),
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }

        onNodeWithTag("ep").performTextReplacement("edited")
        assertEquals("edited", reported.last(), "onValueChange should report the edited text")
        assertEquals("edited", onNodeWithTag("ep").fetch<JEditorPane>().text, "the pane should show the edited text")
    }

    @Test
    fun editorPaneReflectsStateDrivenValue() = runComposeSwingTest {
        var text by mutableStateOf("before")
        setContent { EditorPane(value = text, modifier = SwingModifier.testTag("ep")) }
        assertEquals(
            "before",
            onNodeWithTag("ep").fetch<JEditorPane>().text,
            "the pane should render the initial state value",
        )

        text = "after"
        awaitIdle()
        assertEquals(
            "after",
            onNodeWithTag("ep").fetch<JEditorPane>().text,
            "the pane should reflect the updated state value",
        )
    }

    @Test
    fun editorPaneSwitchesContentTypeAndKeepsReportingEdits() = runComposeSwingTest {
        var html by mutableStateOf(false)
        var text by mutableStateOf("plain")
        val reported = mutableListOf<String>()
        setContent {
            EditorPane(
                value = text,
                modifier = SwingModifier.testTag("ep"),
                contentType = if (html) "text/html" else "text/plain",
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }
        assertEquals(
            "text/plain",
            onNodeWithTag("ep").fetch<JEditorPane>().contentType,
            "the pane should start as plain text",
        )

        // Switching content type installs a fresh document; the edit binding must follow it.
        html = true
        awaitIdle()
        assertEquals(
            "text/html",
            onNodeWithTag("ep").fetch<JEditorPane>().contentType,
            "the pane should switch to HTML content type",
        )

        onNodeWithTag("ep").performTextReplacement("<html><body>typed</body></html>")
        assertTrue(reported.last().contains("typed"), "edits should still be reported after the content-type switch")
    }

    @Test
    fun editorPaneRespectsEditableFlag() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        setContent {
            EditorPane(
                value = "x",
                modifier = SwingModifier.testTag("ep"),
                editable = editable,
            )
        }
        assertTrue(onNodeWithTag("ep").fetch<JEditorPane>().isEditable, "the pane should start editable")

        editable = false
        awaitIdle()
        assertFalse(
            onNodeWithTag("ep").fetch<JEditorPane>().isEditable,
            "the pane should become read-only when editable is false",
        )
    }

    @Test
    fun textPaneRendersValueAndReportsEdits() = runComposeSwingTest {
        var text by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent {
            TextPane(
                value = text,
                modifier = SwingModifier.testTag("tp"),
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }
        assertEquals("hello", onNodeWithTag("tp").fetch<JTextPane>().text, "the text pane should render its value")

        onNodeWithTag("tp").performTextReplacement("world")
        assertEquals("world", reported.last(), "onValueChange should report the edited text")
        assertEquals("world", onNodeWithTag("tp").fetch<JTextPane>().text, "the text pane should show the edited text")
    }

    @Test
    fun textPaneReflectsStateAndRespectsEditableFlag() = runComposeSwingTest {
        var text by mutableStateOf("before")
        var editable by mutableStateOf(true)
        setContent {
            TextPane(
                value = text,
                modifier = SwingModifier.testTag("tp"),
                editable = editable,
            )
        }
        assertEquals(
            "before",
            onNodeWithTag("tp").fetch<JTextPane>().text,
            "the text pane should render the initial state value",
        )
        assertTrue(onNodeWithTag("tp").fetch<JTextPane>().isEditable, "the text pane should start editable")

        text = "after"
        editable = false
        awaitIdle()
        assertEquals(
            "after",
            onNodeWithTag("tp").fetch<JTextPane>().text,
            "the text pane should reflect the updated state value",
        )
        assertFalse(onNodeWithTag("tp").fetch<JTextPane>().isEditable, "the text pane should become read-only")
    }
}
