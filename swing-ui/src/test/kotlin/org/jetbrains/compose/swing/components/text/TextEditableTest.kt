package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isEditable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
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
    @TestFactory
    fun editableTypingContracts(): List<DynamicTest> = listOf(true, false).flatMap { initiallyEditable ->
        listOf(
            DynamicTest.dynamicTest("TextField, initiallyEditable=$initiallyEditable") {
                assertEditableTypingContract<JTextField>(
                    initiallyEditable = initiallyEditable,
                    render = { value, onChange, editable ->
                        TextField(value = value, onValueChange = onChange, editable = editable)
                    },
                    read = { it.text },
                )
            },
            DynamicTest.dynamicTest("TextArea, initiallyEditable=$initiallyEditable") {
                assertEditableTypingContract<JTextArea>(
                    initiallyEditable = initiallyEditable,
                    render = { value, onChange, editable ->
                        TextArea(value = value, onValueChange = onChange, editable = editable)
                    },
                    read = { it.text },
                )
            },
            DynamicTest.dynamicTest("PasswordField, initiallyEditable=$initiallyEditable") {
                assertEditableTypingContract<JPasswordField>(
                    initiallyEditable = initiallyEditable,
                    render = { value, onChange, editable ->
                        PasswordField(
                            value = value.toCharArray(),
                            onValueChange = { onChange(String(it)) },
                            editable = editable,
                        )
                    },
                    read = { String(it.password) },
                )
            },
        )
    }

    @TestFactory
    fun stateTypingContracts(): List<DynamicTest> = listOf(true, false).flatMap { initiallyEditable ->
        listOf(
            DynamicTest.dynamicTest("TextField, initiallyEditable=$initiallyEditable") {
                assertStateTypingContract<JTextField>(
                    initiallyEditable = initiallyEditable,
                    render = { state, editable ->
                        TextField(state = state, editable = editable)
                    },
                    read = { it.text },
                )
            },
            DynamicTest.dynamicTest("TextArea, initiallyEditable=$initiallyEditable") {
                assertStateTypingContract<JTextArea>(
                    initiallyEditable = initiallyEditable,
                    render = { state, editable ->
                        TextArea(state = state, editable = editable)
                    },
                    read = { it.text },
                )
            },
            DynamicTest.dynamicTest("PasswordField, initiallyEditable=$initiallyEditable") {
                assertStateTypingContract<JPasswordField>(
                    initiallyEditable = initiallyEditable,
                    render = { state, editable ->
                        PasswordField(state = state, editable = editable)
                    },
                    read = { String(it.password) },
                )
            },
        )
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

    @TestFactory
    fun formattedTextFieldTypingContracts(): List<DynamicTest> = listOf(true, false).map { initiallyEditable ->
        DynamicTest.dynamicTest("FormattedTextField, initiallyEditable=$initiallyEditable") {
            assertFormattedTypingContract(initiallyEditable)
        }
    }

    private fun assertFormattedTypingContract(initiallyEditable: Boolean) = runComposeSwingTest {
        var value by mutableStateOf<Any?>("seed")
        var editable by mutableStateOf(initiallyEditable)
        val changes = mutableListOf<Any?>()
        setContent {
            FormattedTextField(
                value = value,
                onValueChange = {
                    changes += it
                    value = it
                },
                editable = editable,
            )
        }

        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertEquals(emptyList(), changes, "initial composition reports no edit")
        if (initiallyEditable) {
            field.typeCharacter('X')
            field.commitEdit()
            awaitIdle()
            assertEquals("seedX", field.text, "typing lands while the field is editable")
            assertEquals(listOf<Any?>("seedX"), changes, "committing accepted typing reports the edit")
        }
        val acceptedText = if (initiallyEditable) "seedX" else "seed"
        val acceptedChanges = changes.toList()

        editable = false
        awaitIdle()
        field.typeCharacter('Y')
        field.commitEdit()
        awaitIdle()
        assertEquals(acceptedText, field.text, "non-editable typing leaves the text unchanged")
        assertEquals(acceptedChanges, changes, "non-editable typing reports no edit")

        value = "written"
        awaitIdle()
        assertEquals("written", field.text, "programmatic writes remain effective")
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

private inline fun <reified T : JTextComponent> assertEditableTypingContract(
    initiallyEditable: Boolean,
    noinline render: @Composable (String, (String) -> Unit, Boolean) -> Unit,
    noinline read: (T) -> String,
) = runComposeSwingTest {
    var value by mutableStateOf("seed")
    var editable by mutableStateOf(initiallyEditable)
    val changes = mutableListOf<String>()
    setContent {
        render(
            value,
            {
                changes += it
                value = it
            },
            editable,
        )
    }

    val component = onNodeOfType<T>().fetch()
    assertEquals(emptyList(), changes, "initial composition reports no edit")
    if (initiallyEditable) {
        component.typeCharacter('X')
        awaitIdle()
        assertTrue(changes.isNotEmpty(), "editable typing reports an edit")
        assertEquals("seedX", changes.last(), "the reported edit contains the typed character")
        assertEquals("seedX", read(component), "typing lands while the component is editable")
    }
    val acceptedText = if (initiallyEditable) "seedX" else "seed"
    val acceptedChanges = changes.toList()

    editable = false
    awaitIdle()
    component.typeCharacter('Y')
    awaitIdle()
    assertEquals(acceptedChanges, changes, "non-editable typing reports no edit")
    assertEquals(acceptedText, read(component), "non-editable typing leaves the text unchanged")

    value = "written"
    awaitIdle()
    assertEquals("written", read(component), "programmatic writes remain effective")
}

private inline fun <reified T : JTextComponent> assertStateTypingContract(
    initiallyEditable: Boolean,
    noinline render: @Composable (DocumentState, Boolean) -> Unit,
    noinline read: (T) -> String,
) = runComposeSwingTest {
    lateinit var state: DocumentState
    var editable by mutableStateOf(initiallyEditable)
    setContent {
        state = rememberDocumentState("seed")
        render(state, editable)
    }

    val component = onNodeOfType<T>().fetch()
    if (initiallyEditable) {
        component.typeCharacter('X')
        awaitIdle()
        assertEquals("seedX", state.text.toString(), "editable typing reaches the document state")
        assertEquals("seedX", read(component), "editable typing reaches the component")
    }
    val acceptedText = if (initiallyEditable) "seedX" else "seed"

    editable = false
    awaitIdle()
    component.typeCharacter('Y')
    awaitIdle()
    assertEquals(acceptedText, state.text.toString(), "non-editable typing leaves the state unchanged")
    assertEquals(acceptedText, read(component), "non-editable typing leaves the component unchanged")

    state.text = "written"
    awaitIdle()
    assertEquals("written", read(component), "the state remains writable while non-editable")
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
