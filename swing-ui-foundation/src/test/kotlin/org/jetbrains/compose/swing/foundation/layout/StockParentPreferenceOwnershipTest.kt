package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import kotlin.test.Test

class StockParentPreferenceOwnershipTest {
    @Test
    fun movingThroughAFoundationParentUsesTheStockParentsCurrentWidth() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    Layout(HalfWidthHeightPolicy, SwingModifier.testTag("subject"))
                    Box(SwingModifier.testTag("foundation")) {}
                }
            }
            val subjectNode = onNodeWithTag("subject")
            // No interaction drives Swing's setSize/doLayout/add lifecycle, so only those mutations use components.
            val subject = subjectNode.fetch<JComponent>()
            val foundation = onNodeWithTag("foundation").fetch<JComponent>()
            val stock = subject.parent

            subject.setSize(80, 100)
            subject.invalidate()
            subjectNode.assertProperty(40) { preferredSize.height }
            subject.doLayout()
            subject.setSize(160, 100)
            subjectNode.assertProperty(80) { preferredSize.height }
            subject.setSize(80, 100)
            subject.doLayout()
            subjectNode.assertProperty(40, "the stock parent uses the current width again") {
                preferredSize.height
            }

            foundation.add(subject)
            stock.add(subject)

            subjectNode.assertProperty(
                40,
                "the stock parent uses the current width after Foundation owned the panel",
            ) { preferredSize.height }
        }
}

private object HalfWidthHeightPolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.constrainWidth(320)
        return layout(width, constraints.constrainHeight(width / 2)) {}
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = 320

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = width / 2
}
