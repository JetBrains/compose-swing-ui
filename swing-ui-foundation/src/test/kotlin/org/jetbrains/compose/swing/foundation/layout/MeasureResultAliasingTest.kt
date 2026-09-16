package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A measured result belongs to the pass that produced it. Asking that policy for its intrinsic extent
 * before the parent places the retained result must not rewrite what that pass places.
 */
@ExtendWith(ComposedPanels::class)
class MeasureResultAliasingTest {
    @Test
    fun anIntrinsicQuestionDoesNotRewriteARowsRetainedPlacementResult() {
        val panel = composed(rowPolicyPanel(arrangement = Arrangement.End))
        val layout = panel.policyLayout
        val child = FixedSizeChild(width = 20, height = 10)
        panel.add(
            child,
            LinearConstraint(weight = WeightPlacement(weight = 1f, fill = true)),
        )
        layout.declareLayoutChain(child, layoutChainOf { SwingModifier.fillMaxHeight() })

        placeRetainedResultAfterAnIntrinsicQuestion(layout)

        assertEquals(
            Rectangle(0, 0, 100, 50),
            child.bounds,
            "the retained row result still places the weighted, filling extent it measured",
        )
    }

    @Test
    fun anIntrinsicQuestionDoesNotClearABoxsRetainedPlacementResult() {
        val layout = MeasurePolicyLayout(BoxMeasurePolicy(Alignment.BottomEnd, false), null)
        val panel = composed(ConstrainedPanel(layout))
        val child = FixedSizeChild(width = 20, height = 10)
        panel.add(child, BoxConstraint())
        layout.declareLayoutChain(child, layoutChainOf { SwingModifier.fillMaxSize() })

        placeRetainedResultAfterAnIntrinsicQuestion(layout)

        assertEquals(
            Rectangle(0, 0, 100, 50),
            child.bounds,
            "the retained box result still places the filling extent it measured",
        )
    }

    private fun placeRetainedResultAfterAnIntrinsicQuestion(policy: MeasurePolicyLayout) {
        val constraints = Constraints(minWidth = 100, maxWidth = 100, minHeight = 50, maxHeight = 50)
        val parent =
            ConstrainedPanel(
                MeasurePolicyLayout(
                    MeasurePolicy { _, _ ->
                        val retained =
                            with(policy.policy) {
                                PolicyMeasureScope.measure(policy.measurables.layoutPassOf(), constraints)
                            }
                        policy.measurables.askedSize(MeasureMode.Preferred)
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            with(retained) { placeChildren() }
                        }
                    },
                    null,
                ),
            )
        parent.setSize(constraints.maxWidth, constraints.maxHeight)
        parent.doLayout()
    }
}
