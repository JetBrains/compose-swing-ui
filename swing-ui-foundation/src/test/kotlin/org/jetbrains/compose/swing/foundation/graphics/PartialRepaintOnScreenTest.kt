package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.frame
import org.jetbrains.compose.swing.foundation.layout.offset
import org.jetbrains.compose.swing.foundation.layout.placementLayer
import org.jetbrains.compose.swing.foundation.layout.setWindowContent
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.windowNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What a partial repaint under decorated components leaves on a real screen, read back with [Robot] and compared
 * with the same window repainted whole.
 *
 * The pixels read are whatever window is on top at that spot, a machine-wide state, so the class runs apart from
 * other tests that show windows.
 */
@ExclusiveWindowSystem
class PartialRepaintOnScreenTest {
    /**
     * A leaf draws a mark it did not draw before and repaints the mark's area. The shadow the mark casts reaches the
     * bounds of all four [neighbors][LeafAmongNeighbors], so that repaints the leaf and each of them whole, and leaves
     * on screen what a repaint of the whole window shows.
     */
    @Test
    fun aChangeInALeafAmongNeighborsWithOverlappingPaintOutsetsShowsAsARepaintOfTheWholeWindowShowsIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var marked = false
            var recording = false
            val repainted = ArrayList<Rectangle>()
            setWindowContent { LeafAmongNeighbors(marked = { marked }, onPaint = { if (recording) repainted += it }) }

            assertPartialRepaintShowsAsAWholeRepaint(
                change = {
                    marked = true
                    recording = true
                },
                repainted = { recording = false },
            )

            val root = windowNode("root")
            val reached =
                listOf("leaf", "above", "left", "right", "below")
                    .map { windowNode(it).run { SwingUtilities.convertRectangle(parent, bounds, root) } }
                    .reduce(Rectangle::union)
            assertEquals(listOf(reached), repainted, "the leaf repaints whole, with every neighbor")
        }

    /**
     * A shadowed leaf under no decorated container draws a mark it did not draw before and repaints the mark's area.
     * That repaints the shadow the mark casts, and leaves on screen what a repaint of the whole window shows.
     */
    @Test
    fun aChangeInAShadowedLeafUnderNoDecoratedContainerShowsAsARepaintOfTheWholeWindowShowsIt() =
        assertShadowedLeafUnderNoDecoratedContainerShowsAsAWholeRepaint(mergedIntoContentPane = false)

    /**
     * The same holds where Swing merges the leaf's repaint into a repaint of the window's content pane, which is no
     * Foundation container.
     */
    @Test
    fun aChangeInAShadowedLeafMergedIntoTheContentPanesRepaintShowsAsARepaintOfTheWholeWindowShowsIt() =
        assertShadowedLeafUnderNoDecoratedContainerShowsAsAWholeRepaint(mergedIntoContentPane = true)

    private fun assertShadowedLeafUnderNoDecoratedContainerShowsAsAWholeRepaint(mergedIntoContentPane: Boolean) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var marked = false
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    Box(modifier = SwingModifier.size(300, 300)) {
                        MarkedLeaf(SwingModifier.size(80, 80).offset(110, 110), marked = { marked })
                    }
                }
            }

            assertPartialRepaintShowsAsAWholeRepaint(change = { marked = true }, mergedIntoContentPane)
        }

    /**
     * Shows the window on top and runs [change], which has the leaf tagged `leaf` draw [MARK]. Repaints the mark's
     * area, together with the bottom right corner of the window's content pane where [mergedIntoContentPane], runs
     * [repainted], and checks that the mark is on screen, and that the screen shows what it shows once the box tagged
     * `root` is repainted whole.
     */
    private suspend fun ComposeSwingTest.assertPartialRepaintShowsAsAWholeRepaint(
        change: () -> Unit,
        mergedIntoContentPane: Boolean = false,
        repainted: () -> Unit = {},
    ) {
        val frame = frame()
        frame.isAlwaysOnTop = true
        frame.toFront()
        settle()
        assumeTrue(frame.isShowing, "requires a window system that shows the window")
        val root = windowNode("root")
        val leaf = windowNode("leaf")
        val robot = Robot()
        val onScreen = Rectangle(root.locationOnScreen, root.size)
        val mark = Rectangle(MARK).apply { translate(leaf.paintOutsets.left, leaf.paintOutsets.top) }
        val before = robot.createScreenCapture(onScreen)

        change()
        leaf.repaint(mark)
        if (mergedIntoContentPane) (root.parent as JComponent).run { repaint(width - 2, height - 2, 1, 1) }
        settle()
        repainted()
        val partial = robot.createScreenCapture(onScreen)
        root.paintImmediately(0, 0, root.width, root.height)
        settle()
        val whole = robot.createScreenCapture(onScreen)

        val markInRoot = SwingUtilities.convertRectangle(leaf, mark, root)
        assertNotEquals(
            before.getRGB(markInRoot.x, markInRoot.y),
            partial.getRGB(markInRoot.x, markInRoot.y),
            "the mark is on screen",
        )
        assertImagesPixelPerfect(whole, partial)
    }

    /**
     * A [MarkedLeaf] between four neighbors whose paint outsets overlap its own: a shadowed leaf, a shadowed leaf
     * placed in a translucent layer, a blurred leaf, and a box that casts a shadow and clips its content. They stand
     * in a plain box in a box tagged `root`, which has a background and hands the clip of each of its paints to
     * [onPaint].
     */
    @Composable
    private fun LeafAmongNeighbors(
        marked: () -> Boolean,
        onPaint: (Rectangle) -> Unit,
    ) {
        Box(
            modifier =
                SwingModifier
                    .testTag("root")
                    .drawBehind { graphics.clipBounds?.let(onPaint) }
                    .background(Brush.of(Color.WHITE)),
        ) {
            Box(modifier = SwingModifier.size(300, 300)) {
                val neighbor = SwingModifier.size(80, 80)
                Canvas(modifier = neighbor.testTag("above").offset(110, 30).shadow(8, Color.BLACK)) {
                    fill(Color.ORANGE)
                }
                val translucent = neighbor.testTag("left").offset(30, 110).placementLayer { alpha = 0.5f }
                Canvas(modifier = translucent.shadow(8, Color.BLACK)) { fill(Color.GREEN) }
                MarkedLeaf(neighbor.offset(110, 110), marked)
                Canvas(modifier = neighbor.testTag("right").offset(190, 110).blur(6)) { fill(Color.BLUE) }
                val clipped = neighbor.testTag("below").offset(110, 190).shadow(8, Color.BLACK)
                Box(modifier = clipped.clip(RectangleShape)) {
                    Canvas(modifier = neighbor) { fill(Color.MAGENTA) }
                }
            }
        }
    }

    /**
     * A shadowed leaf tagged `leaf`, which draws [MARK] while [marked]. Both scenes place it, 80 by 80, at (110, 110)
     * in a box of 300 by 300.
     */
    @Composable
    private fun MarkedLeaf(
        modifier: SwingModifier,
        marked: () -> Boolean,
    ) {
        Canvas(modifier = modifier.testTag("leaf").shadow(8, Color.BLACK)) {
            if (marked()) {
                graphics.color = Color.RED
                graphics.fill(MARK)
            }
        }
    }

    private fun DrawScope.fill(color: Color) {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
    }

    /** Lets the event queue drain and the window system put the painted frame on screen. */
    private suspend fun ComposeSwingTest.settle() {
        awaitIdle()
        val start = System.nanoTime()
        waitUntil(timeout = 10.seconds) { System.nanoTime() - start >= 500.milliseconds.inWholeNanoseconds }
    }

    private companion object {
        /** Inside the leaf's layout bounds, clear of every neighbor's paint outsets. */
        val MARK = Rectangle(34, 24, 12, 12)
    }
}
