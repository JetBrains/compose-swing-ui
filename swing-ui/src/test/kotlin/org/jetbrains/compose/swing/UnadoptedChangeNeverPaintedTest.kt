package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.components.Slider
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.test.Test

/** Behavioral tests for what [assertUnadoptedChangeIsNeverPainted] counts. */
class UnadoptedChangeNeverPaintedTest {
    @Test
    fun aRepaintAComponentOutsideTheCompositionAsksForIsNotCounted() = runSwingTest {
        val outside = JPanel().apply { setSize(10, 10) }
        assertUnadoptedChangeIsNeverPainted(
            type = JSlider::class.java,
            declared = 10,
            content = { report -> Slider(value = 10, onValueChange = { report() }) },
            // The request lands just ahead of the step, as a timer tick of an animation elsewhere would,
            // so serving it would read the slider before the declared value is put back.
            change = { slider ->
                outside.repaint()
                slider.pressKey(KeyEvent.VK_RIGHT)
            },
            read = { it.value },
        )
    }
}
