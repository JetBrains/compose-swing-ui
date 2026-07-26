package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPasswordField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for [PasswordField] over a real [SwingApplier]. They assert observable behavior on
 * the rendered [JPasswordField]: the controlled [CharArray] round-trips through `getPassword()`, typing
 * fires `onValueChange` with the typed characters, an external value change reflects into the field
 * without thrashing the caret (the content-equality guard skips a no-op set), and the echo character
 * tracks the composed value across recomposition, reverting to the look-and-feel default when null is
 * re-applied.
 */
class PasswordFieldBehaviorTest {
    @Test
    fun controlledValueRoundTripsThroughGetPassword() = runComposeSwingTest {
        setContent { PasswordField(value = "hunter2".toCharArray(), onValueChange = {}) }

        val field = onNodeOfType<JPasswordField>().fetch<JPasswordField>()
        assertEquals("hunter2", String(field.password))
    }

    @Test
    fun typingFiresOnValueChangeWithTypedCharacters() = runComposeSwingTest {
        var latest = CharArray(0)
        setContent { PasswordField(value = "".toCharArray(), onValueChange = { latest = it }) }

        val field = onNodeOfType<JPasswordField>().fetch<JPasswordField>()
        field.text = "abc"
        awaitIdle()

        assertEquals("abc", String(latest))
    }

    @Test
    fun externalValueChangeReflectsWithoutCaretThrash() = runComposeSwingTest {
        var value by mutableStateOf("first".toCharArray())
        setContent { PasswordField(value = value, onValueChange = {}) }

        val field = onNodeOfType<JPasswordField>().fetch<JPasswordField>()
        // Park the caret in the middle of the text; a no-op set on an unchanged content would call
        // setText and reset it to the end.
        field.caretPosition = 2
        awaitIdle()

        // Recompose with the SAME content (new array, equal characters): the content-equality guard
        // must skip the set, leaving the caret untouched.
        value = "first".toCharArray()
        awaitIdle()
        assertEquals(2, field.caretPosition, "no-op set thrashed the caret")

        // A genuinely different value updates the field.
        value = "second".toCharArray()
        awaitIdle()
        assertEquals("second", String(field.password), "a genuinely different value should update the field")
        assertTrue(field.password.isNotEmpty(), "the updated field should expose a non-empty password")
    }

    @Test
    fun echoCharTracksComposedValueAcrossRecomposition() = runComposeSwingTest {
        val defaultEchoChar = JPasswordField().echoChar
        var echoChar by mutableStateOf<Char?>('#')
        setContent { PasswordField(value = "hunter2".toCharArray(), echoChar = echoChar) }

        val field = onNodeOfType<JPasswordField>().fetch<JPasswordField>()
        assertEquals('#', field.echoChar, "a non-null echo character should be applied")

        // Re-applying null must revert to the look-and-feel default rather than leaving the custom mask.
        echoChar = null
        awaitIdle()
        assertEquals(defaultEchoChar, field.echoChar, "null should reset to the look-and-feel default")

        // The NUL character (U+0000) renders the text in clear text.
        echoChar = '\u0000'
        awaitIdle()
        assertEquals('\u0000', field.echoChar, "NUL should be applied to show clear text")
    }
}
