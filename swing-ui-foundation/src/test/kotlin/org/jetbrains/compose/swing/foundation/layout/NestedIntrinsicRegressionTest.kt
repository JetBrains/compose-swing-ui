package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
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
    fun nestedIntrinsicsStayIndependentOfSwingPreferredSize() = assertIndependentIntrinsicSizes()

    @Test
    fun nestedIntrinsicsRespectExplicitSwingSizesWithAnIndependentPreferredPolicy() =
        assertIndependentIntrinsicSizes(explicitSizes = true)

    @Test
    fun nestedMaxIntrinsicHeightIgnoresAWarmedSwingPreferredSize() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("parent")) {
                    Layout(IndependentPreferredPolicy(), SwingModifier.testTag("child"))
                }
            }
            val parent = onNodeWithTag("parent").fetch<ConstrainedPanel>()
            val child = onNodeWithTag("child").fetch<ConstrainedPanel>()
            assertTrue(child.isValid, "the child is valid after layout")
            parent.maxIntrinsicWidth(Constraints.Infinity)
            assertEquals(7, child.maxIntrinsicHeight(345))
            assertEquals(7, parent.maxIntrinsicHeight(345))
        }

    @Test
    fun nestedIntrinsicsRetainObservedReadsWithAnIndependentPreferredPolicy() =
        runComposeSwingTest {
            val states = List(4) { mutableIntStateOf(it + 1) }
            var measurements = 0
            var sizes = emptyList<Int>()
            val parentPolicy =
                MeasurePolicy { children, _ ->
                    measurements++
                    val child = children.single()
                    sizes =
                        listOf(80, Constraints.Infinity).flatMap { extent ->
                            (0..3).map { query(child, it, extent) }
                        }
                    val placeable = child.measure(Constraints.fixed(1, 1))
                    layout(200, 200) { placeable.place(sizes.sum(), 0) }
                }
            setContent {
                Layout(parentPolicy) {
                    Layout(
                        IndependentPreferredPolicy { states[it].intValue },
                        SwingModifier.testTag("child"),
                    )
                }
            }
            val child = onNodeWithTag("child").fetch<ConstrainedPanel>()
            assertEquals(listOf(1, 2, 3, 4, 1, 2, 3, 4), sizes)
            assertEquals(20, child.x)
            child.preferredSize
            for (state in states) {
                val before = measurements
                state.intValue++
                awaitIdle()
                val expected = states.map { it.intValue }
                assertTrue(measurements > before, "each intrinsic read invalidates the parent measurement")
                assertEquals(expected + expected, sizes)
                assertEquals(sizes.sum(), child.x)
                assertEquals(Dimension(345, 123), child.preferredSize)
            }
        }

    private class IndependentPreferredPolicy(
        intrinsicSize: (Int) -> Int = { listOf(5, 11, 3, 7)[it] },
    ) : MeasurePolicy by IntrinsicQueryPolicy(querySize = { _, hook, _ -> intrinsicSize(hook) }) {
        override fun IntrinsicMeasureScope.preferredSize(
            measurables: List<IntrinsicMeasurable>,
            assignedWidth: Int?,
        ): Dimension = Dimension(345, 123)
    }

    private fun assertIndependentIntrinsicSizes(explicitSizes: Boolean = false) =
        runComposeSwingTest {
            setContent {
                Layout(
                    IntrinsicQueryPolicy { children, hook, extent -> query(children.single(), hook, extent) },
                    SwingModifier.testTag("parent"),
                ) {
                    Layout(IndependentPreferredPolicy(), SwingModifier.testTag("child"))
                }
            }
            val child = onNodeWithTag("child").fetch<ConstrainedPanel>()
            val parent = onNodeWithTag("parent").fetch<ConstrainedPanel>()
            if (explicitSizes) {
                child.minimumSize = Dimension(17, 19)
                child.preferredSize = Dimension(23, 29)
            }
            val expected = if (explicitSizes) listOf(17, 23, 19, 29) else listOf(5, 11, 3, 7)
            for (warm in listOf(false, true)) {
                child.invalidate()
                if (warm) {
                    root.validate()
                    assertTrue(child.isValid, "the warmed child is valid")
                    assertEquals(if (explicitSizes) Dimension(23, 29) else Dimension(345, 123), child.preferredSize)
                    parent.preferredSize
                }
                for (opposite in listOf(80, 345, Constraints.Infinity)) {
                    assertEquals(expected, intrinsicSizes(child, opposite), "direct, warm=$warm, opposite=$opposite")
                    assertEquals(expected, intrinsicSizes(parent, opposite), "nested, warm=$warm, opposite=$opposite")
                }
            }
        }

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

    @Test
    fun finiteHooksKeepTheirIndependentObservedReadsInAPublicHost() =
        runComposeSwingTest {
            val states = List(4) { mutableIntStateOf(0) }
            val policy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult = layout(1, 1) {}

                    override fun IntrinsicMeasureScope.minIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = height / 2 + states[0].intValue

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = height / 2 + states[1].intValue

                    override fun IntrinsicMeasureScope.minIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int = width / 2 + states[2].intValue

                    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int = width / 2 + states[3].intValue
                }
            val child = ConstrainedPanel(policy)
            val parent = ConstrainedPanel()
            setContent { SwingNode(factory = { parent }) { SwingNode(factory = { child }) } }

            fun reports() =
                listOf(
                    parent.minIntrinsicWidth(80),
                    parent.maxIntrinsicWidth(80),
                    parent.minIntrinsicHeight(80),
                    parent.maxIntrinsicHeight(80),
                )
            assertEquals(listOf(40, 40, 40, 40), reports())
            for ((hook, state) in states.withIndex()) {
                withRecordedRepaints { recorder ->
                    state.intValue++
                    awaitIdle()
                    assertTrue(child in recorder.relayouts, "Every finite hook must retain its own reads.")
                }
                assertEquals(
                    List(4) { if (it <= hook) 41 else 40 },
                    reports(),
                    "hook $hook reports its changed read and the hooks before it keep theirs",
                )
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

/**
 * Measures with [measuring] and answers each of the four intrinsic queries with [querySize]. It leaves
 * `measureAtAssignedSize` and `preferredSize` to the interface defaults, which reach the overridden intrinsics.
 */
internal class IntrinsicQueryPolicy(
    private val measuring: MeasurePolicy =
        MeasurePolicy { measurables, constraints ->
            val children = measurables.map { it.measure(constraints) }
            layout(constraints.constrainWidth(1), constraints.constrainHeight(1)) {
                children.forEach { it.place(0, 0) }
            }
        },
    private val querySize: (List<IntrinsicMeasurable>, hook: Int, extent: Int) -> Int,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult = with(measuring) { measure(measurables, constraints) }

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
