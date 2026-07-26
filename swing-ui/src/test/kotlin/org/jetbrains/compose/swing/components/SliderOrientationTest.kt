package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSlider
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the axis a [Slider] is laid out on: the declared orientation and the
 * direction the value runs in both reach the live `JSlider` and follow a state-driven recomposition,
 * and neither of them disturbs the value the slider carries.
 */
class SliderOrientationTest {
    @Test
    fun aVerticalInvertedSliderRendersItsAxisDirectionAndValue() = runComposeSwingTest {
        setContent {
            Slider(
                value = 30,
                min = 0,
                max = 100,
                orientation = SwingConstants.VERTICAL,
                inverted = true,
            )
        }

        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(SwingConstants.VERTICAL, slider.orientation, "the slider should render its orientation")
        assertTrue(slider.inverted, "the slider should render an inverted value axis")
        assertEquals(30, slider.value, "the axis declaration should leave the value untouched")
    }

    @Test
    fun theOrientationAndTheDirectionFollowStateDrivenRecomposition() = runComposeSwingTest {
        var orientation by mutableIntStateOf(SwingConstants.HORIZONTAL)
        var inverted by mutableStateOf(false)
        setContent { Slider(value = 30, orientation = orientation, inverted = inverted) }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(
            SwingConstants.HORIZONTAL,
            slider.orientation,
            "the slider should start on the declared orientation",
        )
        assertFalse(slider.inverted, "the slider should start with a forward value axis")

        orientation = SwingConstants.VERTICAL
        inverted = true
        awaitIdle()

        assertEquals(SwingConstants.VERTICAL, slider.orientation, "the slider should adopt the new orientation")
        assertTrue(slider.inverted, "the slider should invert its value axis")
        assertEquals(30, slider.value, "turning the axis around should leave the value untouched")
    }
}
