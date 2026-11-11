// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The state machine behind an [AnimatedVisibility]: which spec each half of a transition runs under, what
 * a transition replaced mid-flight does with what it was animating, and when the content leaves.
 */
internal class AnimatedVisibilityStateMachineTest {
    @Test
    fun `each half of a transition runs under the spec that half was given`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(100)),
                    exit = fadeOut(animationSpec = tween(1600)),
                ) {
                    Block()
                }
            }

            // Twelve frames outlast the 100 ms spec and leave the 1600 ms one most of its way to go.
            mainClock.autoAdvance = false
            visible = true
            repeat(12) { mainClock.advanceTimeByFrame() }
            assertEquals(1f, onRoot().onChild().paintedAlpha(), "the enter ran under a spec longer than it was given")

            visible = false
            repeat(12) { mainClock.advanceTimeByFrame() }
            assertEquals(1, root.componentCount)
            val alpha = onRoot().onChild().paintedAlpha()
            assertTrue(alpha < 1f, "the exit ran under a spec shorter than the one it was given: $alpha")
        }

    @Test
    fun `an exit replaced with None while it runs carries the content back to rest before dropping it`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var exit: ExitTransition by mutableStateOf(fadeOut(animationSpec = tween(320)))
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = exit) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = false
            repeat(8) { mainClock.advanceTimeByFrame() }
            val partway = onRoot().onChild().paintedAlpha()
            assertTrue(partway < 0.9f, "precondition: the fade was under way, it stood at $partway")

            exit = ExitTransition.None
            repeat(4) { mainClock.advanceTimeByFrame() }
            val returning = onRoot().onChild().paintedAlpha()
            assertTrue(
                returning in partway..0.99f,
                "the content the replaced exit had faded snapped back to rest instead of traveling there: " +
                    "$returning out of $partway",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount)
        }

    @Test
    fun `an exit changed as the content is hidden is the one that runs`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var exit: ExitTransition by mutableStateOf(scaleOut(animationSpec = tween(320)))
            setContent {
                Box {
                    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = exit) {
                        Block()
                    }
                }
            }

            mainClock.autoAdvance = false
            // Both writes reach the composition together, so the pass that starts the exit is the pass
            // that first sees it.
            exit = fadeOut(animationSpec = tween(320))
            visible = false
            repeat(8) { mainClock.advanceTimeByFrame() }

            assertTrue(
                onRoot().onChild().paintedAlpha() < 1f,
                "the exit declared as the content was hidden did not run",
            )
            assertEquals(
                40,
                onRoot().onChild().paintedWidth(),
                "the exit declared while the content was still visible ran instead of the one replacing it",
            )
        }

    @Test
    fun `content shown again after a settled exit expands out of the size it was measured at`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = expandIn(animationSpec = tween(1600)) { Dimension(it.width / 2, it.height) },
                    exit = fadeOut(animationSpec = tween(160)),
                ) {
                    Block(width = 80, height = 40)
                }
            }
            assertEquals(1, root.componentCount)

            visible = false
            awaitIdle()
            assertEquals(0, root.componentCount)

            mainClock.autoAdvance = false
            visible = true
            mainClock.advanceTimeByFrame()
            onRoot().onChild().assertProperty(
                Dimension(40, 40),
                "the expand did not start from the size its lambda names over the content's measurement",
            ) { preferredSize }
        }

    @ParameterizedTest(name = "{0} at {1}")
    @MethodSource("sizeChangesUnderFoundationParent")
    fun `a size change under a Foundation parent anchors the corner its alignment names`(
        change: ContainerSizeChange,
        anchor: Anchor,
        box: Dimension,
        content: Point,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(change.startsVisible)
        setContent {
            Column {
                org.jetbrains.compose.swing.animation.AnimatedVisibility(
                    visible = visible,
                    modifier = SwingModifier.testTag("container"),
                    enter = change.enter(anchor.alignment),
                    exit = change.exit(anchor.alignment),
                ) {
                    Block(width = 80, height = 40)
                }
            }
        }

        mainClock.autoAdvance = false
        visible = !change.startsVisible
        repeat(12) { mainClock.advanceTimeByFrame() }

        val container = onNodeWithTag("container")
        container.assertProperty(box, "the ${change.name.lowercase()} was not at the frame the rows pin") { size }
        container.onChild().assertProperty(
            content,
            "the content was anchored at a corner the ${change.name.lowercase()} does not name",
        ) { location }
    }

    @Test
    fun `an exit that changes no size under a Foundation parent keeps the anchor the enter named`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Box {
                    org.jetbrains.compose.swing.animation.AnimatedVisibility(
                        visible = visible,
                        enter = expandIn(animationSpec = tween(1600)),
                        exit = fadeOut(animationSpec = tween(1600)),
                    ) {
                        Block(width = 80, height = 40)
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(12) { mainClock.advanceTimeByFrame() }
            val panel = onRoot().onChild().onChildAt(0)
            panel.assertProperty(Dimension(2, 1), "precondition: the expand was not at the pinned frame") { size }
            panel.onChildAt(0).assertProperty(
                Point(-78, -39),
                "the expand did not anchor the trailing corner it names by default",
            ) { location }

            visible = false
            mainClock.advanceTimeByFrame()
            panel.assertProperty(Dimension(3, 1), "precondition: the exit was not at the pinned frame") { size }
            panel.onChildAt(0).assertProperty(
                Point(-77, -39),
                "the exit, which changes no size of its own, dropped the anchor the enter named",
            ) { location }
        }

    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("sizeChangesUnderStockParents")
    fun `content stands at the origin on an axis its stock parent sets and at its alignment's edge on one it follows`(
        change: ContainerSizeChange,
        parent: StockParentKind,
        box: Dimension,
        content: List<Rectangle>,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(change.startsVisible)
        setContent {
            for (anchor in Anchor.entries) {
                parent.Hosting { constraint ->
                    AnimatedVisibility(
                        visible = visible,
                        modifier = constraint.testTag("$anchor"),
                        enter = change.enter(anchor.alignment),
                        exit = change.exit(anchor.alignment),
                    ) {
                        Block(width = 80, height = 40)
                    }
                }
            }
        }

        mainClock.autoAdvance = false
        change.visibilities.forEachIndexed { step, shown ->
            visible = shown
            repeat(if (step == 0) 12 else 1) { mainClock.advanceTimeByFrame() }
        }

        Anchor.entries.forEachIndexed { index, anchor ->
            val container = onNodeWithTag("$anchor")
            container.assertProperty(box, "$anchor: the size change was not at the frame the rows pin") { size }
            container.onChild().assertProperty(
                content[index],
                "$anchor: the content was not laid out at the bounds this kind of parent gives it",
            ) { bounds }
        }
    }

    @Test
    fun `with neither an enter nor an exit the content leaves the pass after the transition settles`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    Label(text = "body")
                }
            }
            assertEquals(1, root.componentCount, "the container did not mount")

            mainClock.autoAdvance = false
            visible = false
            val counts =
                List(4) {
                    mainClock.advanceTimeByFrame()
                    root.componentCount
                }
            assertEquals(
                listOf(1, 0, 0, 0),
                counts,
                "content with nothing to animate left on a frame other than the one after the frame that " +
                    "carried the transition to its target state: $counts",
            )
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("containersAndTheirLabels")
    fun `the label names the transition the container builds`(row: LabelRow) =
        runComposeSwingTest {
            val seen = MutableList<String?>(row.expected.size) { null }
            setContent { row.build(seen) }
            row.expected.forEachIndexed { index, (label, message) -> assertEquals(label, seen[index], message) }
        }

    @Test
    fun `an enter replaced with None while it runs carries the content on to rest`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var enter: EnterTransition by mutableStateOf(fadeIn(animationSpec = tween(320)))
            setContent {
                AnimatedVisibility(visible = visible, enter = enter, exit = ExitTransition.None) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(8) { mainClock.advanceTimeByFrame() }
            val partway = onRoot().onChild().paintedAlpha()
            assertTrue(partway in 0.1f..0.9f, "precondition: the fade was under way, it stood at $partway")

            enter = EnterTransition.None
            repeat(4) { mainClock.advanceTimeByFrame() }
            val carried = onRoot().onChild().paintedAlpha()
            assertTrue(
                carried in partway..0.99f,
                "the content the replaced enter had faded snapped to full opacity instead of traveling there: " +
                    "$carried out of $partway",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1f, onRoot().onChild().paintedAlpha(), "the enter that outlived its parameter never finished")
        }

    @Test
    fun `a veil enter interrupted by a veil exit travels to the exit's color from where it stands`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = unveilIn(animationSpec = tween(640), initialColor = Color.RED),
                    exit = veilOut(animationSpec = tween(640), targetColor = Color.BLUE),
                ) {
                    // Paints nothing of its own, so a pixel read back is the scrim alone.
                    Label(text = "", style = { preferredSize(width = 40, height = 20) })
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(4) { mainClock.advanceTimeByFrame() }
            val unveiling = scrim()
            assertTrue(
                unveiling.red > 0 && unveiling.alpha in 1..254,
                "precondition: the unveil was under way, the scrim stood at $unveiling",
            )

            visible = false
            val scrims =
                List(6) {
                    mainClock.advanceTimeByFrame()
                    scrim()
                }
            assertTrue(
                scrims.first().red > unveiling.red / 2,
                "the interrupted veil restarted from the exit's own color instead of the $unveiling the " +
                    "enter had left it at: $scrims",
            )
            assertTrue(
                scrims.map { it.blue } == scrims.map { it.blue }.sorted(),
                "the scrim did not travel steadily towards the exit's color: $scrims",
            )
            assertTrue(
                scrims.last().blue > scrims.first().blue,
                "the scrim never reached for the exit's color: $scrims",
            )
            assertTrue(scrims.all { it.alpha > 0 }, "the scrim dropped out while it traveled: $scrims")
        }

    private fun ComposeSwingTest.scrim(): Color = Color(onRoot().onChild().captureToImage().getRGB(2, 2), true)

    /**
     * A size change of an [AnimatedVisibility] over content 80 by 40, begun from [startsVisible] and driven through
     * [visibilities], the first for a dozen frames and each next for one.
     */
    enum class ContainerSizeChange(
        val startsVisible: Boolean,
        val visibilities: List<Boolean>,
        val enter: (Alignment) -> EnterTransition,
        val exit: (Alignment) -> ExitTransition,
    ) {
        Expand(false, listOf(true), { expandIn(tween(1600), it) }, { ExitTransition.None }),
        Shrink(true, listOf(false), { EnterTransition.None }, { shrinkOut(tween(1600), it) }),
        ExitChangingNoSize(false, listOf(true, false), { expandIn(tween(1600), it) }, { fadeOut(tween(1600)) }),
    }

    /**
     * A stock parent, by what it does with the extent a container requests along each axis.
     */
    internal enum class StockParentKind {
        /** Assigns each container a fixed 60 by 30, whatever the size it requests: it sets both axes. */
        GridCell {
            @Composable
            override fun Hosting(content: @Composable (constraint: SwingModifier) -> Unit) =
                SwingNode(factory = { JPanel(GridLayout(0, 1)).apply { preferredSize = Dimension(60, 30) } }) {
                    content(SwingModifier)
                }
        },

        /** Sizes each container to the size it requests: it follows both axes. */
        FlowLayout {
            @Composable
            override fun Hosting(content: @Composable (constraint: SwingModifier) -> Unit) =
                SwingNode(factory = { JPanel(FlowLayout()) }) { content(SwingModifier) }
        },

        /** Sets the width of each container, wider than the content, and follows the height it requests. */
        NorthRegion {
            @Composable
            override fun Hosting(content: @Composable (constraint: SwingModifier) -> Unit) =
                SwingNode(factory = { JPanel(BorderLayout()).apply { preferredSize = Dimension(200, 300) } }) {
                    content(SwingModifier.layoutConstraint(BorderLayout.NORTH))
                }
        },
        ;

        /** Lays out the container [content] declares under this kind of parent, given the constraint it takes. */
        @Composable
        abstract fun Hosting(content: @Composable (constraint: SwingModifier) -> Unit)
    }

    private companion object {
        @JvmStatic
        fun containersAndTheirLabels(): List<LabelRow> =
            listOf(
                LabelRow(
                    "AnimatedContent",
                    listOf(
                        "AnimatedContent" to "the container named its transition something else",
                        "page" to "the declared label did not reach the transition",
                    ),
                ) { seen ->
                    AnimatedContent(targetState = "a") {
                        seen[0] = transition.parentTransition?.label
                        AnimatedContentBody(text = it, width = 40)
                    }
                    AnimatedContent(targetState = "a", label = "page") {
                        seen[1] = transition.parentTransition?.label
                        AnimatedContentBody(text = it, width = 40)
                    }
                },
                LabelRow(
                    "AnimatedVisibility",
                    listOf(
                        "AnimatedVisibility" to "the container named its transition something else",
                        "drawer" to "the declared label did not reach the transition",
                        "sheet" to "the declared label did not reach the transition built over a state",
                    ),
                ) { seen ->
                    AnimatedVisibility(visible = true) { seen[0] = transition.parentTransition?.label }
                    AnimatedVisibility(visible = true, label = "drawer") {
                        seen[1] = transition.parentTransition?.label
                    }
                    AnimatedVisibility(visibleState = remember { MutableTransitionState(true) }, label = "sheet") {
                        seen[2] = transition.parentTransition?.label
                    }
                },
            )

        @JvmStatic
        fun sizeChangesUnderFoundationParent(): List<Arguments> =
            listOf(
                arguments(ContainerSizeChange.Expand, Anchor.TopLeading, Dimension(2, 1), Point(0, 0)),
                arguments(ContainerSizeChange.Expand, Anchor.BottomTrailing, Dimension(2, 1), Point(-78, -39)),
                arguments(ContainerSizeChange.Expand, Anchor.BottomLeading, Dimension(2, 1), Point(0, -39)),
                arguments(ContainerSizeChange.Shrink, Anchor.TopLeading, Dimension(78, 39), Point(0, 0)),
                arguments(ContainerSizeChange.Shrink, Anchor.BottomTrailing, Dimension(78, 39), Point(-2, -1)),
                arguments(ContainerSizeChange.Shrink, Anchor.BottomLeading, Dimension(78, 39), Point(0, -1)),
            )

        @JvmStatic
        fun sizeChangesUnderStockParents(): List<Arguments> =
            listOf(
                arguments(
                    ContainerSizeChange.Expand,
                    StockParentKind.GridCell,
                    Dimension(60, 30),
                    listOf(Rectangle(0, 0, 60, 30), Rectangle(0, 0, 60, 30), Rectangle(0, 0, 60, 30)),
                ),
                arguments(
                    ContainerSizeChange.Expand,
                    StockParentKind.FlowLayout,
                    Dimension(2, 1),
                    listOf(Rectangle(0, 0, 80, 40), Rectangle(-78, -39, 80, 40), Rectangle(0, -39, 80, 40)),
                ),
                arguments(
                    ContainerSizeChange.Expand,
                    StockParentKind.NorthRegion,
                    Dimension(200, 1),
                    listOf(Rectangle(0, 0, 80, 40), Rectangle(0, -39, 80, 40), Rectangle(0, -39, 80, 40)),
                ),
                arguments(
                    ContainerSizeChange.Shrink,
                    StockParentKind.GridCell,
                    Dimension(60, 30),
                    listOf(Rectangle(0, 0, 60, 30), Rectangle(0, 0, 60, 30), Rectangle(0, 0, 60, 30)),
                ),
                arguments(
                    ContainerSizeChange.Shrink,
                    StockParentKind.FlowLayout,
                    Dimension(78, 39),
                    listOf(Rectangle(0, 0, 80, 40), Rectangle(-2, -1, 80, 40), Rectangle(0, -1, 80, 40)),
                ),
                arguments(
                    ContainerSizeChange.Shrink,
                    StockParentKind.NorthRegion,
                    Dimension(200, 39),
                    listOf(Rectangle(0, 0, 80, 40), Rectangle(0, -1, 80, 40), Rectangle(0, -1, 80, 40)),
                ),
                arguments(
                    ContainerSizeChange.ExitChangingNoSize,
                    StockParentKind.GridCell,
                    Dimension(60, 30),
                    listOf(Rectangle(0, 0, 60, 30), Rectangle(0, 0, 60, 30), Rectangle(0, 0, 60, 30)),
                ),
                arguments(
                    ContainerSizeChange.ExitChangingNoSize,
                    StockParentKind.FlowLayout,
                    Dimension(3, 1),
                    listOf(Rectangle(0, 0, 80, 40), Rectangle(-77, -39, 80, 40), Rectangle(0, -39, 80, 40)),
                ),
                arguments(
                    ContainerSizeChange.ExitChangingNoSize,
                    StockParentKind.NorthRegion,
                    Dimension(200, 1),
                    listOf(Rectangle(0, 0, 80, 40), Rectangle(0, -39, 80, 40), Rectangle(0, -39, 80, 40)),
                ),
            )
    }
}

/** The leading corner, the trailing one, and a pair naming a different edge on each axis. */
internal enum class Anchor(
    val alignment: Alignment,
) {
    TopLeading(Alignment.TopStart),
    BottomTrailing(Alignment.BottomEnd),
    BottomLeading(Alignment.BottomStart),
}

/** A container, the label it and its transition are expected to carry in each declaration, and how it is declared. */
class LabelRow(
    private val component: String,
    val expected: List<Pair<String, String>>,
    val build: @Composable (seen: MutableList<String?>) -> Unit,
) {
    override fun toString(): String = component
}
