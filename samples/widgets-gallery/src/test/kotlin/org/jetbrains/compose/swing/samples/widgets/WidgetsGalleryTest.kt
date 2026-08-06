package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetsGalleryTest {
    @Test
    fun theDefaultSectionRendersTheButtonCounter() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            onNodeWithText("Counter: 0", substring = true).assertExists()
            onNodeWithText("Increment").performClick()
            onNodeWithText("Counter: 1", substring = true).assertExists()
            onNodeWithText("Reset").performClick()
            onNodeWithText("Counter: 0", substring = true).assertExists()
        }

    @Test
    fun theCheckBoxTogglesItsEcho() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            onNodeWithText("Feature is off", substring = true).assertExists()
            onNodeWithText("Enable feature").performClick()
            onNodeWithText("Feature is on", substring = true).assertExists()
        }

    @Test
    fun togglingTheCheckBoxDoesNotMoveIt() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            val checkBox = onNodeWithText("Enable feature").fetch<JCheckBox>()
            val before = checkBox.bounds
            assertTrue(before != Rectangle(), "the checkbox must have a real, laid-out bounds")
            assertEquals(
                checkBox.parent.insets.left,
                before.x,
                "the checkbox must sit flush with the card column's leading edge",
            )

            onNodeWithText("Enable feature").performClick()
            awaitIdle()
            val after = onNodeWithText("Enable feature").fetch<JCheckBox>().bounds
            assertEquals(before, after, "the checkbox must not move when toggled")
        }

    @Test
    fun theComboBoxSelectionFeedsTheEcho() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            onNodeWithText("Selected: Kotlin", substring = true).assertExists()

            val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<*>>()
            combo.selectedIndex = 2
            awaitIdle()
            onNodeWithText("Selected: Scala", substring = true).assertExists()
        }

    @Test
    fun theComponentsSliderDrivesItsProgressBar() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            onNodeWithText("Amount: 40", substring = true).assertExists()

            val slider = onNodeOfType<JSlider>().fetch<JSlider>()
            slider.value = 75
            awaitIdle()
            onNodeWithText("Amount: 75", substring = true).assertExists()
            assertEquals(75, slider.value)
        }
}
