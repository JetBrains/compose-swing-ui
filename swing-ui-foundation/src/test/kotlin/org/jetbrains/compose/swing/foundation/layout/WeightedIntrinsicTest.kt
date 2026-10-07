package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The intrinsic reads a `Row` performs on its weighted children. */
class WeightedIntrinsicTest {
    @Test
    fun aRowsIntrinsicHeightAtAnUnboundedWidthReadsAFillingWeightedChildWithNoMinimumWidth() =
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
}
