package org.jetbrains.compose.swing.foundation.layout

import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A measured result belongs to the pass that produced it. Asking that policy for its intrinsic extent
 * before the parent places the retained result must not rewrite what that pass places.
 */
class MeasureResultAliasingTest {
    @Test
    fun anIntrinsicQuestionDoesNotRewriteARowsRetainedPlacementResult() {
        val layout = LinearLayout(LayoutAxis.Horizontal, HorizontalAxisArrangement(Arrangement.End), TOP)
        val panel = JPanel(layout)
        val child = FixedSizeChild(width = 20, height = 10)
        panel.add(
            child,
            LinearConstraint(
                weight = WeightPlacement(weight = 1f, fill = true),
                fillsCrossAxis = true,
            ),
        )

        placeRetainedResultAfterAnIntrinsicQuestion(layout, panel)

        assertEquals(
            Rectangle(0, 0, 100, 50),
            child.bounds,
            "the retained row result still places the weighted, filling extent it measured",
        )
    }

    @Test
    fun anIntrinsicQuestionDoesNotClearABoxsRetainedPlacementResult() {
        val layout = OverlapLayout(Alignment.BottomEnd)
        val panel = JPanel(layout)
        val child = FixedSizeChild(width = 20, height = 10)
        panel.add(child, BoxConstraint(fillsWidth = true, fillsHeight = true))

        placeRetainedResultAfterAnIntrinsicQuestion(layout, panel)

        assertEquals(
            Rectangle(0, 0, 100, 50),
            child.bounds,
            "the retained box result still places the filling extent it measured",
        )
    }

    private fun placeRetainedResultAfterAnIntrinsicQuestion(
        policy: MeasurePolicyLayout,
        target: JPanel,
    ) {
        val constraints = Constraints(minWidth = 100, maxWidth = 100, minHeight = 50, maxHeight = 50)
        val parent =
            JPanel(
                TestPolicyLayout { _, _ ->
                    val retained =
                        with(policy.policy) {
                            PolicyMeasureScope.measure(policy.measurables.layoutPassOf(target), constraints)
                        }
                    policy.measurables.askedSize(target, MeasureMode.Preferred)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        with(retained) { placeChildren() }
                    }
                },
            )
        parent.setSize(constraints.maxWidth, constraints.maxHeight)
        parent.doLayout()
    }

    private companion object {
        val TOP = VerticalAxisAlignment(Alignment.Top)
    }
}
