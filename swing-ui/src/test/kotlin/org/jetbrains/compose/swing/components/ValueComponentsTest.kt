package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.JSeparator
import javax.swing.JSlider
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
            Slider(value = 30, min = 10, max = 90)
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

        assertEquals(listOf(42), reported, "the move should be reported once, with the value moved to")
        assertEquals(42, slider.value, "the slider should land on the value it was moved to")
    }

    @Test
    fun sliderReportsAUserMoveAndNotTheValueItWasGiven() = runComposeSwingTest {
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
        setContent { Slider(value = value) }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(10, slider.value, "the slider should start at the initial value")

        value = 75
        awaitIdle()
        assertEquals(75, slider.value, "the slider should reflect the state-driven value")
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
        // clamp is the wrapper settling its own declaration, not a move the user made.
        max = 30
        awaitIdle()

        assertEquals(30, slider.maximum, "the slider should take the narrowed range")
        assertEquals(30, slider.value, "the slider should clamp to the narrowed range")
        assertEquals(listOf(30), reported, "the clamp should be reported exactly once, as the value settled on")
    }

    @Test
    fun aMoveTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        // The declaration never moves: what the widget is left holding is the whole of what this pins.
        val value by mutableIntStateOf(30)
        setContent {
            Slider(value = value, min = 0, max = 100)
        }
        val slider = onNodeOfType<JSlider>().fetch()

        // Moving the knob directly is the same write path a drag takes; with no onValueChange to
        // adopt it, the move does not stand past the settle awaitIdle drives.
        slider.value = 70
        awaitIdle()

        assertEquals(30, slider.value, "a move the caller does not adopt does not stand")
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
    fun progressBarWidenedWithItsValueKeepsTheValue() = runComposeSwingTest {
        var max by mutableIntStateOf(100)
        var value by mutableIntStateOf(50)
        setContent {
            ProgressBar(min = 0, max = max, value = value)
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
}
