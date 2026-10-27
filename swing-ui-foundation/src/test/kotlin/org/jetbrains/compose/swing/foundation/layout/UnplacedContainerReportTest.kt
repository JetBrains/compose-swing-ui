package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `onPlaced` and `onSizeChanged` for the children of a Foundation container inside one its parent leaves unplaced. */
class UnplacedContainerReportTest {
    /**
     * A `Box` whose parent `Box` is left unplaced by its own parent lays nothing out, so its child reports nothing
     * while that parent stays unplaced. Placed again, it lays its child out, which reports its placement and size.
     */
    @ParameterizedTest(name = "stockAncestor={0}")
    @ValueSource(booleans = [false, true])
    fun aBoxInsideAnUnplacedBoxLaysNothingOutAndReportsNothing(stockAncestor: Boolean) =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            var placed by mutableStateOf(true)
            var leafSize by mutableStateOf(10)

            setContent {
                UnplacedBoxTree(
                    stockAncestor = stockAncestor,
                    placed = { placed },
                    leafSize = { leafSize },
                    reportPlaced = { placements += it },
                    reportSized = { sizes += it },
                )
            }
            awaitIdle()
            placements.clear()
            sizes.clear()

            placed = false
            awaitIdle()
            leafSize = 20
            awaitIdle()

            assertEquals(10, onNodeOfType<JLabel>().fetch().width, "the inner Box lays nothing out")
            assertEquals(emptyList(), placements, "its child must report no placement")
            assertEquals(emptyList(), sizes, "its child must report no size")

            placed = true
            awaitIdle()

            assertEquals(20, onNodeOfType<JLabel>().fetch().width, "placed again, the inner Box lays out its child")
            assertEquals(listOf(Rectangle(0, 0, 20, 20)), placements, "placed again, the child reports its placement")
            assertEquals(listOf(Dimension(20, 20)), sizes, "placed again, the child reports the size it took")
        }

    // Keeping an outer container's settled result while a nested container places reaches through Foundation
    // containers only, so under a stock ancestor the outer container may measure once more.
    @ParameterizedTest(name = "stockAncestor={0}")
    @CsvSource("false, 2", "true, 3")
    fun aHiddenChildInADirectLayoutBoundsTheOuterContainersMeasures(
        stockAncestor: Boolean,
        mostMeasures: Int,
    ) = runComposeSwingTest {
        var measures = 0
        var showLeaf = true
        setContent {
            Layout(
                content = {
                    StockAncestor(stockAncestor) {
                        Layout(
                            content = { Label("leaf", SwingModifier.preferredSize(10, 10)) },
                            modifier = SwingModifier.testTag("inner"),
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(placeable.width, placeable.height) { if (showLeaf) placeable.place(0, 0) }
                            },
                        )
                    }
                },
                measurePolicy = { measurables, constraints ->
                    measures++
                    val placeable = measurables.single().measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            )
        }
        awaitIdle()
        onNodeWithTag("inner").assertProperty(true, "the tree is valid") { isValid }
        val settled = measures

        showLeaf = false
        onNodeWithTag("inner").fetch<JComponent>().doLayout()
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty(false, "the placement hid the leaf") { isVisible }
        onNodeWithTag("inner").assertProperty(true, "the inner container is valid again") { isValid }
        assertTrue(measures - settled <= mostMeasures, "the outer container's measures: ${measures - settled}")
    }
}

/** One movable slot serves both branches, so [content] has a single call site. */
@Composable
private fun StockAncestor(
    present: Boolean,
    content: @Composable () -> Unit,
) {
    val slot = remember(content) { movableContentOf(content) }
    if (present) {
        SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false } }) { slot() }
    } else {
        slot()
    }
}

@Composable
private fun UnplacedBoxTree(
    stockAncestor: Boolean,
    placed: () -> Boolean,
    leafSize: () -> Int,
    reportPlaced: (Rectangle) -> Unit,
    reportSized: (Dimension) -> Unit,
) {
    Layout(
        content = {
            StockAncestor(stockAncestor) {
                Layout(
                    content = {
                        Box {
                            Box {
                                Label(
                                    text = "leaf",
                                    modifier =
                                        SwingModifier
                                            .preferredSize(leafSize(), leafSize())
                                            .onPlaced(reportPlaced)
                                            .onSizeChanged(reportSized),
                                )
                            }
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width, placeable.height) { if (placed()) placeable.place(0, 0) }
                    },
                )
            }
        },
        measurePolicy = { measurables, constraints ->
            val placeable = measurables.single().measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
    )
}
