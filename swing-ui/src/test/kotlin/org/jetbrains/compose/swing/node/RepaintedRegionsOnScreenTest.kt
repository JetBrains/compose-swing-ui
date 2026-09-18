package org.jetbrains.compose.swing.node

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Robot
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * What a change leaves on a real screen, read back with [Robot].
 *
 * [RepaintedRegionsTest] replays the regions a change asks for offscreen; these cases hold that replay to
 * what a shown window actually displays. The pixels read are whatever window is on top at that spot, a
 * machine-wide state, so the class runs apart from other tests that show windows.
 */
@ExclusiveWindowSystem
class RepaintedRegionsOnScreenTest {
    @Test
    fun aRemovedChildLeavesNoneOfItsPixels() = onScreen(blocks = 3) { applier, colorAt ->
        assertShows(Color.RED, colorAt(60), "the child is on screen before it leaves")

        applier.pass(applier.root) { remove(1, 1) }
        settle()

        assertShows(Color.WHITE, colorAt(60), "the removed child's area shows the container")
        assertShows(Color.BLUE, colorAt(20), "the sibling before it still shows")
        assertShows(Color.GREEN, colorAt(100), "the sibling after it still shows")
    }

    @Test
    fun aChildArrivingWithItsOwnBoundsIsShown() = onScreen(blocks = 2) { applier, colorAt ->
        assertShows(Color.WHITE, colorAt(100), "the area is empty before the child arrives")

        applier.pass(applier.root) { insert(2, SwingNodeHolder(block(Color.BLUE, x = 80))) }
        settle()

        assertShows(Color.BLUE, colorAt(100), "the arriving child is on screen")
        assertShows(Color.RED, colorAt(60), "the sibling before it still shows")
    }

    /**
     * Shows an undecorated, always-on-top 200x40 white window holding [blocks] blocks of 40x20 in a row -
     * blue, red, green - and runs [assertions] on the event dispatch thread with the applier over its
     * content pane and a reader of the screen color at a given x on the blocks' row.
     */
    private fun onScreen(
        blocks: Int,
        assertions: suspend (applier: SwingApplier, colorAt: (x: Int) -> Color) -> Unit,
    ): TestResult {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        return runSwingTest {
            val robot = Robot()
            val owner = TestCompositionOwner.observing()
            val content =
                JPanel(null).apply {
                    background = Color.WHITE
                    isOpaque = true
                }
            val frame =
                JFrame().apply {
                    isUndecorated = true
                    isAlwaysOnTop = true
                    contentPane = content
                    setBounds(200, 200, 200, 40)
                }
            try {
                frame.isVisible = true
                frame.toFront()
                val colors = listOf(Color.BLUE, Color.RED, Color.GREEN).take(blocks)
                val applier =
                    SwingApplier(SwingNodeHolder(content).attachedTo(owner)).apply {
                        pass(root) {
                            colors.forEachIndexed { index, color ->
                                insert(index, SwingNodeHolder(block(color, x = index * 40)))
                            }
                        }
                    }
                settle()
                assumeTrue(frame.isShowing, "requires a window system that shows the window")

                val origin = content.locationOnScreen
                assertions(applier) { x -> robot.getPixelColor(origin.x + x, origin.y + 10) }
            } finally {
                owner.dispose()
                frame.dispose()
            }
        }
    }

    /**
     * Asserts that [actual], a color read off the screen, is the painted [expected] within [CHANNEL_DELTA]
     * on each channel.
     *
     * A window's sRGB pixels are converted to the display's color profile on the way to the screen, and
     * [Robot] converts what it reads back to sRGB. Unless the display profile is sRGB itself, that round
     * trip rounds: on a wide-gamut display, pure green reads back as (3, 255, 0). The delta absorbs that
     * rounding and stays far below the distance between any two colors the scene paints.
     */
    private fun assertShows(
        expected: Color,
        actual: Color,
        message: String,
    ) {
        val channelDelta =
            maxOf(
                abs(expected.red - actual.red),
                abs(expected.green - actual.green),
                abs(expected.blue - actual.blue),
            )
        assertTrue(channelDelta <= CHANNEL_DELTA, "$message: expected $expected, was $actual")
    }

    private fun block(
        color: Color,
        x: Int,
    ): JPanel = JPanel(null).apply {
        background = color
        isOpaque = true
        setBounds(x, 0, 40, 20)
    }

    /** Lets the event queue drain and the window system put the painted frame on screen. */
    private suspend fun settle() {
        repeat(3) { yield() }
        delay(500.milliseconds)
    }

    private companion object {
        const val CHANNEL_DELTA = 8
    }
}
