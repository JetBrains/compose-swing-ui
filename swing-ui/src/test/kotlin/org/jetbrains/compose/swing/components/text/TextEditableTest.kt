package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isEditable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionEvent
import javax.swing.JFormattedTextField
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for the `editable` parameter across the text component family. A non-editable
 * component turns away the characters a user types while staying enabled, keeping its text selectable,
 * and - for the [DocumentState]-driven overloads - remaining writable through its state.
 */
class TextEditableTest {
    @Test
    fun editableTextFieldAcceptsTypedText() = runComposeSwingTest {
        // The caller adopts what is typed, so the character stands rather than being settled away.
        var text by mutableStateOf("seed")
        setContent { TextField(value = text, onValueChange = { text = it }) }

        val field = onNodeOfType<JTextField>().fetch()
        field.typeCharacter('X')
        awaitIdle()

        assertEquals("seedX", field.text)
    }

    @Test
    fun nonEditableTextFieldRejectsTypedText() = runComposeSwingTest {
        setContent { TextField(value = "seed", onValueChange = {}, editable = false) }

        val field = onNodeOfType<JTextField>().fetch()
        field.typeCharacter('X')
        awaitIdle()

        assertEquals("seed", field.text)
    }

    @Test
    fun nonEditableTextFieldStaysEnabledAndSelectable() = runComposeSwingTest {
        setContent { TextField(value = "seed", onValueChange = {}, editable = false) }

        onNodeOfType<JTextField>().assert(isEditable(false)).assertIsEnabled()

        val field = onNodeOfType<JTextField>().fetch()
        field.selectAll()
        assertEquals("seed", field.selectedText, "a non-editable field is still selectable")
    }

    @Test
    fun textFieldEditableFollowsRecomposition() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        setContent { TextField(value = "seed", onValueChange = {}, editable = editable) }

        val field = onNodeOfType<JTextField>().fetch()
        assertTrue(field.isEditable)

        editable = false
        awaitIdle()
        assertFalse(field.isEditable)

        editable = true
        awaitIdle()
        assertTrue(field.isEditable)
    }

    @Test
    fun nonEditableStateTextFieldStillTakesStateWrites() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            TextField(state = state, editable = false)
        }

        val field = onNodeOfType<JTextField>().fetch()
        field.typeCharacter('X')
        awaitIdle()
        assertEquals("seed", state.text.toString(), "typing must not reach a non-editable field")

        state.text = "written"
        awaitIdle()
        assertEquals("written", field.text, "the state stays the source of truth")
    }

    @Test
    fun nonEditableTextAreaRejectsTypedText() = runComposeSwingTest {
        setContent { TextArea(value = "seed", onValueChange = {}, editable = false) }

        val area = onNodeOfType<JTextArea>().fetch()
        area.typeCharacter('X')
        awaitIdle()

        assertEquals("seed", area.text)
    }

    @Test
    fun textAreaEditableFollowsRecomposition() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        setContent { TextArea(value = "seed", onValueChange = {}, editable = editable) }

        val area = onNodeOfType<JTextArea>().fetch()
        assertTrue(area.isEditable)

        editable = false
        awaitIdle()
        assertFalse(area.isEditable)

        editable = true
        awaitIdle()
        assertTrue(area.isEditable)
    }

    @Test
    fun nonEditableStateTextAreaStillTakesStateWrites() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            TextArea(state = state, editable = false)
        }

        val area = onNodeOfType<JTextArea>().fetch()
        area.typeCharacter('X')
        awaitIdle()
        assertEquals("seed", state.text.toString())

        state.text = "written"
        awaitIdle()
        assertEquals("written", area.text)
    }

    @Test
    fun nonEditablePasswordFieldRejectsTypedText() = runComposeSwingTest {
        setContent { PasswordField(value = "seed".toCharArray(), onValueChange = {}, editable = false) }

        val field = onNodeOfType<JPasswordField>().fetch()
        field.typeCharacter('X')
        awaitIdle()

        assertEquals("seed", String(field.password))
    }

    @Test
    fun passwordFieldEditableFollowsRecomposition() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        setContent { PasswordField(value = "seed".toCharArray(), onValueChange = {}, editable = editable) }

        val field = onNodeOfType<JPasswordField>().fetch()
        assertTrue(field.isEditable)

        editable = false
        awaitIdle()
        assertFalse(field.isEditable)

        editable = true
        awaitIdle()
        assertTrue(field.isEditable)
    }

    @Test
    fun nonEditableStatePasswordFieldStillTakesStateWrites() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            PasswordField(state = state, editable = false)
        }

        val field = onNodeOfType<JPasswordField>().fetch()
        field.typeCharacter('X')
        awaitIdle()
        assertEquals("seed", state.text.toString())

        state.text = "written"
        awaitIdle()
        assertEquals("written", String(field.password))
    }

    @Test
    fun nonEditableFormattedTextFieldRejectsTypedText() = runComposeSwingTest {
        setContent { FormattedTextField(value = "seed", onValueChange = {}, editable = false) }

        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.typeCharacter('X')
        awaitIdle()

        assertEquals("seed", field.text)
    }

    @Test
    fun formattedTextFieldEditableFollowsRecomposition() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        setContent { FormattedTextField(value = "seed", onValueChange = {}, editable = editable) }

        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertTrue(field.isEditable)

        editable = false
        awaitIdle()
        assertFalse(field.isEditable)

        editable = true
        awaitIdle()
        assertTrue(field.isEditable)
    }
}

/**
 * Enters [character] at the end of the component's text the way Swing enters a typed key: through the
 * keymap's default action, which is what a resolved key event ultimately invokes. Assigning `text`
 * writes the document directly and would never consult the component's editability.
 */
private fun JTextComponent.typeCharacter(character: Char) {
    caretPosition = document.length
    keymap.defaultAction.actionPerformed(
        ActionEvent(this, ActionEvent.ACTION_PERFORMED, character.toString()),
    )
}
