package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.interaction.documentFilter
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import javax.swing.JTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Behavioral coverage for the `SwingModifier.documentFilter` seam. Each test drives the live
 * `JTextField`'s document the way a typed edit would and asserts what an observer sees: a rejected
 * edit leaves the document unchanged, an accepted (rewritten) edit lands, and clearing the filter
 * restores unfiltered editing.
 */
class DocumentFilterModifierTest {
    /** Accepts only digit characters, dropping any non-digit from an insert or replace. */
    private object DigitsOnlyFilter : DocumentFilter() {
        override fun insertString(
            fb: FilterBypass,
            offset: Int,
            string: String?,
            attr: AttributeSet?,
        ) {
            fb.insertString(offset, string?.filter(Char::isDigit).orEmpty(), attr)
        }

        override fun replace(
            fb: FilterBypass,
            offset: Int,
            length: Int,
            text: String?,
            attrs: AttributeSet?,
        ) {
            fb.replace(offset, length, text?.filter(Char::isDigit).orEmpty(), attrs)
        }
    }

    /** Rewrites every inserted or replaced character to upper case. */
    private object UppercaseFilter : DocumentFilter() {
        override fun insertString(
            fb: FilterBypass,
            offset: Int,
            string: String?,
            attr: AttributeSet?,
        ) {
            fb.insertString(offset, string.orEmpty().uppercase(), attr)
        }

        override fun replace(
            fb: FilterBypass,
            offset: Int,
            length: Int,
            text: String?,
            attrs: AttributeSet?,
        ) {
            fb.replace(offset, length, text.orEmpty().uppercase(), attrs)
        }
    }

    @Test
    fun filterIsInstalledOnTheDocument() = runComposeSwingTest {
        setContent {
            TextField(value = "", modifier = SwingModifier.documentFilter(DigitsOnlyFilter))
        }
        val document = onNodeOfType<JTextField>().fetch().document as AbstractDocument
        assertEquals(DigitsOnlyFilter, document.documentFilter, "the declared filter should reach the document")
    }

    @Test
    fun validInputPassesThroughTheFilter() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent {
            TextField(
                value = "",
                modifier = SwingModifier.documentFilter(DigitsOnlyFilter),
                onValueChange = { reported += it },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.document.insertString(0, "123", null)
        awaitIdle()

        assertEquals("123", field.text, "valid input should reach the field unchanged")
        assertEquals("123", reported.last(), "onValueChange should report the valid input")
    }

    @Test
    fun invalidCharactersAreGatedOut() = runComposeSwingTest {
        setContent {
            TextField(value = "", modifier = SwingModifier.documentFilter(DigitsOnlyFilter))
        }
        val field = onNodeOfType<JTextField>().fetch()
        // Mixed input: the filter keeps the digits and drops the letters.
        field.document.insertString(0, "a1b2c3", null)
        awaitIdle()

        assertEquals("123", field.text, "the filter should keep the digits and drop the letters")
    }

    @Test
    fun clearingTheFilterRestoresUnfilteredEditing() = runComposeSwingTest {
        var filtered by mutableStateOf(true)
        setContent {
            TextField(
                value = "",
                modifier = SwingModifier.documentFilter(if (filtered) DigitsOnlyFilter else null),
            )
        }
        val document = onNodeOfType<JTextField>().fetch().document as AbstractDocument
        assertEquals(DigitsOnlyFilter, document.documentFilter, "the filter should be installed while present")

        filtered = false
        awaitIdle()
        assertNull(document.documentFilter, "clearing the modifier should remove the document filter")

        // With the filter gone, previously rejected characters now land.
        val field = onNodeOfType<JTextField>().fetch()
        field.document.insertString(0, "abc", null)
        awaitIdle()
        assertEquals("abc", field.text, "previously rejected characters should now land unfiltered")
    }

    @Test
    fun removingTheModifierRestoresThePreInstallFilter() = runComposeSwingTest {
        var filtering by mutableStateOf(false)
        setContent {
            TextField(
                value = "",
                modifier = if (filtering) SwingModifier.documentFilter(DigitsOnlyFilter) else SwingModifier,
            )
        }
        val document = onNodeOfType<JTextField>().fetch().document as AbstractDocument
        // A filter the caller installed on the document itself, outside the modifier chain.
        document.documentFilter = UppercaseFilter

        filtering = true
        awaitIdle()
        assertSame(DigitsOnlyFilter, document.documentFilter, "the modifier should take over the document's filter")

        filtering = false
        awaitIdle()
        assertSame(
            UppercaseFilter,
            document.documentFilter,
            "leaving the chain should hand the document back the filter it had before install",
        )

        // The restored filter is live again, not merely referenced.
        val field = onNodeOfType<JTextField>().fetch()
        field.document.insertString(0, "ab", null)
        awaitIdle()
        assertEquals("AB", field.text, "the restored filter should gate edits again")
    }

    @Test
    fun aDocumentSwapKeepsTheFilterActive() = runComposeSwingTest {
        var contentType by mutableStateOf("text/plain")
        setContent {
            EditorPane(
                value = "",
                modifier = SwingModifier.documentFilter(DigitsOnlyFilter),
                contentType = contentType,
            )
        }
        val pane = onNodeOfType<JEditorPane>().fetch()
        val before = pane.document as AbstractDocument
        assertSame(DigitsOnlyFilter, before.documentFilter, "the filter should start on the original document")

        // Switching content type installs a fresh document; the filter must follow onto it rather
        // than being left behind on the old one.
        contentType = "text/html"
        awaitIdle()
        val after = onNodeOfType<JEditorPane>().fetch().document as AbstractDocument
        assertSame(DigitsOnlyFilter, after.documentFilter, "the filter should migrate onto the new document")
        assertNull(before.documentFilter, "the old document's filter must be released on the swap")

        // The migrated filter still gates edits on the new document: the letters are dropped and
        // only the digits reach the document.
        after.insertString(0, "a1b2", null)
        awaitIdle()
        assertEquals(
            "12",
            after.getText(0, after.length),
            "the migrated filter should still gate edits on the new document",
        )
    }
}
