// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.svg

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.accessibility.AccessibleRole
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Tests the SVG section as a viewer meets it: a document is loaded, painted, resized and played.
 *
 * The harness's infinite-animation policy cancels a timeline that repeats indefinitely when it requests its
 * first frame, so every test here drives the frame clock by hand.
 */
class SvgSectionTest {
    private fun ComposeSwingTest.sizeSlider() =
        onAllNodesOfType<JSlider>().filterToOne(SwingMatcher.hasAccessibleName("Image size"))

    private fun ComposeSwingTest.positionSlider() =
        onAllNodesOfType<JSlider>().filterToOne(SwingMatcher.hasAccessibleName("Playback position"))

    /**
     * Composes the section under manual frame control and waits for the requested artwork.
     * Its document loads off the event dispatch thread, so the wait uses a wall-clock deadline.
     */
    private fun ComposeSwingTest.openSvgSection(
        artwork: SwingMatcher,
        content: @Composable () -> Unit = { SvgSection() },
    ): SwingNodeInteraction<Component> {
        mainClock.autoAdvance = false
        setContent(content)
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (onAllNodes(artwork).fetchSize() == 0) {
            if (deadline.hasPassedNow()) fail("the document matching $artwork never arrived")
            mainClock.advanceTimeByFrame()
        }
        return onNode(artwork).assertExists()
    }

    @Test
    fun theSectionOpensFromTheShell() =
        runComposeSwingTest {
            openSection("SVG")

            sizeSlider().assertExists()
        }

    @Test
    fun theDocumentIsPaintedAndTakesTheSizeTheSliderAsksForScreenshotTest() =
        runComposeSwingTest {
            val artwork = openSvgSection(scaledArtwork)

            val image = artwork.captureToImage()
            val painted =
                (0 until image.height).sumOf { y ->
                    (0 until image.width).count { x -> (image.getRGB(x, y) ushr 24) != 0 }
                }
            assertTrue(
                painted > image.width * image.height / 2,
                "the document must cover the panel it is drawn on, but only $painted pixels were painted",
            )

            // End takes a slider to the top of its range.
            sizeSlider().performKeyPress(KeyEvent.VK_END)
            mainClock.advanceTimeByFrame()

            val largest = 192
            onNodeWithText("Size: ${largest}px").assertExists()
            artwork.assertProperty(Dimension(largest, largest)) { preferredSize }
        }

    @Test
    fun seekingRedrawsTheDocumentAtThatPositionScreenshotTest() =
        runComposeSwingTest {
            val artwork = openSvgSection(animatedArtwork)
            onNodeWithText("Pause").performClick()
            mainClock.advanceTimeByFrame()

            positionSlider().performKeyPress(KeyEvent.VK_HOME)
            mainClock.advanceTimeByFrame()
            val atStart = artwork.captureToImage()

            // Three pages on, the presses seek to position 447ms.
            repeat(3) { positionSlider().performKeyPress(KeyEvent.VK_PAGE_UP) }
            mainClock.advanceTimeByFrame()
            onNodeWithText("Position: 447ms").assertExists()
            val midway = artwork.captureToImage()

            assertTrue(differ(atStart, midway), "seeking must redraw the document at the position asked for")
        }

    @Test
    fun theAnimationCaptionRendersAsReadableText() =
        runComposeSwingTest {
            openSvgSection(animatedArtwork)

            onNodeWithText("This document's", substring = true)
                .assertIsOfType<JLabel>()
                .assertProperty(true, "the rendered caption should describe animation tracks") {
                    val document = (getClientProperty(BasicHTML.propertyKey) as View).document
                    "animation tracks" in document.getText(0, document.length)
                }
        }

    @Test
    fun theSeekSliderFollowsThePositionWithinTheTurnItIsOn() =
        runComposeSwingTest {
            openSvgSection(animatedArtwork)

            // The slider spans positions 0 through 1499 of the 1500 ms turn.
            positionSlider().assertProperty(1499, "the slider must not offer the turn's own end") { maximum }
            // One frame anchors the timeline on the clock this test drives. The hundred frames after it move
            // the pulse past one full turn, at the harness's sixtieth of a second each.
            mainClock.advanceTimeByFrame()
            repeat(100) { mainClock.advanceTimeByFrame() }

            onNodeWithText("Position: 1666ms").assertExists()
            positionSlider().assertProperty(
                166,
                "past the first turn the slider must follow the position within the turn, not sit at its end",
            ) { value }

            mainClock.advanceTimeByFrame(2_147_483_648L.milliseconds - 1666.milliseconds)

            positionSlider().assertProperty(1148) { value }
            onNodeWithText("Position: 2147483648ms").assertExists()
        }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun theSwitchIsIdleAsSoonAsItsArtworkLoads(checked: Boolean) =
        runComposeSwingTest {
            val name = if (checked) "Night" else "Day"
            openSvgSection(SwingMatcher.hasAccessibleName(name)) { DayNightSwitch(checked) {} }
            val loadedAt = mainClock.currentTime

            mainClock.autoAdvance = true
            awaitIdle()

            assertEquals(loadedAt, mainClock.currentTime, "a switch at rest should need no animation frames")
        }

    @Test
    fun clickingTheSwitchCarriesTheDocumentToItsOtherStateAndBackScreenshotTest() =
        runComposeSwingTest {
            val switch = openSvgSection(switchArtwork)
            val day = switch.captureToImage()

            switch.performClick()
            repeat(SWITCH_FRAMES) { mainClock.advanceTimeByFrame() }
            onNodeWithText("Night").assertExists()
            val night = switch.captureToImage()
            assertTrue(differ(day, night), "clicking must carry the document to the other end of its timeline")

            switch.performClick()
            repeat(SWITCH_FRAMES) { mainClock.advanceTimeByFrame() }

            onNodeWithText("Day").assertExists()
            val back = switch.captureToImage()
            assertTrue(!differ(day, back), "clicking back must return the document to where it started")
        }

    @Test
    fun everyFrameOfTheSwitchTurningDrawsItsPictureScreenshotTest() =
        runComposeSwingTest {
            val switch = openSvgSection(switchArtwork)
            val atDay = switch.captureToImage()

            val toNight = listOf(atDay) + framesAfterClicking(switch)
            val backToDay = toNight.takeLast(1) + framesAfterClicking(switch)

            (toNight + backToDay).forEachIndexed { index, frame ->
                assertTrue(
                    isOpaque(frame, frame.width / 2, frame.height / 2),
                    "frame $index lost the switch's track, so the picture blinked out",
                )
                assertTrue(bodyEdge(frame) != null, "frame $index shows neither the sun nor the moon")
            }
            val toNightEdges = toNight.map { bodyEdge(it) ?: Int.MAX_VALUE }
            val backEdges = backToDay.map { bodyEdge(it) ?: Int.MIN_VALUE }
            assertTrue(
                toNightEdges.first() < atDay.width / 2 && toNightEdges.last() > atDay.width / 2,
                "turning to night must carry the body from the left half to the right half",
            )
            assertTrue(
                toNightEdges.zipWithNext().all { (before, after) -> before <= after },
                "turning to night must only move the body to the right, but its edge went $toNightEdges",
            )
            assertTrue(
                backEdges.zipWithNext().all { (before, after) -> before >= after },
                "turning back to day must only move the body to the left, but its edge went $backEdges",
            )
            assertTrue(differ(atDay, backToDay.last()).not(), "turning back must return the picture to day")
        }

    @Test
    fun onlyTheLeftButtonTurnsTheSwitch() =
        runComposeSwingTest {
            val switch = openSvgSection(switchArtwork)

            listOf(MouseEvent.BUTTON2, MouseEvent.BUTTON3).forEach { button ->
                switch.performClick(button = button)
                repeat(SWITCH_FRAMES) { mainClock.advanceTimeByFrame() }
                onNodeWithText("Day").assertExists()
            }

            switch.performClick()
            repeat(SWITCH_FRAMES) { mainClock.advanceTimeByFrame() }
            onNodeWithText("Night").assertExists()
        }

    @Test
    fun theSwitchNamesItsStateForAccessibility() =
        runComposeSwingTest {
            val switch = openSvgSection(switchArtwork)
            switch.assertProperty("Day") { accessibleContext.accessibleName }

            switch.performClick()
            mainClock.advanceTimeByFrame()

            switch.assertProperty("Night") { accessibleContext.accessibleName }
        }

    @Test
    fun theButtonReadsPlayWhilePausedAndPlayResumesThePosition() =
        runComposeSwingTest {
            openSvgSection(animatedArtwork)
            mainClock.advanceTimeByFrame()
            repeat(FRAMES) { mainClock.advanceTimeByFrame() }

            onNodeWithText("Pause").performClick()
            mainClock.advanceTimeByFrame()
            onNodeWithText("Play").assertExists()
            onNodeWithText("Position: 166ms").assertExists()
            repeat(FRAMES) { mainClock.advanceTimeByFrame() }
            onNodeWithText("Position: 166ms").assertExists()

            onNodeWithText("Play").performClick()
            // The first frame after Play re-anchors the timeline, so it still shows the paused position.
            mainClock.advanceTimeByFrame()
            onNodeWithText("Position: 166ms").assertExists()
            mainClock.advanceTimeByFrame()
            onNodeWithText("Position: 183ms").assertExists()
            repeat(8) { mainClock.advanceTimeByFrame() }
            onNodeWithText("Position: 166ms").assertDoesNotExist()
            onNodeWithText("Pause").assertExists()
        }

    @Test
    fun restartReturnsThePausedPositionToZero() =
        runComposeSwingTest {
            openSvgSection(animatedArtwork)
            mainClock.advanceTimeByFrame()
            repeat(FRAMES) { mainClock.advanceTimeByFrame() }
            onNodeWithText("Pause").performClick()
            mainClock.advanceTimeByFrame()
            onNodeWithText("Position: 166ms").assertExists()

            onNodeWithText("Restart").performClick()
            mainClock.advanceTimeByFrame()

            onNodeWithText("Position: 0ms").assertExists()
        }

    @Test
    fun turningTheSwitchBackPartwayContinuesFromWhereItHadReachedScreenshotTest() =
        runComposeSwingTest {
            val switch = openSvgSection(switchArtwork)
            val dayEdge = bodyEdge(switch.captureToImage()) ?: fail("day has no body")

            // Fifteen frames are few enough that the toggle is still under way.
            val partway = framesAfterClicking(switch, 15).map { bodyEdge(it) ?: fail("no body") }
            val back = framesAfterClicking(switch).map { bodyEdge(it) ?: fail("no body") }

            assertTrue(partway.last() > dayEdge, "the switch must have left day, but its edge is ${partway.last()}")
            val lastStep = partway.last() - partway[partway.lastIndex - 1]
            assertTrue(
                back.first() in (dayEdge + 1)..(partway.last() + lastStep),
                "turning back must continue from edge $partway, but the frames after it went $back",
            )
            assertTrue(
                back.zipWithNext().all { (before, after) -> before >= after },
                "turning back must only move the body to the left, but its edge went $back",
            )
        }

    /** Clicks the switch, then returns its picture after each of [frames] frames of the clock. */
    private suspend fun ComposeSwingTest.framesAfterClicking(
        switch: SwingNodeInteraction<Component>,
        frames: Int = SWITCH_FRAMES,
    ): List<BufferedImage> {
        switch.performClick()
        val pictures = mutableListOf<BufferedImage>()
        repeat(frames) {
            mainClock.advanceTimeByFrame()
            pictures += switch.captureToImage()
        }
        return pictures
    }

    private companion object {
        val scaledArtwork = artworkInCard("SvgImage at any size")
        val animatedArtwork = artworkInCard("Animated SVG")
        val switchArtwork = artworkInCard("A widget drawn as an SVG")

        fun artworkInCard(title: String): SwingMatcher =
            SwingMatcher.hasAccessibleRole(AccessibleRole.ICON) and
                SwingMatcher.hasParent(SwingMatcher.hasAccessibleName(title))

        const val FRAMES = 10

        // Well above the toggle's own duration, so the toggle has come to rest.
        const val SWITCH_FRAMES = 80

        fun isOpaque(
            image: BufferedImage,
            x: Int,
            y: Int,
        ): Boolean = (image.getRGB(x, y) ushr 24) == 0xFF

        /**
         * Returns the leftmost pixel of the body on the row through its center, or null when the row has none.
         * The body is the one pale shape level with the middle of the track at both ends of its travel.
         * A pixel belongs to the body when its red channel is at least 0xE6, the lighter red of the sun's #ffdf71
         * and the moon's #e6e8ee.
         */
        fun bodyEdge(image: BufferedImage): Int? =
            (0 until image.width).firstOrNull { x -> (image.getRGB(x, image.height / 2) shr 16 and 0xFF) >= 0xE6 }

        fun differ(
            first: BufferedImage,
            second: BufferedImage,
        ): Boolean =
            (0 until first.height).any { y ->
                (0 until first.width).any { x -> first.getRGB(x, y) != second.getRGB(x, y) }
            }
    }
}
