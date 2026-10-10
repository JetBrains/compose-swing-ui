package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExtendWith(ComposedPanels::class)
class NestedIntrinsicRegressionTest {
    @Test
    fun aFiniteIntrinsicQuestionReachesANestedFoundationPolicy() =
        runSwingTest {
            val childPolicy = finitePolicy()
            val parent = ConstrainedPanel(MeasurePolicyLayout(finitePolicy()))
            val child = ConstrainedPanel(MeasurePolicyLayout(childPolicy))
            parent.add(child)
            val measurable = parent.policyLayout.measurables.of(child)
            val actual =
                listOf(
                    measurable.minIntrinsicWidth(80),
                    measurable.maxIntrinsicWidth(80),
                    measurable.minIntrinsicHeight(80),
                    measurable.maxIntrinsicHeight(80),
                )
            assertEquals(listOf(40, 40, 40, 40), actual, "Every finite intrinsic question must reach the child policy.")
        }

    @Test
    fun aBoxPreservesFiniteAspectRatioOffers() =
        runComposeSwingTest {
            val childPolicy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult = layout(constraints.constrainWidth(50), constraints.constrainHeight(40)) {}

                    override fun IntrinsicMeasureScope.minIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = 50

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = 50

                    override fun IntrinsicMeasureScope.minIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int = 40

                    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int = 40
                }
            setContent {
                Column {
                    Box(modifier = SwingModifier.testTag("box")) {
                        Layout(content = {}, measurePolicy = childPolicy, modifier = SwingModifier.aspectRatio(2f))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<ConstrainedPanel>()
            val parent = box.parent as ConstrainedPanel
            val measurable = parent.policyLayout.measurables.of(box)
            assertEquals(
                80,
                measurable.maxIntrinsicWidth(40),
                "A 2:1 aspect ratio offered 40px in height asks for 80px.",
            )
        }

    @Test
    fun bordersTakeSpaceInBothAxesAndExplicitSizesRemainAuthoritative() =
        runSwingTest {
            val parent = ConstrainedPanel(MeasurePolicyLayout(finitePolicy()))
            val child = ConstrainedPanel(MeasurePolicyLayout(finitePolicy()))
            child.border = BorderFactory.createEmptyBorder(2, 3, 4, 5)
            parent.add(child)
            val measurable = parent.policyLayout.measurables.of(child)
            assertEquals(listOf(45, 45, 42, 42), answers(measurable, 80))
            child.minimumSize = Dimension(17, 19)
            child.preferredSize = Dimension(23, 29)
            assertEquals(listOf(17, 23, 19, 29), answers(measurable, 80))
        }

    @Test
    fun stockChildrenKeepTheirNativeSizeAnswers() =
        runSwingTest {
            val parent = ConstrainedPanel(MeasurePolicyLayout(finitePolicy()))
            val child =
                JPanel().apply {
                    minimumSize = Dimension(11, 13)
                    preferredSize = Dimension(17, 19)
                }
            parent.add(child)
            assertEquals(listOf(11, 17, 13, 19), answers(parent.policyLayout.measurables.of(child), 80))
        }

    @Test
    fun intrinsicModifiersForwardFiniteQuestionsWithoutReplacingTheSettledResult() =
        runSwingTest {
            val parent =
                composed(
                    ConstrainedPanel(
                        MeasurePolicyLayout(
                            MeasurePolicy { children, _ ->
                                val placeable = children.firstOrNull()?.measure(Constraints.fixed(30, 20))
                                layout(30, 20) { placeable?.place(0, 0) }
                            },
                        ),
                    ),
                )
            val child = composed(ConstrainedPanel(MeasurePolicyLayout(finitePolicy())))
            parent.add(child)
            parent.setSize(30, 20)
            parent.doLayout()
            child.doLayout()
            val state = child.policyLayout.measurables
            val measured = state.measuredSize(Constraints.fixed(30, 20))
            val retained = checkNotNull(state.measured)
            val placed = checkNotNull(child.policyLayout.lastPlaced)
            val measurable = parent.policyLayout.measurables.of(child)
            measurable.declare(null, layoutChainOf { SwingModifier.width(IntrinsicSize.Min) })
            assertEquals(listOf(40, 40, 40, 40), answers(measurable, 80))
            assertSame(retained, state.measured)
            assertSame(placed, child.policyLayout.lastPlaced)
            assertEquals(Dimension(30, 20), measured)
            assertEquals(Dimension(1_073_741_823, 536_870_911), child.preferredSize)
            assertEquals(Dimension(1_073_741_823, 536_870_911), child.minimumSize)
            assertSame(retained, state.measured)
            assertSame(placed, child.policyLayout.lastPlaced)
        }

    @Test
    fun twoFiniteArgumentsKeepBothParentMeasurementDependencies() {
        for (hook in 0..3) {
            runComposeSwingTest {
                val first = mutableIntStateOf(11)
                val second = mutableIntStateOf(17)
                var measurements = 0
                var answers = emptyList<Int>()

                fun answer(opposite: Int): Int =
                    when (opposite) {
                        40 -> first.intValue
                        80 -> second.intValue
                        else -> 0
                    }
                val childPolicy =
                    object : MeasurePolicy {
                        override fun MeasureScope.measure(
                            measurables: List<Measurable>,
                            constraints: Constraints,
                        ): MeasureResult = layout(1, 1) {}

                        override fun IntrinsicMeasureScope.minIntrinsicWidth(
                            measurables: List<IntrinsicMeasurable>,
                            height: Int,
                        ): Int = answer(height)

                        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                            measurables: List<IntrinsicMeasurable>,
                            height: Int,
                        ): Int = answer(height)

                        override fun IntrinsicMeasureScope.minIntrinsicHeight(
                            measurables: List<IntrinsicMeasurable>,
                            width: Int,
                        ): Int = answer(width)

                        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                            measurables: List<IntrinsicMeasurable>,
                            width: Int,
                        ): Int = answer(width)
                    }
                val horizontal = hook < 2
                val parentPolicy =
                    MeasurePolicy { children, constraints ->
                        measurements++
                        val child = children.single()
                        answers = listOf(query(child, hook, 40), query(child, hook, 80))
                        val offset = answers.sum()
                        val placeable = child.measure(Constraints.fixed(1, 1))
                        layout(constraints.constrainWidth(200), constraints.constrainHeight(200)) {
                            placeable.place(if (horizontal) offset else 0, if (horizontal) 0 else offset)
                        }
                    }
                setContent {
                    Layout(
                        content = {
                            Layout(
                                content = {},
                                measurePolicy = childPolicy,
                                modifier = SwingModifier.testTag("nested"),
                            )
                        },
                        measurePolicy = parentPolicy,
                        modifier = SwingModifier.preferredSize(200, 200),
                    )
                }
                val child = onNodeWithTag("nested").fetch<Component>()
                assertEquals(listOf(11, 17), answers, "hook $hook: both finite arguments must reach the child policy.")
                assertEquals(if (horizontal) Rectangle(28, 0, 1, 1) else Rectangle(0, 28, 1, 1), child.bounds)
                val before = measurements
                awaitIdle()
                assertEquals(before, measurements, "hook $hook: an unchanged parent must keep its measured result.")
                first.intValue = 23
                awaitIdle()
                assertTrue(measurements > before, "hook $hook: the first dependency must remeasure the parent.")
                assertEquals(listOf(23, 17), answers, "hook $hook: and the parent asks again")
                assertEquals(if (horizontal) Rectangle(40, 0, 1, 1) else Rectangle(0, 40, 1, 1), child.bounds)
                val afterFirst = measurements
                second.intValue = 29
                awaitIdle()
                assertTrue(measurements > afterFirst, "hook $hook: the second dependency must remeasure the parent.")
                assertEquals(listOf(23, 29), answers, "hook $hook: and the parent asks again")
                assertEquals(if (horizontal) Rectangle(52, 0, 1, 1) else Rectangle(0, 52, 1, 1), child.bounds)
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

    @Test
    fun finiteHooksKeepTheirIndependentObservedReads() =
        runSwingTest {
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
            val child = IntrinsicRevalidationPanel(policy)
            composed(child)
            val parent = ConstrainedPanel(MeasurePolicyLayout(finitePolicy()))
            parent.add(child)
            assertEquals(listOf(40, 40, 40, 40), answers(parent.policyLayout.measurables.of(child), 80))
            for (state in states) {
                val before = child.revalidations
                state.intValue++
                Snapshot.sendApplyNotifications()
                assertTrue(child.revalidations > before, "Every finite hook must retain its own reads.")
            }
        }

    private fun answers(
        measurable: IntrinsicMeasurable,
        opposite: Int,
    ): List<Int> =
        listOf(
            measurable.minIntrinsicWidth(opposite),
            measurable.maxIntrinsicWidth(opposite),
            measurable.minIntrinsicHeight(opposite),
            measurable.maxIntrinsicHeight(opposite),
        )
}

private fun finitePolicy(): MeasurePolicy =
    object : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(constraints.constrainWidth(1), constraints.constrainHeight(1)) {}

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = height / 2

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = height / 2

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = width / 2

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = width / 2
    }

private class IntrinsicRevalidationPanel(
    policy: MeasurePolicy,
) : ConstrainedPanel(MeasurePolicyLayout(policy)) {
    var revalidations: Int = 0

    override fun revalidate() {
        revalidations++
        super.revalidate()
    }
}
