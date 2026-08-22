package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParseException
import java.util.Locale
import javax.swing.JFormattedTextField
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [FormattedTextField] over a real `JFormattedTextField`. Each test asserts what
 * an observer of the live field sees: the committed value formatted into the display, the value the
 * user's callback receives once an edit commits, and what a value written back by that callback - or a
 * newly declared formatter factory - does to the characters the field is holding.
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
                onValueChange = {},
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
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
                onValueChange = {
                    reported += it
                    value = it
                },
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
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
                onValueChange = {
                    reported += it
                    value = it
                },
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "not-a-number"
        assertFailsWith<ParseException>("committing an unparsable edit should fail") { field.commitEdit() }
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
                onValueChange = {},
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
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
                onValueChange = {
                    reported += it
                    value = it
                },
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "5"
        field.commitEdit()
        awaitIdle()

        assertEquals(listOf<Any?>(5), reported, "the callback should fire exactly once, not echo")
        assertEquals(5, field.value, "the field should hold the committed value")
    }

    @Test
    fun aValueWrittenBackKeepsTheCharactersTypedSinceTheCommit() = runComposeSwingTest {
        var value by mutableStateOf(0 as Any?)
        setContent {
            val factory = remember { integerFactory() }
            FormattedTextField(
                value = value,
                onValueChange = { value = it },
                formatterFactory = factory,
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "5"
        field.commitEdit()

        // The user keeps typing while the callback's write is still on its way back through the
        // composition. Applying a value the field has already committed would reinstall the formatter and
        // regenerate the characters from it, dropping these digits.
        field.document.insertString(field.document.length, "67", null)
        awaitIdle()

        assertEquals("567", field.text, "the uncommitted characters should survive the value written back")
        assertEquals(5, field.value, "the committed value is the one the callback reported")
    }

    @Test
    fun aCommitThatLeavesTheValueUnchangedIsNotReported() = runComposeSwingTest {
        val reported = mutableListOf<Any?>()
        setContent {
            val factory = remember { DefaultFormatterFactory(BlankIsNoValueFormatter()) }
            FormattedTextField(
                value = null,
                onValueChange = { reported += it },
                formatterFactory = factory,
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertNull(field.value, "the field starts with no value")

        // Committing blank text parses to the value the field already holds. The field fires the value
        // property for the reformat it does either way, and nothing was committed to report.
        field.commitEdit()
        awaitIdle()

        assertTrue(reported.isEmpty(), "a commit that does not move the value should report nothing")
    }

    @Test
    fun aFreshFormatterFactoryInstanceRerendersTheCommittedValue() = runComposeSwingTest {
        var generation by mutableStateOf(0)
        setContent {
            // Read so that bumping it recomposes this content.
            generation
            FormattedTextField(
                value = 42,
                onValueChange = {},
                // A new instance per recomposition: a new formatter to install, whatever it formats.
                formatterFactory = integerFactory(),
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "429"
        field.caretPosition = 1

        generation++
        awaitIdle()

        assertEquals("42", field.text, "installing a formatter re-renders the committed value through it")
    }

    @Test
    fun aHeldFormatterFactoryLeavesAnUncommittedEditAlone() = runComposeSwingTest {
        var generation by mutableStateOf(0)
        setContent {
            // Read so that bumping it recomposes this content.
            generation
            val factory = remember { integerFactory() }
            FormattedTextField(
                value = 42,
                onValueChange = {},
                formatterFactory = factory,
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "429"
        field.caretPosition = 1

        generation++
        awaitIdle()

        assertEquals("429", field.text, "one held factory installs one formatter, so an uncommitted edit stands")
        assertEquals(1, field.caretPosition, "and the caret stays where the user left it")
    }

    @Test
    fun focusLostBehaviorIsApplied() = runComposeSwingTest {
        setContent {
            FormattedTextField(
                value = 3,
                onValueChange = {},
                formatterFactory = integerFactory(),
                focusLostBehavior = JFormattedTextField.PERSIST,
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertEquals(JFormattedTextField.PERSIST, field.focusLostBehavior)
    }

    @Test
    fun aNewFormatterFactoryRerendersTheValueWithoutReportingACommit() = runComposeSwingTest {
        var grouped by mutableStateOf(false)
        val reported = mutableListOf<Any?>()
        setContent {
            val groups = grouped
            // One factory instance per pattern, so the field is re-rendered by the pattern changing.
            val factory = remember(groups) { patternFactory(if (groups) "#,###" else "#") }
            FormattedTextField(
                value = 1234567,
                onValueChange = { reported += it },
                formatterFactory = factory,
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertEquals("1234567", field.text, "the field renders the value through the composed formatter")

        grouped = true
        awaitIdle()

        assertEquals("1,234,567", field.text, "a new formatter re-renders the committed value")
        assertEquals(1234567, field.value, "the value survives the formatter it is rendered through")
        assertTrue(reported.isEmpty(), "re-rendering the value is not a commit")
    }

    @Test
    fun anUndeclaredFormattedTextFieldIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { FormattedTextField(value = null, onValueChange = {}) }
        onNodeOfType<JFormattedTextField>().assertTreeMatches(JFormattedTextField())
    }
}

// A factory formatting integers by [pattern], with US symbols so the grouping separator is the same
// wherever the test runs.
private fun patternFactory(pattern: String): DefaultFormatterFactory {
    val formatter = NumberFormatter(DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)))
    formatter.valueClass = Int::class.javaObjectType
    return DefaultFormatterFactory(formatter)
}

// A formatter for an optional value: blank text stands for no value at all, and any other text is
// unparsable. Committing blank text therefore parses to null, the value an empty field starts with.
private class BlankIsNoValueFormatter : JFormattedTextField.AbstractFormatter() {
    override fun stringToValue(text: String?): Any? {
        if (!text.isNullOrBlank()) throw ParseException(text, 0)
        return null
    }

    override fun valueToString(value: Any?): String = value?.toString().orEmpty()
}
