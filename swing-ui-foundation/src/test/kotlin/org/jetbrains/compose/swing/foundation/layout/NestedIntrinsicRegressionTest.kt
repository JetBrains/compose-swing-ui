package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NestedIntrinsicRegressionTest {
    @Test
    fun nestedFiniteQueriesReachTheChildPolicy() = assertNestedIntrinsicSizes(expected = { List(4) { 40 } })

    @Test
    fun nestedFiniteQueriesIncludeTheChildBorder() =
        assertNestedIntrinsicSizes(
            expected = { listOf(45, 45, 42, 42) },
            configure = { it.border = BorderFactory.createEmptyBorder(2, 3, 4, 5) },
        )

    @Test
    fun nestedFiniteQueriesRespectExplicitSwingSizes() =
        assertNestedIntrinsicSizes(
            configure = {
                it.border = BorderFactory.createEmptyBorder(2, 3, 4, 5)
                it.minimumSize = Dimension(17, 19)
                it.preferredSize = Dimension(23, 29)
            },
        )

    @Test
    fun nestedFiniteQueriesUseStockSwingSizes() =
        assertNestedIntrinsicSizes {
            SwingNode(
                factory = {
                    JPanel().apply {
                        minimumSize = Dimension(11, 13)
                        preferredSize = Dimension(17, 19)
                    }
                },
                modifier = SwingModifier.testTag("child"),
            )
        }

    @Test
    fun nestedFiniteQueriesCarryRatioOffers() =
        assertNestedIntrinsicSizes(expected = { listOf(160, 160, 40, 40) }) {
            Box(modifier = SwingModifier.testTag("child")) {
                Layout(IntrinsicQueryPolicy { _, _, extent -> extent / 2 }, SwingModifier.aspectRatio(2f))
            }
        }

    private fun assertNestedIntrinsicSizes(
        expected: (JComponent) -> List<Int> = {
            listOf(it.minimumSize.width, it.preferredSize.width, it.minimumSize.height, it.preferredSize.height)
        },
        configure: (JComponent) -> Unit = {},
        content: @Composable () -> Unit = {
            Layout(IntrinsicQueryPolicy { _, _, extent -> extent / 2 }, SwingModifier.testTag("child"))
        },
    ) = runComposeSwingTest {
        setContent {
            Layout(
                IntrinsicQueryPolicy { children, hook, extent -> query(children.single(), hook, extent) },
                SwingModifier.testTag("parent"),
            ) { content() }
        }
        val child = onNodeWithTag("child").fetch<JComponent>()
        configure(child)
        val parent = onNodeWithTag("parent").fetch<ConstrainedPanel>()
        assertEquals(expected(child), intrinsicSizes(parent, opposite = 80))
    }

    @Test
    fun intrinsicModifiersKeepSettledBoundsAndReplayPlacementAfterFiniteQueries() =
        runComposeSwingTest {
            var measurements = 0
            var childMeasurements = 0
            var placements = 0
            val parentPolicy =
                IntrinsicQueryPolicy(
                    measuring =
                        MeasurePolicy { measurables, _ ->
                            measurements++
                            val child = measurables.single().measure(Constraints.fixed(width = 30, height = 20))
                            layout(width = 30, height = 20) {
                                placements++
                                child.place(0, 0)
                            }
                        },
                ) { children, hook, extent -> query(children.single(), hook, extent) }
            val childPolicy =
                IntrinsicQueryPolicy(
                    measuring =
                        MeasurePolicy { measurables, constraints ->
                            childMeasurements++
                            val children = measurables.map { it.measure(constraints) }
                            layout(constraints.constrainWidth(1), constraints.constrainHeight(1)) {
                                children.forEach { it.place(0, 0) }
                            }
                        },
                ) { _, _, extent -> extent / 2 }
            setContent {
                Box {
                    Layout(parentPolicy, SwingModifier.testTag("parent").size(30, 20)) {
                        Layout(childPolicy, SwingModifier.testTag("child").width(IntrinsicSize.Min)) {
                            SwingNode(factory = { JPanel() }, modifier = SwingModifier.testTag("descendant"))
                        }
                    }
                }
            }
            val parent = onNodeWithTag("parent").fetch<ConstrainedPanel>()
            val child = onNodeWithTag("child").fetch<ConstrainedPanel>()
            val descendant = onNodeWithTag("descendant").fetch<JComponent>()
            val bounds = listOf(child.bounds, descendant.bounds)
            val before = measurements
            val childBefore = childMeasurements
            assertEquals(List(4) { 40 }, intrinsicSizes(parent, opposite = 80))
            val unboundedWidth = Constraints.Infinity / 2
            assertEquals(Dimension(unboundedWidth, unboundedWidth / 2), child.preferredSize)
            assertEquals(child.preferredSize, child.minimumSize)
            assertEquals(bounds, listOf(child.bounds, descendant.bounds))
            val placed = placements
            parent.doLayout()
            child.doLayout()
            awaitIdle()
            assertEquals(before, measurements, "queries preserve the parent's settled measurement")
            assertEquals(childBefore, childMeasurements, "queries preserve the child's settled measurement")
            assertTrue(placements > placed, "the settled result can still place its descendant")
            assertEquals(bounds, listOf(child.bounds, descendant.bounds))
        }

    @Test
    fun twoFiniteArgumentsKeepBothParentMeasurementDependencies() {
        for (hook in 0..3) {
            runComposeSwingTest {
                val first = mutableIntStateOf(11)
                val second = mutableIntStateOf(17)
                var measurements = 0
                var intrinsicSizes = emptyList<Int>()

                val childPolicy =
                    IntrinsicQueryPolicy { _, _, opposite ->
                        when (opposite) {
                            40 -> first.intValue
                            80 -> second.intValue
                            else -> 0
                        }
                    }
                val horizontal = hook < 2
                val parentPolicy =
                    MeasurePolicy { children, constraints ->
                        measurements++
                        val child = children.single()
                        intrinsicSizes = listOf(query(child, hook, 40), query(child, hook, 80))
                        val offset = intrinsicSizes.sum()
                        val placeable = child.measure(Constraints.fixed(1, 1))
                        layout(constraints.constrainWidth(200), constraints.constrainHeight(200)) {
                            placeable.place(if (horizontal) offset else 0, if (horizontal) 0 else offset)
                        }
                    }
                setContent {
                    Layout(parentPolicy, SwingModifier.preferredSize(200, 200)) {
                        Layout(childPolicy, SwingModifier.testTag("nested"))
                    }
                }
                val child = onNodeWithTag("nested").fetch<Component>()
                assertEquals(
                    listOf(11, 17),
                    intrinsicSizes,
                    "hook $hook: both finite arguments must reach the child policy.",
                )
                assertEquals(if (horizontal) Rectangle(28, 0, 1, 1) else Rectangle(0, 28, 1, 1), child.bounds)
                val before = measurements
                awaitIdle()
                assertEquals(before, measurements, "hook $hook: an unchanged parent must keep its measured result.")
                for ((state, value) in listOf(first to 23, second to 29)) {
                    val previouslyMeasured = measurements
                    state.intValue = value
                    awaitIdle()
                    assertTrue(measurements > previouslyMeasured, "hook $hook: each argument retains its dependency")
                    val expected = listOf(first.intValue, second.intValue)
                    assertEquals(expected, intrinsicSizes, "hook $hook: the parent reads both arguments again")
                    val offset = expected.sum()
                    assertEquals(
                        if (horizontal) Rectangle(offset, 0, 1, 1) else Rectangle(0, offset, 1, 1),
                        child.bounds,
                    )
                }
            }
        }
    }

    @Test
    fun finiteHooksKeepTheirIndependentObservedReads() =
        runComposeSwingTest {
            val states = List(4) { mutableIntStateOf(0) }
            var measurements = 0
            setContent {
                Layout(
                    measurePolicy =
                        IntrinsicQueryPolicy(
                            measuring =
                                MeasurePolicy { children, _ ->
                                    measurements++
                                    val placeable = children.single().measure(Constraints.fixed(width = 1, height = 1))
                                    layout(1, 1) { placeable.place(0, 0) }
                                },
                        ) { _, _, _ -> 1 },
                ) {
                    Layout(
                        IntrinsicQueryPolicy { _, hook, extent -> extent / 2 + states[hook].intValue },
                        SwingModifier.testTag("child"),
                    )
                }
            }
            val child = onNodeWithTag("child").fetch<ConstrainedPanel>()
            assertEquals(List(4) { 40 }, intrinsicSizes(child, opposite = 80))
            for ((hook, state) in states.withIndex()) {
                val before = measurements
                state.intValue++
                awaitIdle()
                assertTrue(measurements > before, "hook $hook must retain its own reads")
            }
        }
}

private fun query(
    child: IntrinsicMeasurable,
    hook: Int,
    opposite: Int,
): Int =
    when (hook) {
        0 -> child.minIntrinsicWidth(opposite)
        1 -> child.maxIntrinsicWidth(opposite)
        2 -> child.minIntrinsicHeight(opposite)
        else -> child.maxIntrinsicHeight(opposite)
    }

private fun intrinsicSizes(
    child: Constrainable,
    opposite: Int,
): List<Int> =
    listOf(
        child.minIntrinsicWidth(opposite),
        child.maxIntrinsicWidth(opposite),
        child.minIntrinsicHeight(opposite),
        child.maxIntrinsicHeight(opposite),
    )

internal class IntrinsicQueryPolicy(
    measuring: MeasurePolicy =
        MeasurePolicy { measurables, constraints ->
            val children = measurables.map { it.measure(constraints) }
            layout(constraints.constrainWidth(1), constraints.constrainHeight(1)) {
                children.forEach { it.place(0, 0) }
            }
        },
    private val querySize: (List<IntrinsicMeasurable>, hook: Int, extent: Int) -> Int,
) : MeasurePolicy by measuring {
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = querySize(measurables, 0, height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = querySize(measurables, 1, height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = querySize(measurables, 2, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = querySize(measurables, 3, width)
}
