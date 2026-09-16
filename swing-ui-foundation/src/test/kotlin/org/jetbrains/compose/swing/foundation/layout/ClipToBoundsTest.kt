package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for `clipToBounds()`: it narrows a child's own paint to the box the
 * modifiers after it on the chain report for it, even where a modifier further along - a size transition
 * mid-flight, standing in here for [ReportedSizeElement] - places something larger inside that box.
 */
class ClipToBoundsTest {
    @Test
    fun clipToBoundsReportsItselfUnderItsOwnName() {
        val declared = with(BoxScopeInstance) { SwingModifier.clipToBounds() }

        assertEquals("clipToBounds", declared.lastElement().name, "clipToBounds must report itself under its own name")
        assertEquals(emptyMap(), declared.lastElement().declaredValues, "and must report no declared value")
    }

    @Test
    fun theClippedChildsRealContentDoesNotBleedBeyondTheBoxItsChainReports() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag("row").preferredSize(Dimension(200, 200))) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("clipped")
                                .clipToBounds()
                                .reportedSize(20, 20)
                                .preferredSize(Dimension(80, 80))
                                .background(brush = { _, _ -> Color.RED }),
                    )
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("sibling")
                                .preferredSize(Dimension(30, 30))
                                .background(brush = { _, _ -> Color.BLUE }),
                    )
                }
            }

            val painted = onNodeWithTag("clipped").captureToImage()
            val clip = Rectangle(0, 0, 20, 20)
            check(clip.x + clip.width + 5 < painted.width) {
                "the sampled overflow pixel must still sit inside the clipped child's own real bounds"
            }

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(clip.x + clip.width / 2, clip.y + clip.height / 2),
                "inside the box the chain reports, the real content still paints as normal",
            )
            assertEquals(
                0,
                painted.getRGB(clip.x + clip.width + 5, clip.y + clip.height / 2) ushr 24,
                "beyond the box the chain reports, the clip must stop this child's own paint from reaching it - " +
                    "and so from ever reaching a sibling placed against that same reported box",
            )
        }

    @Test
    fun aClippedBoxBecomesThePaintingOriginSoADescendantsOwnRepaintStaysClipped() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag("row").preferredSize(Dimension(200, 200))) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("clipped")
                                .clipToBounds()
                                .reportedSize(20, 20)
                                .preferredSize(Dimension(80, 80)),
                    )
                    Box(modifier = SwingModifier.testTag("sibling").preferredSize(Dimension(30, 30)))
                }
            }

            val clipped = onNodeWithTag("clipped").fetch<ConstrainedPanel>()
            val sibling = onNodeWithTag("sibling").fetch<ConstrainedPanel>()

            assertTrue(
                clipped.isPaintingOrigin(),
                "a clipped box must become the painting origin, or Swing would route a descendant's own " +
                    "repaint - a hover, a caret - around it and paint the overflow unclipped",
            )
            assertFalse(
                sibling.isPaintingOrigin(),
                "an ordinary box carries no clip, so nothing routes a descendant's repaint through it",
            )
        }

    @Test
    fun aClippedChildAnswersABaselineQuery() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(CHILD_WIDTH * 2, CHILD_HEIGHT * 2)) {
                    DecoratedBaselineChild(10, SwingModifier.alignByBaseline().clipToBounds())
                    DecoratedBaselineChild(30, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                listOf(20, 0),
                childBounds().map { it.y },
                "a clipped child reports its content's baseline",
            )
        }

    @Test
    fun nestedClipsIntersect() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag("row").preferredSize(Dimension(200, 200))) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("clipped")
                                .clipToBounds()
                                .reportedSize(20, 80)
                                .preferredSize(Dimension(80, 80)),
                    ) {
                        Box(
                            modifier =
                                SwingModifier
                                    .clipToBounds()
                                    .reportedSize(80, 20)
                                    .preferredSize(Dimension(80, 80))
                                    .background(brush = { _, _ -> Color.RED }),
                        )
                    }
                }
            }

            val painted = onNodeWithTag("clipped").captureToImage()

            assertEquals(Color.RED.rgb, painted.getRGB(10, 10), "inside both clips")
            assertEquals(0, painted.getRGB(25, 10), "inside the inner clip only")
            assertEquals(0, painted.getRGB(10, 25), "inside the outer clip only")
        }

    /** A child repainting on its own is painted from its clipped container, inside the clip. */
    @Test
    fun aChildRepaintingOnItsOwnStaysInsideItsContainersClip() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val child = ClipRecordingChild()
            setWindowContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER_TAG)
                                .clipToBounds()
                                .reportedSize(20, 20)
                                .preferredSize(Dimension(80, 80)),
                    ) {
                        SwingNode(factory = { child }, modifier = SwingModifier.preferredSize(Dimension(80, 80)))
                    }
                }
            }
            child.clips.clear()

            child.paintImmediately(0, 0, child.width, child.height)

            val clip = checkNotNull(child.clips.lastOrNull()) { "the child must have painted" }
            assertTrue(
                Rectangle(0, 0, 20, 20).contains(clip),
                "the child's own repaint must stay inside its container's clip, but painted within $clip",
            )
        }

    /** Composing a stock widget with the clip succeeds; laying it out refuses the target. */
    @Test
    fun aStockWidgetTargetIsRefusedAtTheFirstPlacement() =
        runComposeSwingTest {
            val host = JPanel()
            val recomposer = SwingRecomposer.create(host)
            val composition =
                host.setContent(parent = recomposer.compositionContext) {
                    Row { SwingNode(factory = { JPanel() }, modifier = SwingModifier.clipToBounds()) }
                }
            try {
                val row = host.components.single() as Container

                val failure = assertFailsWith<IllegalStateException> { row.doLayout() }

                assertTrue(
                    failure.message.orEmpty().contains("JPanel") && failure.message.orEmpty().contains("Decoratable"),
                    "the error names the component it was handed and the type it needs: ${failure.message}",
                )
            } finally {
                composition.dispose()
                recomposer.dispose()
            }
        }
}

/**
 * Reports a fixed ([width], [height]) apparent extent to whatever measures this modifier, while still
 * measuring and placing its wrapped content at that content's own real size - standing in for a size
 * transition mid-flight without needing the animation layer this module lands ahead of.
 */
private data class ReportedSizeElement(
    val width: Int,
    val height: Int,
) : LayoutModifierNodeElement<ReportedSizeNode>() {
    override fun create(): ReportedSizeNode = ReportedSizeNode(width, height)

    override fun update(node: ReportedSizeNode) {
        node.width = width
        node.height = height
    }
}

private class ReportedSizeNode(
    var width: Int,
    var height: Int,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(width, height) { placeable.place(0, 0) }
    }
}

private fun SwingModifier.reportedSize(
    width: Int,
    height: Int,
): SwingModifier = this then ReportedSizeElement(width, height)

/** Records the clip each of its paints ran under. */
private class ClipRecordingChild : JComponent() {
    val clips = ArrayList<Rectangle>()

    override fun paintComponent(g: Graphics) {
        clips += g.clipBounds
    }
}
