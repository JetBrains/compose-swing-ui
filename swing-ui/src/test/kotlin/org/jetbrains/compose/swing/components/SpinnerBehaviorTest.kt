package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSpinner
import javax.swing.SpinnerListModel
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for [Spinner] and [SpinnerState], driven through the real composition pipeline and
 * asserting against the live `JSpinner`, the state, and the shared model.
 *
 * The central guarantees: a numeric factory renders its value and bounds into a number model; a change
 * originating from the spinner is reported through [SpinnerState.value]; a value written through
 * [SpinnerState.value] reaches the spinner without echoing back as a spurious extra change; the value
 * argument is the initial value only while the bounds, step and items are declarative — a later change
 * to a bound or the item list updates the spinner in place, preserving the current value; the list
 * factory cycles through its items; and the raw-`ChangeListener` overload notifies on every change of an
 * arbitrary model.
 */
class SpinnerBehaviorTest {
    private fun ComposeSwingTest.spinner(): JSpinner = onNodeOfType<JSpinner>().fetch()

    @Test
    fun theIntFactoryRendersValueAndBoundsIntoTheNumberModel() = runComposeSwingTest {
        setContent { Spinner(rememberSpinnerState(value = 5, min = 0, max = 10, step = 2)) }

        val model = spinner().model as SpinnerNumberModel
        assertEquals(5, model.value, "the model should render the initial value")
        assertEquals(0, model.minimum, "the model should render the minimum bound")
        assertEquals(10, model.maximum, "the model should render the maximum bound")
        assertEquals(2, model.stepSize, "the model should render the step size")
    }

    @Test
    fun aNullBoundLeavesThatSideOpen() = runComposeSwingTest {
        setContent { Spinner(rememberSpinnerState(value = 5)) }

        val model = spinner().model as SpinnerNumberModel
        assertEquals(null, model.minimum, "a null min leaves the lower side unbounded")
        assertEquals(null, model.maximum, "a null max leaves the upper side unbounded")
    }

    @Test
    fun steppingTheSpinnerIsReportedThroughStateValue() = runComposeSwingTest {
        lateinit var state: SpinnerState
        val observed = mutableListOf<Any?>()
        setContent {
            state = rememberSpinnerState(value = 5, min = 0, max = 10)
            observed += state.value
            Spinner(state)
        }
        awaitIdle()

        val spinner = spinner()
        // getNextValue is what the up arrow commits; setting it drives the same model write path.
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(6, state.value, "a step through the spinner is reported through the state")
        assertTrue(6 in observed, "the recomposition observing state.value sees the stepped value")
    }

    @Test
    fun aValueWrittenThroughStateReachesTheSpinnerWithoutAnExtraChange() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var changes = 0
        setContent {
            state = rememberSpinnerState(value = 5, min = 0, max = 10)
            Spinner(state)
        }
        awaitIdle()
        val spinner = spinner()
        spinner.model.addChangeListener { changes++ }

        state.value = 8
        awaitIdle()

        assertEquals(8, spinner.value, "a value written through the state reaches the spinner")
        assertEquals(1, changes, "one write produces exactly one model change")

        // Writing the same value again is a no-op and must not fire another change.
        state.value = 8
        awaitIdle()
        assertEquals(1, changes, "re-writing the current value does not fire a change")
    }

    @Test
    fun theDoubleFactoryStepsByAFractionalStep() = runComposeSwingTest {
        lateinit var state: SpinnerState
        setContent {
            state = rememberSpinnerState(value = 1.0, min = 0.0, max = 2.0, step = 0.25)
            Spinner(state)
        }
        awaitIdle()

        val spinner = spinner()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(1.25, state.value, "a fractional step is reported through the state")
    }

    @Test
    fun theListFactoryCyclesThroughItsItems() = runComposeSwingTest {
        lateinit var state: SpinnerState
        setContent {
            state = rememberSpinnerState(items = listOf("red", "green", "blue"), selectedIndex = 0)
            Spinner(state)
        }
        awaitIdle()

        val spinner = spinner()
        assertEquals("red", state.value, "the list spinner starts at the selected item")

        // The up arrow advances to the next list element; driving nextValue takes the same path.
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals("green", state.value, "advancing moves to the next list item")
    }

    @Test
    fun theListFactoryHonoursTheSelectedIndex() = runComposeSwingTest {
        lateinit var state: SpinnerState
        setContent {
            state = rememberSpinnerState(items = listOf("a", "b", "c"), selectedIndex = 1)
            Spinner(state)
        }
        awaitIdle()

        assertEquals("b", state.value, "the list spinner starts at the selected index")
        assertEquals(
            listOf("a", "b", "c"),
            (spinner().model as SpinnerListModel).list,
            "the model holds the items",
        )
    }

    @Test
    fun theRawListenerOverloadNotifiesOnEveryChangeOfAnArbitraryModel() = runComposeSwingTest {
        val model = SpinnerNumberModel(5, 0, 10, 1)
        val received = mutableListOf<Any?>()
        val listener = ChangeListener { event -> received += (event.source as JSpinner).value }
        setContent { Spinner(model = model, changeListener = listener) }
        awaitIdle()

        val spinner = spinner()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(listOf<Any?>(6), received, "a step fires the raw change listener with the new value")
    }

    @Test
    fun swappingTheStateRebindsTheModel() = runComposeSwingTest {
        var useFirst by mutableStateOf(true)
        setContent {
            val first = rememberSpinnerState(value = 1, min = 0, max = 10)
            val second = rememberSpinnerState(value = 7, min = 0, max = 10)
            Spinner(if (useFirst) first else second)
        }
        awaitIdle()

        assertEquals(1, spinner().value, "the first state's model renders initially")

        useFirst = false
        awaitIdle()
        assertEquals(7, spinner().value, "swapping the state rebinds the spinner to the new model")
    }

    @Test
    fun raisingTheMaxAcrossRecompositionIsHonouredAndPreservesTheValue() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var max by mutableStateOf(10)
        setContent {
            state = rememberSpinnerState(value = 8, min = 0, max = max, step = 1)
            Spinner(state)
        }
        awaitIdle()

        val model = spinner().model as SpinnerNumberModel
        assertEquals(10, model.maximum, "the model starts at the original maximum")

        max = 20
        awaitIdle()

        assertEquals(20, model.maximum, "the raised maximum updates the model in place")
        assertEquals(8, state.value, "the current value survives the bound change")

        // With the max lifted past 10, the spinner can now step above the old ceiling.
        repeat(5) {
            val spinner = spinner()
            spinner.value = spinner.model.nextValue
        }
        awaitIdle()
        assertEquals(13, state.value, "the value can now step past the old maximum")
    }

    @Test
    fun changingTheItemsAcrossRecompositionIsHonoured() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var items by mutableStateOf(listOf("red", "green", "blue"))
        setContent {
            state = rememberSpinnerState(items = items, selectedIndex = 0)
            Spinner(state)
        }
        awaitIdle()
        assertEquals("red", state.value, "the list spinner starts at the first item")

        items = listOf("one", "two", "three")
        awaitIdle()

        assertEquals(
            listOf("one", "two", "three"),
            (spinner().model as SpinnerListModel).list,
            "the new items update the model in place",
        )

        // Cycling now advances through the replacement items.
        val spinner = spinner()
        spinner.value = spinner.model.nextValue
        awaitIdle()
        assertEquals("two", state.value, "the spinner cycles the new items")
    }

    @Test
    fun theValueArgumentIsStateOwnedAndNotDrivenByRecomposition() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var value by mutableStateOf(3)
        setContent {
            state = rememberSpinnerState(value = value, min = 0, max = 10, step = 1)
            Spinner(state)
        }
        awaitIdle()
        assertEquals(3, state.value, "the spinner starts at the initial value")

        value = 9
        awaitIdle()

        assertEquals(3, state.value, "a later change to the value argument does not move the spinner")

        // The value is driven through the state instead.
        state.value = 6
        awaitIdle()
        assertEquals(6, state.value, "the value is driven through the state")
    }
}
