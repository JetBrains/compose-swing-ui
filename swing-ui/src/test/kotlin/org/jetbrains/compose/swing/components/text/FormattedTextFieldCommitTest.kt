package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeListener
import javax.swing.JFormattedTextField
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for taking a [FormattedTextField]'s typed text on demand, and for what the field
 * reports about text it cannot read.
 *
 * A field commits on its own only when the user leaves it or presses Enter, so an application event - a
 * dialog's confirm button, a form the user submits - reaches the edit where it stands through
 * [FormattedValueState.commit]. Text that does not parse is not committed, and the field reports that -
 * through [FormattedValueState.isEditValid] or through `onEditValidChange` - so a caller can tell the
 * field is showing something its formatter cannot read.
 */
class FormattedTextFieldCommitTest {
    // A factory formatting integers, so a committed value is an Int and the field's text is its digits.
    private fun integerFactory(): DefaultFormatterFactory {
        val formatter = NumberFormatter()
        formatter.valueClass = Int::class.javaObjectType
        return DefaultFormatterFactory(formatter)
    }

    @Test
    fun commitTakesTheTypedTextAndWritesTheParsedValueIntoTheState() = runComposeSwingTest {
        lateinit var state: FormattedValueState
        setContent {
            state = rememberFormattedValueState(1)
            val factory = remember { integerFactory() }
            FormattedTextField(state = state, formatterFactory = factory)
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "250"

        assertTrue(state.commit(), "text the formatter reads is taken as the field's value")
        awaitIdle()

        assertEquals(250, state.value, "the commit is written into the state")
        assertEquals(250, field.value, "and the field holds what was committed")
    }

    @Test
    fun commitAnswersFalseForTextTheFormatterCannotRead() = runComposeSwingTest {
        lateinit var state: FormattedValueState
        setContent {
            state = rememberFormattedValueState(1)
            val factory = remember { integerFactory() }
            FormattedTextField(state = state, formatterFactory = factory)
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        field.text = "not a number"

        assertFalse(state.commit(), "text that does not parse is answered, not raised")
        awaitIdle()

        assertEquals(1, state.value, "the state stays on the value it already held")
        assertEquals(1, field.value, "and so does the field")
    }

    @Test
    fun aStateTheFieldLeftBehindTakesNothing() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        lateinit var state: FormattedValueState
        setContent {
            state = rememberFormattedValueState(1)
            val factory = remember { integerFactory() }
            if (shown) FormattedTextField(state = state, formatterFactory = factory)
        }
        onNodeOfType<JFormattedTextField>().fetch().text = "250"

        shown = false
        awaitIdle()

        assertFalse(state.commit(), "a state bound to nothing commits nothing")
    }

    @Test
    fun editValidityFollowsWhetherTheTypedTextParses() = runComposeSwingTest {
        lateinit var state: FormattedValueState
        setContent {
            state = rememberFormattedValueState(1)
            val factory = remember { integerFactory() }
            FormattedTextField(state = state, formatterFactory = factory)
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertTrue(state.isEditValid, "a field rendering its own value shows text that parses")

        field.text = "not a number"
        awaitIdle()
        assertFalse(state.isEditValid, "text the formatter cannot read is reported invalid")
        assertEquals(1, state.value, "and the value stands where the last commit left it")

        field.text = "250"
        awaitIdle()
        assertTrue(state.isEditValid, "text it can read again is reported valid")
    }

    @Test
    fun editValidityIsReportedAsTheTypedTextStopsAndStartsParsing() = runComposeSwingTest {
        val reported = mutableListOf<Boolean>()
        setContent {
            val factory = remember { integerFactory() }
            FormattedTextField(
                value = 1,
                formatterFactory = factory,
                onEditValidChange = { reported += it },
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertTrue(reported.isEmpty(), "a field rendering its own value has nothing to report")

        field.text = "not a number"
        awaitIdle()
        assertEquals(listOf(false), reported, "text the formatter cannot read is reported invalid once")

        field.text = "250"
        awaitIdle()
        assertEquals(listOf(false, true), reported, "text it can read again is reported valid")
    }

    @Test
    fun editValidityIsReportedToARawListenerDrivenFieldToo() = runComposeSwingTest {
        val reported = mutableListOf<Boolean>()
        setContent {
            val factory = remember { integerFactory() }
            FormattedTextField(
                value = 1,
                valuePropertyChangeListener = PropertyChangeListener { },
                formatterFactory = factory,
                onEditValidChange = { reported += it },
            )
        }

        onNodeOfType<JFormattedTextField>().fetch().text = "not a number"
        awaitIdle()

        assertEquals(listOf(false), reported, "validity is its own channel, whatever drives the value")
    }
}
