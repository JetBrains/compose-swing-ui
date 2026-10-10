package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import kotlin.test.Test

/**
 * A stock parent asks a container for its size before it lays the container out, and the policy answers at a width
 * the layout does not grant. The stock child it asks is sized to that width and not laid out there, so one validation
 * settles.
 */
class StockParentQuestionWidthTest {
    @Test
    fun aStockParentsQuestionAnsweredAtAWidthTheLayoutDoesNotGrantSettlesInOneValidation() =
        assertQuestionsSettle(
            QuestionContainers +
                mapOf(
                    "a policy asking narrower than it lays out" to AsksNarrower,
                    "a policy reading a baseline narrower than it lays out" to ReadsBaselineNarrower,
                ),
            widthFromLeafHeight = WidthFromLeafHeightContainers,
        )

    private companion object {
        /** Asks its child's height at a narrower width than it asks for, and lays it out at the width it prefers. */
        val AsksNarrower: Asker = { modifier, leaf ->
            Layout(
                content = { leaf(SwingModifier) },
                modifier = modifier,
                measurePolicy =
                    object : MeasurePolicy {
                        override fun MeasureScope.measure(
                            measurables: List<Measurable>,
                            constraints: Constraints,
                        ): MeasureResult {
                            val placeable = measurables.single().measure(Constraints())
                            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }

                        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                            measurables: List<IntrinsicMeasurable>,
                            height: Int,
                        ): Int = measurables.single().maxIntrinsicWidth(height)

                        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                            measurables: List<IntrinsicMeasurable>,
                            width: Int,
                        ): Int = measurables.single().maxIntrinsicHeight(width.narrower())
                    },
            )
        }

        /** Reads its child's baseline at a narrower width than it asks its height at, and lays it out as it prefers. */
        val ReadsBaselineNarrower: Asker = { modifier, leaf ->
            Layout(
                content = { leaf(SwingModifier) },
                modifier = modifier,
                measurePolicy =
                    object : MeasurePolicy {
                        override fun MeasureScope.measure(
                            measurables: List<Measurable>,
                            constraints: Constraints,
                        ): MeasureResult {
                            val placeable = measurables.single().measure(Constraints())
                            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }

                        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                            measurables: List<IntrinsicMeasurable>,
                            height: Int,
                        ): Int = measurables.single().maxIntrinsicWidth(height)

                        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                            measurables: List<IntrinsicMeasurable>,
                            width: Int,
                        ): Int {
                            val child = measurables.single()
                            child.intrinsicPlaceable(width.narrower(), 100)?.get(FirstBaseline)
                            return child.maxIntrinsicHeight(width)
                        }
                    },
            )
        }
    }
}
