package org.jetbrains.compose.swing.samples.agent.context

import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

class MeterSectionTest {
    @Test
    fun theMeterShowsTheUsedFractionAndFollowsLaterChanges() =
        runAgentTest(rootSize = paneSize) {
            val input = ContextInput(usedTokens = 50_000)
            showContextPane(input)
            onNodeWithText(percent(0.25)).assertIsDisplayed()
            onNodeWithText(message(English.locale, "context.usage", 50_000, 200_000)).assertIsDisplayed()

            input.usedTokens = 190_000
            awaitIdle()
            onNodeWithText(percent(0.95)).assertIsDisplayed()
            onNodeWithText(message(English.locale, "context.usage", 190_000, 200_000)).assertIsDisplayed()
        }

    @Test
    fun theMeterStopsAtFullWhenTheContextOverflows() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(usedTokens = 300_000))
            onNodeWithText(percent(1.0)).assertIsDisplayed()
        }

    @Test
    fun aZeroLimitShowsAnEmptyMeter() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(usedTokens = 300_000), contextLimit = 0)
            onNodeWithText(percent(0.0)).assertIsDisplayed()
        }

    @Test
    fun theRingFillsClockwiseFromTwelve() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(usedTokens = 50_000))
            awaitIdle()
            val ring = onNodeWithText(percent(0.25)).onParent().captureToImage()
            val accent = readAgentTheme().colors.accent
            // A quarter of the ring is the arc from twelve to three o'clock: the top right is filled, the top left not.
            val distance = { x: Int, y: Int ->
                val c = Color(ring.getRGB(x, y))
                abs(c.red - accent.red) + abs(c.green - accent.green) + abs(c.blue - accent.blue)
            }
            val offset = (ring.width / 2 * 0.65).roundToInt()
            val center = ring.width / 2
            assertTrue(distance(center + offset, center - offset) < 60, "the top right shows the accent")
            assertTrue(distance(center - offset, center - offset) > 120, "the top left shows the track")
        }

    private fun percent(fraction: Double) = message(English.locale, "context.meter.percent", fraction)
}
