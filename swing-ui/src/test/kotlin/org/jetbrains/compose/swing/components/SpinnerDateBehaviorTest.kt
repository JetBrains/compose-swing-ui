package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral tests for the date [Spinner] overload, driven through the real composition
 * pipeline and asserting against the live `JSpinner` and the shared model.
 */
class SpinnerDateBehaviorTest {
    @Test
    fun theDateFactoryDefaultsToAnUnboundedDaySequence() = runComposeSwingTest {
        val march15 = date(Calendar.MARCH, 15)
        setContent { Spinner(value = march15) }

        val model = onNodeOfType<JSpinner>().fetch().model as SpinnerDateModel
        assertEquals(march15, model.value, "the model renders the initial date")
        assertEquals(null, model.start, "the sequence is open below by default")
        assertEquals(null, model.end, "the sequence is open above by default")
        assertEquals(Calendar.DAY_OF_MONTH, model.calendarField, "the sequence steps by a day by default")
    }

    @Test
    fun steppingADateSpinnerIsReportedThroughCallback() = runComposeSwingTest {
        var stateValue by mutableStateOf(date(Calendar.MARCH, 15))
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it })
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(date(Calendar.MARCH, 16), stateValue, "a step moves the date on by one day")
    }

    @Test
    fun theDateBoundsFenceTheSequence() = runComposeSwingTest {
        val march15 = date(Calendar.MARCH, 15)
        setContent {
            Spinner(
                value = march15,
                start = march15,
                end = date(Calendar.MARCH, 16),
            )
        }

        val model = onNodeOfType<JSpinner>().fetch().model
        assertEquals(null, model.previousValue, "the start fences the sequence below")
        assertEquals(date(Calendar.MARCH, 16), model.nextValue, "a step inside the range is allowed")
    }

    @Test
    fun changingTheCalendarFieldAcrossRecompositionIsHonoured() = runComposeSwingTest {
        val march15 = date(Calendar.MARCH, 15)
        var calendarField by mutableStateOf(Calendar.DAY_OF_MONTH)
        setContent { Spinner(value = march15, calendarField = calendarField) }
        awaitIdle()

        val model = onNodeOfType<JSpinner>().fetch().model
        assertEquals(date(Calendar.MARCH, 16), model.nextValue, "a day unit steps on to the next day")

        calendarField = Calendar.MONTH
        awaitIdle()

        assertEquals(date(Calendar.APRIL, 15), model.nextValue, "the new unit updates the model in place")
        assertEquals(date(Calendar.FEBRUARY, 15), model.previousValue, "the new unit steps back by a month too")
    }

    @Test
    fun changingTheDateBoundsAcrossRecompositionIsHonoured() = runComposeSwingTest {
        val march15 = date(Calendar.MARCH, 15)
        var end: Date? by mutableStateOf(march15)
        setContent { Spinner(value = march15, end = end) }
        awaitIdle()

        val model = onNodeOfType<JSpinner>().fetch().model
        assertEquals(null, model.nextValue, "the original ceiling blocks a step up")

        end = date(Calendar.MARCH, 20)
        awaitIdle()

        assertEquals(date(Calendar.MARCH, 16), model.nextValue, "the raised ceiling updates the model in place")
    }

    @Test
    fun anInitialDateOutsideTheBoundsIsRefused() = runComposeSwingTest {
        assertFailsWith<IllegalArgumentException> {
            setContent {
                Spinner(
                    value = date(Calendar.MARCH, 15),
                    start = date(Calendar.MARCH, 16),
                )
            }
        }
    }
}

/** Midnight on the given day of 2024, local time - the calendar the date factory's sequences run over. */
private fun date(
    month: Int,
    dayOfMonth: Int,
): Date = GregorianCalendar(2024, month, dayOfMonth).time
