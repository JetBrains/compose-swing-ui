package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val REGION_HEIGHT = 300
private const val CONTENT_WIDTH = 120
private const val CONTENT_HEIGHT = 40
private const val CONTENT_NAME = "filled"

// Enough frames for the shrink to be under way, and few enough that it has not finished.
private const val FRAMES_INTO_THE_EXIT = 3

/**
 * Content that takes the animated container's own extent rather than the one it prefers: the side panel
 * case, where the region the container sits in hands it a height the content has no way to ask for.
 *
 * The container is measured over what the content prefers either way, so a shrink still animates the
 * width the content declares. What these pin is how the filled axis behaves while that happens, in the layout and
 * in what reaches the screen: it stands at the region's extent when the container does not shrink, and takes the
 * content's own extent while the width shrinks away.
 */
class FillContainerTest {
    /**
     * Known limit: while the width shrinks away the content is laid out with no maximum on either axis, so a filled
     * height takes the content's own height. The content is disposed when the exit ends. androidx measures the
     * content with the constraints its parent hands the container (`EnterExitTransitionModifierNode.measure`,
     * EnterExitTransition.kt:1369 under compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation),
     * so the filled height stands. Aligning with androidx turns this test red.
     */
    @Test
    fun `a filled height takes the content's own while the width shrinks away`() =
        runComposeSwingTest {
            var shown by mutableStateOf(true)
            setContent { Region(shown = shown) }

            val content = filledContent()
            assertEquals(REGION_HEIGHT, content.height, "the settled content did not take the region's height")
            assertEquals(0, content.y)

            mainClock.autoAdvance = false
            shown = false
            repeat(FRAMES_INTO_THE_EXIT) { driveOneFrame() }

            assertTrue(content.parent.width < CONTENT_WIDTH, "the container was not shrinking yet")
            assertEquals(CONTENT_HEIGHT, content.height, "the shrinking box laid the content out at another height")
            assertEquals((REGION_HEIGHT - CONTENT_HEIGHT) / 2, content.y, "the content was not centered in the box")
        }

    /**
     * Known limit: the filled content is laid out at its own height while the width shrinks away, so it is not painted
     * to the region's bottom edge, and the region's background shows there. androidx keeps the filled height, as the
     * test of a filled height taking the content's own while the width shrinks away describes. Aligning with androidx
     * turns this test red.
     */
    @Test
    fun `a filled height is not painted to the region's edge while the width shrinks away`() =
        runComposeSwingTest {
            var shown by mutableStateOf(true)
            setContent { Region(shown = shown) }

            mainClock.autoAdvance = false
            shown = false
            repeat(FRAMES_INTO_THE_EXIT) { driveOneFrame() }

            val container = filledContent().parent
            val bottomLeft = SwingUtilities.convertPoint(container, 1, REGION_HEIGHT - 1, root)
            assertEquals(
                container.parent.background.rgb,
                captureToImage().getRGB(bottomLeft.x, bottomLeft.y),
                "the region's background shows below the content",
            )
        }

    @Test
    fun `a filled width takes the container's width and leaves the other axis preferring its own`() =
        runComposeSwingTest {
            setContent { Region(shown = true, fillsWidth = true) }

            val content = filledContent()
            assertTrue(content.parent.width > CONTENT_WIDTH, "the fill test parent did not exceed preferred width")
            assertEquals(content.parent.width, content.width, "the content did not take the container's width")
            assertEquals(CONTENT_HEIGHT, content.height, "the unfilled axis stopped preferring its own height")
            val rightEdge = SwingUtilities.convertPoint(content.parent, content.parent.width - 1, 1, root)
            assertEquals(Color.RED.rgb, captureToImage().getRGB(rightEdge.x, rightEdge.y))
        }

    @Test
    fun `a scroll pane filling the height scrolls content taller than the region`() =
        runComposeSwingTest {
            setContent { Region(shown = true, scrolling = true) }

            val scrollPane = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
            assertEquals(REGION_HEIGHT, scrollPane.height, "the filled scroll pane did not take the region's height")
            assertTrue(
                scrollPane.verticalScrollBar.isVisible,
                "content taller than the filled height did not scroll",
            )
        }

    private fun ComposeSwingTest.filledContent(): Component =
        onNode(SwingMatcher.hasAccessibleName(CONTENT_NAME)).fetch()
}

/**
 * A border layout handing an animated container a west region taller than the content it holds, which is
 * the shape a side panel takes.
 *
 * @param shown whether the container is showing its content.
 * @param fillsWidth whether the content fills the container's width instead of its height.
 * @param scrolling whether the content is a scroll pane over content taller than the region.
 */
@Composable
private fun Region(
    shown: Boolean,
    fillsWidth: Boolean = false,
    scrolling: Boolean = false,
) {
    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, REGION_HEIGHT)) {
        AnimatedVisibility(
            visible = shown,
            modifier =
                if (fillsWidth) {
                    SwingModifier.center()
                } else {
                    SwingModifier.west()
                },
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            when {
                scrolling -> {
                    ScrollPane(modifier = SwingModifier.fillMaxHeight()) {
                        Viewport {
                            RedCanvas(
                                SwingModifier
                                    .preferredSize(CONTENT_WIDTH, REGION_HEIGHT * 2),
                            )
                        }
                    }
                }

                fillsWidth -> {
                    RedCanvas(SwingModifier.fillMaxWidth().preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
                }

                else -> {
                    RedCanvas(SwingModifier.fillMaxHeight().preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
                }
            }
        }
    }
}

/** A canvas painting itself over whatever bounds it is laid out at, so its extent is visible on screen. */
@Composable
private fun RedCanvas(modifier: SwingModifier) {
    Canvas(modifier = modifier.accessibleName(CONTENT_NAME)) {
        drawRect(Color.RED)
    }
}
