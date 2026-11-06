package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JEditorPane
import javax.swing.JTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the `documentFilter` style property, driving a live text component's
 * document the way a typed edit would.
 */
class DocumentFilterStyleTest {
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

    @ParameterizedTest
    @ValueSource(strings = ["123", "a1b2c3"])
    fun filterReportsOnlyAcceptedInput(input: String) = runComposeSwingTest {
        var text by mutableStateOf("")
        val reported = mutableListOf<String>()
        setContent {
            TextField(text, {
                reported += it
                text = it
            }, style = { documentFilter(DigitsOnlyFilter) })
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.document.insertString(0, input, null)
        awaitIdle()
        assertEquals("123", field.text)
        assertEquals("123", reported.last())
    }

    @Test
    fun clearingFilterAllowsUnfilteredInput() = runComposeSwingTest {
        var filtered by mutableStateOf(true)
        var text by mutableStateOf("")
        setContent {
            TextField(
                value = text,
                onValueChange = { text = it },
                style = { documentFilter(if (filtered) DigitsOnlyFilter else null) },
            )
        }
        val document = onNodeOfType<JTextField>().fetch().document as AbstractDocument
        assertEquals(DigitsOnlyFilter, document.documentFilter)

        filtered = false
        awaitIdle()
        assertNull(document.documentFilter, "clearing the style should remove the document filter")

        val field = onNodeOfType<JTextField>().fetch()
        field.document.insertString(0, "abc", null)
        awaitIdle()
        assertEquals("abc", field.text)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun removalRestoresThePreviousFilter(initiallyStyled: Boolean) = runComposeSwingTest {
        var filtering by mutableStateOf(initiallyStyled)
        setContent {
            SwingNode(
                factory = { JTextField().apply { (document as AbstractDocument).documentFilter = UppercaseFilter } },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (filtering) documentFilter(DigitsOnlyFilter)
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val document = field.document as AbstractDocument
        if (!initiallyStyled) {
            filtering = true
            awaitIdle()
        }
        assertSame(DigitsOnlyFilter, document.documentFilter)
        document.insertString(0, "a1b2c3", null)
        awaitIdle()
        assertEquals("123", field.text)

        filtering = false
        awaitIdle()
        assertSame(UppercaseFilter, document.documentFilter)
        document.insertString(field.text.length, "ab", null)
        awaitIdle()
        assertEquals("123AB", field.text)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun filterFollowsDocumentSwapsAndRestoresEachOriginal(hasOriginalFilter: Boolean) = runComposeSwingTest {
        var filtering by mutableStateOf(!hasOriginalFilter)
        var contentType by mutableStateOf("text/plain")
        setContent {
            EditorPane("", {}, style = { if (filtering) documentFilter(DigitsOnlyFilter) }, contentType = contentType)
        }
        val pane = onNodeOfType<JEditorPane>().fetch()
        val before = pane.document as AbstractDocument
        if (hasOriginalFilter) {
            before.documentFilter = UppercaseFilter
            filtering = true
            awaitIdle()
        }
        assertSame(DigitsOnlyFilter, before.documentFilter)

        contentType = "text/html"
        awaitIdle()
        val after = pane.document as AbstractDocument
        assertSame(DigitsOnlyFilter, after.documentFilter)
        assertSame(if (hasOriginalFilter) UppercaseFilter else null, before.documentFilter)
        after.insertString(0, "a1b2", null)
        awaitIdle()
        assertEquals("12", after.getText(0, after.length))

        filtering = false
        awaitIdle()
        assertNull(after.documentFilter)
    }

    @Test
    fun formatterOwnedFilterRejectsStyle() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        FormattedTextField(
                            value = 42,
                            onValueChange = {},
                            style = { documentFilter(DigitsOnlyFilter) },
                        )
                    }
                }
            }

        assertTrue(
            "getDocumentFilter" in failure.message.orEmpty(),
            "the message should name the formatter method that owns the filter, but was: ${failure.message}",
        )
    }
}
