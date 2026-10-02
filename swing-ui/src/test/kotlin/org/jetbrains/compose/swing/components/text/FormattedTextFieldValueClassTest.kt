package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeListener
import java.text.DateFormat
import java.util.Date
import javax.swing.JFormattedTextField
import javax.swing.JFormattedTextField.AbstractFormatterFactory
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * The class a field with no declared formatter factory edits in. `JFormattedTextField` derives its
 * default formatter from the class of the value it is given only while it has no factory, so a field
 * declared a value of another class needs that default derived again - one derived for an `Int` renders
 * every later value as an `Int` and parses every commit back to one.
 */
class FormattedTextFieldValueClassTest {
    @Test
    fun aCommitCarriesTheClassOfTheValueDeclaredLast() = runComposeSwingTest {
        var value by mutableStateOf(10 as Any?)
        val reported = mutableListOf<Any?>()
        setContent {
            FormattedTextField(
                value = value,
                onValueChange = {
                    reported += it
                    value = it
                },
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()

        value = 10L
        awaitIdle()
        field.text = "7"
        field.commitEdit()
        awaitIdle()

        assertEquals(7L, reported.last(), "the commit should carry the declared value's class")
        assertEquals(7L, field.value, "and the field should hold it in that class")
    }

    @Test
    fun aDateDeclaredAfterANumberIsRenderedAsADate() = runComposeSwingTest {
        val moment = Date(0)
        var value by mutableStateOf(10 as Any?)
        setContent { FormattedTextField(value = value, onValueChange = {}) }
        val field = onNodeOfType<JFormattedTextField>().fetch()

        value = moment
        awaitIdle()

        assertEquals(
            DateFormat.getDateInstance().format(moment),
            field.text,
            "a date declared after a number should be rendered as a date",
        )
    }

    @Test
    fun aNumberDeclaredAfterADateIsRenderedAsANumber() = runComposeSwingTest {
        var value by mutableStateOf(Date(0) as Any?)
        setContent { FormattedTextField(value = value, onValueChange = {}) }
        val field = onNodeOfType<JFormattedTextField>().fetch()

        value = 10
        awaitIdle()

        assertEquals("10", field.text, "a number declared after a date should be rendered as a number")
        assertEquals(10, field.value, "and the field should hold it")
    }

    @Test
    fun aListenerThrowingOutOfTheDeriveLeavesTheFieldFormatting() = runComposeSwingTest {
        // Deriving the default again carries the field to the declared value with no formatter installed
        // and asks for one against the value it then holds, and the first of those two writes publishes
        // the value property at the caller's own listener.
        var value by mutableStateOf(10 as Any?)
        val listener =
            PropertyChangeListener { event ->
                if (event.newValue == 10L) error("the caller refuses this value")
                value = event.newValue
            }
        setContent { FormattedTextField(value = value, valuePropertyChangeListener = listener) }
        val field = onNodeOfType<JFormattedTextField>().fetch()

        value = 10L
        val failure = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertEquals("the caller refuses this value", failure.message, "the listener's failure should be reported")
        assertEquals(emptyList(), failure.suppressed.toList(), "and it should be the only one")

        field.text = "seven"
        assertFalse(field.isEditValid, "the field should still filter what is typed into it")

        field.text = "7"
        field.commitEdit()
        awaitIdle()

        assertEquals(7L, field.value, "and still commit an edit, in the class the declaration named")
    }

    @Test
    fun takingTheDeclaredFactoryAwayRestoresTheDefaultFormatter() = runComposeSwingTest {
        val numeric = DefaultFormatterFactory(NumberFormatter())
        var factory by mutableStateOf<AbstractFormatterFactory?>(numeric)
        val reported = mutableListOf<Any?>()
        setContent {
            FormattedTextField(
                value = 10,
                onValueChange = { reported += it },
                formatterFactory = factory,
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()

        factory = null
        awaitIdle()

        field.text = "seven"
        assertFalse(field.isEditValid, "a field left without a factory should still filter what is typed into it")

        field.text = "7"
        field.commitEdit()
        awaitIdle()

        assertEquals(7, reported.last(), "and still commit an edit")
    }
}
