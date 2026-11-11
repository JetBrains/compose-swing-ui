// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal fun Int.narrower(): Int = if (this == Constraints.Infinity) this else (this - 30).coerceAtLeast(0)

/**
 * A container reads its child's height or baseline while it measures, at a width it then measures the child at another.
 * The stock child whose preferences it reads is sized to the specified width and not laid out there.
 */
class MeasuringAtWidthTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("containers")
    fun preferencesReadWhileMeasuringAtAWidthTheLayoutDoesNotGrantLayNothingOutThere(
        name: String,
        container: ContainerContent,
    ) = assertPreferencesSettle(mapOf(name to container))

    companion object {
        @JvmStatic
        fun containers(): Stream<Arguments> =
            mapOf<String, ContainerContent>(
                "a policy reading preferences narrower than it measures" to { modifier, leaf ->
                    Layout(
                        content = { leaf(SwingModifier) },
                        modifier = modifier,
                        measurePolicy =
                            MeasurePolicy { measurables, constraints ->
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
                        measurePolicy =
                            MeasurePolicy { measurables, constraints ->
                                val child = measurables.single()
                                child.intrinsicPlaceable(constraints.maxWidth.narrower(), 100)?.get(FirstBaseline)
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
            ).map { (name, container) -> Arguments.of(Named.of(name, name), container) }.stream()
    }
}
