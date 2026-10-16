package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Swing-specific policy operations and their interaction with parent ownership. */
@ExtendWith(ComposedPanels::class)
class MeasurePolicyOperationsTest {
    @Test
    fun measureAtAssignedSizeDelegatesToMeasureWithExactConstraintsByDefault() =
        runSwingTest {
            var measuredConstraints: Constraints? = null
            val policy =
                MeasurePolicy { _, constraints ->
                    measuredConstraints = constraints
                    layout(constraints.maxWidth, constraints.maxHeight) {}
                }
            val panel = policyPanel(policy)
            panel.setSize(120, 80)
            panel.doLayout()
            assertEquals(Constraints(120, 120, 80, 80), measuredConstraints)
        }

    @Test
    fun customMeasureInBoundsOverridesAssignedLayout() =
        runSwingTest {
            val policy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult = layout(10, 10) {}

                    override fun MeasureScope.measureAtAssignedSize(
                        measurables: List<Measurable>,
                        width: Int,
                        height: Int,
                    ): MeasureResult {
                        val placeable = measurables.single().measure(Constraints.fixed(width / 2, height / 2))
                        return layout(width, height) { placeable.place(7, 3) }
                    }
                }
            val child = FixedSizeChild()
            val panel = policyPanel(policy, child)
            panel.setSize(200, 150)
            panel.doLayout()
            assertEquals(Rectangle(7, 3, 100, 75), child.bounds)
        }

    @ParameterizedTest(name = "held width {0}")
    @CsvSource("0, 100, 100", "50, 100, 200")
    fun preferredSizeDefaultsToNaturalWidthAndHeightAtEffectiveWidth(
        heldWidth: Int,
        expectedWidth: Int,
        expectedHeight: Int,
    ) = runSwingTest {
        val policy =
            MeasurePolicy { measurables, constraints ->
                val placeable = measurables.first().measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        val panel = policyPanel(policy, NarrowWrappingComponent())
        panel.setSize(heldWidth, 10)
        assertEquals(
            Dimension(expectedWidth, expectedHeight),
            panel.preferredSize,
            "the height is read at the inner width Swing holds, or at the natural width where it holds none",
        )
    }

    @Test
    fun customPreferredSizeOverridesSwingPreferredQueries() =
        runSwingTest {
            val preferred = Dimension(345, 123)
            assertEquals(preferred, policyPanel(preferring(preferred)).preferredSize)
        }

    @ParameterizedTest(name = "inner width {0}, Foundation parent {1}")
    @CsvSource(value = ["0, false, none", "50, false, 50", "50, true, none"], nullValues = ["none"])
    fun customPreferredSizeIsAskedAtTheInnerWidthSwingHoldsExceptUnderAFoundationParent(
        heldWidth: Int,
        underFoundationParent: Boolean,
        expected: Int?,
    ) = runSwingTest {
        val askedAt = mutableListOf<Int?>()
        val panel = policyPanel(preferring(Dimension(345, 123), askedAt))
        if (underFoundationParent) policyPanel(MeasurePolicy { _, _ -> layout(0, 0) {} }, panel)
        panel.setSize(heldWidth, 10)
        panel.preferredSize
        assertEquals(listOf(expected), askedAt.distinct())
    }

    @Test
    fun customPreferredSizeIsGrownByTheBorderInsets() =
        runSwingTest {
            val panel = policyPanel(preferring(Dimension(345, 123)))
            panel.border = EmptyBorder(5, 10, 15, 20)
            assertEquals(Dimension(375, 143), panel.preferredSize)
        }

    @Test
    fun customPreferredSizeIsNotUsedForTheMinimumSize() =
        runSwingTest {
            val policy =
                object : MeasurePolicy by preferring(Dimension(345, 123)) {
                    override fun IntrinsicMeasureScope.minIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = 11

                    override fun IntrinsicMeasureScope.minIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int = 7
                }
            assertEquals(Dimension(11, 7), policyPanel(policy).minimumSize)
        }

    @Test
    fun aPreferredSizeSetOnThePanelReplacesTheCustomPreferredSize() =
        runSwingTest {
            val panel = policyPanel(preferring(Dimension(345, 123)))
            panel.preferredSize = Dimension(9, 8)
            assertEquals(Dimension(9, 8), panel.preferredSize)
        }

    @ParameterizedTest(name = "{0} by {1}")
    @CsvSource("-1, 10", "10, -1")
    fun customPreferredSizeRejectsNegativeExtents(
        width: Int,
        height: Int,
    ) = runSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                policyPanel(preferring(Dimension(width, height))).preferredSize
            }
        assertTrue(
            "$width by $height" in failure.message.orEmpty(),
            "the refusal must name the extent the policy requested, but was: ${failure.message}",
        )
    }

    @Test
    fun measureAtAssignedSizeIsHandedTheExtentInsideTheBorderInsets() =
        runSwingTest {
            val handed = mutableListOf<Dimension>()
            val policy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult = layout(10, 10) {}

                    override fun MeasureScope.measureAtAssignedSize(
                        measurables: List<Measurable>,
                        width: Int,
                        height: Int,
                    ): MeasureResult {
                        handed += Dimension(width, height)
                        return layout(width, height) {}
                    }
                }
            val panel = policyPanel(policy)
            panel.border = EmptyBorder(5, 10, 15, 20)
            panel.setSize(200, 150)
            panel.doLayout()
            assertEquals(listOf(Dimension(170, 130)), handed.distinct())
        }

    @Test
    fun measureAtAssignedSizeMeasuresEachChildAtMostOnce() =
        runSwingTest {
            val policy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult = layout(10, 10) {}

                    override fun MeasureScope.measureAtAssignedSize(
                        measurables: List<Measurable>,
                        width: Int,
                        height: Int,
                    ): MeasureResult {
                        val child = measurables.single()
                        child.measure(Constraints())
                        child.measure(Constraints())
                        return layout(width, height) {}
                    }
                }
            val panel = policyPanel(policy, FixedSizeChild())
            panel.setSize(50, 50)
            val failure = assertFailsWith<IllegalStateException> { panel.doLayout() }
            val message = failure.message.orEmpty()
            assertTrue(
                message.startsWith("measure() may not be called multiple times on the same Measurable."),
                "the second measure must be refused, but the message was: $message",
            )
        }

    @Test
    fun aMatchingPlacementReplaysTheOfferedResult() =
        runSwingTest {
            val calls = mutableListOf<String>()
            val panel = policyPanel(recordingOperations(calls))
            peered(panel) {
                layOutAtTheOfferedExtent(panel)
                assertEquals(listOf("offer"), calls, "matching placement replays the offered result")
            }
        }

    @Test
    fun anInvalidStandaloneLayoutUsesTheAssignedBounds() =
        runSwingTest {
            val calls = mutableListOf<String>()
            val panel = policyPanel(recordingOperations(calls))
            peered(panel) {
                layOutAtTheOfferedExtent(panel)

                panel.revalidate()
                panel.doLayout()
                assertEquals(listOf("offer", "bounds"), calls, "invalid standalone layout uses assigned bounds")
            }
        }

    @Test
    fun ownedChildWaitsForItsParentsNextOfferAfterInvalidation() =
        runSwingTest {
            val calls = mutableListOf<String>()
            val child = policyPanel(recordingOperations(calls))
            val parent =
                policyPanel(
                    MeasurePolicy { children, _ ->
                        val placed = children.single().measure(Constraints.fixed(80, 60))
                        layout(80, 60) { placed.place(0, 0) }
                    },
                    child,
                )
            peered(parent) {
                parent.setSize(80, 60)
                parent.doLayout()
                child.doLayout()
                assertEquals(listOf("offer"), calls)

                child.revalidate()
                child.doLayout()
                assertEquals(listOf("offer"), calls, "an invalid owned child waits for its parent")

                parent.doLayout()
                child.doLayout()
                assertEquals(listOf("offer", "offer"), calls, "the parent supplies the next offer")
            }
        }
}

/** A panel laid out by [policy], holding [children] under no constraint of their own. */
private fun policyPanel(
    policy: MeasurePolicy,
    vararg children: Component,
): ConstrainedPanel {
    val panel = composed(ConstrainedPanel(policy))
    children.forEach(panel::add)
    return panel
}

/** Measures [panel] at an offer of exactly 80 by 60, then lays it out at that size. */
private fun layOutAtTheOfferedExtent(panel: ConstrainedPanel) {
    panel.measure(Constraints.fixed(80, 60))
    panel.setSize(80, 60)
    panel.doLayout()
}

/**
 * A policy whose `measure` appends "offer" to [calls] and answers 80 by 60, and whose `measureAtAssignedSize` appends
 * "bounds" and answers the assigned extent.
 */
private fun recordingOperations(calls: MutableList<String>): MeasurePolicy =
    object : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            calls += "offer"
            return layout(80, 60) {}
        }

        override fun MeasureScope.measureAtAssignedSize(
            measurables: List<Measurable>,
            width: Int,
            height: Int,
        ): MeasureResult {
            calls += "bounds"
            return layout(width, height) {}
        }
    }

/**
 * A policy that measures 10 by 10 and answers [size] as its preferred size, appending each width it is asked at to
 * [askedAt].
 */
private fun preferring(
    size: Dimension,
    askedAt: MutableList<Int?> = mutableListOf(),
): MeasurePolicy =
    object : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(10, 10) {}

        override fun IntrinsicMeasureScope.preferredSize(
            measurables: List<IntrinsicMeasurable>,
            assignedWidth: Int?,
        ): Dimension {
            askedAt += assignedWidth
            return size
        }
    }
