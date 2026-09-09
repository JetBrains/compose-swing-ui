package org.jetbrains.compose.swing.samples.widgets.animation

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JCheckBox
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedVisibilityCardsTest {
    @Test
    fun anExpandingContainerTakesTheColumnsRoomAFrameAtATime() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNodeWithText("Expand / shrink").performClick()
            val trailingLine = onNodeWithText("This line sits below the animated container.").fetch<Component>()
            val hidden = trailingLine.y

            mainClock.autoAdvance = false
            onNodeWithText("Show order details").performClick()
            driveFramesIntoTheTransition()
            val midway = trailingLine.y

            mainClock.autoAdvance = true
            awaitIdle()
            val shown = trailingLine.y

            assertTrue(hidden < midway, "the container took no room on its first frames: $hidden then $midway")
            assertTrue(midway < shown, "the container was already at its full height midway: $midway of $shown")
        }

    @Test
    fun theRevealedOrderDetailsHaveWorkingControlsAndLeaveTheSwingTreeWhenClosed() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNodeWithText("Show order details").performClick()
            onNodeWithText("Mark as shipped").performClick()
            onNodeWithText("Undo shipment").assertExists()
            val emailUpdates = onNodeWithText("Email me when it ships").fetch<JCheckBox>()
            assertTrue(emailUpdates.isSelected, "email updates should be enabled initially")
            onNodeWithText("Email me when it ships").performClick()
            assertFalse(emailUpdates.isSelected, "the email updates control did not change state")

            onNodeWithText("Hide order details").performClick()
            awaitIdle()
            onNodeWithText("Undo shipment").assertDoesNotExist()
            onNodeWithText("Show order details").performClick()
            awaitIdle()
            assertFalse(
                onNodeWithText("Email me when it ships").fetch<JCheckBox>().isSelected,
                "closing the order reset its email preference",
            )
        }

    @Test
    fun perChildTransitionsMoveInOppositeDirectionsAndStayInteractiveThroughExit() =
        runComposeSwingTest {
            openSection("Animated containers")

            mainClock.autoAdvance = false
            onNodeWithText("Show order actions").performClick()
            driveFramesIntoTheTransition()
            val root = onRoot().fetch()
            val review = onNodeWithText("Review order").fetch<Component>()
            val tracking = onNodeWithText("View tracking").fetch<Component>()
            val reviewEnteringX = review.xInRoot(root)
            val trackingEnteringX = tracking.xInRoot(root)

            mainClock.autoAdvance = true
            awaitIdle()
            val reviewSettledX = review.xInRoot(root)
            val trackingSettledX = tracking.xInRoot(root)
            assertTrue(reviewEnteringX < reviewSettledX, "the review action did not enter from the left")
            assertTrue(trackingEnteringX > trackingSettledX, "the tracking action did not enter from the right")

            onNodeWithText("Review order").performClick()
            onNodeWithText("Last action: Review order").assertExists()
            onNodeWithText("View tracking").performClick()
            onNodeWithText("Last action: View tracking").assertExists()

            mainClock.autoAdvance = false
            onNodeWithText("Hide order actions").performClick()
            mainClock.advanceTimeByFrame()
            onNodeWithText("Review order").assertExists()
            onNodeWithText("View tracking").performClick()
            onNodeWithText("Last action: View tracking").assertExists()

            mainClock.autoAdvance = true
            awaitIdle()
            onNodeWithText("Review order").assertDoesNotExist()
            onNodeWithText("View tracking").assertDoesNotExist()
        }

    @Test
    fun theVeilDarkensTheContentAndLeavesTheRestOfTheBoxAlone() =
        runComposeSwingTest {
            openSection("Animated containers")

            mainClock.autoAdvance = false
            onNodeWithText("Reveal both panels").performClick()
            driveFramesIntoTheTransition()
            val (content, margin) = veiledRectangles()
            val running = captureToImage()

            mainClock.autoAdvance = true
            awaitIdle()
            val settled = captureToImage()

            assertTrue(
                running.brightnessOver(content) < settled.brightnessOver(content),
                "the content was painted unveiled: ${running.brightnessOver(content)} of " +
                    "${settled.brightnessOver(content)}",
            )
            assertEquals(
                settled.brightnessOver(margin),
                running.brightnessOver(margin),
                "the scrim reached past the content without \"Scrim covers the whole box\"",
            )
        }

    @Test
    fun tickingTheCheckboxPutsTheScrimOverTheWholeBox() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNodeWithText("Scrim covers the whole box").performClick()
            mainClock.autoAdvance = false
            onNodeWithText("Reveal both panels").performClick()
            driveFramesIntoTheTransition()
            val (_, margin) = veiledRectangles()
            val running = captureToImage()

            mainClock.autoAdvance = true
            awaitIdle()
            val settled = captureToImage()

            assertTrue(
                running.brightnessOver(margin) < settled.brightnessOver(margin),
                "the scrim covered only the content: ${running.brightnessOver(margin)} of " +
                    "${settled.brightnessOver(margin)}",
            )
        }

    @Test
    fun theVeiledPanelsStayWithinTheirScrollViewport() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNodeWithText("Reveal both panels").performClick()
            val faded = onNodeWithText("Faded: the text itself goes translucent.").fetch<Component>()
            val veiled = onNodeWithText("Veiled: a scrim over sharp text.").fetch<Component>()
            val box = faded.childOf(containerHolding(faded, veiled))
            val viewport = generateSequence(box.parent) { it.parent }.filterIsInstance<JViewport>().first()
            val rightEdge = SwingUtilities.convertPoint(box.parent, box.x, box.y, viewport).x + box.width
            assertTrue(
                rightEdge <= viewport.width,
                "the veiled panels (right edge $rightEdge) must fit within the viewport (${viewport.width})",
            )
        }
}

/**
 * The veiled panel's content and an empty corner of the box holding it, both in the coordinates of the
 * captured root. The scrim covers the first always and the second only with `matchParentSize`.
 */
private fun ComposeSwingTest.veiledRectangles(): Pair<Rectangle, Rectangle> {
    val veiled = onNodeWithText("Veiled: a scrim over sharp text.").fetch<Component>()
    val faded = onNodeWithText("Faded: the text itself goes translucent.").fetch<Component>()
    val box = veiled.childOf(containerHolding(veiled, faded))
    val panel = veiled.childOf(box)
    val root = onRoot().fetch()
    val content = SwingUtilities.convertRectangle(box, panel.bounds, root)
    val margin = SwingUtilities.convertRectangle(box, Rectangle(box.width - 8, box.height - 8, 4, 4), root)
    assertFalse(content.intersects(margin), "the content fills its box, leaving no corner only a scrim can reach")
    return content to margin
}

/** Mean channel value over [area], which a scrim lowers however the content's text falls inside it. */
private fun BufferedImage.brightnessOver(area: Rectangle): Int {
    var total = 0L
    for (y in area.y until area.y + area.height) {
        for (x in area.x until area.x + area.width) {
            val pixel = Color(getRGB(x, y))
            total += pixel.red + pixel.green + pixel.blue
        }
    }
    return (total / (area.width.toLong() * area.height * 3)).toInt()
}
