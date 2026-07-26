package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertTrue

class CanvasSectionTest {
    @Test
    fun theSweepSliderEchoRecomposes() =
        runComposeSwingTest {
            openSection("Canvas")

            onNodeWithText("Sweep: 70%", substring = true).assertExists()

            val sliders = onAllNodesOfType<JSlider>().fetchAll<JSlider>()
            assertTrue(sliders.size >= 2, "the Canvas card exposes a petal slider and a sweep slider")
            sliders[1].value = 30
            awaitIdle()
            onNodeWithText("Sweep: 30%", substring = true).assertExists()
        }

    @Test
    fun theCanvasSurfaceRendersToAStableBitmapScreenshotTest() =
        runComposeSwingTest {
            openSection("Canvas")

            val initial = onNodeWithTag(CANVAS_TAG).captureToImage()
            assertTrue(initial.width > 0 && initial.height > 0, "the captured surface has real size")

            var painted = 0
            val colours = HashSet<Int>()
            for (y in 0 until initial.height) {
                for (x in 0 until initial.width) {
                    val argb = initial.getRGB(x, y)
                    if ((argb ushr 24) != 0) painted++
                    colours.add(argb)
                }
            }
            val total = initial.width * initial.height
            assertTrue(
                painted > total / 2,
                "the Canvas surface must be substantially painted, but only $painted/$total pixels were drawn",
            )
            assertTrue(
                colours.size > 50,
                "the hand-drawn surface must show many distinct colours, but only ${colours.size} were present",
            )

            onNodeWithTag(CANVAS_TAG).assertImageMatches(expected = initial)
        }
}
