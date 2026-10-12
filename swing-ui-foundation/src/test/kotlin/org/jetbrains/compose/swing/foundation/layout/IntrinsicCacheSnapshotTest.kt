package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntrinsicCacheSnapshotTest {
    @ParameterizedTest(name = "hook={0}")
    @ValueSource(ints = [0, 1, 2, 3])
    fun applyingSnapshotStateOnTheEdtDefersIntrinsicPoliciesUntilTheNextQuery(hook: Int) =
        runComposeSwingTest {
            val state = mutableIntStateOf(17)
            var calls = 0
            setContent {
                Layout(
                    IntrinsicQueryPolicy { _, queriedHook, _ ->
                        if (queriedHook == hook) {
                            calls++
                            state.intValue
                        } else {
                            1
                        }
                    },
                    SwingModifier.testTag("panel"),
                )
            }
            val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
            root.validate()
            assertTrue(panel.isDisplayable, "hook $hook has a peered host")
            assertTrue(panel.isValid, "hook $hook starts with valid geometry")
            assertEquals(17, panel.query(hook), "hook $hook warms the intrinsic result")
            val warmCalls = calls
            assertEquals(17, panel.query(hook), "hook $hook repeats the same query")
            assertEquals(warmCalls, calls, "hook $hook reuses its warm result")

            state.intValue = 31
            Snapshot.sendApplyNotifications()
            assertEquals(warmCalls, calls, "hook $hook must not run its policy during snapshot apply")
            assertEquals(31, panel.query(hook), "hook $hook answers the next query with fresh state")
            assertEquals(warmCalls + 1, calls, "hook $hook computes the fresh result once")
        }

    @ParameterizedTest(name = "hook={0}")
    @ValueSource(ints = [0, 1, 2, 3])
    fun applyingSnapshotStateInTheBackgroundDoesNotRunIntrinsicPoliciesOffTheEdt(hook: Int) =
        runComposeSwingTest {
            val state = mutableIntStateOf(17)
            val calls = AtomicInteger()
            val offEdtCalls = AtomicInteger()
            setContent {
                Layout(
                    IntrinsicQueryPolicy { _, queriedHook, _ ->
                        if (queriedHook == hook) {
                            calls.incrementAndGet()
                            if (!SwingUtilities.isEventDispatchThread()) offEdtCalls.incrementAndGet()
                            state.intValue
                        } else {
                            1
                        }
                    },
                    SwingModifier.testTag("panel"),
                )
            }
            val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
            root.validate()
            assertTrue(panel.isDisplayable, "hook $hook has a peered host")
            assertTrue(panel.isValid, "hook $hook starts with valid geometry")
            assertEquals(17, panel.query(hook), "hook $hook warms the intrinsic result")
            val warmCalls = calls.get()
            val apply =
                FutureTask {
                    runCatching { Snapshot.withMutableSnapshot { state.intValue = 31 } }.exceptionOrNull()
                }
            Thread(apply, "intrinsic-snapshot-apply").apply { isDaemon = true }.start()

            assertNull(apply.get(5, TimeUnit.SECONDS), "hook $hook applies background state without an exception")
            assertEquals(0, offEdtCalls.get(), "hook $hook keeps intrinsic policies on the EDT")
            assertEquals(warmCalls, calls.get(), "hook $hook defers computation until a query or layout")
            awaitIdle()
            assertTrue(panel.isValid, "hook $hook settles the queued EDT invalidation")
            val callsAfterIdle = calls.get()
            assertEquals(31, panel.query(hook), "hook $hook answers the next EDT query with fresh state")
            assertTrue(calls.get() <= callsAfterIdle + 1, "hook $hook computes at most once for the fresh query")
            val freshCalls = calls.get()
            assertEquals(31, panel.query(hook), "hook $hook repeats the fresh EDT query")
            assertEquals(freshCalls, calls.get(), "hook $hook reuses the fresh result")
        }

    @ParameterizedTest(name = "hook={0}")
    @ValueSource(ints = [0, 1, 2, 3])
    fun aChangedIntrinsicExtentComputesItsOwnResult(hook: Int) =
        runComposeSwingTest {
            var calls = 0
            setContent {
                Layout(
                    IntrinsicQueryPolicy { _, queriedHook, extent ->
                        if (queriedHook == hook) {
                            calls++
                            extent.coerceAtMost(100) + 17
                        } else {
                            1
                        }
                    },
                    SwingModifier.testTag("panel"),
                )
            }
            val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
            root.validate()
            assertTrue(panel.isDisplayable, "hook $hook has a peered host")
            assertTrue(panel.isValid, "hook $hook starts with valid geometry")
            assertEquals(97, panel.query(hook, 80), "hook $hook warms the first extent")
            val warmCalls = calls
            assertEquals(112, panel.query(hook, 95), "hook $hook answers the changed extent")
            assertEquals(warmCalls + 1, calls, "hook $hook computes the changed extent once")
            assertEquals(112, panel.query(hook, 95), "hook $hook repeats the changed extent")
            assertEquals(warmCalls + 1, calls, "hook $hook reuses the changed extent")
        }

    @ParameterizedTest(name = "hook={0}, firstExtent={1}")
    @CsvSource("0,40", "1,40", "2,40", "3,40", "0,80", "1,80", "2,80", "3,80")
    fun aCachedQueryKeepsItsParentDependencyWhenAnotherExtentIsQueried(
        hook: Int,
        firstExtent: Int,
    ) = runComposeSwingTest {
        val fixture = IntrinsicDependencyFixture(hook, firstExtent)
        setContent {
            Layout(fixture.parentPolicy) {
                Layout(fixture.childPolicy, SwingModifier.testTag("child"))
            }
        }
        val child = onNodeWithTag("child").fetch<ConstrainedPanel>()
        root.validate()
        assertTrue(child.isDisplayable, "hook $hook has a peered child")
        assertTrue(child.isValid, "hook $hook starts with valid child geometry")
        fixture.parentState.intValue++
        awaitIdle()
        val warmCalls = fixture.firstCalls
        assertTrue(warmCalls > 0, "hook $hook warms the first extent")
        fixture.parentState.intValue++
        awaitIdle()
        assertEquals(warmCalls, fixture.firstCalls, "hook $hook reuses the first extent in a new parent pass")

        val callsBeforeSecondQuery = fixture.secondCalls
        fixture.queryBoth.intValue = 1
        awaitIdle()
        assertEquals(
            warmCalls,
            fixture.firstCallsBeforeSecondQuery,
            "hook $hook hits the first extent before querying the second",
        )
        assertEquals(warmCalls + 1, fixture.firstCalls, "hook $hook restores the first extent's dependency once")
        assertEquals(callsBeforeSecondQuery + 1, fixture.secondCalls, "hook $hook computes the second extent once")
        assertEquals(40, child.x, "hook $hook places with both intrinsic answers")
        val measurementsBeforeChange = fixture.measurements

        fixture.firstState.intValue = 31
        awaitIdle()
        assertTrue(
            fixture.measurements > measurementsBeforeChange,
            "hook $hook keeps the first answer's parent dependency",
        )
        assertEquals(54, child.x, "hook $hook places with the changed first answer")
    }

    @ParameterizedTest(name = "hook={0}")
    @ValueSource(ints = [2, 3])
    fun aPlacementReplayRefreshesAnAncestorsStockChildIntrinsicHeight(hook: Int) =
        runComposeSwingTest {
            val width = mutableIntStateOf(40)
            val leaf = widthFollowingStockPanel()
            var intrinsicCalls = 0
            lateinit var ancestor: ConstrainedPanel
            var captureReplay = false
            var replayedHeight: Int? = null
            val ancestorPolicy =
                IntrinsicQueryPolicy { children, queriedHook, extent ->
                    if (queriedHook == hook) intrinsicCalls++
                    children.single().query(queriedHook, extent)
                }
            val innerPolicy =
                IntrinsicQueryPolicy(
                    measuring =
                        { children, constraints ->
                            layout(constraints.constrainWidth(100), constraints.constrainHeight(100)) {
                                children.single().measure(Constraints.fixed(width.intValue, 1)).place(0, 0)
                                if (captureReplay && replayedHeight == null) {
                                    replayedHeight = ancestor.query(hook, Constraints.Infinity)
                                }
                            }
                        },
                ) { children, queriedHook, extent ->
                    if (queriedHook == hook) children.single().query(hook, extent) else 1
                }
            setContent {
                Layout(ancestorPolicy, SwingModifier.testTag("ancestor")) {
                    Layout(innerPolicy, SwingModifier.testTag("inner")) {
                        SwingNode(factory = { leaf })
                    }
                }
            }
            ancestor = onNodeWithTag("ancestor").fetch<ConstrainedPanel>()
            val inner = onNodeWithTag("inner").fetch<ConstrainedPanel>()
            root.validate()
            assertTrue(ancestor.isValid, "hook $hook starts with a valid ancestor")
            assertTrue(inner.isValid, "hook $hook starts with a valid inner panel")
            assertTrue(leaf.isValid, "hook $hook starts with a valid stock child")
            assertEquals(40, leaf.width, "hook $hook places the stock child at its first width")
            assertEquals(40, ancestor.query(hook, Constraints.Infinity), "hook $hook warms the ancestor's result")
            val warmCalls = intrinsicCalls
            assertEquals(40, ancestor.query(hook, Constraints.Infinity), "hook $hook repeats the warm query")
            assertEquals(warmCalls, intrinsicCalls, "hook $hook reuses the ancestor's result")

            captureReplay = true
            width.intValue = 80
            awaitIdle()
            assertEquals(80, replayedHeight, "hook $hook reads fresh height inside the descendant's first replay")
            assertEquals(80, leaf.width, "hook $hook replays placement at the changed stock-child width")
            assertEquals(1, leaf.height, "hook $hook keeps the height the placement grants")
            assertTrue(ancestor.isValid, "hook $hook keeps the ancestor valid after placement replay")
            assertEquals(80, leaf.preferredSize.height, "hook $hook changes the stock child's preferred height")
            assertEquals(
                80,
                ancestor.query(hook, Constraints.Infinity),
                "hook $hook refreshes the ancestor after its descendant's placement replay",
            )
            assertEquals(80, leaf.width, "hook $hook leaves the stock child at its replayed width")
            assertEquals(1, leaf.height, "hook $hook leaves the stock child at its replayed height")
        }

    @Test
    fun aValidHostedPanelReadsChangedStockLabelTextAfterWarmingItsIntrinsics() =
        runComposeSwingTest {
            val label = JLabel("Short")
            val reference = JLabel("Short")
            setContent {
                Box(modifier = SwingModifier.testTag("panel")) {
                    SwingNode(factory = { label })
                }
            }
            val panel = onNodeWithTag("panel").fetch<ConstrainedPanel>()
            root.validate()
            assertTrue(panel.isDisplayable, "the panel has a peered host")
            assertTrue(panel.isValid, "the panel starts with valid geometry")
            assertTrue(label.isValid, "the stock child starts with valid geometry")
            val warmWidth = panel.maxIntrinsicWidth(Constraints.Infinity)
            assertEquals(reference.preferredSize.width, warmWidth, "the warm query matches raw Swing")
            assertEquals(warmWidth, panel.maxIntrinsicWidth(Constraints.Infinity), "the repeated query stays warm")

            label.text = "A much longer stock Swing label"
            reference.text = label.text
            val changedWidth = reference.preferredSize.width
            assertTrue(changedWidth > warmWidth, "the changed text has a distinct raw Swing width")
            assertEquals(
                changedWidth,
                panel.maxIntrinsicWidth(Constraints.Infinity),
                "the next query reads the new text",
            )
        }
}

private class IntrinsicDependencyFixture(
    private val hook: Int,
    private val firstExtent: Int,
) {
    val firstState = mutableIntStateOf(17)
    val secondState = mutableIntStateOf(23)
    val parentState = mutableIntStateOf(0)
    val queryBoth = mutableIntStateOf(0)
    val secondExtent = if (firstExtent == 40) 80 else 40
    var firstCalls = 0
    var firstCallsBeforeSecondQuery = 0
    var secondCalls = 0
    var measurements = 0
    val parentPolicy =
        IntrinsicQueryPolicy(
            measuring =
                { measurables, constraints ->
                    measurements++
                    val width = 100 + parentState.intValue - parentState.intValue
                    val child = measurables.single()
                    val first = child.query(hook, firstExtent)
                    val second =
                        if (queryBoth.intValue == 0) {
                            0
                        } else {
                            firstCallsBeforeSecondQuery = firstCalls
                            child.query(hook, secondExtent)
                        }
                    val placeable = child.measure(Constraints.fixed(1, 1))
                    layout(constraints.constrainWidth(width), constraints.constrainHeight(100)) {
                        placeable.place(first + second, 0)
                    }
                },
        ) { _, _, _ -> 1 }
    val childPolicy =
        IntrinsicQueryPolicy { _, queriedHook, extent ->
            when {
                queriedHook != hook -> {
                    1
                }

                extent == firstExtent -> {
                    firstCalls++
                    firstState.intValue
                }

                else -> {
                    secondCalls++
                    secondState.intValue
                }
            }
        }
}

private fun widthFollowingStockPanel(): JPanel =
    object : JPanel() {
        override fun getPreferredSize(): Dimension = Dimension(100, this.width)

        override fun getMinimumSize(): Dimension = preferredSize
    }

private fun ConstrainedPanel.query(
    hook: Int,
    extent: Int = 80,
): Int =
    when (hook) {
        0 -> minIntrinsicWidth(extent)
        1 -> maxIntrinsicWidth(extent)
        2 -> minIntrinsicHeight(extent)
        else -> maxIntrinsicHeight(extent)
    }

private fun IntrinsicMeasurable.query(
    hook: Int,
    extent: Int,
): Int =
    when (hook) {
        0 -> minIntrinsicWidth(extent)
        1 -> maxIntrinsicWidth(extent)
        2 -> minIntrinsicHeight(extent)
        else -> maxIntrinsicHeight(extent)
    }
