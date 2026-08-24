package org.jetbrains.compose.swing.components

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.AbstractSpinnerModel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpinnerBehaviorTest {
    @Test
    fun theIntFactoryRendersValueAndBoundsIntoTheNumberModel() = runComposeSwingTest {
        setContent { Spinner(value = 5, min = 0, max = 10, step = 2) }

        val model = onNodeOfType<JSpinner>().fetch().model as SpinnerNumberModel
        assertEquals(5, model.value, "the model should render the initial value")
        assertEquals(0, model.minimum, "the model should render the minimum bound")
        assertEquals(10, model.maximum, "the model should render the maximum bound")
        assertEquals(2, model.stepSize, "the model should render the step size")
    }

    @Test
    fun anInitialValueOutsideTheBoundsIsRefused() = runComposeSwingTest {
        assertFailsWith<IllegalArgumentException> {
            setContent { Spinner(value = -1, min = 0, max = 100) }
        }
    }

    @Test
    fun aNullBoundLeavesThatSideOpen() = runComposeSwingTest {
        setContent { Spinner(value = 5) }

        val model = onNodeOfType<JSpinner>().fetch().model as SpinnerNumberModel
        assertEquals(null, model.minimum, "a null min leaves the lower side unbounded")
        assertEquals(null, model.maximum, "a null max leaves the upper side unbounded")
    }

    @Test
    fun steppingTheSpinnerIsReportedThroughCallback() = runComposeSwingTest {
        var stateValue by mutableStateOf<Number>(5)
        val observed = mutableListOf<Any?>()
        setContent {
            observed += stateValue
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0, max = 10)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(6, stateValue, "a step through the spinner is reported through the state")
        assertTrue(6 in observed, "the recomposition observing state.value sees the stepped value")
    }

    @Test
    fun aValueWrittenThroughStateReachesTheSpinnerWithoutAnExtraChange() = runComposeSwingTest {
        var stateValue by mutableStateOf<Number>(5)
        var changes = 0
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0, max = 10)
        }
        awaitIdle()
        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.model.addChangeListener { changes++ }

        stateValue = 8
        awaitIdle()

        assertEquals(8, spinner.value, "a value written through the state reaches the spinner")
        assertEquals(1, changes, "one write produces exactly one model change")

        stateValue = 8
        awaitIdle()
        assertEquals(1, changes, "re-writing the current value does not fire a change")
    }

    @Test
    fun theDoubleFactoryStepsByAFractionalStep() = runComposeSwingTest {
        var stateValue by mutableStateOf<Number>(1.0)
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0.0, max = 2.0, step = 0.25)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(1.25, stateValue, "a fractional step is reported through the state")
    }

    @Test
    fun theListFactoryCyclesThroughItsItems() = runComposeSwingTest {
        var stateValue by mutableStateOf("red")
        setContent {
            Spinner(items = listOf("red", "green", "blue"), value = stateValue, onValueChange = { stateValue = it })
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals("red", stateValue, "the list spinner starts at the selected item")

        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals("green", stateValue, "advancing moves to the next list item")
    }

    @Test
    fun theListFactoryHonorsTheInitialValue() = runComposeSwingTest {
        var stateValue by mutableStateOf("b")
        setContent {
            Spinner(items = listOf("a", "b", "c"), value = stateValue, onValueChange = { stateValue = it })
        }
        awaitIdle()

        assertEquals("b", stateValue, "the list spinner starts at the selected item")

        val model = onNodeOfType<JSpinner>().fetch().model
        assertEquals("a", model.previousValue, "the item before the selection is the one preceding it")
        assertEquals("c", model.nextValue, "the item after the selection is the one following it")
    }

    @Test
    fun anInitialValueOutsideTheItemsLeavesTheSpinnerAtTheFirstItem() = runComposeSwingTest {
        var stateValue by mutableStateOf("d")
        setContent {
            Spinner(items = listOf("a", "b", "c"), value = stateValue, onValueChange = { stateValue = it })
        }
        awaitIdle()

        assertEquals("a", stateValue, "the state value updates to the first item")
        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals("a", spinner.value, "the spinner model defaults to the first item")

        spinner.value = spinner.model.nextValue
        awaitIdle()
        assertEquals("b", stateValue, "the spinner cycles on from the first item")
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
    fun swappingTheModelRebindsTheSpinner() = runComposeSwingTest {
        var useFirst by mutableStateOf(true)
        val firstModel = SpinnerNumberModel(1, 0, 10, 1)
        val secondModel = SpinnerNumberModel(7, 0, 10, 1)
        setContent {
            Spinner(model = if (useFirst) firstModel else secondModel, changeListener = ChangeListener {})
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals(1, spinner.value, "the first model renders initially")

        useFirst = false
        awaitIdle()
        assertEquals(7, spinner.value, "swapping the model rebinds the spinner to the new model")
    }

    @Test
    fun raisingTheMaxAcrossRecompositionIsHonoredAndPreservesTheValue() = runComposeSwingTest {
        var max by mutableStateOf(10)
        var stateValue by mutableStateOf<Number>(8)
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0, max = max, step = 1)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(10, model.maximum, "the model starts at the original maximum")

        max = 20
        awaitIdle()

        assertEquals(20, model.maximum, "the raised maximum updates the model in place")
        assertEquals(8, stateValue, "the current value survives the bound change")

        repeat(5) { spinner.value = spinner.model.nextValue }
        awaitIdle()
        assertEquals(13, stateValue, "the value can now step past the old maximum")
    }

    @Test
    fun changingTheItemsAcrossRecompositionIsHonored() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green", "blue"))
        var stateValue by mutableStateOf("red")
        setContent {
            Spinner(items = items, value = stateValue, onValueChange = { stateValue = it })
        }
        awaitIdle()
        assertEquals("red", stateValue, "the list spinner starts at the first item")

        items = listOf("one", "two", "three")
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals("one", stateValue, "the selection moves to the head of the new items")
        assertEquals(null, spinner.model.previousValue, "the head of the new items has nothing before it")

        spinner.value = spinner.model.nextValue
        awaitIdle()
        assertEquals("two", stateValue, "the spinner cycles the new items")

        spinner.value = spinner.model.nextValue
        awaitIdle()
        assertEquals("three", stateValue, "the spinner reaches the last of the new items")
        assertEquals(null, spinner.model.nextValue, "the new items end where the new list does")
    }

    @Test
    fun anEmptyItemListLeavesTheSpinnerWithNoValue() = runComposeSwingTest {
        var changes = 0
        setContent {
            // No items means no selection, and the declaration says so rather than naming a value the
            // spinner could not show anyway.
            Spinner(items = emptyList<String>(), value = null, onValueChange = { changes++ })
        }
        awaitIdle()

        val model = onNodeOfType<JSpinner>().fetch().model
        assertEquals(null, model.value, "a spinner over no items holds no value")
        assertEquals(null, model.nextValue, "a spinner over no items has nothing to step up to")
        assertEquals(null, model.previousValue, "a spinner over no items has nothing to step down to")
        assertEquals(0, changes, "an empty spinner holds no selection, so there is nothing to report")
    }

    @Test
    fun aListLoadingIntoAnUndeclaredSelectionSettlesOnTheHeadAndReportsIt() = runComposeSwingTest {
        // The shape a caller loading a list asynchronously writes: no selection until the items arrive.
        // A spinner over items always shows one of them, so the head is what it settles on, and the
        // caller's own state follows through the callback rather than drifting from what is displayed.
        var items by mutableStateOf(emptyList<String>())
        var selected by mutableStateOf<String?>(null)
        setContent {
            Spinner(items = items, value = selected, onValueChange = { selected = it })
        }
        awaitIdle()
        assertEquals(null, selected, "nothing is selected while the list is still empty")

        items = listOf("red", "green")
        awaitIdle()

        assertEquals("red", selected, "the arriving list settles the spinner on its head and reports it")
        assertEquals("red", onNodeOfType<JSpinner>().fetch().value, "and the widget shows that head")
    }

    @Test
    fun emptyingAndRefillingTheItemsAcrossRecompositionIsHonored() = runComposeSwingTest {
        var items by mutableStateOf(listOf("red", "green"))
        var stateValue by mutableStateOf("red")
        setContent {
            Spinner(items = items, value = stateValue, onValueChange = { stateValue = it })
        }
        awaitIdle()
        assertEquals("red", stateValue, "the list spinner starts at the first item")

        items = emptyList()
        awaitIdle()
        val model = onNodeOfType<JSpinner>().fetch().model
        assertEquals(null, model.value, "emptying the items leaves the spinner with no value")

        items = listOf("one", "two")
        awaitIdle()
        assertEquals("one", stateValue, "refilling the items selects the head of the new list")

        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()
        assertEquals("two", stateValue, "the spinner cycles the refilled items")
    }

    @Test
    fun loweringTheMinAcrossRecompositionIsHonoredAndOpensTheLowerRange() = runComposeSwingTest {
        var min by mutableStateOf(4)
        var stateValue by mutableStateOf<Number>(5)
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = min, max = 10, step = 1)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(4, model.minimum, "the model starts at the original minimum")
        spinner.value = model.previousValue
        awaitIdle()
        assertEquals(4, stateValue, "the value steps down to the original floor")
        assertEquals(null, model.previousValue, "the original floor blocks a further step down")

        min = 0
        awaitIdle()

        assertEquals(0, model.minimum, "the lowered minimum updates the model in place")
        assertEquals(4, stateValue, "the current value survives the bound change")

        repeat(3) { spinner.value = spinner.model.previousValue }
        awaitIdle()
        assertEquals(1, stateValue, "the value can now step below the old minimum")
    }

    @Test
    fun changingTheStepAcrossRecompositionIsHonored() = runComposeSwingTest {
        var step by mutableStateOf(2)
        var stateValue by mutableStateOf<Number>(0)
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0, max = 100, step = step)
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val model = spinner.model as SpinnerNumberModel
        assertEquals(2, model.stepSize, "the model starts at the original step")
        spinner.value = model.nextValue
        awaitIdle()
        assertEquals(2, stateValue, "a step advances by the original step size")

        step = 5
        awaitIdle()

        assertEquals(5, model.stepSize, "the new step updates the model in place")
        assertEquals(2, stateValue, "the current value survives the step change")

        spinner.value = model.nextValue
        awaitIdle()
        assertEquals(7, stateValue, "a later step advances by the new step size")
    }

    @Test
    fun clearingTheMaxAcrossRecompositionOpensTheUpperSide() = runComposeSwingTest {
        var max by mutableStateOf<Int?>(10)
        var stateValue by mutableStateOf<Number>(9)
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0, max = max, step = 1)
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
        assertEquals(10, stateValue, "the current value survives the bound being cleared")

        repeat(3) { spinner.value = spinner.model.nextValue }
        awaitIdle()
        assertEquals(13, stateValue, "the value can now step past the cleared bound")
    }

    @Test
    fun clearingTheMinAcrossRecompositionOpensTheLowerSide() = runComposeSwingTest {
        var min by mutableStateOf<Int?>(4)
        var stateValue by mutableStateOf<Number>(5)
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = min, max = 10, step = 1)
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
        assertEquals(4, stateValue, "the current value survives the bound being cleared")

        repeat(3) { spinner.value = spinner.model.previousValue }
        awaitIdle()
        assertEquals(1, stateValue, "the value can now step past the cleared bound")
    }

    @Test
    fun tighteningTheBoundsAcrossRecompositionIsHonoredAndLeavesTheValueWhereItIs() = runComposeSwingTest {
        var bounds by mutableStateOf<IntRange?>(null)
        var stateValue by mutableStateOf<Number>(5)
        setContent {
            Spinner(
                value = stateValue,
                onValueChange = { stateValue = it },
                min = bounds?.first,
                max = bounds?.last,
                step = 1,
            )
        }
        awaitIdle()

        val model = onNodeOfType<JSpinner>().fetch().model as SpinnerNumberModel
        assertEquals(4, model.previousValue, "an open lower side lets the value step down")

        bounds = 6..8
        awaitIdle()

        assertEquals(6, model.minimum, "a minimum applied to an open side updates the model in place")
        assertEquals(8, model.maximum, "a maximum applied to an open side updates the model in place")
        assertEquals(5, stateValue, "a bound tightened past the value does not move the value")
        assertEquals(null, model.previousValue, "a step away from the new range is refused")
        assertEquals(6, model.nextValue, "a step that lands inside the new range is allowed")

        stateValue = 7
        awaitIdle()
        assertEquals(6, model.previousValue, "a value inside the new range steps down again")
        assertEquals(8, model.nextValue, "a value inside the new range steps up again")
    }

    @Test
    fun theValueIsDrivenByRecomposition() = runComposeSwingTest {
        var stateValue by mutableStateOf<Number>(3)
        setContent {
            Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0, max = 10, step = 1)
        }
        awaitIdle()
        assertEquals(3, stateValue, "the spinner starts at the initial value")

        stateValue = 9
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals(9, spinner.value, "a later change to the value moves the spinner")

        spinner.value = 6
        awaitIdle()
        assertEquals(6, stateValue, "the value is driven through the state")
    }

    @Test
    fun aParkedSpinnerUnbindsItsListeners() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var stateValue by mutableStateOf<Number>(3)
        setContent {
            ReusableContentHost(active = active) {
                Spinner(value = stateValue, onValueChange = { stateValue = it }, min = 0, max = 10)
            }
        }
        awaitIdle()

        active = false
        awaitIdle()

        active = true
        awaitIdle()

        onNodeOfType<JSpinner>().fetch().value = 8
        awaitIdle()
        assertEquals(8, stateValue, "the reactivated spinner drives the state")
    }

    @Test
    fun aModelSteppingItsValueInPlaceStillInvalidatesReaders() = runComposeSwingTest {
        val model = InPlaceSteppingSpinnerModel()
        val observed = mutableListOf<Int>()
        setContent {
            var counter by mutableStateOf(model.value as Counter, androidx.compose.runtime.neverEqualPolicy())
            observed += counter.n
            Spinner(model = model, changeListener = { counter = model.value as Counter })
        }
        awaitIdle()
        assertEquals(0, observed.last(), "the reader starts at the value the model holds")

        val spinner = onNodeOfType<JSpinner>().fetch()
        spinner.value = spinner.model.nextValue
        awaitIdle()

        assertEquals(1, (model.value as Counter).n, "the step reaches the model")
        assertTrue(1 in observed, "a step the model takes in place invalidates whoever reads the state")
    }

    @Test
    fun aWriteTheModelSettlesElsewhereIsReportedAsTheModelHoldsIt() = runComposeSwingTest {
        val model = ClampingSpinnerModel(maximum = 3)
        var stateValue by mutableStateOf<Number>(0)
        setContent {
            Spinner(model = model, changeListener = { stateValue = model.value as Number })
        }
        awaitIdle()

        model.value = 10
        awaitIdle()

        assertEquals(3, model.value, "the model clamps a write above its ceiling")
        assertEquals(3, stateValue, "the state reports what the model settled on, not what it was asked for")
    }
}

private class Counter(
    var n: Int,
)

private class InPlaceSteppingSpinnerModel : AbstractSpinnerModel() {
    private val counter = Counter(0)

    override fun getValue(): Any = counter

    override fun getNextValue(): Any = Counter(counter.n + 1)

    override fun getPreviousValue(): Any = Counter(counter.n - 1)

    override fun setValue(value: Any?) {
        val stepped = (value as Counter).n
        if (stepped != counter.n) {
            counter.n = stepped
            fireStateChanged()
        }
    }
}

private class ClampingSpinnerModel(
    private val maximum: Int,
) : AbstractSpinnerModel() {
    private var held = 0

    override fun getValue(): Any = held

    override fun getNextValue(): Any = minOf(held + 1, maximum)

    override fun getPreviousValue(): Any = held - 1

    override fun setValue(value: Any?) {
        val settled = (value as Int).coerceAtMost(maximum)
        if (settled != held) {
            held = settled
            SwingUtilities.invokeLater { fireStateChanged() }
        }
    }
}
