package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.BoundedRangeModel
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JSlider
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for a model-driven [Slider] declaring a new [BoundedRangeModel] instance: the
 * slider adopts the model in place and renders its value, announcing the swap as a property change
 * rather than as a change event, so the swap reaches neither `onValueChange`/`onValueSettled` nor a raw
 * `changeListener` as a move. A move the user makes on the model the swap left current reaches them all,
 * the slider having carried its change events over to it.
 */
class SliderModelSwapTest {
    private fun model(value: Int): BoundedRangeModel = DefaultBoundedRangeModel(value, 0, 0, 100)

    @Test
    fun swappingTheModelRendersItsValueWithoutReportingIt() = runComposeSwingTest {
        var model by mutableStateOf(model(30))
        val changed = mutableListOf<Int>()
        val settled = mutableListOf<Int>()
        setContent {
            Slider(
                model = model,
                onValueChange = { changed += it },
                onValueSettled = { settled += it },
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(30, slider.value, "the slider should start on the first model's value")

        model = model(70)
        awaitIdle()

        assertEquals(70, slider.value, "the slider should render the swapped model's value")
        assertEquals(emptyList(), changed, "declaring a new model should not reach onValueChange")
        assertEquals(emptyList(), settled, "declaring a new model should not reach onValueSettled")
    }

    @Test
    fun aMoveOnTheSwappedModelIsStillReported() = runComposeSwingTest {
        var model by mutableStateOf(model(30))
        val changed = mutableListOf<Int>()
        val settled = mutableListOf<Int>()
        setContent {
            Slider(
                model = model,
                onValueChange = { changed += it },
                onValueSettled = { settled += it },
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()

        model = model(70)
        awaitIdle()

        // Moving the knob directly is the same write path a drag takes; the channel should still be
        // listening for it on the model the swap just left current.
        slider.value = 55
        awaitIdle()

        assertEquals(listOf(55), changed, "a move on the model current after the swap should reach onValueChange")
        assertEquals(listOf(55), settled, "a move on the model current after the swap should reach onValueSettled")
    }

    @Test
    fun aRawChangeListenerHearsAMoveOnTheSwappedModel() = runComposeSwingTest {
        var model by mutableStateOf(model(30))
        val heard = mutableListOf<Int>()
        val listener = ChangeListener { event -> heard += (event.source as JSlider).value }
        setContent {
            Slider(model = model, changeListener = listener)
        }
        val slider = onNodeOfType<JSlider>().fetch()

        model = model(70)
        awaitIdle()

        assertEquals(emptyList(), heard, "declaring a new model should not reach a raw change listener")

        slider.value = 55
        awaitIdle()

        assertEquals(
            listOf(55),
            heard,
            "a listener attached to the component directly should hear a move on the model current after the swap",
        )
    }
}
