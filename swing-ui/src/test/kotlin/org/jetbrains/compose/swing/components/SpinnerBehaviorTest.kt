package org.jetbrains.compose.swing.components

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.AbstractSpinnerModel
import javax.swing.JSpinner
import javax.swing.SpinnerListModel
import javax.swing.SpinnerModel
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
 * [SpinnerState.value] reaches the spinner without echoing back as a spurious extra change; the initial
 * value and initial selection seed the model once while the bounds, step and items are declarative - a
 * later change to a bound, the step or the item list updates the spinner in place; the list factory
 * cycles through its items; and the raw-`ChangeListener` overload notifies on every change of an
 * arbitrary model.
 */
class SpinnerBehaviorTest {
    /** How many of the model's change listeners belong to a [SpinnerState], as opposed to the spinner's own. */
    private fun SpinnerModel.stateListeners(): Int =
        (this as AbstractSpinnerModel).changeListeners.count { it.javaClass.name.startsWith(STATE_PACKAGE) }

    @Test
    fun theIntFactoryRendersValueAndBoundsIntoTheNumberModel() = runComposeSwingTest {
        setContent { Spinner(rememberSpinnerState(initialValue = 5, min = 0, max = 10, step = 2)) }

        val model = onNodeOfType<JSpinner>().fetch().model as SpinnerNumberModel
        assertEquals(5, model.value, "the model should render the initial value")
        assertEquals(0, model.minimum, "the model should render the minimum bound")
        assertEquals(10, model.maximum, "the model should render the maximum bound")
        assertEquals(2, model.stepSize, "the model should render the step size")
    }

    @Test
    fun aNullBoundLeavesThatSideOpen() = runComposeSwingTest {
        setContent { Spinner(rememberSpinnerState(initialValue = 5)) }

        val model = onNodeOfType<JSpinner>().fetch().model as SpinnerNumberModel
        assertEquals(null, model.minimum, "a null min leaves the lower side unbounded")
        assertEquals(null, model.maximum, "a null max leaves the upper side unbounded")
    }

    @Test
    fun steppingTheSpinnerIsReportedThroughStateValue() = runComposeSwingTest {
        lateinit var state: SpinnerState
        val observed = mutableListOf<Any?>()
        setContent {
            state = rememberSpinnerState(initialValue = 5, min = 0, max = 10)
            observed += state.value
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
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
            state = rememberSpinnerState(initialValue = 5, min = 0, max = 10)
            Spinner(state)
        }
        awaitIdle()
        val spinner = onNodeOfType<JSpinner>().fetch()
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
            state = rememberSpinnerState(initialValue = 1.0, min = 0.0, max = 2.0, step = 0.25)
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(1.25, state.value, "a fractional step is reported through the state")
    }

    @Test
    fun theListFactoryCyclesThroughItsItems() = runComposeSwingTest {
        lateinit var state: SpinnerState
        setContent {
            state = rememberSpinnerState(items = listOf("red", "green", "blue"), initialSelectedIndex = 0)
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals("red", state.value, "the list spinner starts at the selected item")

        // The up arrow advances to the next list element; driving nextValue takes the same path.
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals("green", state.value, "advancing moves to the next list item")
    }

    @Test
    fun theListFactoryHonoursTheInitialSelectedIndex() = runComposeSwingTest {
        lateinit var state: SpinnerState
        setContent {
            state = rememberSpinnerState(items = listOf("a", "b", "c"), initialSelectedIndex = 1)
            Spinner(state)
        }
        awaitIdle()

        assertEquals("b", state.value, "the list spinner starts at the selected index")
        assertEquals(
            listOf("a", "b", "c"),
            (onNodeOfType<JSpinner>().fetch().model as SpinnerListModel).list,
            "the model holds the items",
        )
    }

    @Test
    fun anInitialSelectedIndexOutsideTheItemsLeavesTheSpinnerAtTheFirstItem() = runComposeSwingTest {
        lateinit var state: SpinnerState
        setContent {
            state = rememberSpinnerState(items = listOf("a", "b", "c"), initialSelectedIndex = 7)
            Spinner(state)
        }
        awaitIdle()

        assertEquals("a", state.value, "an index outside the list starts the spinner at the first item")

        // Cycling from there advances through the list, so the spinner really sits on the first item.
        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()
        assertEquals("b", state.value, "the spinner cycles on from the first item")
    }

    @Test
    fun theRawListenerOverloadNotifiesOnEveryChangeOfAnArbitraryModel() = runComposeSwingTest {
        val model = SpinnerNumberModel(5, 0, 10, 1)
        val received = mutableListOf<Any?>()
        val listener = ChangeListener { event -> received += (event.source as JSpinner).value }
        setContent { Spinner(model = model, changeListener = listener) }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(listOf<Any?>(6), received, "a step fires the raw change listener with the new value")
    }

    @Test
    fun swappingTheStateRebindsTheModel() = runComposeSwingTest {
        var useFirst by mutableStateOf(true)
        setContent {
            val first = rememberSpinnerState(initialValue = 1, min = 0, max = 10)
            val second = rememberSpinnerState(initialValue = 7, min = 0, max = 10)
            Spinner(if (useFirst) first else second)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals(1, spinner.value, "the first state's model renders initially")

        useFirst = false
        awaitIdle()
        assertEquals(7, spinner.value, "swapping the state rebinds the spinner to the new model")
    }

    @Test
    fun raisingTheMaxAcrossRecompositionIsHonouredAndPreservesTheValue() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var max by mutableStateOf(10)
        setContent {
            state = rememberSpinnerState(initialValue = 8, min = 0, max = max, step = 1)
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(10, model.maximum, "the model starts at the original maximum")

        max = 20
        awaitIdle()

        assertEquals(20, model.maximum, "the raised maximum updates the model in place")
        assertEquals(8, state.value, "the current value survives the bound change")

        // With the max lifted past 10, the spinner can now step above the old ceiling.
        repeat(5) { spinner.value = spinner.model.nextValue }
        awaitIdle()
        assertEquals(13, state.value, "the value can now step past the old maximum")
    }

    @Test
    fun changingTheItemsAcrossRecompositionIsHonoured() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var items by mutableStateOf(listOf("red", "green", "blue"))
        setContent {
            state = rememberSpinnerState(items = items, initialSelectedIndex = 0)
            Spinner(state)
        }
        awaitIdle()
        assertEquals("red", state.value, "the list spinner starts at the first item")

        items = listOf("one", "two", "three")
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals(
            listOf("one", "two", "three"),
            (spinner.model as SpinnerListModel).list,
            "the new items update the model in place",
        )
        assertEquals("one", state.value, "the selection moves to the head of the new items")

        // Cycling now advances through the replacement items.
        spinner.value = spinner.model.nextValue
        awaitIdle()
        assertEquals("two", state.value, "the spinner cycles the new items")
    }

    @Test
    fun loweringTheMinAcrossRecompositionIsHonouredAndOpensTheLowerRange() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var min by mutableStateOf(4)
        setContent {
            state = rememberSpinnerState(initialValue = 5, min = min, max = 10, step = 1)
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(4, model.minimum, "the model starts at the original minimum")
        spinner.value = model.previousValue
        awaitIdle()
        assertEquals(4, state.value, "the value steps down to the original floor")
        assertEquals(null, model.previousValue, "the original floor blocks a further step down")

        min = 0
        awaitIdle()

        assertEquals(0, model.minimum, "the lowered minimum updates the model in place")
        assertEquals(4, state.value, "the current value survives the bound change")

        repeat(3) { spinner.value = spinner.model.previousValue }
        awaitIdle()
        assertEquals(1, state.value, "the value can now step below the old minimum")
    }

    @Test
    fun changingTheStepAcrossRecompositionIsHonoured() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var step by mutableStateOf(2)
        setContent {
            state = rememberSpinnerState(initialValue = 0, min = 0, max = 100, step = step)
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(2, model.stepSize, "the model starts at the original step")
        spinner.value = model.nextValue
        awaitIdle()
        assertEquals(2, state.value, "a step advances by the original step size")

        step = 5
        awaitIdle()

        assertEquals(5, model.stepSize, "the new step updates the model in place")
        assertEquals(2, state.value, "the current value survives the step change")

        spinner.value = model.nextValue
        awaitIdle()
        assertEquals(7, state.value, "a later step advances by the new step size")
    }

    @Test
    fun clearingTheMaxAcrossRecompositionOpensTheUpperSide() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var max by mutableStateOf<Int?>(10)
        setContent {
            state = rememberSpinnerState(initialValue = 9, min = 0, max = max, step = 1)
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(10, model.maximum, "the model starts at the original maximum")
        spinner.value = model.nextValue
        awaitIdle()
        assertEquals(null, model.nextValue, "the original ceiling blocks a further step up")

        max = null
        awaitIdle()

        assertEquals(null, model.maximum, "clearing the maximum leaves the upper side unbounded")
        assertEquals(10, state.value, "the current value survives the bound being cleared")

        repeat(3) { spinner.value = spinner.model.nextValue }
        awaitIdle()
        assertEquals(13, state.value, "the value can now step past the cleared bound")
    }

    @Test
    fun clearingTheMinAcrossRecompositionOpensTheLowerSide() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var min by mutableStateOf<Int?>(4)
        setContent {
            state = rememberSpinnerState(initialValue = 5, min = min, max = 10, step = 1)
            Spinner(state)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(4, model.minimum, "the model starts at the original minimum")
        spinner.value = model.previousValue
        awaitIdle()
        assertEquals(null, model.previousValue, "the original floor blocks a further step down")

        min = null
        awaitIdle()

        assertEquals(null, model.minimum, "clearing the minimum leaves the lower side unbounded")
        assertEquals(4, state.value, "the current value survives the bound being cleared")

        repeat(3) { spinner.value = spinner.model.previousValue }
        awaitIdle()
        assertEquals(1, state.value, "the value can now step past the cleared bound")
    }

    @Test
    fun tighteningTheBoundsAcrossRecompositionIsHonouredAndLeavesTheValueWhereItIs() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var bounds by mutableStateOf<IntRange?>(null)
        setContent {
            state = rememberSpinnerState(initialValue = 5, min = bounds?.first, max = bounds?.last, step = 1)
            Spinner(state)
        }
        awaitIdle()

        val model = onNodeOfType<JSpinner>().fetch().model as SpinnerNumberModel
        assertEquals(4, model.previousValue, "an open lower side lets the value step down")

        bounds = 6..8
        awaitIdle()

        assertEquals(6, model.minimum, "a minimum applied to an open side updates the model in place")
        assertEquals(8, model.maximum, "a maximum applied to an open side updates the model in place")
        assertEquals(5, state.value, "a bound tightened past the value does not move the value")
        assertEquals(null, model.previousValue, "a step away from the new range is refused")
        assertEquals(6, model.nextValue, "a step that lands inside the new range is allowed")

        // A value written back inside the new range restores stepping in both directions.
        state.value = 7
        awaitIdle()
        assertEquals(6, model.previousValue, "a value inside the new range steps down again")
        assertEquals(8, model.nextValue, "a value inside the new range steps up again")
    }

    @Test
    fun theInitialValueSeedsTheModelOnceAndIsNotDrivenByRecomposition() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var initialValue by mutableStateOf(3)
        setContent {
            state = rememberSpinnerState(initialValue = initialValue, min = 0, max = 10, step = 1)
            Spinner(state)
        }
        awaitIdle()
        assertEquals(3, state.value, "the spinner starts at the initial value")

        initialValue = 9
        awaitIdle()

        assertEquals(3, state.value, "a later change to the initial value does not move the spinner")

        // The value is driven through the state instead.
        state.value = 6
        awaitIdle()
        assertEquals(6, state.value, "the value is driven through the state")
    }

    @Test
    fun theInitialSelectedIndexSeedsTheModelOnceAndIsNotDrivenByRecomposition() = runComposeSwingTest {
        lateinit var state: SpinnerState
        var initialSelectedIndex by mutableStateOf(1)
        setContent {
            state =
                rememberSpinnerState(
                    items = listOf("a", "b", "c"),
                    initialSelectedIndex = initialSelectedIndex,
                )
            Spinner(state)
        }
        awaitIdle()
        assertEquals("b", state.value, "the spinner starts at the initially selected item")

        initialSelectedIndex = 2
        awaitIdle()

        assertEquals("b", state.value, "a later change to the initial index does not move the spinner")

        // The selection is driven through the state instead.
        state.value = "c"
        awaitIdle()
        assertEquals("c", state.value, "the selection is driven through the state")
    }

    @Test
    fun aParkedSpinnerLeavesASurvivingStateBoundOnce() = runComposeSwingTest {
        // The state is remembered above the parked region, so it outlives the spinner: it stands in for a
        // state hoisted above a collapsible or reused region. One state observes its model once for as long
        // as it lives, so the census of its own listeners on the model is the same before, during and after
        // a park - the spinner's lifecycle is not the state's.
        lateinit var state: SpinnerState
        var active by mutableStateOf(true)
        setContent {
            state = rememberSpinnerState(initialValue = 3, min = 0, max = 10)
            ReusableContentHost(active = active) {
                Spinner(state)
            }
        }

        val model = state.model
        assertEquals(1, model.stateListeners(), "the state listens to its model once while the spinner is mounted")

        active = false
        awaitIdle()
        assertEquals(1, model.stateListeners(), "parking the spinner does not touch the surviving state")

        active = true
        awaitIdle()
        assertEquals(1, model.stateListeners(), "reactivating the spinner does not register the state again")

        // The state still reports the spinner's steps through the one registration it has.
        onNodeOfType<JSpinner>().fetch().value = 8
        awaitIdle()
        assertEquals(8, state.value, "the reactivated spinner drives the state")
    }
}

/** The package a [SpinnerState]'s own listeners live in. */
private const val STATE_PACKAGE = "org.jetbrains.compose.swing.components"
