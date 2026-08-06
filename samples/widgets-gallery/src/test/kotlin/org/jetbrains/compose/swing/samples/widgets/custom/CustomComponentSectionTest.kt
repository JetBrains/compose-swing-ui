package org.jetbrains.compose.swing.samples.widgets.custom

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CustomComponentSectionTest {
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
