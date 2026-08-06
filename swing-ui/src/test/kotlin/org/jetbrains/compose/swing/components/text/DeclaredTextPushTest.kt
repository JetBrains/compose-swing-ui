package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeListener
import java.text.ParseException
import javax.swing.JEditorPane
import javax.swing.JFormattedTextField
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.DocumentFilter
import javax.swing.text.JTextComponent
import javax.swing.text.NumberFormatter
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins what applying a declared value onto a text component does - to the callback, and to the state the
 * component already holds.
 *
 * An `onValueChange` callback reports the edits made to the component and only those. A declared value
 * the composition applies is not an edit, and `JTextComponent.setText` applies it as a removal of the
 * whole document followed by an insertion, so an unguarded binding would report an empty document and
 * then the pushed text for a change the application already knows about. The suppression covers the
 * wrapper's own write only: a raw `DocumentListener` the application supplies is attached to the document
 * as-is and keeps observing every change, the applied value included.
 *
 * Applying a value the component already holds is skipped, so a re-application does not move the caret;
 * and a declared write that throws leaves the callback reporting the user's later edits.
 */
class DeclaredTextPushTest {
    @Test
    fun textFieldReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextField(value = value, onValueChange = { reported += it }) }

        value = "world"
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("world")
        assertEquals(emptyList(), reported, "applying a value is not an edit")
    }

    @Test
    fun textFieldReportsAUserEditExactlyOnce() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent { TextField(value = "hello", onValueChange = { reported += it }) }

        onNodeOfType<JTextField>().fetch().type("!")
        awaitIdle()

        assertEquals(listOf("hello!"), reported, "a keystroke is reported once, with the new text")
    }

    @Test
    fun textAreaReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextArea(value = value, onValueChange = { reported += it }) }

        value = "world"
        awaitIdle()

        val area = onNodeOfType<JTextArea>()
        area.assertTextEquals("world")
        assertEquals(emptyList(), reported, "applying a value is not an edit")

        area.fetch().type("!")
        awaitIdle()
        assertEquals(listOf("world!"), reported, "a keystroke is reported once, with the new text")
    }

    @Test
    fun passwordFieldReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hunter2".toCharArray())
        val reported = mutableListOf<String>()
        setContent { PasswordField(value = value, onValueChange = { reported += String(it) }) }

        value = "secret".toCharArray()
        awaitIdle()

        val field = onNodeOfType<JPasswordField>().fetch()
        assertEquals("secret", String(field.password), "the field renders the value")
        assertEquals(emptyList(), reported, "applying a value is not an edit")

        field.type("!")
        awaitIdle()
        assertEquals(listOf("secret!"), reported, "a keystroke is reported once, with the new characters")
    }

    @Test
    fun editorPaneReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { EditorPane(value = value, onValueChange = { reported += it }) }

        value = "world"
        awaitIdle()

        val pane = onNodeOfType<JEditorPane>()
        pane.assertTextEquals("world")
        assertEquals(emptyList(), reported, "applying a value is not an edit")

        pane.fetch().type("!")
        awaitIdle()
        assertEquals(listOf("world!"), reported, "a keystroke is reported once, with the new text")
    }

    @Test
    fun editorPaneReportsNoEditForAnAppliedContentType() = runComposeSwingTest {
        var html by mutableStateOf(false)
        val reported = mutableListOf<String>()
        setContent {
            EditorPane(
                value = "hello",
                contentType = if (html) "text/html" else "text/plain",
                onValueChange = { reported += it },
            )
        }

        html = true
        awaitIdle()

        assertEquals(
            "text/html",
            onNodeOfType<JEditorPane>().fetch().contentType,
            "the pane switches content type",
        )
        assertEquals(emptyList(), reported, "reinterpreting the value under a new content type is not an edit")
    }

    @Test
    fun textPaneReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextPane(value = value, onValueChange = { reported += it }) }

        value = "world"
        awaitIdle()

        val pane = onNodeOfType<JTextPane>()
        pane.assertTextEquals("world")
        assertEquals(emptyList(), reported, "applying a value is not an edit")

        pane.fetch().type("!")
        awaitIdle()
        assertEquals(listOf("world!"), reported, "a keystroke is reported once, with the new text")
    }

    @Test
    fun formattedTextFieldReportsNoCommitForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf(1 as Any?)
        val reported = mutableListOf<Any?>()
        setContent {
            val factory = remember { integerFactory() }
            FormattedTextField(value = value, formatterFactory = factory, onValueChange = { reported += it })
        }

        value = 250
        awaitIdle()

        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertEquals(250, field.value, "the field holds the applied value")
        assertEquals(emptyList(), reported, "applying a value is not a commit the field made from an edit")
    }

    @Test
    fun rawPropertyChangeListenerObservesTheAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf(1 as Any?)
        val seen = mutableListOf<Any?>()
        val listener = PropertyChangeListener { event -> seen += event.newValue }
        setContent {
            val factory = remember { integerFactory() }
            FormattedTextField(value = value, valuePropertyChangeListener = listener, formatterFactory = factory)
        }

        seen.clear()
        value = 250
        awaitIdle()

        assertEquals(listOf<Any?>(250), seen, "a listener attached as-is is notified of every value change")
    }

    @Test
    fun rawDocumentListenerObservesTheAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val seen = mutableListOf<String>()
        // Each event carries the length the document has once it is applied, so the sequence shows the
        // shape of the write and not merely that something happened.
        val listener =
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    seen += "insert@${e.document.length}"
                }

                override fun removeUpdate(e: DocumentEvent) {
                    seen += "remove@${e.document.length}"
                }

                override fun changedUpdate(e: DocumentEvent) {
                    seen += "changed@${e.document.length}"
                }
            }
        setContent { TextField(value = value, documentListener = listener) }

        seen.clear()
        value = "world"
        awaitIdle()

        assertEquals(
            listOf("remove@0", "insert@5"),
            seen,
            "a listener attached as-is observes the applied value as a removal of the document then an insertion",
        )
    }

    @Test
    fun aReactivatedFieldKeepsItsCaretWhereTheUserLeftIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                TextField(value = "hello")
            }
        }
        val field = onNodeOfType<JTextField>()
        field.fetch().caretPosition = 2

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        field.assertTextEquals("hello")
        assertEquals(
            2,
            field.fetch().caretPosition,
            "re-applying the text the field already holds must not move the caret",
        )
    }

    @Test
    fun reportingSurvivesADeclaredWriteThatThrows() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextField(value = value, onValueChange = { reported += it }) }
        val document = onNodeOfType<JTextField>().fetch().document as AbstractDocument
        document.documentFilter = RefusingFilter()

        // The filter refuses the write that applies the declared value, so the value never reaches the
        // document and the callback has nothing to report.
        value = "world"
        awaitIdle()
        assertEquals(emptyList(), reported, "a refused write reports nothing")
        val refusal = takeCallerFailures().single()
        assertTrue(
            refusal.message?.contains("the document refuses this write") == true,
            "the contained failure is the one the filter raised, not whatever else reached the thread",
        )

        document.documentFilter = null
        document.insertString(document.length, "!", null)
        awaitIdle()

        assertEquals(listOf("hello!"), reported, "the user's next edit is still reported after a refused write")
    }

    @Test
    fun formattedTextFieldReportingSurvivesADeclaredWriteThatThrows() = runComposeSwingTest {
        var value by mutableStateOf(1 as Any?)
        val reported = mutableListOf<Any?>()
        setContent {
            val factory = remember { DefaultFormatterFactory(IntOnlyFormatter()) }
            FormattedTextField(value = value, formatterFactory = factory, onValueChange = { reported += it })
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()

        // Applying a value renders it through the formatter, and this formatter refuses a value it was
        // not written for - so the write throws where the wrapper is making it.
        value = "not an int"
        awaitIdle()
        assertEquals(emptyList(), reported, "a refused write reports nothing")
        val refusal = takeCallerFailures().single()
        assertTrue(
            refusal.stackTraceToString().contains("IntOnlyFormatter"),
            "the contained failure is the one this formatter raised, not whatever else reached the thread",
        )

        field.text = "7"
        field.commitEdit()
        awaitIdle()

        assertEquals(listOf<Any?>(7), reported, "the field's next commit is still reported after a refused write")
    }

    @Test
    fun aTypedEditSurvivesARecompositionTheKeystrokeDidNotCause() = runComposeSwingTest {
        // columns is unrelated to value, so changing it recomposes this component without the mirror
        // itself moving - the one pass a value pushed only on change must not fight the user's edit on.
        var columns by mutableStateOf(0)
        setContent { TextField(value = "hello", columns = columns) }

        onNodeOfType<JTextField>().fetch().type("!")
        awaitIdle()

        columns = 8
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("hello!")
    }

    @Test
    fun aNestedWriteKeepsTheOuterOneSilent() {
        // No wrapper nests a write of its own inside another, so the write is driven directly here to
        // hold it to the nesting its callers may rely on.
        val reported = mutableListOf<String>()
        val document = PlainDocument()
        val applied = AppliedValue(Unit)
        document.addDocumentListener(
            documentChangeListener { event -> if (!applied.isWriting) reported += event.document.fullText() },
        )

        applied.write {
            applied.write { document.insertString(0, "inner", null) }
            document.insertString(document.length, "-outer", null)
        }
        assertEquals(emptyList(), reported, "a write nested in another leaves the outer one silent too")

        document.insertString(document.length, "!", null)
        assertEquals(listOf("inner-outer!"), reported, "the edit after the outermost write is reported")
    }
}

private class RefusingFilter : DocumentFilter() {
    override fun replace(
        fb: FilterBypass,
        offset: Int,
        length: Int,
        text: String?,
        attrs: AttributeSet?,
    ): Unit = error("the document refuses this write")
}

// A formatter for integer values: it renders an Int, reads one back, and refuses a value of any other
// type - the way a formatter written for one value class treats a value it cannot render.
private class IntOnlyFormatter : JFormattedTextField.AbstractFormatter() {
    override fun stringToValue(text: String?): Any = text?.trim()?.toIntOrNull() ?: throw ParseException(text, 0)

    override fun valueToString(value: Any?): String {
        if (value == null) return ""
        require(value is Int) { "this formatter renders Int values, not ${value.javaClass.name}" }
        return value.toString()
    }
}

// A factory formatting integers, so a committed value is an Int and the field's text is its digits.
private fun integerFactory(): DefaultFormatterFactory {
    val formatter = NumberFormatter()
    formatter.valueClass = Int::class.javaObjectType
    return DefaultFormatterFactory(formatter)
}

// Types [text] at the end of the component's content, as a keystroke reaches the document.
private fun JTextComponent.type(text: String) {
    document.insertString(document.length, text, null)
}
