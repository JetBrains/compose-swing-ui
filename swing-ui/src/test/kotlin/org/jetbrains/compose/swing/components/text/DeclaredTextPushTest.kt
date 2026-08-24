package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedMoveIsPutBack
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeListener
import java.text.ParseException
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
import kotlin.test.assertTrue

/**
 * Pins what applying a declared value onto a text component does to the `onValueChange` callback, and
 * to the state the component already holds.
 *
 * A callback reports only edits made to the component. A declared value the composition applies is not
 * an edit. `JTextComponent.setText` applies it as a removal of the whole document followed by an
 * insertion, so an unguarded binding would report an empty document and then the pushed text for a
 * change the application already knows about. The suppression covers only the wrapper's own write: a raw
 * `DocumentListener` the application supplies stays attached to the document as-is and keeps observing
 * every change, the applied value included.
 *
 * A declared write that throws leaves the callback reporting the user's later edits.
 *
 * The declaration is the source of truth in both directions: an edit the caller does not answer with a
 * matching value settles back onto the declared one on the pass that carries their answer, so a
 * component never stands on content the caller has not adopted.
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
    fun aTextAreaCallbackFollowsItsDocumentAcrossASwap() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent { TextArea(value = "hello", onValueChange = { reported += it }) }

        val area = onNodeOfType<JTextArea>().fetch()
        val swapped = PlainDocument().apply { insertString(0, "swapped", null) }
        area.document = swapped

        swapped.insertString(swapped.length, "!", null)
        awaitIdle()

        assertEquals(
            listOf("swapped!"),
            reported,
            "the callback stays on the document the area currently holds after a swap, not the outgoing one",
        )
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
            FormattedTextField(value = value, onValueChange = { reported += it }, formatterFactory = factory)
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
        // Each event carries the document's length at that point, so the sequence shows the shape of
        // the write, not just that something happened.
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
    fun reportingSurvivesADeclaredWriteThatThrows() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextField(value = value, onValueChange = { reported += it }) }
        val document = onNodeOfType<JTextField>().fetch().document as AbstractDocument
        document.documentFilter = RefusingFilter()

        // The filter refuses the write that applies the declared value, so it never reaches the document.
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
            FormattedTextField(
                value = value,
                formatterFactory = factory,
                // The commit is adopted, so the field settles on the caller's declared value, not the
                // refused one.
                onValueChange = {
                    reported += it
                    value = it
                },
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()

        // Applying a value renders it through the formatter, and this formatter refuses a value it was
        // not written for, so the write throws where the wrapper makes it.
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
    fun aTextFieldEditTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        setContent { TextField(value = "hello", onValueChange = {}) }

        onNodeOfType<JTextField>().fetch().type("!")
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("hello")
    }

    @Test
    fun aTextAreaEditTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        setContent { TextArea(value = "hello", onValueChange = {}) }

        onNodeOfType<JTextArea>().fetch().type("!")
        awaitIdle()

        onNodeOfType<JTextArea>().assertTextEquals("hello")
    }

    @Test
    fun aTextPaneEditTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        setContent { TextPane(value = "hello", onValueChange = {}) }

        onNodeOfType<JTextPane>().fetch().type("!")
        awaitIdle()

        onNodeOfType<JTextPane>().assertTextEquals("hello")
    }

    @Test
    fun aCommitTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        setContent {
            val factory = remember { integerFactory() }
            FormattedTextField(value = 1, onValueChange = {}, formatterFactory = factory)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "250"
        field.commitEdit()
        awaitIdle()

        assertEquals(1, field.value, "the declared value is written back over a commit nothing adopted")
        assertEquals("1", field.text, "and the field renders the value it was left on")
    }

    @Test
    fun aRawTextFieldEditTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        val listener = documentChangeListener { seen += it.document.fullText() }
        setContent { TextField(value = "hello", documentListener = listener) }

        onNodeOfType<JTextField>().fetch().type("!")
        awaitIdle()

        onNodeOfType<JTextField>().assertTextEquals("hello")
        assertEquals("hello", seen.last(), "the raw listener observes the settling write like any other change")
    }

    @Test
    fun aRawTextAreaEditTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        val listener = documentChangeListener {}
        setContent { TextArea(value = "hello", documentListener = listener) }

        onNodeOfType<JTextArea>().fetch().type("!")
        awaitIdle()

        onNodeOfType<JTextArea>().assertTextEquals("hello")
    }

    @Test
    fun aRawTextPaneEditTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        val listener = documentChangeListener {}
        setContent { TextPane(value = "hello", documentListener = listener) }

        onNodeOfType<JTextPane>().fetch().type("!")
        awaitIdle()

        onNodeOfType<JTextPane>().assertTextEquals("hello")
    }

    @Test
    fun aRawFormattedCommitTheCallerDoesNotAdoptIsSettledBack() = runComposeSwingTest {
        val listener = PropertyChangeListener {}
        setContent {
            val factory = remember { integerFactory() }
            FormattedTextField(
                value = 1,
                valuePropertyChangeListener = listener,
                formatterFactory = factory,
            )
        }

        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "250"
        field.commitEdit()
        awaitIdle()

        assertEquals(1, field.value, "the wrapper's own mirror settles the commit back with no callback in play")
    }

    @Test
    fun aNestedWriteKeepsTheOuterOneSilent() {
        // No wrapper nests a write inside another, so this drives AppliedValue directly to pin the
        // nesting behavior callers may rely on.
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

    @Test
    fun aTextFieldEditTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedMoveIsPutBack(
            type = JTextField::class.java,
            declared = "Ada",
            content = { TextField(value = "Ada", onValueChange = {}) },
            move = { it.text = "Adam" },
            read = { it.text },
        )
    }

    @Test
    fun aTextAreaEditTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedMoveIsPutBack(
            type = JTextArea::class.java,
            declared = "Ada",
            content = { TextArea(value = "Ada", onValueChange = {}) },
            move = { it.text = "Adam" },
            read = { it.text },
        )
    }

    @Test
    fun aTextPaneEditTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedMoveIsPutBack(
            type = JTextPane::class.java,
            declared = "Ada",
            content = { TextPane(value = "Ada", onValueChange = {}) },
            move = { it.text = "Adam" },
            read = { it.text },
        )
    }

    @Test
    fun aCommitTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedMoveIsPutBack(
            type = JFormattedTextField::class.java,
            declared = 10,
            content = { FormattedTextField(value = 10, onValueChange = {}) },
            move = { it.value = 42 },
            read = { it.value },
        )
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

// Formats Int values only: renders an Int, parses one back, and refuses any other type.
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
