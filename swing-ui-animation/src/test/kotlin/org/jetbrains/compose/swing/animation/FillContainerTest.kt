package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Color
import java.awt.Component
import java.awt.Point
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FILLED_EXTENT = 300
private const val CONTENT_WIDTH = 120
private const val CONTENT_HEIGHT = 40
private const val CANVAS = "filled"

/**
 * Content that takes the animated container's own extent along one axis rather than the one it prefers: the side
 * panel case, where the region the container sits in hands it an extent the content has no way to ask for.
 *
 * The container is measured over what the content prefers either way, so a shrink still animates the extent the
 * content declares. What these pin is that the filled axis stands still while that happens, in the layout and in what
 * reaches the screen.
 */
class FillContainerTest {
    @ParameterizedTest(name = "{0}")
    @EnumSource(FillCase::class)
    fun `a filled axis stands at its settled extent and paints to its edge while the container shrinks`(
        case: FillCase,
    ) = runComposeSwingTest {
        var shown by mutableStateOf(true)
        setContent { case.scene(shown) }

        val filled = case.filled(this)
        assertEquals(0 until FILLED_EXTENT, case.axis.span(filled), "the settled content did not take the extent")

        mainClock.autoAdvance = false
        shown = false
        // Enough frames for the transition to be under way, and few enough that it has not finished.
        repeat(3) { driveOneFrame() }

        assertTrue(case.underway(filled), "the transition was not under way yet")
        assertEquals(0 until FILLED_EXTENT, case.axis.span(filled), "the shrink took the filled extent with it")
        val (container, edge) = case.edge(filled)
        val onScreen = SwingUtilities.convertPoint(container, edge, root)
        assertEquals(
            Color.RED.rgb,
            captureToImage().getRGB(onScreen.x, onScreen.y),
            "the shrinking container clipped the filled content short of its edge",
        )
    }

    @Test
    fun `a filled width takes the container's width and leaves the other axis preferring its own`() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, FILLED_EXTENT)) {
                    AnimatedVisibility(
                        visible = true,
                        modifier = SwingModifier.center(),
                        enter = expandHorizontally(),
                        exit = shrinkHorizontally(),
                    ) {
                        RedCanvas(SwingModifier.fillMaxWidth().preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
                    }
                }
            }

            val content = filledCanvas()
            assertTrue(content.parent.width > CONTENT_WIDTH, "the container was not wider than the content prefers")
            assertEquals(content.parent.width, content.width, "the content did not take the container's width")
            assertEquals(CONTENT_HEIGHT, content.height, "the unfilled axis stopped preferring its own height")
            val rightEdge = SwingUtilities.convertPoint(content.parent, content.parent.width - 1, 1, root)
            assertEquals(
                Color.RED.rgb,
                captureToImage().getRGB(rightEdge.x, rightEdge.y),
                "the filled width was not painted to the container's right edge",
            )
        }

    @Test
    fun `a scroll pane filling the height scrolls content taller than the region`() =
        runComposeSwingTest {
            setContent { Region(shown = true) { TallScrollPane() } }

            val scrollPane = onNodeOfType<JScrollPane>().fetch()
            assertEquals(FILLED_EXTENT, scrollPane.height, "the filled scroll pane did not take the region's height")
            assertTrue(scrollPane.verticalScrollBar.isVisible, "content taller than the filled height did not scroll")
        }

    /** The axis a scene fills. */
    enum class FilledAxis(
        val span: (Component) -> IntRange,
    ) {
        Height({ it.y until it.y + it.height }),
        Width({ it.x until it.x + it.width }),
    }

    /**
     * A scene whose content fills one axis of its animated container.
     *
     * @property scene the container and its content, shrunk by setting `shown` to false.
     * @property filled finds the node that fills the axis.
     * @property axis the axis the node fills.
     * @property underway whether the transition has begun to take the container's room from the filled node.
     * @property edge the point where the filled node ends, and the component whose coordinates it is in.
     */
    enum class FillCase(
        val scene: @Composable (shown: Boolean) -> Unit,
        val filled: ComposeSwingTest.() -> Component,
        val axis: FilledAxis,
        val underway: (Component) -> Boolean,
        val edge: (Component) -> Pair<Component, Point>,
    ) {
        FilledHeight(
            scene = { shown ->
                Region(shown) {
                    RedCanvas(SwingModifier.fillMaxHeight().preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
                }
            },
            filled = { filledCanvas() },
            axis = FilledAxis.Height,
            underway = { it.parent.width < CONTENT_WIDTH },
            edge = { it.parent to Point(1, FILLED_EXTENT - 1) },
        ),
        ScrollPaneFillingHeight(
            scene = { shown -> Region(shown) { TallScrollPane() } },
            filled = { onNodeOfType<JScrollPane>().fetch() },
            axis = FilledAxis.Height,
            underway = { it.parent.width < CONTENT_WIDTH },
            edge = {
                val viewport = (it as JScrollPane).viewport
                viewport to Point(1, viewport.height - 1)
            },
        ),
        FilledWidthOfNorthRegion(
            scene = { shown ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(FILLED_EXTENT, 400)) {
                    AnimatedVisibility(
                        visible = shown,
                        modifier = SwingModifier.north(),
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        RedCanvas(SwingModifier.fillMaxWidth().preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
                    }
                }
            },
            filled = { filledCanvas() },
            axis = FilledAxis.Width,
            underway = { it.parent.height < CONTENT_HEIGHT },
            edge = { it.parent to Point(it.parent.width - 1, 1) },
        ),
        AnimatedContentFillingHeight(
            scene = { shown ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, FILLED_EXTENT)) {
                    AnimatedContent(
                        targetState = shown,
                        modifier = SwingModifier.west(),
                        transitionSpec = { fadeIn() togetherWith shrinkHorizontally() },
                    ) { target ->
                        RedCanvas(
                            SwingModifier
                                .accessibleName(target.toString())
                                .fillMaxHeight()
                                .preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT),
                        )
                    }
                }
            },
            filled = { filledCanvas() },
            axis = FilledAxis.Height,
            underway = { it.parent.x < 0 },
            edge = { it.parent to Point(it.parent.width - 1, FILLED_EXTENT - 1) },
        ),
    }
}

/**
 * A border layout handing an animated container a west region taller than the content it holds, which is the shape a
 * side panel takes.
 */
@Composable
private fun Region(
    shown: Boolean,
    content: @Composable ConstrainedScope.() -> Unit,
) {
    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, FILLED_EXTENT)) {
        AnimatedVisibility(
            visible = shown,
            modifier = SwingModifier.west(),
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            content()
        }
    }
}

/** A scroll pane that fills the height of the container it is in over content taller than that. */
@Composable
private fun ConstrainedScope.TallScrollPane() {
    ScrollPane(modifier = SwingModifier.fillMaxHeight()) {
        Viewport { RedCanvas(SwingModifier.preferredSize(CONTENT_WIDTH, 2 * FILLED_EXTENT)) }
    }
}

/** A canvas painting itself over whatever bounds it is laid out at, so its extent is visible on screen. */
@Composable
private fun RedCanvas(modifier: SwingModifier) {
    Canvas(modifier = modifier.testTag(CANVAS)) {
        drawRect(Color.RED)
    }
}

private fun ComposeSwingTest.filledCanvas(): Component = onNodeWithTag(CANVAS).fetch()
