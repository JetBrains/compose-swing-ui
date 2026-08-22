package org.jetbrains.compose.swing.swingmark.raw

import org.jetbrains.compose.swing.swingmark.harness.rest
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider

/** `SliderTest`: one ticked, labeled slider, advanced a value at a time to its maximum. */
internal class SliderTest : RawTest() {
    override val testName: String = "Sliders"

    private lateinit var slider1: JSlider

    override fun testComponent(): JComponent {
        val panel = JPanel()
        slider1 = CountSlider(JSlider.HORIZONTAL, 0, VALUES, 0)
        slider1.majorTickSpacing = VALUES / MAJOR_DIVISIONS
        slider1.minorTickSpacing = VALUES / MINOR_DIVISIONS
        slider1.paintTicks = true
        slider1.paintLabels = true
        panel.add(slider1)
        return panel
    }

    override fun runTest() {
        testSlider(slider1, 1)
    }

    private fun testSlider(
        currentSlider: JSlider,
        incrementBy: Int,
    ) {
        val inc = SliderInc(currentSlider, incrementBy)
        var i = currentSlider.value
        while (i < currentSlider.maximum) {
            post(inc)
            rest()
            i++
        }
    }

    private inner class CountSlider(
        orientation: Int,
        min: Int,
        max: Int,
        current: Int,
    ) : JSlider(orientation, min, max, current) {
        override fun paint(g: Graphics) {
            super.paint(g)
            paintCount++
        }
    }

    private companion object {
        const val VALUES = 500
        const val MAJOR_DIVISIONS = 5
        const val MINOR_DIVISIONS = 10
    }
}

/** Advances a slider by a fixed amount, which is what each of the test's passes posts. */
private class SliderInc(
    private val slider: JSlider,
    private val incAmount: Int,
) : Runnable {
    override fun run() {
        val currentVal = slider.value
        slider.value = currentVal + incAmount
    }
}
