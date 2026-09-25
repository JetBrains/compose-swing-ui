package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Under a Foundation parent a `paintOutsets` value counts the component's border insets and the outsets its decoration
 * steps take, such as a shadow's reach. What the component paints past its layout bounds for another reason takes no
 * layout space under any value: a step painted at a layout modifier's box, a rotating layer, a child placed or painting
 * past the edge. A layout node that changes those outsets during a pass has its container measured again after it,
 * and a change made while the container awaits validation is laid out by that validation.
 */
class PaintOutsetsStepOutsetsTest {
    @Test
    fun aStepBoxedToAPaddingPaintsOverThePaddingWithNothingDeclared() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(FIRST)
                                .background(Red)
                                .padding(8)
                                .size(40, 40),
                    )
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            assertFirstAt(Rectangle(8, 8, 40, 40), Insets(8, 8, 8, 8), next = 56)
        }

    @Test
    fun noneDeclaredBeforeAStepBoxedToAPaddingReservesThePaddingOnce() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(FIRST)
                                .paintOutsets(PaintOutsets.None)
                                .background(Red)
                                .padding(8)
                                .size(40, 40),
                    )
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            assertFirstAt(Rectangle(8, 8, 40, 40), Insets(8, 8, 8, 8), next = 56)
        }

    @Test
    fun noneDeclaredAfterAStepBoxedToAPaddingSettlesOnTheSameLayout() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(FIRST)
                                .background(Red)
                                .padding(8)
                                .paintOutsets(PaintOutsets.None)
                                .size(40, 40),
                    )
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            assertFirstAt(Rectangle(8, 8, 40, 40), Insets(8, 8, 8, 8), next = 56)
        }

    @Test
    fun underNoneAStepBoxedToAnOffsetKeepsTheBox() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(FIRST)
                                .paintOutsets(PaintOutsets.None)
                                .background(Red)
                                .offset(10, 10)
                                .size(40, 40),
                    )
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            assertFirstAt(Rectangle(10, 10, 40, 40), Insets(10, 10, 0, 0), next = 40)
        }

    @Test
    fun underNoneAShadowTakesItsReachOnceWhereverAPaddingIsDeclared() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("none, shadow, padding")
                                .paintOutsets(PaintOutsets.None)
                                .shadow(8, Color.BLACK)
                                .padding(8)
                                .size(40, 40),
                    )
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("shadow, padding, none")
                                .shadow(8, Color.BLACK)
                                .padding(8)
                                .paintOutsets(PaintOutsets.None)
                                .size(40, 40),
                    )
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("padding, none, shadow")
                                .padding(8)
                                .paintOutsets(PaintOutsets.None)
                                .shadow(8, Color.BLACK)
                                .size(40, 40),
                    )
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("undeclared")
                                .shadow(8, Color.BLACK)
                                .padding(8)
                                .size(40, 40),
                    )
                }
            }
            val undeclared = onNodeWithTag("undeclared").fetch<JComponent>()
            val reach = undeclared.paintOutsets.top
            val box = 56 + 2 * reach
            assertNotEquals(0, reach, "the shadow reaches past the box")
            val orders = listOf("none, shadow, padding", "shadow, padding, none", "padding, none, shadow")
            for ((index, order) in orders.withIndex()) {
                assertEquals(
                    Rectangle(reach + 8, index * box + reach + 8, 40, 40),
                    onNodeWithTag(order).fetch().layoutBounds,
                    "$order: the shadow's reach and the padding, each once",
                )
            }
            assertEquals(Rectangle(8, 3 * box + 8, 40, 40), undeclared.layoutBounds, "the last box ends at 3 * $box")
        }

    @Test
    fun underNoneARotatingLayerTakesNoSpaceWhereverItIsDeclared() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(FIRST)
                                .paintOutsets(PaintOutsets.None)
                                .size(60, 40)
                                .placementLayer { rotationZ = 30f },
                    )
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("second")
                                .size(60, 40)
                                .placementLayer { rotationZ = 30f }
                                .paintOutsets(PaintOutsets.None),
                    )
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            val first = onNodeWithTag(FIRST).fetch<JComponent>()
            val second = onNodeWithTag("second").fetch<JComponent>()
            assertNotEquals(Insets(0, 0, 0, 0), first.paintOutsets, "the rotated box reaches past its layout bounds")
            assertEquals(first.paintOutsets, second.paintOutsets)
            assertEquals(Rectangle(0, 0, 60, 40), first.layoutBounds, "declared before the layer")
            assertEquals(Rectangle(0, 40, 60, 40), second.layoutBounds, "declared after the layer")
            assertEquals(80, onNodeWithTag(NEXT).fetch().layoutBounds.y)
        }

    @Test
    fun noneOnARowLeavesAChildsShadowPastItOutOfItsBox() =
        runComposeSwingTest {
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag(FIRST).paintOutsets(PaintOutsets.None)) {
                        SwingNode(
                            factory = { Card() },
                            modifier = SwingModifier.testTag("card").emptyBorder(3).shadow(8, Color.BLACK),
                        )
                    }
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            val reach = onNodeWithTag("card").fetch<JComponent>().paintOutsets
            assertNotEquals(Insets(0, 0, 0, 0), reach, "the card's shadow reaches past the row")
            assertFirstAt(Rectangle(0, 0, 126, 86), reach, next = 86)

            onNodeWithTag(FIRST).fetch().parent.revalidate()
            awaitIdle()
            assertFirstAt(Rectangle(0, 0, 126, 86), reach, next = 86)
        }

    /** The value names 4 px, clamped to the 3 px border: the box is the padding's less the border on each side. */
    @Test
    fun aFixedValueOnABorderedBoxCountsTheBorderAndNotAStepBoxedToAPadding() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(FIRST)
                                .paintOutsets(PaintOutsets(4))
                                .emptyBorder(3)
                                .background(Red)
                                .padding(8)
                                .size(40, 40),
                    )
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            assertFirstAt(Rectangle(5, 5, 40, 40), Insets(8, 8, 8, 8), next = 50)
        }

    /**
     * The panel's preferred size is set, so its layout asks the row's once, as it lays it out. That answer comes from
     * the node's intrinsic hooks, which run its `measure`.
     */
    @Test
    fun outsetsALayoutNodeTakesInASizeQueryAreSizedWithUnderASwingParent() =
        runComposeSwingTest {
            val reach = { 8 }
            setContent {
                SwingNode(
                    factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
                    modifier = SwingModifier.preferredSize(200, 200),
                ) {
                    Row(modifier = SwingModifier.testTag("row")) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag(FIRST)
                                    .paintOutsets(PaintOutsets.None)
                                    .then(ReachingElement(reach))
                                    .size(40, 40),
                        )
                        Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                    }
                }
            }
            assertEquals(Dimension(66, 56), onNodeWithTag("row").fetch().size)
            assertEquals(Rectangle(8, 8, 40, 40), onNodeWithTag(FIRST).fetch().layoutBounds)
            assertEquals(56, onNodeWithTag(NEXT).fetch().layoutBounds.x)
        }

    /** A changed read in the node's placement places the column's children again while the column is valid. */
    @Test
    fun outsetsALayoutNodeChangesAsItIsPlacedAgainAreMeasuredWith() =
        runComposeSwingTest {
            var taken by mutableIntStateOf(8)
            val reach = { taken }
            setContent {
                Column {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(FIRST)
                                .paintOutsets(PaintOutsets.None)
                                .then(ReachingElement(reach, whilePlacing = true))
                                .size(40, 40),
                    )
                    Box(modifier = SwingModifier.testTag(NEXT).size(10, 10))
                }
            }
            assertFirstAt(Rectangle(8, 8, 40, 40), Insets(8, 8, 8, 8), next = 56)

            taken = 12
            awaitIdle()

            assertFirstAt(Rectangle(12, 12, 40, 40), Insets(12, 12, 12, 12), next = 64)
        }

    /**
     * The border is applied first, so the container awaits validation as the shadow's new reach is written. The
     * panel validates in the event after the one that invalidates it, ahead of anything queued later.
     */
    @Test
    fun outsetsChangedInAContainerAwaitingValidationLayItOutOnce() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(4)
            var passes = 0
            val counting =
                MeasurePolicy { measurables, constraints ->
                    val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        passes++
                        placeable.place(0, 0)
                    }
                }
            setContent {
                SwingNode(factory = { SelfValidatingPanel() }) {
                    Layout(measurePolicy = counting) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag(FIRST)
                                    .paintOutsets(PaintOutsets.None)
                                    .emptyBorder(radius)
                                    .shadow(radius, Color.BLACK)
                                    .size(40, 40),
                        )
                    }
                }
            }
            passes = 0

            radius = 8
            awaitIdle()

            val reach = blurOutsets(8)
            assertEquals(1, passes, "the change joins the validation the container awaits")
            assertEquals(Rectangle(reach, reach, 40, 40), onNodeWithTag(FIRST).fetch().layoutBounds)
        }

    /** The layout bounds and paint outsets of the component tagged [FIRST], and where the one tagged [NEXT] starts. */
    private fun ComposeSwingTest.assertFirstAt(
        layoutBounds: Rectangle,
        paintOutsets: Insets,
        next: Int,
    ) {
        val first = onNodeWithTag(FIRST).fetch<JComponent>()
        assertEquals(layoutBounds, first.layoutBounds, "the layout bounds")
        assertEquals(paintOutsets, first.paintOutsets, "the paint outsets")
        assertEquals(next, onNodeWithTag(NEXT).fetch().layoutBounds.y, "where the next sibling starts")
    }

    private companion object {
        const val FIRST = "first"
        const val NEXT = "next"
        val Red = Brush.of(Color.RED)
    }
}

/** Validates itself in the event after the one that invalidates it, as a window's validate root does. */
private class SelfValidatingPanel : JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) {
    override fun invalidate() {
        val wasValid = isValid
        super.invalidate()
        if (wasValid) SwingUtilities.invokeLater(::validate)
    }
}
