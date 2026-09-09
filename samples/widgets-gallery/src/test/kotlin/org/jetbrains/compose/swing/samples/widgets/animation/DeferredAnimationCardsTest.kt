package org.jetbrains.compose.swing.samples.widgets.animation

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the deferred cards put on screen: a slider moves the content of a phase that stands still, and the
 * transition the phase ends with carries that content on from where the slider left it.
 */
class DeferredAnimationCardsTest {
    @Test
    fun theSliderMovesTheCardAndTheExitCarriesItOnFromThere() =
        runComposeSwingTest {
            openSection("Animated containers")

            val card = onNodeWithText("Transfer #7716 · Ready").fetch<JLabel>()
            val restingX = card.xInContainer()

            onNode(SwingMatcher.hasAccessibleName("Dismiss drag")).fetch<JSlider>().value = 60
            awaitIdle()
            val dragged = card.xInContainer() - restingX
            assertTrue(dragged > 0, "the hand-driven offset did not reach the card")
            val faded = paintedAlphaOf(card)
            assertTrue(faded < 255, "the hand-driven opacity did not reach the paint, which stayed opaque at $faded")

            // The exit slides the card half its own width away, so a takeover starts where the drag left it
            // and ends short of that.
            val exitEnd = card.width / 2
            mainClock.autoAdvance = false
            onNodeWithText("Commit").performClick()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertTrue(
                card.xInContainer() in (restingX + dragged) until (restingX + exitEnd),
                "the exit put the card at ${card.xInContainer()} instead of taking it over at ${restingX + dragged}",
            )
            repeat(5) {
                mainClock.advanceTimeByFrame()
                awaitIdle()
            }
            assertTrue(
                card.xInContainer() > restingX + dragged,
                "the exit never carried the card on from ${restingX + dragged}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            onNodeWithText("Transfer #7716 · Ready").assertDoesNotExist()
            onNodeWithText("Dragged: 0%").assertExists()

            onNodeWithText("Show again").performClick()
            awaitIdle()
            val shownCard = onNodeWithText("Transfer #7716 · Ready").fetch<JLabel>()
            assertEquals(restingX, shownCard.xInContainer(), "the card came back offset")
        }

    @Test
    fun cancelingTheDismissalBringsTheCardBack() =
        runComposeSwingTest {
            openSection("Animated containers")

            val card = onNodeWithText("Transfer #7716 · Ready").fetch<JLabel>()
            val restingX = card.xInContainer()
            onNode(SwingMatcher.hasAccessibleName("Dismiss drag")).fetch<JSlider>().value = 80
            awaitIdle()
            val dragged = card.xInContainer() - restingX
            assertTrue(dragged > 0, "the drag did not move the card")
            onNodeWithText("Show again").assertDoesNotExist()

            mainClock.autoAdvance = false
            onNodeWithText("Cancel").performClick()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertTrue(
                card.xInContainer() in (restingX + 1)..(restingX + dragged),
                "the given-up phase did not carry the card back toward rest",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(restingX, card.xInContainer(), "the canceled phase left the card away")
            onNodeWithText("Dragged: 0%").assertExists()
        }

    @Test
    fun cancelingAfterTheCommitKeepsTheCardDismissed() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNodeWithText("Commit").performClick()
            awaitIdle()
            onNodeWithText("Transfer #7716 · Ready").assertDoesNotExist()

            onNodeWithText("Cancel").performClick()
            mainClock.autoAdvance = false
            driveFramesIntoTheTransition()
            mainClock.autoAdvance = true
            awaitIdle()
            onNodeWithText("Transfer #7716 · Ready").assertDoesNotExist()
            onNodeWithText("Show again").assertExists()
        }

    @Test
    fun cancelingTheSwipeReturnsToThePageOnScreen() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNode(SwingMatcher.hasAccessibleName("Swipe drag")).fetch<JSlider>().value = 50
            awaitIdle()
            onNodeWithText("Drafts · 1 unsent").assertExists()

            onNodeWithText("Cancel swipe").performClick()
            awaitIdle()
            onNodeWithText("Drafts · 1 unsent").assertDoesNotExist()
            assertEquals(
                0,
                onNodeWithText("Inbox · 3 unread").fetch<JLabel>().xInContainer(),
                "the canceled swipe left the page displaced",
            )
        }

    @Test
    fun theSwipeDrivesBothPagesAndTheTransitionFinishesFromThere() =
        runComposeSwingTest {
            openSection("Animated containers")
            onNodeWithText("Drafts · 1 unsent").assertDoesNotExist()

            onNode(SwingMatcher.hasAccessibleName("Swipe drag")).fetch<JSlider>().value = 50
            awaitIdle()
            val arriving = onNodeWithText("Drafts · 1 unsent").fetch<JLabel>()
            assertTrue(
                onNodeWithText("Inbox · 3 unread").fetch<JLabel>().xInContainer() < 0,
                "the page being left was not dragged off the leading edge",
            )
            val swipedTo = arriving.xInContainer()
            assertTrue(swipedTo > 0, "the announced page was not dragged in from the trailing edge")

            mainClock.autoAdvance = false
            onNodeWithText("Commit swipe").performClick()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            val takenOver = arriving.xInContainer()
            assertTrue(
                takenOver in 1..swipedTo,
                "the enter jumped the announced page to $takenOver instead of taking over at $swipedTo",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, arriving.xInContainer(), "the announced page never arrived where the container places it")
            onNodeWithText("Inbox · 3 unread").assertDoesNotExist()
            onNodeWithText("Swiped: 0%").assertExists()
        }
}

/**
 * Where a page stands in the container that animates between pages: the transform moves the page's own panel, and
 * this is where the container placed that panel. A page placed past the container's edge grows the container's
 * physical bounds to paint it, so the panel's Swing coordinates are not its placement.
 */
private fun JLabel.xInContainer(): Int {
    val page = enclosingDecorated()
    return page.x + page.paintOutsets().left - page.parent.paintOutsets().left
}

/**
 * How opaque [card] is painted, read off the pixel at its center in the container that composites it - the
 * container applies the opacity a phase declares, so the card painted on its own would always be opaque.
 */
private fun paintedAlphaOf(card: JLabel): Int {
    val painted = card.parent.captureToImage()
    return Color(painted.getRGB(card.x + card.width / 2, card.y + card.height / 2), true).alpha
}
