// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.minimumSize
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JSplitPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A container receives two argument-less size reads - its preferred size, and the least it can occupy - and
 * both run its measure policy rather than its layout pass. The policy writes one body; which of the two
 * is being read decides only which extent a child that cannot perform a constrained query returns
 * with.
 *
 * This stands in for androidx `foundation-layout`'s own `IntrinsicTest`. An intrinsic query carries its extent
 * through every container of this library, as androidx's does, and stops at the first Swing widget:
 * `getPreferredSize()` and `getMinimumSize()` take no argument. The cases here pin the queries this tree performs,
 * which
 * of the policy's constrained and four intrinsic entry points each of them reaches, and the extent each carries.
 */
class IntrinsicTest {
    @Test
    fun aRowsMinimumSizeIncludesBothChildren() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(Dimension(50, 30)) { minimumSize }
            onNodeWithTag(CONTAINER_TAG).assertProperty(Dimension(160, 80)) { preferredSize }
        }

    @Test
    fun aNestedRowsMinimumSizeIncludesBothChildren() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Row {
                        ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                        ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                    }
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(Dimension(50, 30)) { minimumSize }
        }

    @Test
    fun aBoxsMinimumSizeIncludesItsLargestChild() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(Dimension(30, 30)) { minimumSize }
        }

    @Test
    fun aWeightedRowsMinimumSizeIncludesBothChildren() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30, modifier = SwingModifier.weight(1f))
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(Dimension(50, 30)) { minimumSize }
        }

    @Test
    fun aBoxsPreferredSizeRespectsANonMatchingChildsMaximum() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, width = 200, height = 160, style = { maximumSize(50, 40) })
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(
                Dimension(50, 40),
                "a box must not ask for more than its non-matching child explicitly accepts",
            ) { preferredSize }
        }

    @Test
    fun aWeightedIntrinsicRoundsTheUnitShareBeforeItMultipliesIt() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label("child", modifier = SwingModifier.weight(2f), style = { preferredSize(1, 1) })
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(
                Dimension(2, 1),
                "a child one unit wide and weighted 2f needs one rounded unit per weight unit, so its row asks for two",
            ) { preferredSize }
        }

    @Test
    fun anIntrinsicWalkAsksANestedContainerNoConstrainedQuery() =
        runComposeSwingTest {
            val counting = CountingPolicy()
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Layout(measurePolicy = counting)
                }
            }
            counting.forget()

            val container = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            container.preferredSize
            container.minimumSize
            assertEquals(
                0,
                counting.measured,
                "a container asked what it prefers has no extent to offer, so it must ask a child its intrinsic " +
                    "sizes and never the constrained query - asking one there would hand the child the outer " +
                    "pass's unbounded constraints",
            )
            assertTrue(counting.intrinsicHeight > 0, "and it must ask, rather than reporting for the child itself")
        }

    @Test
    fun aLayoutPassDoesAskANestedContainerAConstrainedQuery() =
        runComposeSwingTest {
            val counting = CountingPolicy()
            setContent {
                Column(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 200))) {
                    Layout(measurePolicy = counting)
                }
            }

            assertTrue(
                counting.measured > 0,
                "a container laying its children out has an extent to offer, which is what carries a " +
                    "constraint through a container of this library's own",
            )
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intrinsicSizes")
    fun testIntrinsicWidth_skippedWhenIncomingWidthIsFixed(size: IntrinsicSize) =
        runComposeSwingTest {
            val counting = CountingPolicy(measuredSize = 10, intrinsicSize = 20)
            setContent {
                Column(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 200))) {
                    Layout(modifier = SwingModifier.width(50).width(size), measurePolicy = counting)
                }
            }

            assertTrue(counting.measured > 0, "the layout pass measures the child")
            assertEquals(0, counting.intrinsicHeight, "$size: a fixed incoming width makes the intrinsic query moot")
            assertEquals(Dimension(50, 10), childBounds().single().size)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intrinsicSizes")
    fun testIntrinsicWidth_notSkippedWhenIncomingWidthIsNotFixed(size: IntrinsicSize) =
        runComposeSwingTest {
            val counting = CountingPolicy(measuredSize = 10, intrinsicSize = 20)
            setContent {
                Column(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 200))) {
                    Layout(modifier = SwingModifier.width(size), measurePolicy = counting)
                }
            }

            assertTrue(counting.intrinsicHeight > 0, "$size: an incoming width that is not fixed is still queried")
            assertEquals(Dimension(20, 10), childBounds().single().size)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intrinsicSizes")
    fun testIntrinsicHeight_skippedWhenIncomingHeightIsFixed(size: IntrinsicSize) =
        runComposeSwingTest {
            val counting = CountingPolicy(measuredSize = 10, intrinsicSize = 20)
            setContent {
                Column(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 200))) {
                    Layout(modifier = SwingModifier.height(50).height(size), measurePolicy = counting)
                }
            }

            assertTrue(counting.measured > 0, "the layout pass measures the child")
            assertEquals(0, counting.intrinsicHeight, "$size: a fixed incoming height makes the intrinsic query moot")
            assertEquals(Dimension(10, 50), childBounds().single().size)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intrinsicSizes")
    fun testIntrinsicHeight_notSkippedWhenIncomingHeightIsNotFixed(size: IntrinsicSize) =
        runComposeSwingTest {
            val counting = CountingPolicy(measuredSize = 10, intrinsicSize = 20)
            setContent {
                Column(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 200))) {
                    Layout(modifier = SwingModifier.height(size), measurePolicy = counting)
                }
            }

            assertTrue(counting.intrinsicHeight > 0, "$size: an incoming height that is not fixed is still queried")
            assertEquals(Dimension(10, 20), childBounds().single().size)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intrinsicSizes")
    fun testRequiredWidth_notSkippedWhenIncomingWidthIsFixed(size: IntrinsicSize) =
        runComposeSwingTest {
            val counting = CountingPolicy()
            setContent {
                Column(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 200))) {
                    Layout(modifier = SwingModifier.width(50).requiredWidth(size), measurePolicy = counting)
                }
            }

            assertTrue(counting.intrinsicHeight > 0, "$size: a required width ignores the incoming one, so it queries")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intrinsicSizes")
    fun testRequiredHeight_notSkippedWhenIncomingHeightIsFixed(size: IntrinsicSize) =
        runComposeSwingTest {
            val counting = CountingPolicy()
            setContent {
                Column(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 200))) {
                    Layout(modifier = SwingModifier.height(50).requiredHeight(size), measurePolicy = counting)
                }
            }

            assertTrue(counting.intrinsicHeight > 0, "$size: a required height ignores the incoming one, so it queries")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queryCases")
    fun intrinsicQueriesAndLayoutUseDifferentPolicyEntryPoints(queryOnly: Boolean) {
        runComposeSwingTest {
            var measured = 0
            var intrinsicHeight = 0
            val policy =
                IntrinsicQueryPolicy(
                    measuring =
                        MeasurePolicy { _, _ ->
                            measured++
                            layout(0, 0) {}
                        },
                ) { _, _, _ ->
                    intrinsicHeight++
                    0
                }
            setContent {
                Column(
                    modifier =
                        SwingModifier.containerTag().styleable(if (queryOnly) SwingStyle else containerStyle(200, 200)),
                ) { Layout(measurePolicy = policy) }
            }
            if (queryOnly) {
                measured = 0
                intrinsicHeight = 0
                val container = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
                container.preferredSize
                container.minimumSize
                assertEquals(0, measured, "queryOnly=$queryOnly: intrinsic queries must not measure a nested container")
                assertTrue(intrinsicHeight > 0, "the nested container performs its own intrinsic reads")
            } else {
                assertTrue(measured > 0, "a layout pass performs a constrained query")
            }
        }
    }

    @Test
    fun anExplicitContainerSizeAlsoDeterminesItsConstrainedSize() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.containerTag().styleable(containerStyle(400, 100))) {
                    Row(modifier = SwingModifier.styleable { preferredSize(200, 60) }) { SizedChild(0) }
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 200, 60)),
                childBounds(),
                "a size set on a container returns a result for it whatever the query, so the inner row occupies " +
                    "the size it was given rather than the one its own policy works out for its child",
            )
        }

    @Test
    fun aSplitPaneTakesItsDividerLimitFromTheMinimumTheRowsPolicyWorksOut() =
        runComposeSwingTest {
            setContent {
                SplitPane(modifier = SwingModifier.testTag(CONTAINER_TAG), style = { preferredSize(400, 100) }) {
                    Row(modifier = SwingModifier.first()) {
                        ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                        ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                    }
                    Label(text = "second", modifier = SwingModifier.second())
                }
            }

            val pane = onNodeWithTag(CONTAINER_TAG).fetch<JSplitPane>()
            assertEquals(
                50,
                pane.minimumDividerLocation - pane.insets.left,
                "a split pane holds its divider clear of what the side it splits can shrink to, and that " +
                    "extent is what the row's own policy works out in its minimum mode",
            )
        }

    companion object {
        @JvmStatic
        fun queryCases(): List<Arguments> = listOf(true, false).map { Arguments.of(Named.of("queryOnly=$it", it)) }

        @JvmStatic
        fun intrinsicSizes(): List<Arguments> = IntrinsicSize.entries.map { Arguments.of(Named.of(it.name, it)) }
    }
}

/** A child that prefers one extent on both axes and declares it can shrink to another. */
@Composable
private fun ShrinkableChild(
    index: Int,
    prefers: Int,
    shrinksTo: Int,
    modifier: SwingModifier = SwingModifier,
) {
    Label(
        "child $index",
        modifier = modifier,
        style = {
            preferredSize(prefers, prefers)
            minimumSize(shrinksTo, shrinksTo)
        },
    )
}

/**
 * A policy with fixed intrinsic and measured sizes that counts which of its two kinds of entry points its container's
 * parent reaches: `measure`, which only a caller holding an extent to offer can ask, and one of the four intrinsic
 * functions, which a parent's own intrinsic queries reach.
 */
private class CountingPolicy(
    private val measuredSize: Int = 0,
    private val intrinsicSize: Int = 0,
) : MeasurePolicy {
    var measured: Int = 0
        private set

    var intrinsicHeight: Int = 0
        private set

    fun forget() {
        measured = 0
        intrinsicHeight = 0
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        measured++
        return layout(constraints.constrainWidth(measuredSize), constraints.constrainHeight(measuredSize)) {}
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int {
        intrinsicHeight++
        return intrinsicSize
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int {
        intrinsicHeight++
        return intrinsicSize
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        intrinsicHeight++
        return intrinsicSize
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        intrinsicHeight++
        return intrinsicSize
    }
}
