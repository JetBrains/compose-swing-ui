package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import javax.swing.JComponent
import kotlin.math.roundToInt

/**
 * A stock parent reads a container's size before it lays the container out, and the policy returns a result at a width
 * the layout does not grant. The stock child whose preferences it reads is sized to that width and not laid out there,
 * so one validation settles.
 */
class StockParentAtWidthTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("containers")
    fun aStockParentsPreferencesReadAtAWidthTheLayoutDoesNotGrantSettleInOneValidation(
        name: String,
        case: AtWidthCase,
    ) = case.assertSettles(name)

    companion object {
        @JvmStatic
        fun containers(): Stream<Arguments> =
            (
                constrainedPanel() + stockContainers() + boxesWithChildModifier() + heightReadingPolicies() +
                    widthFollowingLeafHeight()
            ).map { (name, case) -> Arguments.of(name, case) }
                .stream()

        private fun constrainedPanel() =
            mapOf(
                "ConstrainedPanel" to
                    HoldingCase({ modifier, leaf ->
                        val panel = remember { ConstrainedPanel() }
                        SwingNode(factory = { panel }, modifier = modifier)
                        val context = rememberCompositionContext()
                        DisposableEffect(panel) {
                            val content = panel.setContent(context) { leaf(SwingModifier) }
                            onDispose { content.dispose() }
                        }
                    }, sizeProblems = heightsAround(leafFillsContainer = true)),
            )

        private fun stockContainers() =
            mapOf<String, ContainerContent>(
                "Box" to FillingBox,
                "Column" to { modifier, leaf ->
                    Column(modifier = modifier) { leaf(SwingModifier.fillMaxWidth()) }
                },
                "Row with a weighted child" to { modifier, leaf ->
                    Row(modifier = modifier) {
                        leaf(SwingModifier.weight(1f))
                        Label("|")
                    }
                },
            ).mapValues { (_, container) -> HoldingCase(container) }

        private fun boxesWithChildModifier() =
            mapOf(
                "Box with widthIn" to boxHolding { SwingModifier.widthIn(max = 200) },
                "Box with requiredWidthIn" to boxHolding { SwingModifier.requiredWidthIn(min = 200, max = 400) },
                "Box with padding" to
                    boxHolding(heightsAround(padding = 16)) { SwingModifier.padding(all = 8).fillMaxWidth() },
                "Box with aspectRatio" to
                    boxHolding(heightsAround(leafHeight = { (it.width / 2f).roundToInt() })) {
                        SwingModifier.fillMaxWidth().aspectRatio(2f)
                    },
            )

        private fun boxHolding(
            sizeProblems: (container: JComponent, leaf: JComponent) -> List<String> = heightsAround(),
            childModifier: ConstrainedScope.() -> SwingModifier,
        ) = HoldingCase({ modifier, leaf -> Box(modifier = modifier) { leaf(childModifier()) } }, sizeProblems)

        private fun heightReadingPolicies() =
            mapOf<String, ContainerContent>(
                "a policy reading preferences narrower than it lays out" to { modifier, leaf ->
                    Layout(
                        content = { leaf(SwingModifier) },
                        modifier = modifier,
                        measurePolicy = heightReading { maxIntrinsicHeight(it.narrower()) },
                    )
                },
                "a policy reading a baseline narrower than it lays out" to { modifier, leaf ->
                    Layout(
                        content = { leaf(SwingModifier) },
                        modifier = modifier,
                        measurePolicy =
                            heightReading {
                                intrinsicPlaceable(it.narrower(), 100)?.get(FirstBaseline)
                                maxIntrinsicHeight(it)
                            },
                    )
                },
            ).mapValues { (_, container) -> HoldingCase(container) }

        private fun widthFollowingLeafHeight() =
            mapOf(
                "Column with an aspect-ratio leaf" to
                    WidthFollowingCase { modifier, leaf ->
                        Column(modifier = modifier) { leaf(SwingModifier.fillMaxWidth().aspectRatio(2f)) }
                    },
            )
    }
}

/** A container the stock parent test narrows, and what it asserts about it. */
sealed interface AtWidthCase {
    fun assertSettles(name: String)
}

/** A container, and the heights its container and stock leaf take once a validation has settled. */
private class HoldingCase(
    val container: ContainerContent,
    val sizeProblems: (container: JComponent, leaf: JComponent) -> List<String> = heightsAround(),
) : AtWidthCase {
    override fun assertSettles(name: String) =
        assertPreferencesSettle(mapOf(name to container), sizeProblems = sizeProblems)
}

/** A container whose preferred width follows a leaf's height at an unbounded width. */
private class WidthFollowingCase(
    val container: ContainerContent,
) : AtWidthCase {
    override fun assertSettles(name: String) =
        assertPreferencesSettle(emptyMap(), widthFromLeafHeight = mapOf(name to container))
}

/**
 * The container prefers the [leafHeight] of the leaf it holds, plus [padding], and the leaf is no taller than that
 * unless [leafFillsContainer]: a leaf left taller keeps the height of a width it was only asked about.
 */
private fun heightsAround(
    padding: Int = 0,
    leafFillsContainer: Boolean = false,
    leafHeight: (leaf: JComponent) -> Int = { it.preferredSize.height },
): (container: JComponent, leaf: JComponent) -> List<String> =
    { container, leaf ->
        val expected = leafHeight(leaf)
        buildList {
            if (container.preferredSize.height != expected + padding) {
                add("the container prefers ${container.preferredSize.height} around a leaf that takes $expected")
            }
            if (!leafFillsContainer && leaf.height > expected) add("the leaf is ${leaf.height} tall, over $expected")
        }
    }

/**
 * A policy that places its single child at its own size, and whose maximum intrinsic height is [read]'s result for
 * the width it is asked at.
 */
private fun heightReading(read: IntrinsicMeasurable.(width: Int) -> Int): MeasurePolicy =
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
        ): Int = measurables.single().read(width)
    }
