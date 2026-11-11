// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isEditable
import org.jetbrains.compose.swing.test.interaction.performTextInput
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JFormattedTextField
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the `editable` parameter across the text component family. A non-editable
 * component turns away the characters a user types while staying enabled, keeping its text selectable,
 * and - for the [DocumentState]-driven overloads - remaining writable through its state.
 */
class TextEditableTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("editableTypingContractsCases")
    fun editableTypingContracts(case: () -> Unit) = case()

    @ParameterizedTest(name = "{0}")
    @MethodSource("stateTypingContractsCases")
    fun stateTypingContracts(case: () -> Unit) = case()

    @Test
    fun nonEditableTextFieldStaysEnabledAndSelectable() = runComposeSwingTest {
        setContent { TextField(value = "seed", onValueChange = {}, editable = false) }

        onNodeOfType<JTextField>().assert(isEditable(false)).assertIsEnabled()

        val field = onNodeOfType<JTextField>().fetch()
        field.selectAll()
        assertEquals("seed", field.selectedText, "a non-editable field is still selectable")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("editableFollowsRecompositionCases")
    fun editableFollowsRecomposition(case: () -> Unit) = case()

    @ParameterizedTest(name = "{0}")
    @MethodSource("formattedTextFieldTypingContractsCases")
    fun formattedTextFieldTypingContracts(initiallyEditable: Boolean) = assertFormattedTypingContract(initiallyEditable)

    companion object {
        @JvmStatic
        fun editableTypingContractsCases(): List<Named<() -> Unit>> = listOf(true, false).flatMap { initiallyEditable ->
            listOf(
                Named.of("TextField, initiallyEditable=$initiallyEditable") {
                    assertEditableTypingContract<JTextField>(
                        initiallyEditable = initiallyEditable,
                        render = { value, onChange, editable ->
                            TextField(value = value, onValueChange = onChange, editable = editable)
                        },
                        read = { it.text },
                    )
                },
                Named.of("TextArea, initiallyEditable=$initiallyEditable") {
                    assertEditableTypingContract<JTextArea>(
                        initiallyEditable = initiallyEditable,
                        render = { value, onChange, editable ->
                            TextArea(value = value, onValueChange = onChange, editable = editable)
                        },
                        read = { it.text },
                    )
                },
                Named.of("PasswordField, initiallyEditable=$initiallyEditable") {
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

        @JvmStatic
        fun stateTypingContractsCases(): List<Named<() -> Unit>> = listOf(true, false).flatMap { initiallyEditable ->
            listOf(
                Named.of("TextField, initiallyEditable=$initiallyEditable") {
                    assertStateTypingContract<JTextField>(
                        initiallyEditable = initiallyEditable,
                        render = { state, editable ->
                            TextField(state = state, editable = editable)
                        },
                        read = { it.text },
                    )
                },
                Named.of("TextArea, initiallyEditable=$initiallyEditable") {
                    assertStateTypingContract<JTextArea>(
                        initiallyEditable = initiallyEditable,
                        render = { state, editable ->
                            TextArea(state = state, editable = editable)
                        },
                        read = { it.text },
                    )
                },
                Named.of("PasswordField, initiallyEditable=$initiallyEditable") {
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

        @JvmStatic
        fun editableFollowsRecompositionCases(): List<Named<() -> Unit>> = listOf(
            Named.of("TextField") {
                assertEditableFollowsRecomposition<JTextField> { editable ->
                    TextField(value = "seed", onValueChange = {}, editable = editable)
                }
            },
            Named.of("TextArea") {
                assertEditableFollowsRecomposition<JTextArea> { editable ->
                    TextArea(value = "seed", onValueChange = {}, editable = editable)
                }
            },
            Named.of("PasswordField") {
                assertEditableFollowsRecomposition<JPasswordField> { editable ->
                    PasswordField(value = "seed".toCharArray(), onValueChange = {}, editable = editable)
                }
            },
            Named.of("FormattedTextField") {
                assertEditableFollowsRecomposition<JFormattedTextField> { editable ->
                    FormattedTextField(value = "seed", onValueChange = {}, editable = editable)
                }
            },
        )

        @JvmStatic
        fun formattedTextFieldTypingContractsCases(): List<Named<Boolean>> = listOf(
            Named.of("initially editable", true),
            Named.of("initially non-editable", false),
        )
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
            onNodeOfType<JFormattedTextField>().performTextInput("X")
            field.commitEdit()
            awaitIdle()
            assertEquals("seedX", field.text, "typing lands while the field is editable")
            assertEquals(listOf<Any?>("seedX"), changes, "committing accepted typing reports the edit")
        }
        val acceptedText = if (initiallyEditable) "seedX" else "seed"
        val acceptedChanges = changes.toList()

        editable = false
        awaitIdle()
        onNodeOfType<JFormattedTextField>().performTextInput("Y")
        field.commitEdit()
        awaitIdle()
        assertEquals(acceptedText, field.text, "non-editable typing leaves the text unchanged")
        assertEquals(acceptedChanges, changes, "non-editable typing reports no edit")

        value = "written"
        awaitIdle()
        assertEquals("written", field.text, "programmatic writes remain effective")
    }
}

private inline fun <reified T : JTextComponent> assertEditableFollowsRecomposition(
    noinline render: @Composable (Boolean) -> Unit,
) = runComposeSwingTest {
    var editable by mutableStateOf(true)
    setContent { render(editable) }

    val field = onNodeOfType<T>()
    val fieldBefore = onNodeOfType<T>().fetch()
    field.assert(isEditable())

    editable = false
    awaitIdle()
    field.assert(isEditable(false))

    editable = true
    awaitIdle()
    assertSame(fieldBefore, onNodeOfType<T>().fetch(), "the component is updated in place, not rebuilt")
    field.assert(isEditable())
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
        onNodeOfType<T>().performTextInput("X")
        assertTrue(changes.isNotEmpty(), "editable typing reports an edit")
        assertEquals("seedX", changes.last(), "the reported edit contains the typed character")
        assertEquals("seedX", read(component), "typing lands while the component is editable")
    }
    val acceptedText = if (initiallyEditable) "seedX" else "seed"
    val acceptedChanges = changes.toList()

    editable = false
    awaitIdle()
    onNodeOfType<T>().performTextInput("Y")
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
        onNodeOfType<T>().performTextInput("X")
        assertEquals("seedX", state.text.toString(), "editable typing reaches the document state")
        assertEquals("seedX", read(component), "editable typing reaches the component")
    }
    val acceptedText = if (initiallyEditable) "seedX" else "seed"

    editable = false
    awaitIdle()
    onNodeOfType<T>().performTextInput("Y")
    assertEquals(acceptedText, state.text.toString(), "non-editable typing leaves the state unchanged")
    assertEquals(acceptedText, read(component), "non-editable typing leaves the component unchanged")

    state.text = "written"
    awaitIdle()
    assertEquals("written", read(component), "the state remains writable while non-editable")
}
