// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import javax.swing.JPasswordField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the state-based [PasswordField] overload driving a realized [JPasswordField]
 * over a real applier. Every assertion goes through the public state API and the rendered field, never
 * a private field: the shared document is the single source of truth, so an edit on either side is
 * observable on the other, and component-specific params (echoChar, columns) reach the widget.
 */
class PasswordFieldStateTest {
    @Test
    fun stateTextAndEditUpdateTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            PasswordField(state = state)
        }

        val field = onNodeOfType<JPasswordField>()
        field.assertProperty("ab") { String(password) }

        state.text = "abc"
        awaitIdle()
        field.assertProperty("abc") { String(password) }

        state.edit { append("d") }
        awaitIdle()
        field.assertProperty("abcd") { String(password) }
    }

    @Test
    fun typingIntoFieldUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            PasswordField(state = state)
        }

        onNodeOfType<JPasswordField>().performTextReplacement("secret")

        assertEquals("secret", state.text.toString())
    }

    @Test
    fun settingSelectionMovesTheCaret() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello world")
            PasswordField(state = state)
        }

        val field = onNodeOfType<JPasswordField>()
        state.selection = TextRange(0, 5)
        awaitIdle()

        field.assertProperty(0) { selectionStart }
        field.assertProperty(5) { selectionEnd }
    }

    @Test
    fun echoCharAppliesToTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hidden")
            PasswordField(state = state, echoChar = '#')
        }

        onNodeOfType<JPasswordField>().assertProperty('#') { echoChar }
    }

    @Test
    fun columnsApplyToTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            PasswordField(state = state, columns = 12)
        }

        onNodeOfType<JPasswordField>().assertProperty(12) { columns }
    }
}
