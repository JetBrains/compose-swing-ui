package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import kotlin.test.Test

/**
 * A container asks its child's height or baseline while it measures, at a width it then measures the child at another.
 * The stock child it asks is sized to the width asked and not laid out there, so one validation settles.
 */
class MeasuringQuestionWidthTest {
    @Test
    fun aQuestionAskedWhileMeasuringAtAWidthTheLayoutDoesNotGrantSettlesInOneValidation() =
        assertQuestionsSettle(
            mapOf(
                "a policy asking narrower than it measures" to { modifier, leaf ->
                    Layout(
                        content = { leaf(SwingModifier) },
                        modifier = modifier,
                        measurePolicy = { measurables, constraints ->
                            val child = measurables.single()
                            child.maxIntrinsicHeight(constraints.maxWidth.narrower())
                            val placeable = child.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                    )
                },
                "a policy reading a baseline narrower than it measures" to { modifier, leaf ->
                    Layout(
                        content = { leaf(SwingModifier) },
                        modifier = modifier,
                        measurePolicy = { measurables, constraints ->
                            val child = measurables.single()
                            val width = constraints.maxWidth.narrower()
                            child.intrinsicPlaceable(width, 100)?.get(FirstBaseline)
                            val placeable = child.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                    )
                },
                "Box of intrinsic height" to { modifier, leaf ->
                    Box(modifier = modifier) {
                        Box(modifier = SwingModifier.height(IntrinsicSize.Max)) { leaf(SwingModifier) }
                    }
                },
                "Row of intrinsic height with a weighted child" to { modifier, leaf ->
                    Column(modifier = modifier) {
                        Row(modifier = SwingModifier.height(IntrinsicSize.Min)) {
                            leaf(SwingModifier.weight(1f))
                            Label("|", modifier = SwingModifier.fillMaxHeight())
                        }
                    }
                },
            ),
        )
}
