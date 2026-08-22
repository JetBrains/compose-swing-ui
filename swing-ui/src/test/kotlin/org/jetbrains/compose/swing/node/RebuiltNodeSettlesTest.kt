package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * That a two-way declaration reaches the widget of a node built again after its content parked, for both
 * routes content parks by: a deactivated [ReusableContentHost], and a `movableContentOf` no host emitted
 * for a pass.
 *
 * Content that parks is released for good and the content that comes back is composed afresh, widget
 * included, so the fresh widget opens on whatever its own constructor gives it. What puts the standing
 * declaration back on it is the settle the first pass over a fresh node makes - a declaration unchanged
 * across the park is still a declaration that widget has never been given.
 */
class RebuiltNodeSettlesTest {
    @Test
    fun reactivatedContentSettlesTheSliderItIsBuiltWith() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                Slider(value = DECLARED, onValueChange = {}, min = MIN, max = MAX)
            }
        }
        val parked = onNodeOfType<JSlider>().fetch()
        assertEquals(DECLARED, parked.value, "the slider should open on what the composition declares")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val rebuilt = onNodeOfType<JSlider>().fetch()
        assertNotSame(parked, rebuilt, "reactivated content should be driving a slider of its own")
        assertEquals(DECLARED, rebuilt.value, "the standing declaration should be settled onto it")
    }

    @Test
    fun contentEmittedAgainAfterParkingSettlesTheSliderItIsBuiltWith() = runComposeSwingTest {
        var parked by mutableStateOf(false)
        setContent {
            val content =
                remember { movableContentOf { Slider(value = DECLARED, onValueChange = {}, min = MIN, max = MAX) } }
            FlowPanel {
                if (!parked) content()
            }
        }
        val before = onNodeOfType<JSlider>().fetch()
        assertEquals(DECLARED, before.value, "the slider should open on what the composition declares")

        parked = true
        awaitIdle()
        parked = false
        awaitIdle()

        val rebuilt = onNodeOfType<JSlider>().fetch()
        assertNotSame(before, rebuilt, "content emitted again should be driving a slider of its own")
        assertEquals(DECLARED, rebuilt.value, "the standing declaration should be settled onto it")
    }

    private companion object {
        const val MIN = 0
        const val MAX = 100

        /** The declaration, held unchanged across the park so only a fresh settle can put it back. */
        const val DECLARED = 40
    }
}
