package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A text component is handed its document, and the caller can hand it another one. What the caller
 * declares governs the document the component holds now, so a component given a document holding text
 * of its own takes the declaration rather than showing what arrived - and the swap itself, being the
 * caller's own doing, reaches no callback.
 *
 * Each overload of a component registers its own binding, so each is measured here: a binding that
 * follows the swap without settling what it holds would go on comparing against a document that is
 * gone, and the first assertion below is what tells that apart.
 */
class TextSwapBaselineTest {
    private class DocumentChangeListener : DocumentListener {
        override fun insertUpdate(event: DocumentEvent) = Unit

        override fun removeUpdate(event: DocumentEvent) = Unit

        override fun changedUpdate(event: DocumentEvent) = Unit
    }

    private fun document(text: String) = PlainDocument().apply { insertString(0, text, null) }

    @Test
    fun aValueDrivenFieldIsMeasuredAgainstTheDocumentItWasGiven() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        var declared by mutableStateOf("first")
        setContent { FlowPanel { TextField(value = declared, onValueChange = { seen += it }) } }
        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertEquals("first", field.text, "the field starts on the declared text")

        field.document = document("stale")
        awaitIdle()
        assertEquals("first", field.text, "a document the field is given takes the declared text")
        assertEquals(emptyList(), seen, "and the swap reaches no callback of the caller's")

        declared = "second"
        awaitIdle()
        assertEquals("second", field.text, "a later declaration lands on it too")
    }

    @Test
    fun aValueDrivenAreaIsMeasuredAgainstTheDocumentItWasGiven() = runComposeSwingTest {
        var declared by mutableStateOf("first")
        setContent { FlowPanel { TextArea(value = declared, onValueChange = { declared = it }) } }
        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()

        area.document = document("stale")
        awaitIdle()
        assertEquals("first", area.text, "a document the area is given takes the declared text")
    }

    @Test
    fun aValueDrivenPasswordFieldIsMeasuredAgainstTheDocumentItWasGiven() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        val declared = "hello".toCharArray()
        setContent {
            FlowPanel { PasswordField(value = declared, onValueChange = { seen += String(it) }) }
        }
        val field = onNodeOfType<JPasswordField>().fetch<JPasswordField>()
        assertEquals("hello", String(field.password), "the field starts on the declared password")

        field.document = document("world")
        awaitIdle()
        assertEquals("hello", String(field.password), "a document the field is given takes the declared password")
        assertEquals(emptyList(), seen, "and the swap reaches no callback of the caller's")
    }

    @Test
    fun aListenerDrivenPasswordFieldIsMeasuredAgainstTheDocumentItWasGiven() = runComposeSwingTest {
        val declared = "hello".toCharArray()
        setContent {
            FlowPanel { PasswordField(value = declared, documentListener = DocumentChangeListener()) }
        }
        val field = onNodeOfType<JPasswordField>().fetch<JPasswordField>()

        field.document = document("world")
        awaitIdle()
        assertEquals("hello", String(field.password), "a document the field is given takes the declared password")
    }
}
