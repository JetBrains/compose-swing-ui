package org.jetbrains.compose.swing.swingmark.declared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.swingmark.harness.change
import javax.swing.JSlider

/** `SliderTest`: one ticked, labeled slider, advanced a value at a time to its maximum. */
internal class SliderTest : DeclaredTest() {
    override val testName: String = "Sliders"

    private var value by mutableIntStateOf(0)

    @Composable
    override fun Content() {
        FlowPanel {
            Slider(
                value = value,
                onValueChange = {},
                min = 0,
                max = VALUES,
                majorTickSpacing = VALUES / MAJOR_DIVISIONS,
                minorTickSpacing = VALUES / MINOR_DIVISIONS,
                paintTicks = true,
                paintLabels = true,
            )
        }
    }

    override fun runTest() {
        val slider = widget(JSlider::class.java)
        repeat(VALUES) { step ->
            val next = step + 1
            change(apply = { value = next }, reached = { slider.value == next })
        }
    }

    private companion object {
        const val VALUES = 500
        const val MAJOR_DIVISIONS = 5
        const val MINOR_DIVISIONS = 10
    }
}
