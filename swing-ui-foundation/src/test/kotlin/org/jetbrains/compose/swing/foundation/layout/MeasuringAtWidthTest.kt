package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import kotlin.test.Test

internal fun Int.narrower(): Int = if (this == Constraints.Infinity) this else (this - 30).coerceAtLeast(0)

/**
 * A container reads its child's height or baseline while it measures, at a width it then measures the child at another.
 * The stock child whose preferences it reads is sized to the specified width and not laid out there, so one validation
 * settles.
 */
class MeasuringAtWidthTest {
    @Test
    fun preferencesReadWhileMeasuringAtAWidthTheLayoutDoesNotGrantSettleInOneValidation() =
        assertPreferencesSettle(
            mapOf(
                "a policy reading preferences narrower than it measures" to { modifier, leaf ->
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
