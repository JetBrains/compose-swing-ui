package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.BoundedRangeModel
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JProgressBar
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral coverage for a model-driven [ProgressBar]. The model owns the value and the range, so the
 * bar declares nothing over it: it renders the caller's own model instance, a mutation the caller makes
 * to that model shows on the bar with no recomposition in between, and a new instance is installed on
 * the recomposition that declares it. One model handed to a [Slider] and to the bar drives both.
 */
class ProgressBarModelTest {
    private fun model(value: Int): BoundedRangeModel = DefaultBoundedRangeModel(value, 0, 0, MAXIMUM)

    @Test
    fun theBarRendersTheModelsValueAndTheModelsRange() = runComposeSwingTest {
        val range = model(50)
        setContent { ProgressBar(model = range) }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertSame(range, bar.model, "the bar should render the caller's own model, not a copy of it")
        assertEquals(50, bar.value, "the bar should start on the value its model holds")
        assertEquals(0, bar.minimum, "the model owns the range, so the bar should take its minimum")
        assertEquals(
            MAXIMUM,
            bar.maximum,
            "the model owns the range, so the bar should take its maximum rather than a declared one",
        )
    }

    @Test
    fun aMutationTheCallerMakesToTheModelShowsOnTheBarWithoutARecomposition() = runComposeSwingTest {
        val range = model(50)
        setContent { ProgressBar(model = range) }

        val bar = onNodeOfType<JProgressBar>().fetch()

        // The model is not composition state, so writing to it starts no recomposition; the reading
        // below is taken without one, which is what makes it a reading of the bar following the model.
        range.value = 70

        assertEquals(70, bar.value, "a model the caller mutates should reach the bar with no recomposition")
    }

    @Test
    fun declaringANewModelInstanceInstallsItOnRecomposition() = runComposeSwingTest {
        var range by mutableStateOf(model(30))
        setContent { ProgressBar(model = range) }

        val bar = onNodeOfType<JProgressBar>().fetch()
        val first = range
        assertSame(first, bar.model, "the bar should start on the first model declared")

        val second = model(70)
        range = second
        awaitIdle()

        assertSame(second, bar.model, "a new model instance should be installed on the recomposition declaring it")
        assertEquals(70, bar.value, "the bar should render the installed model's value")
    }

    @Test
    fun oneRangeDrivesASliderAndTheBarAlike() = runComposeSwingTest {
        val range = model(30)
        setContent {
            Slider(model = range)
            ProgressBar(model = range)
        }

        val slider = onNodeOfType<JSlider>().fetch()
        val bar = onNodeOfType<JProgressBar>().fetch()
        assertSame(range, slider.model, "both widgets should render the one model they are handed")
        assertSame(range, bar.model, "both widgets should render the one model they are handed")
        assertEquals(30, bar.value, "the bar should read out the range the slider starts on")

        // Moving the knob directly is the same write path a drag takes, and it lands on the shared model.
        slider.value = 55
        awaitIdle()

        assertEquals(55, bar.value, "one range driving both widgets should have the bar read out the slider's move")
    }

    private companion object {
        /** The top of every fixture range, apart from the 100 the value-driven overload would default to. */
        const val MAXIMUM = 200
    }
}
