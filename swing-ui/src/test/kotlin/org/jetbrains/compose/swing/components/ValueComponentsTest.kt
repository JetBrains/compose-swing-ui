package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.pressKey
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.KeyEvent
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the value-bearing components - [Slider], [ProgressBar], [TextArea],
 * [Separator], [Label]. Each test asserts the rendered Swing property (value, range, orientation,
 * text) and, where the component is interactive, the value the user's callback receives.
 */
class ValueComponentsTest {
    @Test
    fun sliderRendersValueAndRange() = runComposeSwingTest {
        setContent {
            Slider(value = 30, onValueChange = {}, min = 10, max = 90)
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(10, slider.minimum, "the slider should render its minimum")
        assertEquals(90, slider.maximum, "the slider should render its maximum")
        assertEquals(30, slider.value, "the slider should render its value")
    }

    @Test
    fun movingTheSliderReportsTheNewValue() = runComposeSwingTest {
        var value by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        setContent {
            Slider(
                value = value,
                onValueChange = {
                    reported += it
                    value = it
                },
                min = 0,
                max = 100,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()

        // Moving the knob reaches a JSlider as a write to its value; driving that write takes the same
        // path the user's drag does.
        slider.value = 42
        awaitIdle()

        assertEquals(listOf(42), reported, "the change should be reported once, with the value changed to")
        assertEquals(42, slider.value, "the slider should land on the value it was moved to")
    }

    @Test
    fun sliderReportsAUserChangeAndNotTheValueItWasGiven() = runComposeSwingTest {
        var value by mutableIntStateOf(10)
        val reported = mutableListOf<Int>()
        setContent {
            Slider(
                value = value,
                onValueChange = { reported += it },
                min = 0,
                max = 100,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()

        value = 60
        awaitIdle()
        assertEquals(60, slider.value, "the slider should take the declared value")
        assertEquals(emptyList(), reported, "a value the slider was given should not come back as a change")

        slider.value = 42
        awaitIdle()
        assertEquals(listOf(42), reported, "a value the slider is moved to should be reported")
    }

    @Test
    fun sliderReflectsStateDrivenRecomposition() = runComposeSwingTest {
        var value by mutableIntStateOf(10)
        setContent { Slider(value = value, onValueChange = {}) }
        awaitIdle()
        mainClock.autoAdvance = false

        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(10, slider.value, "the slider should start at the initial value")

        value = 75
        awaitIdle()
        mainClock.advanceTimeByFrame()
        assertEquals(75, slider.value, "the pass declaring the value should leave the slider on it")
    }

    @Test
    fun narrowingTheSliderRangeClampsTheValueAndReportsItOnce() = runComposeSwingTest {
        var max by mutableIntStateOf(100)
        val value = 50
        val reported = mutableListOf<Int>()
        setContent {
            Slider(
                value = value,
                onValueChange = { reported += it },
                min = 0,
                max = max,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(50, slider.value, "the slider should start at its declared value")

        // Narrowing the range below the declared value forces JSlider to clamp it on the spot; that
        // clamp is the wrapper settling its own declaration, not a change the user made.
        max = 30
        awaitIdle()

        assertEquals(30, slider.maximum, "the slider should take the narrowed range")
        assertEquals(30, slider.value, "the slider should clamp to the narrowed range")
        assertEquals(listOf(30), reported, "the clamp should be reported exactly once, as the value settled on")
    }

    @Test
    fun aChangeTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        // The declaration never moves: what the widget is left holding is the whole of what this pins.
        val value by mutableIntStateOf(30)
        setContent {
            Slider(value = value, onValueChange = {}, min = 0, max = 100)
        }
        val slider = onNodeOfType<JSlider>().fetch()

        // Moving the knob directly is the same write path a drag takes; with no onValueChange to
        // adopt it, the change does not stand past the settle awaitIdle drives.
        slider.value = 70
        awaitIdle()

        assertEquals(30, slider.value, "a change the caller does not adopt does not stand")
    }

    @Test
    fun aDragReportsEveryStepAndSettlesOnceOnReleaseWithoutReassertingMidDrag() = runComposeSwingTest {
        val changed = mutableListOf<Int>()
        val settled = mutableListOf<Int>()
        setContent {
            // The declaration is left at 0 throughout and never adopts the drag, so a widget write mid-
            // drag standing unreverted is what shows the fixed value is not reasserted while adjusting.
            Slider(
                value = 0,
                onValueChange = { changed += it },
                onValueSettled = { settled += it },
                min = 0,
                max = 100,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()

        // What a drag does to a JSlider: valueIsAdjusting true for every step it passes through, and
        // false again for the value it is released on.
        slider.setValueIsAdjusting(true)
        slider.value = 10
        slider.value = 20
        slider.value = 30
        awaitIdle()

        assertEquals(listOf(10, 20, 30), changed, "onValueChange should see every value the drag passed through")
        assertEquals(emptyList(), settled, "onValueSettled should not fire while the drag is still adjusting")
        assertEquals(
            30,
            slider.value,
            "the fixed declaration should not be reasserted onto a slider the user is still dragging",
        )

        slider.setValueIsAdjusting(false)
        awaitIdle()

        assertEquals(
            listOf(10, 20, 30),
            changed,
            "releasing should not re-report the steps the drag already published",
        )
        assertEquals(
            listOf(30),
            settled,
            "onValueSettled should fire exactly once, with the value the drag released on",
        )
    }

    @Test
    fun aDragOnAModelDrivenSliderReportsEveryStepAndSettlesOnceOnRelease() = runComposeSwingTest {
        val model = DefaultBoundedRangeModel(0, 0, 0, 100)
        val changed = mutableListOf<Int>()
        val settled = mutableListOf<Int>()
        setContent {
            Slider(model = model, onValueChange = { changed += it }, onValueSettled = { settled += it })
        }
        val slider = onNodeOfType<JSlider>().fetch()

        slider.setValueIsAdjusting(true)
        slider.value = 10
        slider.value = 20
        slider.value = 30
        awaitIdle()

        assertEquals(listOf(10, 20, 30), changed, "onValueChange should see every value the drag passed through")
        assertEquals(emptyList(), settled, "onValueSettled should not fire while the drag is still adjusting")
        assertEquals(30, model.value, "nothing is declared over a caller's model, so the drag's own value stands")

        slider.setValueIsAdjusting(false)
        awaitIdle()

        assertEquals(
            listOf(10, 20, 30),
            changed,
            "releasing should not re-report the steps the drag already published",
        )
        assertEquals(
            listOf(30),
            settled,
            "onValueSettled should fire exactly once, with the value the drag released on",
        )
    }

    @Test
    fun progressBarRendersValueRangeAndDeterminacy() = runComposeSwingTest {
        setContent {
            ProgressBar(
                value = 25,
                min = 0,
                max = 50,
                indeterminate = false,
            )
        }
        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals(0, bar.minimum, "the progress bar should render its minimum")
        assertEquals(50, bar.maximum, "the progress bar should render its maximum")
        assertEquals(25, bar.value, "the progress bar should render its value")
        assertFalse(bar.isIndeterminate, "the progress bar should be determinate")
    }

    @Test
    fun progressBarReflectsValueAndIndeterminateOnRecomposition() = runComposeSwingTest {
        var value by mutableIntStateOf(0)
        var indeterminate by mutableStateOf(false)
        setContent {
            ProgressBar(value = value, indeterminate = indeterminate)
        }
        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals(0, bar.value, "the progress bar should start at zero")
        assertFalse(bar.isIndeterminate, "the progress bar should start determinate")

        value = 80
        indeterminate = true
        awaitIdle()
        assertEquals(80, bar.value, "the progress bar should reflect the updated value")
        assertTrue(bar.isIndeterminate, "the progress bar should become indeterminate")
    }

    @Test
    fun progressBarReflectsARaisedMinimumOnRecomposition() = runComposeSwingTest {
        var min by mutableIntStateOf(0)
        setContent {
            ProgressBar(value = 50, min = min, max = 100)
        }
        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals(0, bar.minimum, "the progress bar should start at its declared minimum")

        min = 30
        awaitIdle()
        assertEquals(30, bar.minimum, "the progress bar should reflect the raised minimum")
        assertEquals(50, bar.value, "a value the raised minimum still admits should stand")
    }

    @Test
    fun progressBarWidenedWithItsValueKeepsTheValue() = runComposeSwingTest {
        var max by mutableIntStateOf(100)
        var value by mutableIntStateOf(50)
        setContent {
            ProgressBar(value = value, min = 0, max = max)
        }
        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals(50, bar.value, "the progress bar should start at its declared value")

        // A bar's range bounds its value, so growing the range and the value together only lands the
        // declared value if the new bound reaches the bar first.
        max = 200
        value = 150
        awaitIdle()

        assertEquals(200, bar.maximum, "the progress bar should take the widened range")
        assertEquals(150, bar.value, "the progress bar should take a value the widened range admits")
    }

    @Test
    fun textAreaRendersValueAndReportsEdits() = runComposeSwingTest {
        var text by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent {
            TextArea(
                value = text,
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }
        onNodeOfType<JTextArea>().assertTextEquals("hello")

        onNodeOfType<JTextArea>().performTextReplacement("world")

        assertEquals("world", reported.last(), "onValueChange should report the edited text")
        onNodeOfType<JTextArea>().assertTextEquals("world")
    }

    @Test
    fun separatorRendersRequestedOrientation() = runComposeSwingTest {
        setContent {
            Separator(orientation = SwingConstants.VERTICAL)
        }
        assertEquals(
            SwingConstants.VERTICAL,
            onNodeOfType<JSeparator>().fetch().orientation,
            "the separator should render the declared orientation",
        )
    }

    @Test
    fun separatorReflectsOrientationOnRecomposition() = runComposeSwingTest {
        var vertical by mutableStateOf(false)
        setContent {
            Separator(orientation = if (vertical) SwingConstants.VERTICAL else SwingConstants.HORIZONTAL)
        }
        val separator = onNodeOfType<JSeparator>().fetch()
        assertEquals(SwingConstants.HORIZONTAL, separator.orientation, "the separator should start horizontal")

        vertical = true
        awaitIdle()
        assertEquals(
            SwingConstants.VERTICAL,
            separator.orientation,
            "the separator should reflect the vertical orientation",
        )
    }

    @Test
    fun labelRendersTextAndReactsToState() = runComposeSwingTest {
        var caption by mutableStateOf("before")
        setContent { Label(text = caption) }
        onNodeOfType<JLabel>().assertTextEquals("before")

        caption = "after"
        awaitIdle()
        onNodeOfType<JLabel>().assertTextEquals("after")
    }

    @Test
    fun aSliderDragTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        // A drag is the one continuous gesture the runtime queues a frame for. The knob follows the
        // pointer through motion events, so a step the caller refuses is painted like any other move
        // unless the frame is queued ahead of the repaint that step asks for.
        assertUnadoptedChangeIsNeverPainted(
            type = JSlider::class.java,
            declared = 10,
            content = { report -> Slider(value = 10, onValueChange = { report() }) },
            // The arrow key a slider binds to its own increment action, which is how a user moves a
            // knob without the pointer.
            change = { slider -> slider.pressKey(KeyEvent.VK_RIGHT) },
            read = { it.value },
        )
    }

    @Test
    fun aSpinnerStepTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JSpinner::class.java,
            declared = 1,
            content = { report -> Spinner(value = 1, onValueChange = { report() }) },
            change = { it.value = 5 },
            read = { it.value },
        )
    }
}
