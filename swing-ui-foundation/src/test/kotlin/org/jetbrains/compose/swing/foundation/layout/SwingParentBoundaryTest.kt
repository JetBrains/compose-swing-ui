package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.renderImage
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Where Foundation meets a parent that is not a Foundation container: the component has no paint outsets there, that
 * parent reads its own sizes, and its allotment is final and clips what the component paints past it.
 */
class SwingParentBoundaryTest {
    @Test
    fun aStockParentsAllotmentIsFinalAndClipsWhatChildrenPaintBeyondIt() =
        runComposeSwingTest {
            var scale by mutableFloatStateOf(1.5f)
            var measures = 0
            setContent {
                SwingNode(
                    factory = { stockPanel() },
                    modifier = SwingModifier.testTag("stock").preferredSize(Dimension(160, 120)),
                ) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(64, 64)
                                .shadow(4, Color.BLACK),
                    ) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("child")
                                    .size(64, 64)
                                    .countingMeasures { measures++ }
                                    .placementLayer { scaleX = scale }
                                    .shadow(6, Color.BLACK)
                                    .background(Brush.of(Color.BLUE)),
                        )
                    }
                }
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            val child = onNodeWithTag("child").fetch<JComponent>()
            val allotted = Rectangle(0, 0, 64, 64)

            assertEquals(allotted.size, panel.preferredSize, "the panel's shadow adds nothing to its size")
            assertEquals(allotted, panel.bounds, "the stock parent's allotment is the panel's bounds")
            assertEquals(allotted, panel.layoutBounds, "the panel's layout bounds are its bounds")
            val image = onNodeWithTag("stock").captureToImage()
            val painted =
                assertNotNull(
                    differingPixelBounds(renderImage(image.width, image.height) {}, image),
                    "the panel paints",
                )
            assertEquals(allotted, allotted.union(painted), "nothing paints outside the allotment")
            assertEquals(Color.BLUE.rgb, image.getRGB(0, allotted.height / 2), "the overflow paints up to it")

            measures = 0
            panel.revalidate()
            awaitIdle()
            assertEquals(1, measures, "one layout pass measures the node once")

            withRecordedRepaints { recorder ->
                scale = 1.25f
                awaitIdle()
                assertEquals(0, recorder.relayoutsOver(child), "a layer change lays nothing out again")
                assertEquals(allotted, panel.bounds, "a layer change keeps the allotment")
            }
        }

    @Test
    fun aChildsOverflowGrowsTheFoundationParentsBounds() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("panel").size(64, 64)) {
                        Box(modifier = SwingModifier.size(64, 64).shadow(6, Color.BLACK))
                    }
                }
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            val outsets = blurOutsets(6)

            assertEquals(Rectangle(0, 0, 64, 64), panel.layoutBounds, "the panel's layout bounds are its own size")
            assertEquals(
                Dimension(64 + 2 * outsets, 64 + 2 * outsets),
                panel.size,
                "the child's shadow grows the panel's bounds",
            )
        }

    @Test
    fun aChildMovedToAStockParentAndBackKeepsItsBoundsAndLeavesItsOldParentAlone() =
        runComposeSwingTest {
            var inFoundation by mutableStateOf(true)
            var scale by mutableFloatStateOf(1f)
            setContent {
                val moving =
                    remember {
                        movableContentOf {
                            Box(
                                modifier =
                                    SwingModifier
                                        .testTag("child")
                                        .preferredSize(64, 64)
                                        .shadow(4, Color.BLACK),
                            ) {
                                Box(
                                    modifier =
                                        SwingModifier
                                            .size(64, 64)
                                            .placementLayer { scaleX = scale },
                                )
                            }
                        }
                    }
                Row {
                    Box(modifier = SwingModifier.testTag("panel").size(160, 120)) {
                        if (inFoundation) moving()
                    }
                    SwingNode(
                        factory = { stockPanel() },
                        modifier = SwingModifier.preferredSize(Dimension(160, 120)),
                    ) {
                        if (!inFoundation) moving()
                    }
                }
            }
            val child = onNodeWithTag("child").fetch<JComponent>()
            val placedByFoundation = Rectangle(0, 0, 64, 64)
            assertEquals(placedByFoundation, child.layoutBounds, "placed at its layout size by a Foundation parent")
            val outsets = blurOutsets(4)
            assertEquals(Dimension(64 + 2 * outsets, 64 + 2 * outsets), child.size, "grown by the shadow's outsets")

            inFoundation = false
            awaitIdle()
            val allotted = Rectangle(0, 0, 64, 64)
            assertEquals(allotted, child.bounds, "a stock parent's allotment is the child's bounds")
            assertEquals(allotted, child.layoutBounds, "the child has no paint outsets in a stock parent")

            scale = 1.5f
            awaitIdle()
            assertEquals(
                allotted,
                child.bounds,
                "a layer change neither grows the child past the allotment nor places it against its old parent",
            )

            scale = 1f
            inFoundation = true
            awaitIdle()
            assertEquals(placedByFoundation, child.layoutBounds, "placed at its layout size again")
            assertEquals(Dimension(64 + 2 * outsets, 64 + 2 * outsets), child.size, "grown by the outsets again")
        }

    @Test
    fun aDecoratedPanelLeavingABoxInATreeThatIsNotDisplayableLaysItsChildOutInsideTheBorder() =
        runComposeSwingTest {
            var inFoundation by mutableStateOf(true)
            setContent {
                val moving =
                    remember {
                        movableContentOf {
                            SwingNode(
                                factory = { DecoratedPanel(BorderLayout()) },
                                modifier =
                                    SwingModifier.testTag("card").emptyBorder(2).shadow(8, Color.BLACK),
                            ) {
                                SwingNode(
                                    factory = { JPanel() },
                                    modifier = SwingModifier.testTag("child").preferredSize(40, 30),
                                )
                            }
                        }
                    }
                Row(modifier = SwingModifier.testTag("row")) {
                    Box {
                        if (inFoundation) moving()
                    }
                    SwingNode(factory = { stockPanel() }, modifier = SwingModifier.testTag("stock")) {
                        if (!inFoundation) moving()
                    }
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val child = onNodeWithTag("child").fetch<JComponent>()
            val outsets = blurOutsets(8)
            assertEquals(Rectangle(outsets + 2, outsets + 2, 40, 30), child.bounds, "inside the border and outsets")
            val row = onNodeWithTag("row").fetch<JComponent>()
            row.removeNotify()
            try {
                inFoundation = false
                awaitIdle()
                assertFalse(card.isDisplayable, "the card now sits in a tree that is not displayable")

                assertEquals(Dimension(44, 34), card.preferredSize, "the card answers its size without the old outsets")
                onNodeWithTag("stock").fetch<JComponent>().validate()
                assertEquals(Rectangle(2, 2, 40, 30), child.bounds, "and lays its child out inside the border alone")
            } finally {
                row.addNotify()
            }
        }

    @Test
    fun aDecoratedPanelMovedOutOfABoxWithoutLeavingTheTreeLaysItsChildOutInsideTheBorder() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box {
                        SwingNode(
                            factory = { DecoratedPanel(BorderLayout()) },
                            modifier =
                                SwingModifier.testTag("card").emptyBorder(2).shadow(8, Color.BLACK),
                        ) {
                            SwingNode(
                                factory = { JPanel() },
                                modifier = SwingModifier.testTag("child").preferredSize(40, 30),
                            )
                        }
                    }
                    SwingNode(factory = { stockPanel() }, modifier = SwingModifier.testTag("stock"))
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val child = onNodeWithTag("child").fetch<JComponent>()
            val stock = onNodeWithTag("stock").fetch<JComponent>()
            val outsets = blurOutsets(8)
            assertEquals(Rectangle(outsets + 2, outsets + 2, 40, 30), child.bounds, "inside the border and outsets")
            assertEquals(Dimension(44 + 2 * outsets, 34 + 2 * outsets), card.preferredSize, "child, border and outsets")

            stock.setComponentZOrder(card, 0)
            assertTrue(card.isDisplayable, "the move keeps the card's peer")
            stock.validate()

            assertEquals(Dimension(44, 34), card.preferredSize, "the card answers its size without the old outsets")
            assertEquals(Rectangle(2, 2, 40, 30), child.bounds, "and lays its child out inside the border alone")
        }

    @Test
    fun aContainerUnderAStockParentAsksItsHeightAtTheWidthItAsksFor() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    Box(modifier = SwingModifier.testTag("box")) {
                        Label("Preview", modifier = SwingModifier.testTag("label").aspectRatio(RATIO))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            val label = onNodeWithTag("label").fetch<JComponent>()
            val atRatio = atRatio(label.preferredSize.width)

            assertEquals(
                atRatio,
                box.preferredSize,
                "the preferred height is the ratio's height at the preferred width",
            )
            assertEquals(
                atRatio(label.minimumSize.width),
                box.minimumSize,
                "the minimum height is the ratio's height at the minimum width",
            )
            assertEquals(atRatio, box.size, "the stock parent grants what the container asks for")
            assertEquals(Rectangle(Point(), atRatio), label.bounds, "the label keeps its full width at the ratio")
        }

    @Test
    fun aContainerUnderAStockParentWhoseHeightDoesNotFollowItsWidthAsksForItsContentsSizes() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    Column(modifier = SwingModifier.testTag("column")) {
                        Label("First", modifier = SwingModifier.testTag("first"))
                        Row(modifier = SwingModifier.testTag("row")) {
                            Label("Second", modifier = SwingModifier.testTag("second"))
                            Label("Third, longer", modifier = SwingModifier.testTag("third"))
                        }
                    }
                }
            }
            val first = onNodeWithTag("first").fetch<JComponent>().preferredSize
            val second = onNodeWithTag("second").fetch<JComponent>().preferredSize
            val third = onNodeWithTag("third").fetch<JComponent>().preferredSize
            val row = Dimension(second.width + third.width, maxOf(second.height, third.height))

            assertEquals(row, onNodeWithTag("row").fetch<JComponent>().size, "the row is its children side by side")
            assertEquals(
                Dimension(maxOf(first.width, row.width), first.height + row.height),
                onNodeWithTag("column").fetch<JComponent>().size,
                "the column is its children stacked",
            )
        }

    private companion object {
        const val RATIO = 16f / 9f

        fun atRatio(width: Int): Dimension = Dimension(width, (width / RATIO).roundToInt())

        fun stockPanel(): JPanel = JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false }
    }
}
