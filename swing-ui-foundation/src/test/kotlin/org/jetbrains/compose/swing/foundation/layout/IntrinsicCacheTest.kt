package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntrinsicCacheTest {
    @Test
    fun aRepeatedQueryReusesItsResultAndKeepsItsStateDependency() {
        for (hook in 0..3) {
            runComposeSwingTest {
                val parentState = mutableIntStateOf(0)
                val intrinsicState = mutableIntStateOf(17)
                var parentMeasurements = 0
                var intrinsicCalls = 0
                var answer = 0
                val parentPolicy =
                    IntrinsicQueryPolicy(
                        measuring =
                            MeasurePolicy { measurables, constraints ->
                                parentMeasurements++
                                val width = 100 + parentState.intValue - parentState.intValue
                                val child = measurables.single()
                                answer = query(child, hook, 80)
                                val placeable = child.measure(Constraints.fixed(1, 1))
                                layout(constraints.constrainWidth(width), constraints.constrainHeight(100)) {
                                    placeable.place(answer, 0)
                                }
                            },
                    ) { _, _, _ -> 1 }
                val childPolicy =
                    IntrinsicQueryPolicy { _, queriedHook, _ ->
                        if (queriedHook == hook) {
                            intrinsicCalls++
                            intrinsicState.intValue
                        } else {
                            1
                        }
                    }
                setContent {
                    Layout(parentPolicy, SwingModifier.testTag("parent")) {
                        Layout(childPolicy, SwingModifier.testTag("child"))
                    }
                }

                val child = onNodeWithTag("child").fetch<ConstrainedPanel>()
                assertEquals(17, answer, "hook $hook returns the child's intrinsic value")
                root.validate()
                assertTrue(child.isDisplayable, "hook $hook runs under the peered test root")
                assertTrue(child.isValid, "hook $hook has a valid child before reusing an intrinsic result")

                parentState.intValue++
                awaitIdle()
                val callsAfterWarmQuery = intrinsicCalls
                assertTrue(callsAfterWarmQuery > 0, "hook $hook reaches the child's policy")

                val measuredBeforeCacheHit = parentMeasurements
                parentState.intValue++
                awaitIdle()
                assertTrue(parentMeasurements > measuredBeforeCacheHit, "hook $hook remeasures its parent")
                assertEquals(callsAfterWarmQuery, intrinsicCalls, "hook $hook reuses the same query result")

                intrinsicState.intValue = 31
                awaitIdle()
                assertEquals(31, answer, "hook $hook observes a changed intrinsic value")
                assertTrue(intrinsicCalls > callsAfterWarmQuery, "hook $hook recomputes after its state changes")
                assertEquals(31, child.x, "hook $hook updates the visible child placement")
            }
        }
    }

    @Test
    fun aPeerlessPanelReadsChangedStockChildPreferencesAgain() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            val child = JPanel().apply { preferredSize = Dimension(20, 30) }
            lateinit var context: CompositionContext
            setContent { context = rememberCompositionContext() }
            panel.setContent(parent = context) { SwingNode(factory = { child }) }
            awaitIdle()

            assertFalse(panel.isDisplayable, "the panel has no peer")
            assertEquals(20, panel.maxIntrinsicWidth(Constraints.Infinity))
            child.preferredSize = Dimension(45, 55)
            assertEquals(45, panel.maxIntrinsicWidth(Constraints.Infinity))
        }

    @Test
    fun aPeerlessPanelReadsChangedSnapshotStateAgain() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            val intrinsicHeight = mutableIntStateOf(23)
            lateinit var context: CompositionContext
            setContent { context = rememberCompositionContext() }
            panel.setContent(parent = context) {
                Layout(
                    IntrinsicQueryPolicy { _, hook, _ -> if (hook == 3) intrinsicHeight.intValue else 1 },
                )
            }
            awaitIdle()

            assertFalse(panel.isDisplayable, "the panel has no peer")
            assertEquals(23, panel.maxIntrinsicHeight(80))
            intrinsicHeight.intValue = 41
            awaitIdle()
            assertEquals(41, panel.maxIntrinsicHeight(80))
        }

    @Test
    fun aFilteredPreferredSizeQueryDoesNotReplaceTheFullIntrinsicChildren() =
        runComposeSwingTest {
            val defaultPolicy =
                MeasurePolicy { measurables, constraints ->
                    val children = measurables.map { it.measure(constraints) }
                    layout(children.maxOfOrNull { it.width } ?: 0, children.maxOfOrNull { it.height } ?: 0) {
                        children.forEach { it.place(0, 0) }
                    }
                }
            val policy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult = with(defaultPolicy) { measure(measurables, constraints) }

                    override fun IntrinsicMeasureScope.preferredSize(
                        measurables: List<IntrinsicMeasurable>,
                        assignedWidth: Int?,
                    ): Dimension = with(defaultPolicy) { preferredSize(listOf(measurables.first()), assignedWidth) }
                }
            setContent {
                Layout(policy, SwingModifier.testTag("parent")) {
                    SwingNode(factory = { JPanel().apply { preferredSize = Dimension(20, 10) } })
                    SwingNode(factory = { JPanel().apply { preferredSize = Dimension(80, 10) } })
                }
            }

            val parent = onNodeWithTag("parent").fetch<ConstrainedPanel>()
            assertEquals(20, parent.preferredSize.width)
            assertEquals(80, parent.maxIntrinsicWidth(Constraints.Infinity))
        }
}

private fun query(
    child: IntrinsicMeasurable,
    hook: Int,
    extent: Int,
): Int =
    when (hook) {
        0 -> child.minIntrinsicWidth(extent)
        1 -> child.maxIntrinsicWidth(extent)
        2 -> child.minIntrinsicHeight(extent)
        else -> child.maxIntrinsicHeight(extent)
    }
