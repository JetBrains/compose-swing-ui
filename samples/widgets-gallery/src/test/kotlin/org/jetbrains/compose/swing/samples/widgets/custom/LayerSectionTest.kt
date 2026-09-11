package org.jetbrains.compose.swing.samples.widgets.custom

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLayer
import javax.swing.JList
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertIs

class LayerSectionTest {
    @Test
    fun theSectionMountsWithTheListAsTheLayersView() =
        runComposeSwingTest {
            openSection("Layer")

            onNodeWithText("Tint: 0", substring = true).assertExists()
            onNodeWithText("Presses over the layer: 0", substring = true).assertExists()

            // The list fills the layer's view region, so the layer wraps the list itself rather
            // than a panel wrapping it.
            val layer = onNodeOfType<JLayer<*>>().fetch()
            assertIs<JList<*>>(layer.view, "the layer wraps the list itself")
        }

    @Test
    fun theTintSliderDrivesItsReadout() =
        runComposeSwingTest {
            openSection("Layer")

            val slider = onAllNodesOfType<JSlider>().fetchAll().single()
            slider.value = 140
            awaitIdle()

            onNodeWithText("Tint: 140", substring = true).assertExists()
        }
}
