package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The intrinsic questions a `Row` asks its weighted children. */
class WeightedIntrinsicTest {
    @Test
    fun aRowAskedItsHeightAtAnUnboundedWidthAsksAFillingWeightedChildUnderNoLeastWidth() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Box(modifier = SwingModifier.weight(1f), propagateMinConstraints = true) {
                        SwingNode(factory = { HalfAsTallAsWide() }, modifier = SwingModifier.fillMaxWidth())
                    }
                }
            }
            val row = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>() as Constrainable

            assertEquals(
                HalfAsTallAsWide.WIDTH / 2,
                row.maxIntrinsicHeight(Constraints.Infinity),
                "no share of an unbounded width is a width the child must be at least as wide as",
            )
            assertEquals(
                HalfAsTallAsWide.WIDTH / 2,
                row.minIntrinsicHeight(Constraints.Infinity),
                "and so for the minimum",
            )
        }

    @Test
    fun aRowsIntrinsicHeightAsksTheWeightedChildThatTakesTheRoundingRemainderAtTheWidthItIsPlacedAt() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(THREE_SHARES_AND_A_PIXEL, 600)) {
                    Column(modifier = SwingModifier.north()) {
                        Row(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxWidth()) {
                            repeat(3) {
                                Box(modifier = SwingModifier.weight(1f)) {
                                    SwingNode(factory = { HalfAsTallAsWide() }, modifier = SwingModifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
            val row = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()

            assertEquals(THREE_SHARES_AND_A_PIXEL / 3 / 2 + 1, row.height, "the first child takes the rounding pixel")
            assertEquals(
                row.height,
                (row as Constrainable).maxIntrinsicHeight(THREE_SHARES_AND_A_PIXEL),
                "and the row's intrinsic height asks it at that width, as the row is laid out",
            )
        }
}

/** The width of a row three weighted children divide into shares of 3, 3 and 3, and a rounding pixel for the first. */
private const val THREE_SHARES_AND_A_PIXEL = 10
