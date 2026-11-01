package org.jetbrains.compose.swing.samples.widgets.custom

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.interaction.performSetValue
import org.jetbrains.compose.swing.test.onAllNodesOfType
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

            onNodeWithTag(CANVAS_PETALS_TAG).performSetValue(12)
            onNodeWithTag(CANVAS_SWEEP_TAG).performSetValue(35)
            onNodeWithTag(CANVAS_ROTATION_TAG).performSetValue(120)
            onNodeWithText("Petals: 12", substring = true).assertExists()
            onNodeWithText("Sweep: 35%", substring = true).assertExists()
            onNodeWithText("Rotation: 120°", substring = true).assertExists()
        }

    @Test
    fun drawSweepSliderShowsItsValue() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithTag(DRAW_SWEEP_TAG).performSetValue(80)
            onNodeWithText("Sweep: 80%").assertExists()
        }

    @Test
    fun decorationSlidersShowTheirValues() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithText("Border: 4 px").assertExists()
            onNodeWithText("Blur: 4 px").assertExists()

            onNodeWithTag(DECORATION_OPACITY_TAG).performSetValue(60)
            onNodeWithText("Opacity: 60%", substring = true).assertExists()

            onNodeWithTag(DECORATION_SHADOW_RADIUS_TAG).performSetValue(18)
            onNodeWithTag(DECORATION_SHADOW_X_TAG).performSetValue(-8)
            onNodeWithTag(DECORATION_SHADOW_Y_TAG).performSetValue(12)
            onNodeWithText("Shadow: 18 px").assertExists()
            onNodeWithText("Offset X: -8", substring = true).assertExists()
            onNodeWithText("Offset Y: 12", substring = true).assertExists()
        }

    @Test
    fun placementOpacitySliderShowsItsValue() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithTag(PLACEMENT_OPACITY_TAG).performSetValue(55)
            onNodeWithText("Opacity: 55%", substring = true).assertExists()
        }

    @Test
    fun sliderLabelsShowTheirExtremeValuesInFull() =
        runComposeSwingTest(rootSize = Dimension(640, 680)) {
            root.layout = BorderLayout()
            openSection("Foundation graphics")
            val shape = onNodeWithTag(DECORATION_SHAPE_TAG)
            val combo = shape.fetch<JComboBox<*>>()
            shape.performAddSelection((0 until combo.itemCount).first { combo.getItemAt(it) == "Star" })
            for ((index, slider) in onAllNodesOfType<JSlider>().fetchAll().withIndex()) {
                val label =
                    slider.parent.components
                        .filterIsInstance<JLabel>()
                        .single()
                for (value in listOf(slider.minimum, slider.maximum)) {
                    onAllNodesOfType<JSlider>()[index].performSetValue(value)
                    assertTrue(
                        label.ui.getPreferredSize(label).width <= label.width,
                        "\"${label.text}\" needs ${label.ui.getPreferredSize(label).width} px, has ${label.width}",
                    )
                }
            }
        }
}
