// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntrinsicCacheTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("hookCases")
    fun aRepeatedQueryReusesItsResultAndKeepsItsStateDependency(hook: Int) {
        runComposeSwingTest {
            val parentState = mutableIntStateOf(0)
            val intrinsicState = mutableIntStateOf(17)
            var parentMeasurements = 0
            var intrinsicCalls = 0
            var result = 0
            val parentPolicy =
                IntrinsicQueryPolicy(
                    measuring =
                        MeasurePolicy { measurables, constraints ->
                            parentMeasurements++
                            val width = 100 + parentState.intValue - parentState.intValue
                            val child = measurables.single()
                            result = query(child, hook, 80)
                            val placeable = child.measure(Constraints.fixed(1, 1))
                            layout(constraints.constrainWidth(width), constraints.constrainHeight(100)) {
                                placeable.place(result, 0)
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

            assertEquals(17, result, "hook $hook returns the child's intrinsic value")
            root.validate()
            onNodeWithTag("child")
                .assertIsOfType<ConstrainedPanel>()
                .assertProperty(true, "hook $hook runs under the peered test root") { isDisplayable }
            onNodeWithTag("child").assertProperty(
                true,
                "hook $hook has a valid child before reusing an intrinsic result",
            ) { isValid }

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
            assertEquals(31, result, "hook $hook observes a changed intrinsic value")
            assertTrue(intrinsicCalls > callsAfterWarmQuery, "hook $hook recomputes after its state changes")
            onNodeWithTag("child").assertProperty(31, "hook $hook updates the visible child placement") { x }
        }
    }

    @Test
    fun aPeerlessPanelReadsChangedStockChildPreferencesAgain() =
        runComposeSwingTest {
            setContent {
                PeerlessPanelHost {
                    SwingNode(
                        factory = { JPanel() },
                        modifier = SwingModifier.testTag("child"),
                        update = { set(Dimension(20, 30)) { preferredSize = it } },
                    )
                }
            }
            awaitIdle()

            onNodeOfType<PeerlessPanelMount>().assertProperty(false, "the panel has no peer") { panel.isDisplayable }
            onNodeOfType<PeerlessPanelMount>()
                .assertProperty(20) { panel.maxIntrinsicWidth(Constraints.Infinity) }
            // The test changes the child's preferred size directly, outside composition, which is its subject.
            val child = onNodeWithTag("child").fetch<JPanel>()
            child.preferredSize = Dimension(45, 55)
            onNodeOfType<PeerlessPanelMount>()
                .assertProperty(45) { panel.maxIntrinsicWidth(Constraints.Infinity) }
        }

    @Test
    fun aPeerlessPanelReadsChangedSnapshotStateAgain() =
        runComposeSwingTest {
            val intrinsicHeight = mutableIntStateOf(23)
            setContent {
                PeerlessPanelHost {
                    Layout(
                        IntrinsicQueryPolicy { _, hook, _ -> if (hook == 3) intrinsicHeight.intValue else 1 },
                    )
                }
            }
            awaitIdle()

            onNodeOfType<PeerlessPanelMount>().assertProperty(false, "the panel has no peer") { panel.isDisplayable }
            onNodeOfType<PeerlessPanelMount>().assertProperty(23) { panel.maxIntrinsicHeight(80) }
            intrinsicHeight.intValue = 41
            awaitIdle()
            onNodeOfType<PeerlessPanelMount>().assertProperty(41) { panel.maxIntrinsicHeight(80) }
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

    companion object {
        @JvmStatic
        fun hookCases(): List<Arguments> = (0..3).map { Arguments.of(Named.of("hook $it", it)) }
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

private class PeerlessPanelMount : JPanel() {
    val panel = ConstrainedPanel()
    private var mountedUnder: CompositionContext? = null
    private var mount: DisposableHandle? = null

    fun mount(
        under: CompositionContext,
        content: State<@Composable () -> Unit>,
    ) {
        if (under === mountedUnder) return
        mount?.dispose()
        mountedUnder = under
        mount = panel.setContent(parent = under) { content.value() }
    }

    fun release() {
        mount?.dispose()
        mount = null
    }
}

@Composable
private fun PeerlessPanelHost(content: @Composable () -> Unit) {
    val parent = rememberCompositionContext()
    val current = rememberUpdatedState(content)
    SwingNode(
        factory = { PeerlessPanelMount() },
        update = { set(parent) { mount(it, current) } },
        onRelease = { release() },
    )
}
