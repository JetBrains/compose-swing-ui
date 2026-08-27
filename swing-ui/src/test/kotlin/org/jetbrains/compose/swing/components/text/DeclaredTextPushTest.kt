package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsPutBack
import org.jetbrains.compose.swing.node.MirrorState
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
 *
 * A declaration reaches its component on the apply pass that makes it and needs no successor - the pass
 * that carries a caller's answer to an edit is a different thing, timed in [TextSettlePassTimingTest].
 * The tests that count that pass drive the frames themselves: with `autoAdvance` off the idle gate
 * publishes a declaration without sending a frame, so the frame that follows is the apply pass carrying
 * it, and what the component holds either side of that frame says which pass wrote it.
 */
class DeclaredTextPushTest {
    @Test
    fun textFieldReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextField(value = value, onValueChange = { reported += it }) }
        awaitIdle()
        mainClock.autoAdvance = false

        val field = onNodeOfType<JTextField>().fetch()
        value = "world"
        awaitIdle()

        assertEquals("hello", field.text, "publishing a declaration is not itself what writes it")

        mainClock.advanceTimeByFrame()

        assertEquals("world", field.text, "the pass declaring the value should leave the field holding it")
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
    fun textFieldReportsAUserEditThatUndoesTheAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextField(value = value, onValueChange = { reported += it }) }

        value = "hello!"
        awaitIdle()
        onNodeOfType<JTextField>().fetch().backspace()
        awaitIdle()

        // The field is back on the text it held before the declared write, so an edit is only told from
        // that write by what the settle read back, not by whatever the write left behind on its way.
        assertEquals(listOf("hello"), reported, "an edit undoing the declared write is the user's own")
    }

    @Test
    fun textAreaReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextArea(value = value, onValueChange = { reported += it }) }
        awaitIdle()
        mainClock.autoAdvance = false

        val area = onNodeOfType<JTextArea>().fetch()
        value = "world"
        awaitIdle()

        assertEquals("hello", area.text, "publishing a declaration is not itself what writes it")

        mainClock.advanceTimeByFrame()

        assertEquals("world", area.text, "the pass declaring the value should leave the area holding it")
        assertEquals(emptyList(), reported, "applying a value is not an edit")

        // The keystroke below is answered by a settling pass, which the harness sends for itself again.
        mainClock.autoAdvance = true
        area.type("!")
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
        awaitIdle()
        mainClock.autoAdvance = false

        val field = onNodeOfType<JPasswordField>().fetch()
        value = "secret".toCharArray()
        awaitIdle()

        assertEquals("hunter2", String(field.password), "publishing a declaration is not itself what writes it")

        mainClock.advanceTimeByFrame()

        assertEquals(
            "secret",
            String(field.password),
            "the pass declaring the value should leave the field holding it",
        )
        assertEquals(emptyList(), reported, "applying a value is not an edit")

        mainClock.autoAdvance = true
        field.type("!")
        awaitIdle()
        assertEquals(listOf("secret!"), reported, "a keystroke is reported once, with the new characters")
    }

    @Test
    fun passwordFieldReportsAUserEditThatUndoesTheAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hunter2".toCharArray())
        val reported = mutableListOf<String>()
        setContent { PasswordField(value = value, onValueChange = { reported += String(it) }) }

        value = "hunter2!".toCharArray()
        awaitIdle()
        onNodeOfType<JPasswordField>().fetch().backspace()
        awaitIdle()

        assertEquals(listOf("hunter2"), reported, "an edit undoing the declared write is the user's own")
    }

    @Test
    fun textPaneReportsNoEditForAnAppliedValue() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent { TextPane(value = value, onValueChange = { reported += it }) }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JTextPane>().fetch()
        value = "world"
        awaitIdle()

        assertEquals("hello", pane.text, "publishing a declaration is not itself what writes it")

        mainClock.advanceTimeByFrame()

        assertEquals("world", pane.text, "the pass declaring the value should leave the pane holding it")
        assertEquals(emptyList(), reported, "applying a value is not an edit")

        mainClock.autoAdvance = true
        pane.type("!")
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
        awaitIdle()
        mainClock.autoAdvance = false

        val field = onNodeOfType<JFormattedTextField>().fetch()
        value = 250
        awaitIdle()

        assertEquals(1, field.value, "publishing a declaration is not itself what writes it")

        mainClock.advanceTimeByFrame()

        assertEquals(250, field.value, "the pass declaring the value should leave the field holding it")
        assertEquals("250", field.text, "and the field should render the value that pass settled it on")
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
        awaitIdle()
        mainClock.autoAdvance = false

        val field = onNodeOfType<JTextField>().fetch()
        seen.clear()
        value = "world"
        awaitIdle()

        assertEquals(emptyList(), seen, "publishing a declaration is not itself what writes it")

        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf("remove@0", "insert@5"),
            seen,
            "a listener attached as-is observes the applied value as a removal of the document then an insertion",
        )
        assertEquals("world", field.text, "the pass declaring the value should leave the field holding it")
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
        // No wrapper nests a write inside another, so this drives MirrorState directly to pin the
        // nesting behavior callers may rely on.
        val reported = mutableListOf<String>()
        val document = PlainDocument()
        val mirror = MirrorState(Unit)
        document.addDocumentListener(
            documentChangeListener { event -> if (!mirror.isWriting) reported += event.document.fullText() },
        )

        mirror.write {
            mirror.write { document.insertString(0, "inner", null) }
            document.insertString(document.length, "-outer", null)
        }
        assertEquals(emptyList(), reported, "a write nested in another leaves the outer one silent too")

        document.insertString(document.length, "!", null)
        assertEquals(listOf("inner-outer!"), reported, "the edit after the outermost write is reported")
    }

    @Test
    fun aTextFieldEditTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedChangeIsPutBack(
            type = JTextField::class.java,
            declared = "Ada",
            content = { TextField(value = "Ada", onValueChange = {}) },
            change = { it.text = "Adam" },
            read = { it.text },
        )
    }

    @Test
    fun aTextAreaEditTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedChangeIsPutBack(
            type = JTextArea::class.java,
            declared = "Ada",
            content = { TextArea(value = "Ada", onValueChange = {}) },
            change = { it.text = "Adam" },
            read = { it.text },
        )
    }

    @Test
    fun aTextPaneEditTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedChangeIsPutBack(
            type = JTextPane::class.java,
            declared = "Ada",
            content = { TextPane(value = "Ada", onValueChange = {}) },
            change = { it.text = "Adam" },
            read = { it.text },
        )
    }

    @Test
    fun aCommitTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedChangeIsPutBack(
            type = JFormattedTextField::class.java,
            declared = 10,
            content = { FormattedTextField(value = 10, onValueChange = {}) },
            change = { it.value = 42 },
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

// Deletes the last character of the component's content, as a backspace reaches the document.
private fun JTextComponent.backspace() {
    document.remove(document.length - 1, 1)
}
