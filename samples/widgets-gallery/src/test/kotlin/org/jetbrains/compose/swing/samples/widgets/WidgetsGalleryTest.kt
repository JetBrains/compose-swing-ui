package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.JSlider
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

            val before = onNodeWithText("Enable feature").fetch<JCheckBox>().bounds
            assertTrue(before != Rectangle(), "the checkbox must have a real, laid-out bounds")
            assertEquals(0, before.x, "the checkbox must sit flush with the column's left edge")

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
    fun switchingToTheAnimationSectionMountsItsProgressBar() =
        runComposeSwingTest {
            openSection("Animation")

            val bar = onNodeOfType<JProgressBar>().fetch<JProgressBar>()
            assertEquals(0, bar.value)
            onNodeWithText("100%").performClick()
            waitUntil { bar.value > 0 }
            assertTrue(bar.value > 0, "the animated progress bar should advance toward the target")
        }

    @Test
    fun theAnimatedProgressBarStaysWithinItsScrollViewport() =
        runComposeSwingTest {
            openSection("Animation")

            val bar = onNodeOfType<JProgressBar>().fetch<JProgressBar>()
            val viewport = generateSequence(bar.parent) { it.parent }.filterIsInstance<JViewport>().first()
            val rightEdge = SwingUtilities.convertPoint(bar.parent, bar.x, bar.y, viewport).x + bar.width
            assertTrue(
                rightEdge <= viewport.width,
                "the progress bar (right edge $rightEdge) must fit within the viewport (${viewport.width})",
            )
        }

    @Test
    fun switchingToTheFormInputsSectionDrivesASpinnerEcho() =
        runComposeSwingTest {
            openSection("Form inputs")

            onNodeWithText("Count is 3", substring = true).assertExists()

            onNodeWithText("Bold is off", substring = true).assertExists()
            onNodeWithText("Bold").performClick()
            onNodeWithText("Bold is on", substring = true).assertExists()
        }

    @Test
    fun theCustomComponentSectionWrapsAJComponentThroughSwingNode() =
        runComposeSwingTest {
            openSection("Custom component")

            onNodeWithText("3 / 5", substring = true).assertExists()
            clickStar(onStarRating().fetch<JComponent>(), starIndex = 4)
            awaitIdle()
            onNodeWithText("5 / 5", substring = true).assertExists()
        }

    @Test
    fun theCustomWidgetRendersToAStableBitmapScreenshotTest() =
        runComposeSwingTest {
            openSection("Custom component")

            val stars = onStarRating().captureToImage()

            onStarRating().assertImageMatches(expected = stars)

            assertFailsWith<AssertionError> {
                onNode(SwingMatcher.hasText("Clear") and SwingMatcher.isOfType<JButton>())
                    .assertImageMatches(expected = stars)
            }
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

    private fun clickStar(
        component: JComponent,
        starIndex: Int,
    ) {
        val box = component.height
        val center = Point(starIndex * box + box / 2, box / 2)
        val event =
            MouseEvent(
                component,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                center.x,
                center.y,
                1,
                false,
            )
        component.dispatchEvent(event)
    }
}

private fun ComposeSwingTest.onStarRating() = onNodeWithTag(STAR_RATING_TAG)
