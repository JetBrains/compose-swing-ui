package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import kotlin.test.Test

/**
 * A Row reports the cross-axis extent its shared baseline demands even past its offer, as androidx's
 * `RowColumnMeasurePolicy` does, and its parent centers it on that offer.
 */
class RowColumnCrossAxisOverflowTest {
    @Test
    fun aRowOfferedLessThanItsChildrensSharedBaselineExtentReportsTheRawExtentAndIsCenteredInTheOffer() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.styleable { preferredSize(200, 40) }) {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        DecoratedBaselineChild(30, SwingModifier.alignByBaseline())
                        DecoratedBaselineChild(10, SwingModifier.alignByBaseline())
                    }
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(
                Rectangle(0, -10, CHILD_WIDTH * 2, 60),
                "a row whose aligned children need more cross-axis space than a smaller offer must report the " +
                    "raw 60 its baselines demand and be centered on the 40 it was offered, rather than clip " +
                    "the deeper child to that offer",
            ) { bounds }
        }
}
