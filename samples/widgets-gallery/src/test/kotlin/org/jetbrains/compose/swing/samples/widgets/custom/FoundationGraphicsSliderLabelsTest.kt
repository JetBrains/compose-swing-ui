package org.jetbrains.compose.swing.samples.widgets.custom

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertTrue

class FoundationGraphicsSliderLabelsTest {
    @Test
    fun canvasSlidersShowTheirValues() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithTag(CANVAS_PETALS_TAG).fetch<JSlider>().value = 12
            onNodeWithTag(CANVAS_SWEEP_TAG).fetch<JSlider>().value = 35
            onNodeWithTag(CANVAS_ROTATION_TAG).fetch<JSlider>().value = 120
            awaitIdle()
            onNodeWithText("Petals: 12", substring = true).assertExists()
            onNodeWithText("Sweep: 35%", substring = true).assertExists()
            onNodeWithText("Rotation: 120°", substring = true).assertExists()
        }

    @Test
    fun drawSweepSliderShowsItsValue() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithTag(DRAW_SWEEP_TAG).fetch<JSlider>().value = 80
            awaitIdle()
            onNodeWithText("Sweep: 80%").assertExists()
        }

    @Test
    fun decorationSlidersShowTheirValues() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithText("Border: 4 px").assertExists()
            onNodeWithText("Blur: 4 px").assertExists()

            onNodeWithTag(DECORATION_OPACITY_TAG).fetch<JSlider>().value = 60
            awaitIdle()
            onNodeWithText("Opacity: 60%", substring = true).assertExists()

            onNodeWithTag(DECORATION_SHADOW_RADIUS_TAG).fetch<JSlider>().value = 18
            onNodeWithTag(DECORATION_SHADOW_X_TAG).fetch<JSlider>().value = -8
            onNodeWithTag(DECORATION_SHADOW_Y_TAG).fetch<JSlider>().value = 12
            awaitIdle()
            onNodeWithText("Shadow: 18 px").assertExists()
            onNodeWithText("Offset X: -8", substring = true).assertExists()
            onNodeWithText("Offset Y: 12", substring = true).assertExists()
        }

    @Test
    fun placementOpacitySliderShowsItsValue() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithTag(PLACEMENT_OPACITY_TAG).fetch<JSlider>().value = 55
            awaitIdle()
            onNodeWithText("Opacity: 55%", substring = true).assertExists()
        }

    @Test
    fun sliderLabelsShowTheirExtremeValuesInFull() =
        runComposeSwingTest(rootSize = Dimension(640, 680)) {
            root.layout = BorderLayout()
            openSection("Foundation graphics")
            onNodeWithTag(DECORATION_SHAPE_TAG).fetch<JComboBox<*>>().selectedItem = "Star"
            awaitIdle()
            for (slider in onAllNodes(SwingMatcher.isOfType<JSlider>()).fetchAll<JSlider>()) {
                val label =
                    slider.parent.components
                        .filterIsInstance<JLabel>()
                        .single()
                for (value in listOf(slider.minimum, slider.maximum)) {
                    slider.value = value
                    awaitIdle()
                    assertTrue(
                        label.ui.getPreferredSize(label).width <= label.width,
                        "\"${label.text}\" needs ${label.ui.getPreferredSize(label).width} px, has ${label.width}",
                    )
                }
            }
        }
}
