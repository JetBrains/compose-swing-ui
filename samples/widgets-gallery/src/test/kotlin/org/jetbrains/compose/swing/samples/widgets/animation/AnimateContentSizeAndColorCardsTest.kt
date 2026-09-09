package org.jetbrains.compose.swing.samples.widgets.animation

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The report an animation that ran out leaves behind, as the card writes it. */
private val REPORT = Regex("""(\d+)x(\d+) to (\d+)x(\d+)""")

/** How far apart two colors are, as the largest difference between two of their components. */
private fun Color.distanceTo(other: Color): Int =
    maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))

class AnimateContentSizeAndColorCardsTest {
    @Test
    fun additionalOrderDetailsTravelTheContainerToTheirRequestedSize() =
        runComposeSwingTest {
            openSection("Animated containers")

            val label = onNodeWithText("Shipment details", substring = true).fetch<Component>()
            val container = label.parent
            // The animated container's own real bounds settle at its content's real size at once; a
            // sibling below it is what the frame it travels through moves, so that is read mid-flight.
            val report = onNodeWithText("Last animation:", substring = true).fetch<JLabel>()
            val beforeReportY = report.y

            // The test owns the frames from here, so the frame can be read while it is still traveling
            // rather than only once it has arrived.
            mainClock.autoAdvance = false
            onNodeWithText("More details").performClick()
            awaitIdle()
            mainClock.advanceTimeUntil { report.y > beforeReportY }
            val midway = report.y

            mainClock.autoAdvance = true
            awaitIdle()
            assertTrue(
                midway < report.y,
                "the container took the taller frame at once instead of traveling to it: $midway",
            )
            val insets = container.insets
            assertEquals(
                label.height + insets.top + insets.bottom,
                container.height,
                "the container did not settle at the size its label asks for",
            )
        }

    @Test
    fun moreOrderDetailsRetargetTheAnimationFromItsCurrentSize() =
        runComposeSwingTest {
            openSection("Animated containers")

            val label = onNodeWithText("Shipment details", substring = true).fetch<Component>()
            val container = label.parent
            val before = container.height
            val report = onNodeWithText("Last animation:", substring = true).fetch<JLabel>()

            // A few frames rather than the first pixel of travel, so the retarget below genuinely lands
            // mid-flight instead of on an animation that only just started.
            mainClock.autoAdvance = false
            onNodeWithText("More details").performClick()
            awaitIdle()
            driveFramesIntoTheTransition()

            onNodeWithText("More details").performClick()
            // Frames stay held across the retarget, so each one is measured the way a window measures it
            // rather than run out in one go.
            driveFramesIntoTheTransition()

            // Only the second animation reports: the first is retargeted before it runs out.
            mainClock.autoAdvance = true
            awaitIdle()
            val reported = assertNotNull(REPORT.find(report.text), "no animation was reported: ${report.text}")
            val initialHeight = reported.groupValues[2].toInt()
            val targetHeight = reported.groupValues[4].toInt()
            assertTrue(
                initialHeight > before && initialHeight < targetHeight,
                "the second animation started over from $before rather than bending from where the " +
                    "container stood: ${report.text}",
            )
        }

    @Test
    fun theFinishedListenerReportsTheSizesTheLastAnimationRanBetween() =
        runComposeSwingTest {
            openSection("Animated containers")

            onNodeWithText("More details").performClick()
            val report = onNodeWithText("Last animation:", substring = true).fetch<JLabel>()
            waitUntil { REPORT.containsMatchIn(report.text) }

            val reported = assertNotNull(REPORT.find(report.text), "no animation was reported: ${report.text}")
            val sizes = reported.groupValues.drop(1).map { it.toInt() }
            val (initialWidth, initialHeight) = sizes
            val (targetWidth, targetHeight) = sizes.drop(2)
            assertTrue(
                targetWidth > initialWidth && targetHeight > initialHeight,
                "the animation reported was not one that grew the container: ${report.text}",
            )
        }

    @Test
    fun aPlacementMovesTheContainerOnlyWhileItIsTraveling() =
        runComposeSwingTest {
            openSection("Animated containers")

            val label = onNodeWithText("Shipment details", substring = true).fetch<Component>()
            val container = label.parent
            onNodeWithText("More details").performClick()
            onNodeWithText("Bottom end").performClick()
            awaitIdle()
            val settledY = container.y

            // The container's own real bounds settle at its content's real size at once, so it is what
            // the frame it is aligned within moves it, not a shift inside the container itself.
            mainClock.autoAdvance = false
            onNodeWithText("Fewer details").performClick()
            awaitIdle()
            mainClock.advanceTimeByFrame()
            assertTrue(
                container.y > settledY,
                "the container's vertical offset (the only axis Bottom end moves in a fillMaxWidth " +
                    "column) stayed at the start of a frame still larger than it: ${container.y}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                settledY,
                container.y,
                "the container's vertical offset did not return to the start of a frame exactly as large as it",
            )
        }

    @Test
    fun choosingReadyTravelsTheSwatchThroughTheRampRatherThanJumpingToItsEnd() =
        runComposeSwingTest {
            openSection("Animation")

            val swatch = onNode(SwingMatcher.hasAccessibleName("Oklab swatch")).fetch<JLabel>()
            assertTrue(
                swatch.background.distanceTo(rampStart) <= TOLERANCE,
                "the swatch did not start at the blue end of the ramp: ${swatch.background}",
            )

            // The test owns the frames from here, so the swatch can be read partway along the ramp
            // rather than only once the animation has run out.
            mainClock.autoAdvance = false
            onNodeWithText("Ready").performClick()
            mainClock.advanceTimeBy(300.milliseconds)
            val midway = swatch.background
            val naiveMidway = onNode(SwingMatcher.hasAccessibleName("sRGB swatch")).fetch<JLabel>().background
            mainClock.advanceTimeBy(600.milliseconds)
            val settled = swatch.background

            assertTrue(
                midway.distanceTo(rampStart) > TOLERANCE && midway.distanceTo(rampEnd) > TOLERANCE,
                "the swatch jumped between the ends of the ramp instead of traveling: $midway",
            )
            assertTrue(
                midway.distanceTo(naiveMidway) > TOLERANCE,
                "the Oklab ramp stood where mixing the sRGB components put it, so the swatches " +
                    "compare nothing: $midway against $naiveMidway",
            )
            assertTrue(
                settled.distanceTo(rampEnd) <= TOLERANCE,
                "the swatch did not settle at the yellow end of the ramp: $settled",
            )
        }
}

/** The slack a color component is compared with, since a ramp lands on its target through Oklab. */
private const val TOLERANCE = 4
