// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FILLED_EXTENT = 300
private const val CONTENT_WIDTH = 120
private const val CONTENT_HEIGHT = 40
private const val SCROLL_PANE_WIDTH = 180
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
        setContent { case.content(shown) }

        val filled = case.filled(this)
        assertEquals(0 until FILLED_EXTENT, case.axis.span(filled), "the settled content did not take the extent")

        mainClock.autoAdvance = false
        shown = false
        // Enough frames for the transition to be under way, and few enough that it has not finished.
        repeat(3) { mainClock.advanceTimeByFrame() }

        assertTrue(case.underway(filled), "the transition was not under way yet")
        assertEquals(0 until FILLED_EXTENT, case.axis.span(filled), "the shrink took the filled extent with it")
        assertEquals(case.bounds(filled), filled.bounds, "the filled node's bounds in the container")
        val (container, edge) = case.edge(filled)
        val onScreen = SwingUtilities.convertPoint(container, edge, root)
        captureToImage().assertPixelColor(
            Color.RED,
            onScreen.x,
            onScreen.y,
            "the shrinking container clipped the filled content short of its edge",
        )
    }

    @Test
    fun `a filled width takes the container's width and leaves the other axis preferring its own`() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), style = { preferredSize(400, FILLED_EXTENT) }) {
                    AnimatedVisibility(
                        visible = true,
                        modifier = SwingModifier.center(),
                        enter = expandHorizontally(),
                        exit = shrinkHorizontally(),
                    ) {
                        RedCanvas(
                            SwingModifier.fillMaxWidth(),
                            style = { preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT) },
                        )
                    }
                }
            }

            val canvas = onNodeWithTag(CANVAS).fetch()
            assertTrue(canvas.parent.width > CONTENT_WIDTH, "the container was not wider than the content prefers")
            onNodeWithTag(CANVAS)
                .assertProperty(canvas.parent.width, "the content did not take the container's width") { width }
                .assertProperty(CONTENT_HEIGHT, "the unfilled axis stopped preferring its own height") { height }
            val rightEdge = SwingUtilities.convertPoint(canvas.parent, canvas.parent.width - 1, 1, root)
            captureToImage().assertPixelColor(
                Color.RED,
                rightEdge.x,
                rightEdge.y,
                "the filled width was not painted to the container's right edge",
            )
        }

    @Test
    fun `a scroll pane filling the height scrolls content taller than the region`() =
        runComposeSwingTest {
            setContent { Region(shown = true) { TallScrollPane() } }

            onNodeOfType<JScrollPane>()
                .assertProperty(FILLED_EXTENT, "the filled scroll pane did not take the region's height") { height }
                .assertProperty(true, "content taller than the filled height did not scroll") {
                    verticalScrollBar.isVisible
                }
        }

    /** The axis that a case fills. */
    enum class FilledAxis(
        val span: (Component) -> IntRange,
    ) {
        Height({ it.y until it.y + it.height }),
        Width({ it.x until it.x + it.width }),
    }

    /**
     * A case whose content fills one axis of its animated container.
     *
     * @property content the container and its content, shrunk by setting `shown` to false.
     * @property filled finds the node that fills the axis.
     * @property axis the axis the node fills.
     * @property underway whether the transition has begun to take the container's room from the filled node.
     * @property edge the point where the filled node ends, and the component whose coordinates it is in.
     * @property bounds where the filled node stands in its container while the transition is under way.
     */
    enum class FillCase(
        val content: @Composable (shown: Boolean) -> Unit,
        val filled: ComposeSwingTest.() -> Component,
        val axis: FilledAxis,
        val underway: (Component) -> Boolean,
        val edge: (Component) -> Pair<Component, Point>,
        val bounds: (Component) -> Rectangle,
    ) {
        FilledHeight(
            content = { shown ->
                Region(shown) {
                    RedCanvas(SwingModifier.fillMaxHeight(), style = { preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT) })
                }
            },
            filled = { onNodeWithTag(CANVAS).fetch<Component>() },
            axis = FilledAxis.Height,
            underway = { it.parent.width < CONTENT_WIDTH },
            edge = { it.parent to Point(1, FILLED_EXTENT - 1) },
            bounds = { Rectangle(it.parent.width - CONTENT_WIDTH, 0, CONTENT_WIDTH, FILLED_EXTENT) },
        ),
        ScrollPaneFillingHeight(
            content = { shown -> Region(shown) { TallScrollPane() } },
            filled = { onNodeOfType<JScrollPane>().fetch() },
            axis = FilledAxis.Height,
            underway = { it.parent.width < SCROLL_PANE_WIDTH },
            edge = {
                val viewport = (it as JScrollPane).viewport
                // Shrinking horizontally slides the left columns out of the clip, so probe the last column of the
                // viewport that the container still shows.
                val lastShown = SwingUtilities.convertPoint(it.parent, it.parent.width - 1, 0, viewport).x
                viewport to Point(minOf(lastShown, viewport.width - 1), viewport.height - 1)
            },
            bounds = { Rectangle(it.parent.width - SCROLL_PANE_WIDTH, 0, SCROLL_PANE_WIDTH, FILLED_EXTENT) },
        ),
        FilledWidthOfNorthRegion(
            content = { shown ->
                Panel(PanelLayout.Border(), style = { preferredSize(FILLED_EXTENT, 400) }) {
                    AnimatedVisibility(
                        visible = shown,
                        modifier = SwingModifier.north(),
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        RedCanvas(
                            SwingModifier.fillMaxWidth(),
                            style = { preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT) },
                        )
                    }
                }
            },
            filled = { onNodeWithTag(CANVAS).fetch<Component>() },
            axis = FilledAxis.Width,
            underway = { it.parent.height < CONTENT_HEIGHT },
            edge = { it.parent to Point(it.parent.width - 1, 1) },
            bounds = { Rectangle(0, it.parent.height - CONTENT_HEIGHT, FILLED_EXTENT, CONTENT_HEIGHT) },
        ),
        AnimatedContentFillingHeight(
            content = { shown ->
                Panel(PanelLayout.Border(), style = { preferredSize(400, FILLED_EXTENT) }) {
                    AnimatedContent(
                        targetState = shown,
                        modifier = SwingModifier.west(),
                        transitionSpec = { fadeIn() togetherWith shrinkHorizontally() },
                    ) { target ->
                        RedCanvas(
                            SwingModifier.fillMaxHeight(),
                            style = {
                                accessibleName(target.toString())
                                preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT)
                            },
                        )
                    }
                }
            },
            filled = { onNodeWithTag(CANVAS).fetch<Component>() },
            axis = FilledAxis.Height,
            underway = { it.parent.x < 0 },
            edge = { it.parent to Point(it.parent.width - 1, FILLED_EXTENT - 1) },
            bounds = { Rectangle(0, 0, CONTENT_WIDTH, FILLED_EXTENT) },
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
    Panel(PanelLayout.Border(), style = { preferredSize(400, FILLED_EXTENT) }) {
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
    ScrollPane(modifier = SwingModifier.fillMaxHeight(), style = { preferredSize(SCROLL_PANE_WIDTH, 0) }) {
        Viewport { RedCanvas(style = { preferredSize(CONTENT_WIDTH, 2 * FILLED_EXTENT) }) }
    }
}

/** A canvas painting itself over whatever bounds it is laid out at, so its extent is visible on screen. */
@Composable
private fun RedCanvas(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ComponentStyleScope> = SwingStyle,
) {
    Canvas(modifier = modifier.testTag(CANVAS).styleable(style)) {
        drawRect(Color.RED)
    }
}
