package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JFormattedTextField
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [FormattedTextField] over a real `JFormattedTextField`. Each test asserts
 * what an observer of the live field sees: the committed value formatted into the display, the value
 * the user's callback receives once an edit commits, and the selection guard that keeps a callback
 * writing the value back from looping.
 */
class FormattedTextFieldTest {
    private fun integerFactory(): DefaultFormatterFactory {
        val formatter = NumberFormatter()
        formatter.valueClass = Int::class.javaObjectType
        return DefaultFormatterFactory(formatter)
    }

    @Test
    fun rendersCommittedValueFormattedIntoText() = runComposeSwingTest {
        setContent {
            FormattedTextField(
                value = 42,
                modifier = SwingModifier.testTag("amount"),
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeWithTag("amount").fetch<JFormattedTextField>()
        assertEquals(42, field.value, "the field should hold the committed value")
        assertEquals("42", field.text, "the field should render the value formatted as text")
    }

    @Test
    fun committingValidEditFiresOnValueChangeWithParsedValue() = runComposeSwingTest {
        var value by mutableStateOf(1 as Any?)
        val reported = mutableListOf<Any?>()
        setContent {
            FormattedTextField(
                value = value,
                modifier = SwingModifier.testTag("amount"),
                formatterFactory = integerFactory(),
                onValueChange = {
                    reported += it
                    value = it
                },
            )
        }
        val field = onNodeWithTag("amount").fetch<JFormattedTextField>()
        field.text = "99"
        field.commitEdit()
        awaitIdle()

        assertEquals(99, reported.last(), "onValueChange should report the parsed committed value")
        assertEquals(99, field.value, "the field should hold the committed value")
    }

    @Test
    fun invalidEditIsNotCommittedAndProducesNoCallback() = runComposeSwingTest {
        var value by mutableStateOf(7 as Any?)
        val reported = mutableListOf<Any?>()
        setContent {
            FormattedTextField(
                value = value,
                modifier = SwingModifier.testTag("amount"),
                formatterFactory = integerFactory(),
                onValueChange = {
                    reported += it
                    value = it
                },
            )
        }
        val field = onNodeWithTag("amount").fetch<JFormattedTextField>()
        field.text = "not-a-number"
        assertTrue(runCatching { field.commitEdit() }.isFailure, "committing an unparsable edit should fail")
        awaitIdle()

        // The unparsable edit never committed, so the value is unchanged and no callback fired.
        assertEquals(7, field.value, "the value should be unchanged after a failed commit")
        assertTrue(reported.isEmpty(), "no callback should fire for an uncommitted edit")
    }

    @Test
    fun externalValueChangeReflectsIntoTheField() = runComposeSwingTest {
        var value by mutableStateOf(1 as Any?)
        setContent {
            FormattedTextField(
                value = value,
                modifier = SwingModifier.testTag("amount"),
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeWithTag("amount").fetch<JFormattedTextField>()
        assertEquals("1", field.text, "the field should render the initial value")

        value = 250
        awaitIdle()
        assertEquals(250, field.value, "an external value change should reflect into the field value")
        assertEquals("250", field.text, "an external value change should reflect into the field text")
    }

    @Test
    fun writingValueBackInCallbackDoesNotLoop() = runComposeSwingTest {
        var value by mutableStateOf(0 as Any?)
        val reported = mutableListOf<Any?>()
        setContent {
            FormattedTextField(
                value = value,
                modifier = SwingModifier.testTag("amount"),
                formatterFactory = integerFactory(),
                onValueChange = {
                    reported += it
                    value = it
                },
            )
        }
        val field = onNodeWithTag("amount").fetch<JFormattedTextField>()
        field.text = "5"
        field.commitEdit()
        awaitIdle()

        // The callback wrote the committed value back; the guard skips the equal set, so the
        // property listener fires exactly once rather than echoing.
        assertEquals(listOf<Any?>(5), reported, "the callback should fire exactly once, not echo")
        assertEquals(5, field.value, "the field should hold the committed value")
    }

    @Test
    fun focusLostBehaviorIsApplied() = runComposeSwingTest {
        setContent {
            FormattedTextField(
                value = 3,
                modifier = SwingModifier.testTag("amount"),
                formatterFactory = integerFactory(),
                focusLostBehavior = JFormattedTextField.PERSIST,
            )
        }
        val field = onNodeWithTag("amount").fetch<JFormattedTextField>()
        assertEquals(JFormattedTextField.PERSIST, field.focusLostBehavior)
    }

    @Test
    fun nullValueRendersEmptyAndDefaultFactoryFormatsByType() = runComposeSwingTest {
        setContent {
            FormattedTextField(value = null, modifier = SwingModifier.testTag("amount"))
        }
        val field = onNodeWithTag("amount").fetch<JFormattedTextField>()
        assertNull(field.value, "a null value should leave the field value null")
        assertEquals("", field.text, "a null value should render as empty text")
    }
}
