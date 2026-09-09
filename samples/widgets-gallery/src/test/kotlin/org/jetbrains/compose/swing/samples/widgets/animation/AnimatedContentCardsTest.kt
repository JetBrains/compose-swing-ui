package org.jetbrains.compose.swing.samples.widgets.animation

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Component
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.accessibility.AccessibleRole
import javax.swing.JCheckBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AnimatedContentCardsTest {
    @Test
    fun clipContentDecidesWhetherASlidingPagePaintsBesideTheContainer() {
        val (clipped, strip) = besideTheContainerWhileSliding(clip = true)
        val (unclipped, _) = besideTheContainerWhileSliding(clip = false, strip)

        assertEquals(1, clipped.distinctColors(), "a clipped page painted beside the container")
        assertTrue(unclipped.distinctColors() > 1, "an unclipped page did not paint beside the container")
    }

    /**
     * A strip of the card right of the container, captured while the first page slides out and the
     * second slides in, and where the strip is. Without [strip], it is taken from the container's bounds.
     */
    private fun besideTheContainerWhileSliding(
        clip: Boolean,
        strip: Rectangle? = null,
    ): Pair<BufferedImage, Rectangle> {
        lateinit var result: Pair<BufferedImage, Rectangle>
        runComposeSwingTest {
            openSection("Animated containers")
            if (!clip) onNodeWithText("Clip content").performClick()

            mainClock.autoAdvance = false
            onNodeWithText("Next").performClick()
            mainClock.advanceTimeBy(100.milliseconds)

            val container = containerHolding(paneBody("Overview").fetch(), paneBody("Details").fetch())
            val bounds = container.bounds
            val area = strip ?: Rectangle(bounds.x + bounds.width, bounds.y, 40, bounds.height)
            result = container.parent.captureToImage().getSubimage(area.x, area.y, area.width, area.height) to area
        }
        return result
    }

    @Test
    fun theArrivingPageSlidesInFromTheRight() =
        runComposeSwingTest {
            openSection("Animated containers")

            mainClock.autoAdvance = false
            onNodeWithText("Next").performClick()
            mainClock.advanceTimeBy(30.milliseconds)

            val arriving = onNodeWithText("Details").fetch()
            val slidStartedAt = arriving.xInRoot(root)
            // Both pages are on screen for the whole transition, which is what the slide moves apart.
            onNodeWithText("Overview").assertExists()

            mainClock.autoAdvance = true
            awaitIdle()
            val restsAt = arriving.xInRoot(root)
            assertTrue(
                slidStartedAt > restsAt,
                "the arriving page should travel leftward into place, but went from $slidStartedAt to $restsAt",
            )
            onNodeWithText("Overview").assertDoesNotExist()
        }

    @Test
    fun steppingBackSlidesTheArrivingPageInFromTheLeft() =
        runComposeSwingTest {
            openSection("Animated containers")
            onNodeWithText("Next").performClick()
            awaitIdle()

            mainClock.autoAdvance = false
            onNodeWithText("Prev").performClick()
            mainClock.advanceTimeBy(30.milliseconds)

            val arriving = onNodeWithText("Overview").fetch()
            val slidStartedAt = arriving.xInRoot(root)
            onNodeWithText("Details").assertExists()

            mainClock.autoAdvance = true
            awaitIdle()
            val restsAt = arriving.xInRoot(root)
            assertTrue(
                slidStartedAt < restsAt,
                "the arriving page should travel rightward into place, but went from $slidStartedAt to $restsAt",
            )
        }

    @Test
    fun orderPagesContainWorkingControlsAndResizeToTheirContent() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNodeWithText("Review orders").performClick()
            onNodeWithText("Shipment #7716").assertExists()
            onNodeWithText("Mark as shipped").performClick()
            onNodeWithText("Undo shipment").assertExists()

            onNodeWithText("Next").performClick()
            onNodeWithText("Next").performClick()
            val updates = onNodeWithText("Email shipping updates").fetch<JCheckBox>()
            assertTrue(updates.isSelected, "shipping updates should be enabled initially")
            onNodeWithText("Email shipping updates").performClick()
            assertFalse(updates.isSelected, "the shipping updates control did not change state")

            val clip = onNodeWithText("Clip content").fetch<JCheckBox>()
            assertTrue(clip.isSelected, "page content should be clipped by default")
            onNodeWithText("Clip content").performClick()
            assertFalse(clip.isSelected, "the clip control did not turn clipping off")
            onNodeWithText("Prev").performClick()
            onNodeWithText("History").assertExists()
        }

    @Test
    fun theCrossingReportsTakeTheRoomBothNeedAndThenTheRoomOfTheOneThatStays() =
        runComposeSwingTest {
            openSection("Animated containers")

            mainClock.autoAdvance = false
            onNodeWithText("Orders").performClick()
            // Two frames in: the arriving pane is composed and laid out, and the 600ms fade has barely
            // started, so a container that traveled to its new size would still be on its way.
            mainClock.advanceTimeBy(30.milliseconds)

            val leaving = paneBody("Summary").fetch()
            val arriving = paneBody("Orders").fetch()
            val container = containerHolding(leaving, arriving)
            val whileOverlapping = container.size
            val leavingPage = leaving.parent
            val arrivingPage = arriving.parent

            mainClock.autoAdvance = true
            awaitIdle()

            assertEquals(
                maxOf(leavingPage.width, arrivingPage.width),
                whileOverlapping.width,
                "the container did not take the width both panes need while they overlapped",
            )
            assertEquals(
                maxOf(leavingPage.height, arrivingPage.height),
                whileOverlapping.height,
                "the container did not take the height both panes need while they overlapped",
            )
            paneBody("Summary").assertDoesNotExist()
            assertEquals(arrivingPage.width, container.width, "the container did not fit the pane that stays")
            assertEquals(arrivingPage.height, container.height, "the container did not fit the pane that stays")
        }

    @Test
    fun withoutTheHoldThePanelBeingLeftGoesAsSoonAsItsOwnExitEnds() =
        runComposeSwingTest {
            openSection("Animated containers")

            mainClock.autoAdvance = false
            onNodeWithText("Swap").performClick()
            mainClock.advanceTimeBy(50.milliseconds)
            // Partway through the 150ms exit, so the assertions below are about an exit that ran and
            // ended, never about an instant swap.
            onNodeWithText("First panel").assertExists()

            mainClock.advanceTimeBy(PAST_THE_EXIT)
            onNodeWithText("First panel").assertDoesNotExist()
            onNodeWithText("Second panel").assertExists()
        }

    @Test
    fun theHoldKeepsThePanelBeingLeftUntilTheWholeTransitionHasFinished() =
        runComposeSwingTest {
            openSection("Animated containers")
            onNodeWithText("Hold the content being left").performClick()

            mainClock.autoAdvance = false
            onNodeWithText("Swap").performClick()
            mainClock.advanceTimeBy(PAST_THE_EXIT)
            onNodeWithText("First panel").assertExists()

            mainClock.autoAdvance = true
            awaitIdle()
            onNodeWithText("First panel").assertDoesNotExist()
            onNodeWithText("Second panel").assertExists()
        }
}

// Well past the 150ms exit of the card holding its content, and well short of its 1200ms enter, so an
// assertion made here is about the hold and never about an exit that has not run out yet.
private val PAST_THE_EXIT = 400.milliseconds

/** The body of the pane named [name], told apart from the radio option carrying the same name. */
private fun ComposeSwingTest.paneBody(name: String): SwingNodeInteraction<Component> =
    onNode(SwingMatcher.hasText(name) and SwingMatcher.hasAccessibleRole(AccessibleRole.LABEL))

private fun BufferedImage.distinctColors(): Int =
    (0 until height).flatMap { y -> (0 until width).map { x -> getRGB(x, y) } }.toSet().size
