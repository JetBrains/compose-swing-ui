package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
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
 * Behavioral coverage for the value-bearing components — [Slider], [ProgressBar], [TextArea],
 * [Separator], [Label]. Each test asserts the rendered Swing property (value, range, orientation,
 * text) and, where the component is interactive, the value the user's callback receives.
 */
class ValueComponentsTest {
    @Test
    fun sliderRendersValueAndRange() = runComposeSwingTest {
        setContent {
            Slider(value = 30, modifier = SwingModifier.name("s"), min = 10, max = 90)
        }
        val slider = onNodeWithName("s").fetch<JSlider>()
        assertEquals(10, slider.minimum, "the slider should render its minimum")
        assertEquals(90, slider.maximum, "the slider should render its maximum")
        assertEquals(30, slider.value, "the slider should render its value")
    }

    @Test
    fun draggingSliderFiresOnValueChange() = runComposeSwingTest {
        var value by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        setContent {
            Slider(
                value = value,
                modifier = SwingModifier.name("s"),
                onValueChange = {
                    reported += it
                    value = it
                },
                min = 0,
                max = 100,
            )
        }
        onNodeWithName("s").fetch<JSlider>().value = 42
        awaitIdle()
        assertEquals(42, reported.last(), "dragging should report the new value")
        assertEquals(42, onNodeWithName("s").fetch<JSlider>().value, "the slider should land on the dragged value")
    }

    @Test
    fun sliderReflectsStateDrivenRecomposition() = runComposeSwingTest {
        var value by mutableIntStateOf(10)
        setContent { Slider(value = value, modifier = SwingModifier.name("s")) }
        assertEquals(10, onNodeWithName("s").fetch<JSlider>().value, "the slider should start at the initial value")

        value = 75
        awaitIdle()
        assertEquals(75, onNodeWithName("s").fetch<JSlider>().value, "the slider should reflect the state-driven value")
    }

    @Test
    fun progressBarRendersValueRangeAndDeterminacy() = runComposeSwingTest {
        setContent {
            ProgressBar(
                modifier = SwingModifier.name("pb"),
                value = 25,
                min = 0,
                max = 50,
                indeterminate = false,
            )
        }
        val bar = onNodeWithName("pb").fetch<JProgressBar>()
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
            ProgressBar(
                modifier = SwingModifier.name("pb"),
                value = value,
                indeterminate = indeterminate,
            )
        }
        assertEquals(0, onNodeWithName("pb").fetch<JProgressBar>().value, "the progress bar should start at zero")
        assertFalse(
            onNodeWithName("pb").fetch<JProgressBar>().isIndeterminate,
            "the progress bar should start determinate",
        )

        value = 80
        indeterminate = true
        awaitIdle()
        val bar = onNodeWithName("pb").fetch<JProgressBar>()
        assertEquals(80, bar.value, "the progress bar should reflect the updated value")
        assertTrue(bar.isIndeterminate, "the progress bar should become indeterminate")
    }

    @Test
    fun textAreaRendersValueAndReportsEdits() = runComposeSwingTest {
        var text by mutableStateOf("hello")
        val reported = mutableListOf<String>()
        setContent {
            TextArea(
                value = text,
                modifier = SwingModifier.name("ta"),
                onValueChange = {
                    reported += it
                    text = it
                },
            )
        }
        assertEquals("hello", onNodeWithName("ta").fetch<JTextArea>().text, "the text area should render its value")

        onNodeWithName("ta").performTextReplacement("world")
        assertEquals("world", reported.last(), "onValueChange should report the edited text")
        assertEquals("world", onNodeWithName("ta").fetch<JTextArea>().text, "the text area should show the edited text")
    }

    @Test
    fun separatorRendersRequestedOrientation() = runComposeSwingTest {
        setContent {
            Separator(modifier = SwingModifier.name("sep"), orientation = SwingConstants.VERTICAL)
        }
        assertEquals(SwingConstants.VERTICAL, onNodeWithName("sep").fetch<JSeparator>().orientation)
    }

    @Test
    fun separatorReflectsOrientationOnRecomposition() = runComposeSwingTest {
        var vertical by mutableStateOf(false)
        setContent {
            Separator(
                modifier = SwingModifier.name("sep"),
                orientation = if (vertical) SwingConstants.VERTICAL else SwingConstants.HORIZONTAL,
            )
        }
        assertEquals(
            SwingConstants.HORIZONTAL,
            onNodeWithName("sep").fetch<JSeparator>().orientation,
            "the separator should start horizontal",
        )

        vertical = true
        awaitIdle()
        assertEquals(
            SwingConstants.VERTICAL,
            onNodeWithName("sep").fetch<JSeparator>().orientation,
            "the separator should reflect the vertical orientation",
        )
    }

    @Test
    fun labelRendersTextAndReactsToState() = runComposeSwingTest {
        var caption by mutableStateOf("before")
        setContent { Label(text = caption, modifier = SwingModifier.name("lbl")) }
        assertEquals(
            "before",
            onNodeWithName("lbl").fetch<JLabel>().text,
            "the label should render the initial caption",
        )

        caption = "after"
        awaitIdle()
        assertEquals("after", onNodeWithName("lbl").fetch<JLabel>().text, "the label should react to the state change")
    }
}
