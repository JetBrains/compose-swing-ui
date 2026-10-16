package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import kotlin.test.Test

/**
 * A stock parent reads a container's size before it lays the container out, and the policy returns a result at a width
 * the layout does not grant. The stock child whose preferences it reads is sized to that width and not laid out there,
 * so one validation
 * settles.
 */
class StockParentAtWidthTest {
    @Test
    fun aStockParentsPreferencesReadAtAWidthTheLayoutDoesNotGrantSettleInOneValidation() {
        // Reads its child's height at a narrower width than it prefers, and lays it out at the width it prefers.
        val readsHeightAtNarrowerWidth: ContainerContent = { modifier, leaf ->
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
        // Reads its child's baseline at a narrower width than it reads its height at, and lays it out as it prefers.
        val readsBaselineNarrower: ContainerContent = { modifier, leaf ->
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
        assertPreferencesSettle(
            mapOf<String, ContainerContent>(
                "ConstrainedPanel" to { modifier, leaf ->
                    val panel = remember { ConstrainedPanel() }
                    SwingNode(factory = { panel }, modifier = modifier)
                    val context = rememberCompositionContext()
                    DisposableEffect(panel) {
                        val content = panel.setContent(context) { leaf(SwingModifier) }
                        onDispose { content.dispose() }
                    }
                },
                "Box" to { modifier, leaf -> Box(modifier = modifier) { leaf(SwingModifier.fillMaxWidth()) } },
                "Column" to { modifier, leaf -> Column(modifier = modifier) { leaf(SwingModifier.fillMaxWidth()) } },
                "Row with a weighted child" to { modifier, leaf ->
                    Row(modifier = modifier) {
                        leaf(SwingModifier.weight(1f))
                        Label("|")
                    }
                },
            ) +
                mapOf<String, ConstrainedScope.() -> SwingModifier>(
                    "Box with widthIn" to { SwingModifier.widthIn(max = 200) },
                    "Box with requiredWidthIn" to { SwingModifier.requiredWidthIn(min = 200, max = 400) },
                    "Box with padding" to { SwingModifier.padding(all = 8).fillMaxWidth() },
                    "Box with aspectRatio" to { SwingModifier.fillMaxWidth().aspectRatio(2f) },
                ).mapValues<String, ConstrainedScope.() -> SwingModifier, ContainerContent> { (_, childModifier) ->
                    { modifier, leaf -> Box(modifier = modifier) { leaf(childModifier()) } }
                } +
                mapOf(
                    "a policy reading preferences narrower than it lays out" to readsHeightAtNarrowerWidth,
                    "a policy reading a baseline narrower than it lays out" to readsBaselineNarrower,
                ),
            widthFromLeafHeight =
                mapOf<String, ContainerContent>(
                    "Column with an aspect-ratio leaf" to { modifier, leaf ->
                        Column(modifier = modifier) { leaf(SwingModifier.fillMaxWidth().aspectRatio(2f)) }
                    },
                ),
        )
    }
}
