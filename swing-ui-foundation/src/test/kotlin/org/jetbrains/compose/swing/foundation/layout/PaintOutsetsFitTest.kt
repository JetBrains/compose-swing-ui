package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card's paint outsets change outside a layout pass: its Foundation containers fit it in place and measure nothing,
 * while a change of the parent data they read measures them again. A change a layout node makes as its container is
 * measured is fitted in place too, and the running pass lays the container out.
 */
class PaintOutsetsFitTest {
    @Test
    fun aContainersPaintOutsetsChangeOutsideLayoutMeasuresNoFoundationAncestor() =
        runComposeSwingTest {
            var scale by mutableFloatStateOf(1f)
            val (outer, inner) = nestedCard { SwingModifier.placementLayer { scaleX = scale } }
            val card = onNodeWithTag("card").fetch<JComponent>()
            outer.measures = 0
            inner.measures = 0

            val outsets =
                listOf(1.25f, 1.5f, 1.75f, 2f).map {
                    scale = it
                    awaitIdle()
                    card.paintOutsets()
                }

            assertEquals(
                listOf(Insets(0, 5, 0, 5), Insets(0, 10, 0, 10), Insets(0, 15, 0, 15), Insets(0, 20, 0, 20)),
                outsets,
                "each scale spreads the 40-wide card by half its growth on either side",
            )
            assertEquals(0, inner.measures, "the card's own relayout must not measure its Foundation parent")
            assertEquals(0, outer.measures, "nor the Foundation container above that")
        }

    @Test
    fun aContainersShadowChangeOutsideLayoutMeasuresNoFoundationAncestor() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(2)
            val (outer, inner) = nestedCard { SwingModifier.shadow(radius, Color.BLACK) }
            val card = onNodeWithTag("card").fetch<JComponent>()
            outer.measures = 0
            inner.measures = 0

            for (next in listOf(4, 6, 8, 10, 12)) {
                radius = next
                awaitIdle()
            }

            val outsets = blurOutsets(12)
            assertEquals(
                Insets(outsets, outsets, outsets, outsets),
                card.paintOutsets(),
                "the card's paint outsets follow the last radius",
            )
            assertEquals(0, inner.measures, "the container holding the card")
            assertEquals(0, outer.measures, "the container above it")
        }

    @Test
    fun aChildsParentDataChangeMeasuresItsContainerAgain() =
        runComposeSwingTest {
            var shift by mutableIntStateOf(0)
            var placed = Rectangle()
            val (_, inner) =
                nestedCard(shift = { shift }, onPlaced = { placed = it }) { SwingModifier.shadow(4, Color.BLACK) }
            inner.measures = 0

            shift = 10
            awaitIdle()

            assertTrue(inner.measures >= 1, "the container reads the parent data, so it measures again")
            assertEquals(Rectangle(10, 0, 40, 20), placed, "and places the card where it now reads")
        }

    @Test
    fun aFoundationContainersOwnPaintOutsetsChangeMeasuresNoFoundationAncestor() =
        runComposeSwingTest {
            var scale by mutableFloatStateOf(1f)
            val outer = CountingPolicy()
            setContent {
                Layout(measurePolicy = outer) {
                    Box(modifier = SwingModifier.testTag("box")) {
                        Box(modifier = SwingModifier.size(40, 20).placementLayer { scaleX = scale })
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            outer.measures = 0

            for (next in listOf(1.5f, 2f)) {
                scale = next
                awaitIdle()
            }

            assertEquals(Insets(0, 20, 0, 20), box.paintOutsets(), "the box spreads by what its scaled child paints")
            assertEquals(0, outer.measures, "fitting the box and moving its child must not measure its parent")
        }

    @Test
    fun aContainerFittedToNewPaintOutsetsIsMeasuredAtItsLayoutSizeLater() =
        runComposeSwingTest {
            var scale by mutableFloatStateOf(1f)
            var width by mutableIntStateOf(10)
            var placed = Rectangle()
            setContent {
                Column(modifier = SwingModifier.testTag("column")) {
                    Row(modifier = SwingModifier.testTag("row").onPlaced { placed = it }) {
                        Box(modifier = SwingModifier.size(40, 40).placementLayer { scaleX = scale })
                    }
                    Box(modifier = SwingModifier.size(width, 10))
                }
            }
            val column = onNodeWithTag("column").fetch<JComponent>()
            val row = onNodeWithTag("row").fetch<JComponent>()
            assertEquals(Dimension(40, 40), row.preferredSize, "the row asks for its child's size while unscaled")
            scale = 2f
            awaitIdle()

            width = 20
            awaitIdle()

            assertEquals(Dimension(80, 40), row.preferredSize, "the row asks for its layout size and outsets")
            assertEquals(Dimension(40, 50), column.preferredSize, "the column measures the row at its layout size")
            assertEquals(Rectangle(0, 0, 40, 40), placed, "and places it there")
        }

    /**
     * The last box's new width lays the column out while the row is valid. The size ahead of the node answers the
     * column's size query, so the node reads its new reach as the row measures the box.
     */
    @Test
    fun outsetsALayoutNodeChangesAsItsValidContainerIsMeasuredAreFittedInThatPass() =
        runComposeSwingTest {
            var taken = 8
            val reach = { taken }
            var width by mutableIntStateOf(10)
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        Box(modifier = SwingModifier.testTag("reaching").size(40, 40).then(ReachingElement(reach)))
                        Box(modifier = SwingModifier.testTag("beside").size(10, 40))
                    }
                    Box(modifier = SwingModifier.testTag("below").size(width, 10))
                }
            }
            val row = onNodeWithTag("row").fetch<JComponent>()
            val reaching = onNodeWithTag("reaching").fetch<JComponent>()
            assertEquals(Insets(8, 8, 8, 0), row.paintOutsets(), "the row spreads by what the box paints past it")

            taken = 12
            width = 20
            awaitIdle()

            assertEquals(Insets(12, 12, 12, 12), reaching.paintOutsets(), "the box takes the new reach")
            assertEquals(Rectangle(0, 0, 40, 40), reaching.layoutBounds, "and keeps its layout bounds")
            assertEquals(Rectangle(40, 0, 10, 40), onNodeWithTag("beside").fetch().layoutBounds)
            assertEquals(Insets(12, 12, 12, 2), row.paintOutsets(), "the row spreads by the new reach")
            assertEquals(Rectangle(0, 0, 50, 40), row.layoutBounds, "and keeps its layout bounds")
            assertEquals(Rectangle(0, 40, 20, 10), onNodeWithTag("below").fetch().layoutBounds)
        }

    /** The box's padding holds its new reach inside the row, so the pass fits the box and leaves the row's bounds. */
    @Test
    fun aContainerFittedAsItIsMeasuredIsLaidOutByThatPass() =
        runComposeSwingTest {
            var taken = 8
            val reach = { taken }
            var width by mutableIntStateOf(10)
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("reaching")
                                    .padding(16)
                                    .size(40, 40)
                                    .then(ReachingElement(reach)),
                        )
                    }
                    Box(modifier = SwingModifier.size(width, 10))
                }
            }
            val reaching = onNodeWithTag("reaching").fetch<JComponent>()

            taken = 12
            width = 20
            awaitIdle()

            assertEquals(Insets(12, 12, 12, 12), reaching.paintOutsets(), "the box takes the new reach")
            assertEquals(Rectangle(16, 16, 40, 40), reaching.layoutBounds, "and keeps its layout bounds")
            assertEquals(Insets(0, 0, 0, 0), onNodeWithTag("row").fetch().paintOutsets(), "inside the row")
            assertTrue(reaching.isValid, "the pass that measured the box validates it")
        }

    /** Measures its one child at its own size and places it at the x its [Shift] reads, counting its measure passes. */
    private class CountingPolicy : MeasurePolicy {
        var measures = 0

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            measures++
            val measurable = measurables.single()
            val x = measurable.parentData as? Int ?: 0
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            return layout(x + placeable.width, placeable.height) { placeable.place(x, 0) }
        }
    }

    /** Parent data a [CountingPolicy] reads: the x it places the child at. */
    private data class Shift(
        private val x: Int,
    ) : ParentDataModifier {
        override val name: String get() = "shift"

        override val parentProtocol: ParentProtocol get() = ShiftProtocol

        override val declaredValues: Map<String, Any?> get() = mapOf("x" to x)

        override fun modifyParentData(parentData: Any?): Any = x
    }

    /**
     * Composes two nested [Layout]s, the outer holding the inner, around a `"card"`: a [DecoratedPanel] laid out by
     * a [FlowLayout], holding a label, which the inner places at [shift] and whose modifier declares [decoration].
     * Returns the outer and the inner policy.
     */
    private fun ComposeSwingTest.nestedCard(
        shift: () -> Int = { 0 },
        onPlaced: (Rectangle) -> Unit = {},
        decoration: () -> SwingModifier,
    ): Pair<CountingPolicy, CountingPolicy> {
        val outer = CountingPolicy()
        val inner = CountingPolicy()
        setContent {
            Layout(measurePolicy = outer) {
                Layout(measurePolicy = inner, parentDataProtocol = ShiftProtocol) {
                    SwingNode(
                        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false } },
                        modifier =
                            SwingModifier.testTag("card").preferredSize(40, 20).onPlaced(onPlaced) then
                                Shift(shift()) then decoration(),
                    ) {
                        Label("child")
                    }
                }
            }
        }
        return outer to inner
    }

    private companion object {
        val ShiftProtocol = layoutParentDataProtocol("shift")

        fun Component.paintOutsets(): Insets = (this as Decoratable).decoration.paintOutsets()
    }
}
