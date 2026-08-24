package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedMoveIsPutBack
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPasswordField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for [PasswordField] over a real
 * [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier], asserting observable behavior on the
 * rendered [JPasswordField]: the controlled [CharArray] round-trips through `getPassword()`, an external
 * value change reflects without thrashing the caret (the content-equality guard skips a no-op set), and
 * an edit the caller does not adopt settles back onto the declared value.
 */
class PasswordFieldBehaviorTest {
    @Test
    fun controlledValueRoundTripsThroughGetPassword() = runComposeSwingTest {
        setContent { PasswordField(value = "hunter2".toCharArray(), onValueChange = {}) }

        val field = onNodeOfType<JPasswordField>().fetch()
        assertEquals("hunter2", String(field.password))
    }

    @Test
    fun typingFiresOnValueChangeWithTypedCharacters() = runComposeSwingTest {
        var latest = CharArray(0)
        setContent { PasswordField(value = "".toCharArray(), onValueChange = { latest = it }) }

        onNodeOfType<JPasswordField>().performTextReplacement("abc")

        assertEquals("abc", String(latest))
    }

    @Test
    fun externalValueChangeReflectsWithoutCaretThrash() = runComposeSwingTest {
        var value by mutableStateOf("first".toCharArray())
        setContent { PasswordField(value = value, onValueChange = {}) }

        val field = onNodeOfType<JPasswordField>().fetch()
        // Park the caret in the middle of the text; a no-op set on an unchanged content would call
        // setText and reset it to the end.
        field.caretPosition = 2
        awaitIdle()

        // Recompose with the SAME content (new array, equal characters): the content-equality guard
        // must skip the set, leaving the caret untouched.
        value = "first".toCharArray()
        awaitIdle()
        assertEquals(2, field.caretPosition, "no-op set thrashed the caret")

        value = "second".toCharArray()
        awaitIdle()
        assertEquals("second", String(field.password), "a genuinely different value should update the field")
    }

    @Test
    fun echoCharTracksComposedValueAcrossRecomposition() = runComposeSwingTest {
        val defaultEchoChar = JPasswordField().echoChar
        var echoChar by mutableStateOf<Char?>('#')
        setContent { PasswordField(value = "hunter2".toCharArray(), onValueChange = {}, echoChar = echoChar) }

        val field = onNodeOfType<JPasswordField>().fetch()
        assertEquals('#', field.echoChar, "a non-null echo character should be applied")

        // Re-applying null must revert to the look-and-feel default rather than leaving the custom mask.
        echoChar = null
        awaitIdle()
        assertEquals(defaultEchoChar, field.echoChar, "null should reset to the look-and-feel default")

        echoChar = '\u0000'
        awaitIdle()
        assertEquals('\u0000', field.echoChar, "NUL should be applied to show clear text")
    }

    @Test
    fun anEditTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        setContent { PasswordField(value = "hunter2".toCharArray(), onValueChange = {}) }

        val field = onNodeOfType<JPasswordField>()
        field.performTextReplacement("intruder")

        // The declared value is written back on the pass the edit itself provokes, so the field never
        // stands on characters the caller has not adopted.
        assertEquals("hunter2", String(field.fetch().password))
    }

    @Test
    fun successiveEditsTheCallerDoesNotAdoptEachSettleBack() = runComposeSwingTest {
        setContent { PasswordField(value = "hunter2".toCharArray(), onValueChange = {}) }

        val field = onNodeOfType<JPasswordField>()
        field.performTextReplacement("intruder")
        assertEquals("hunter2", String(field.fetch().password))

        // A settlement measures against the characters the field holds on its own pass, so the second
        // edit is settled back just as the first was rather than being taken for one already answered.
        field.performTextReplacement("interloper")
        assertEquals("hunter2", String(field.fetch().password))
    }

    @Test
    fun rawOverloadSettlesAnEditTheCallerDoesNotAdopt() = runComposeSwingTest {
        setContent { PasswordField(value = "hunter2".toCharArray(), documentListener = noopDocumentListener()) }

        val field = onNodeOfType<JPasswordField>()
        field.performTextReplacement("intruder")

        // The wrapper's own mirror listener, attached alongside the caller's raw one, is what keeps the
        // declared value settling back with no onValueChange callback to report through.
        assertEquals("hunter2", String(field.fetch().password))
    }

    @Test
    fun anEditTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedMoveIsPutBack(
            type = JPasswordField::class.java,
            declared = "hunter2",
            content = { PasswordField(value = "hunter2".toCharArray(), onValueChange = {}) },
            move = { it.text = "hunter3" },
            read = { String(it.password) },
        )
    }
}

private fun noopDocumentListener(): DocumentListener = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) = Unit

    override fun removeUpdate(e: DocumentEvent) = Unit

    override fun changedUpdate(e: DocumentEvent) = Unit
}
