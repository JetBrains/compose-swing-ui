package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a container driven by a [MeasurePolicy] offers the policy, and what survives between the Swing
 * calls that drive it: constraints compared by value, the extents a caller may name and the ones
 * refused, one object that is both the child's [Measurable] and the [Placeable] its measure hands back,
 * what a child is and is not asked, what a pass a parent ran leaves behind for the placement that
 * follows, and a placement that is absolute unless the policy asks for the container's reading order to
 * mirror it.
 *
 * These cases build their containers by hand rather than declaring them, and the frame [peered] grants
 * a peer is what a reading kept between two passes turns on - the tree a composed test mounts has none.
 *
 * One case here per case in the upstream Compose measurement test suites, in their order and under
 * their names, so the files can be read against each other and a case dropped in translation shows
 * up as a gap. That correspondence is what holds the class together and what the size suppression
 * below protects: it is as large as its upstream source rather than a class that grew by accretion,
 * and splitting it would break the reading it exists for.
 */
@Suppress("LargeClass")
@ExtendWith(ComposedPanels::class)
class MeasurePolicyTest {
    @Test
    fun constraintsBuiltFromTheSameExtentsAreEqualAndHashAlike() {
        val constraints = Constraints(minWidth = 10, maxWidth = 20, minHeight = 30, maxHeight = 40)
        val same = Constraints(minWidth = 10, maxWidth = 20, minHeight = 30, maxHeight = 40)

        assertEquals(same, constraints, "two constraints offering the same extents describe the same offer")
        assertEquals(same.hashCode(), constraints.hashCode(), "equal constraints must hash alike")
        listOf(
            Constraints(minWidth = 11, maxWidth = 20, minHeight = 30, maxHeight = 40) to "minimum width",
            Constraints(minWidth = 10, maxWidth = 21, minHeight = 30, maxHeight = 40) to "maximum width",
            Constraints(minWidth = 10, maxWidth = 20, minHeight = 31, maxHeight = 40) to "minimum height",
            Constraints(minWidth = 10, maxWidth = 20, minHeight = 30, maxHeight = 41) to "maximum height",
        ).forEach { (different, extent) ->
            assertNotEquals(constraints, different, "changing the $extent must change the constraints")
        }
    }

    @Test
    fun anExtentIsHeldInsideTheConstraintsItIsCoercedAgainst() {
        val constraints = Constraints(minWidth = 10, maxWidth = 20, minHeight = 30, maxHeight = 40)

        assertEquals(10, constraints.constrainWidth(5), "a width below the minimum is raised to it")
        assertEquals(20, constraints.constrainWidth(50), "a width above the maximum is held to it")
        assertEquals(35, constraints.constrainHeight(35), "a height already inside them is left alone")
    }

    @Test
    fun constraintsRangingFromAMinimumPastItsMaximumAreRefused() {
        val widths =
            assertFailsWith<IllegalArgumentException> { Constraints(minWidth = 20, maxWidth = 10) }
        val heights =
            assertFailsWith<IllegalArgumentException> { Constraints(minHeight = 20, maxHeight = 10) }

        assertTrue(
            "minWidth is 20 and maxWidth is 10" in widths.message.orEmpty(),
            "the refusal must name the width range it was given, but was: ${widths.message}",
        )
        assertTrue(
            "minHeight is 20 and maxHeight is 10" in heights.message.orEmpty(),
            "the refusal must name the height range it was given, but was: ${heights.message}",
        )
    }

    @Test
    fun constraintsWhoseMinimumIsNegativeAreRefused() {
        val widths = assertFailsWith<IllegalArgumentException> { Constraints(minWidth = -1) }
        val heights = assertFailsWith<IllegalArgumentException> { Constraints(minHeight = -1) }

        assertTrue(
            "zero or more" in widths.message.orEmpty(),
            "the refusal must say what a minimum width has to be, but was: ${widths.message}",
        )
        assertTrue(
            "zero or more" in heights.message.orEmpty(),
            "the refusal must say what a minimum height has to be, but was: ${heights.message}",
        )
    }

    @Test
    fun aPolicyNamingANegativeExtentIsRefused() =
        onEventDispatchThread {
            val panel = policyPanel({ _, _ -> layout(-1, 10) {} }, FixedSizeChild())

            val failure = assertFailsWith<IllegalArgumentException> { panel.doLayout() }

            assertTrue(
                "-1 by 10" in failure.message.orEmpty(),
                "the refusal must name the extent the policy asked for, but was: ${failure.message}",
            )
        }

    @Test
    fun aPolicyNamingANegativeHeightIsRefused() =
        onEventDispatchThread {
            val panel = policyPanel({ _, _ -> layout(10, -1) {} }, FixedSizeChild())

            val failure = assertFailsWith<IllegalArgumentException> { panel.doLayout() }

            assertTrue(
                "10 by -1" in failure.message.orEmpty(),
                "the refusal must name the extent the policy asked for, but was: ${failure.message}",
            )
        }

    @Test
    fun anOrdinarySwingChildIsClampedBecauseItCannotMeasureOutsideItsOffer() =
        onEventDispatchThread {
            var measured: Placeable? = null
            val panel =
                policyPanel(
                    { measurables, _ ->
                        val child = measurables.single().measure(Constraints(30, 50, 20, 30))
                        measured = child
                        layout(100, 100) { child.place(10, 15) }
                    },
                    FixedSizeChild(70, 40),
                )
            panel.setSize(100, 100)

            panel.doLayout()

            assertEquals(50, measured?.width, "a parent must arrange from the width it offered")
            assertEquals(30, measured?.height, "a parent must arrange from the height it offered")
            assertEquals(50, measured?.measuredWidth, "Swing reports the width the offered layout grants it")
            assertEquals(30, measured?.measuredHeight, "Swing reports the height the offered layout grants it")
            assertEquals(
                Rectangle(10, 15, 50, 30),
                panel.getComponent(0).bounds,
                "a stock component has no raw overflow to center because it cannot answer a constrained measure",
            )
        }

    @Test
    fun anOversizedPolicyContainerExposesACoercedExtentAndIsCenteredAtItsRawExtent() =
        onEventDispatchThread {
            var measured: Placeable? = null
            val child =
                composed(ConstrainedPanel(MeasurePolicyLayout(MeasurePolicy { _, _ -> layout(70, 40) {} }, null)))
            val panel =
                policyPanel(
                    { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints(30, 50, 20, 30))
                        measured = placeable
                        layout(100, 100) { placeable.place(10, 15) }
                    },
                    child,
                )
            panel.setSize(100, 100)

            panel.doLayout()

            assertEquals(50, measured?.width, "a parent must arrange from the width it offered")
            assertEquals(30, measured?.height, "a parent must arrange from the height it offered")
            assertEquals(70, measured?.measuredWidth, "the placeable must retain the policy's actual width")
            assertEquals(40, measured?.measuredHeight, "the placeable must retain the policy's actual height")
            assertEquals(
                Rectangle(0, 10, 70, 40),
                child.bounds,
                "the raw oversized container must be centered on the apparent space the policy placed",
            )
        }

    @Test
    fun anUndersizedPolicyContainerExposesACoercedExtentAndIsCenteredAtItsRawExtent() =
        onEventDispatchThread {
            var measured: Placeable? = null
            val child =
                composed(ConstrainedPanel(MeasurePolicyLayout(MeasurePolicy { _, _ -> layout(10, 5) {} }, null)))
            val panel =
                policyPanel(
                    { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints(30, 50, 20, 40))
                        measured = placeable
                        layout(100, 100) { placeable.place(10, 15) }
                    },
                    child,
                )
            panel.setSize(100, 100)

            panel.doLayout()

            assertEquals(30, measured?.width, "a parent must arrange from the minimum width it offered")
            assertEquals(20, measured?.height, "a parent must arrange from the minimum height it offered")
            assertEquals(10, measured?.measuredWidth, "the placeable must retain the policy's undersized width")
            assertEquals(5, measured?.measuredHeight, "the placeable must retain the policy's undersized height")
            assertEquals(
                Rectangle(20, 22, 10, 5),
                child.bounds,
                "the raw undersized container must be centered on the apparent minimum space the policy placed",
            )
        }

    @Test
    fun aModifierChainPreservesItsChildsCenteredOverflowPlacement() =
        onEventDispatchThread {
            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, _ ->
                        val child = measurables.single().measure(Constraints(maxWidth = 50, maxHeight = 30))
                        layout(100, 100) { child.place(10, 15) }
                    },
                    null,
                )
            val child = FixedSizeChild(10, 10)
            val panel = composed(ConstrainedPanel(layout))
            panel.add(child)
            layout.declareLayoutChain(child, listOf(ForcedRawExtentNode()))
            panel.setSize(100, 100)

            panel.doLayout()

            assertEquals(
                Rectangle(7, 19, 10, 10),
                child.bounds,
                "a modifier's raw overflow must center before it replays the child placement it retains",
            )
        }

    @Test
    fun defaultIntrinsicHooksAdaptStockSwingsMinimumAndPreferredSizes() =
        onEventDispatchThread {
            val policy =
                MeasurePolicy { measurables, constraints ->
                    val child = measurables.single().measure(constraints)
                    layout(child.width, child.height) {}
                }
            val layout = MeasurePolicyLayout(policy, null)
            val child = FixedSizeChild(70, 40).also { it.minimumSize = Dimension(5, 7) }
            val panel = composed(ConstrainedPanel(layout))
            panel.add(child)
            val measurable = layout.measurables.of(child)

            with(policy) {
                assertEquals(
                    5,
                    PolicyMeasureScope.minIntrinsicWidth(listOf(measurable), Int.MAX_VALUE),
                    "the default minimum-width hook must ask a stock Swing child for its minimum size",
                )
                assertEquals(
                    7,
                    PolicyMeasureScope.minIntrinsicHeight(listOf(measurable), Int.MAX_VALUE),
                    "the default minimum-height hook must ask a stock Swing child for its minimum size",
                )
                assertEquals(
                    70,
                    PolicyMeasureScope.maxIntrinsicWidth(listOf(measurable), Int.MAX_VALUE),
                    "the default maximum-width hook must ask a stock Swing child for its preferred size",
                )
                assertEquals(
                    40,
                    PolicyMeasureScope.maxIntrinsicHeight(listOf(measurable), Int.MAX_VALUE),
                    "the default maximum-height hook must ask a stock Swing child for its preferred size",
                )
            }
        }

    @Test
    fun aMeasureResultExposesItsAlignmentLinesAndCanPlaceWithTheStandaloneSurface() =
        onEventDispatchThread {
            val line = HorizontalAlignmentLine { first, second -> minOf(first, second) }
            var result: MeasureResult? = null
            val panel =
                policyPanel(
                    { measurables, _ ->
                        val child = measurables.single().measure(Constraints(maxWidth = 10, maxHeight = 10))
                        layout(20, 10, mapOf(line to 6)) { child.placeRelative(0, 0) }
                            .also { result = it }
                    },
                    FixedSizeChild(10, 10),
                )
            panel.setSize(20, 10)
            panel.doLayout()
            panel.getComponent(0).setBounds(99, 99, 0, 0)

            result?.placeChildren()

            assertEquals(
                mapOf<AlignmentLine, Int>(line to 6),
                result?.alignmentLines,
                "layout must retain its explicit alignment lines",
            )
            assertEquals(
                Rectangle(0, 0, 10, 10),
                panel.getComponent(0).bounds,
                "the standalone CMP surface must replay into its zero-origin, left-to-right Swing adaptation",
            )
        }

    @Test
    fun aRowMeasuredUnderUnboundedConstraintsCollapsesItsWeightedChildren() =
        onEventDispatchThread {
            val row = rowPolicy()
            var settled: MeasureResult? = null
            // A caller outside the library reaches the row's policy with measurables of its own container's,
            // measuring without asking the intrinsic functions first.
            val policy =
                MeasurePolicy { measurables, _ ->
                    with(row) {
                        PolicyMeasureScope.measure(measurables, Constraints.Unbounded)
                    }.also { settled = it }
                }
            val panel = composed(ConstrainedPanel(MeasurePolicyLayout(policy, null)))
            panel.add(
                FixedSizeChild(30, 40),
                LinearConstraint(weight = WeightPlacement(1f, fill = true)),
            )
            panel.add(FixedSizeChild(30, 40))
            panel.setSize(200, 200)

            panel.doLayout()

            assertEquals(
                30,
                settled?.width,
                "with no extent to divide, a weighted child collapses rather than claiming the whole axis",
            )
        }

    @Test
    fun hugeSpacingCannotWrapIntoSpaceForWeightedChildren() =
        onEventDispatchThread {
            val row =
                rowPolicy(arrangement = Arrangement.spacedBy(Int.MAX_VALUE))
            val panel = composed(ConstrainedPanel(MeasurePolicyLayout(row, null)))
            repeat(3) {
                panel.add(FixedSizeChild(), LinearConstraint(weight = WeightPlacement(1f, fill = true)))
            }
            panel.setSize(10, 10)

            panel.doLayout()

            assertEquals(
                listOf(
                    Rectangle(0, 0, 0, 0),
                    Rectangle(10, 0, 0, 0),
                    Rectangle(10, 0, 0, 0),
                ),
                panel.declarationBounds(),
                "two maximum gaps exhaust the row's width of 10, rather than overflowing into a negative total and " +
                    "granting the weighted children a width of 12",
            )
        }

    @Test
    fun largeInsetsSaturateBeforeTheyReachMeasurementOrLayout() =
        onEventDispatchThread {
            var offered: Constraints? = null
            val panel =
                composed(
                    HugeInsetPanel(
                        MeasurePolicyLayout(
                            MeasurePolicy { _, constraints ->
                                offered = constraints
                                layout(0, 0) {}
                            },
                            null,
                        ),
                    ),
                )

            assertEquals(
                Dimension(Int.MAX_VALUE, 0),
                panel.preferredSize,
                "two large horizontal insets reserve every finite pixel instead of wrapping into a negative extent",
            )

            panel.measure(Constraints(maxWidth = 100, maxHeight = 0))

            assertEquals(
                Constraints(0, 0, 0, 0),
                offered,
                "the constrained measurement gives a policy no inner width once the insets exhaust the offer",
            )

            panel.setSize(0, 0)
            panel.doLayout()

            assertEquals(
                Constraints(0, 0, 0, 0),
                offered,
                "insets wider than the panel leave the policy no inner extent rather than wrapping around to two " +
                    "pixels",
            )
        }

    @Test
    fun anIntMinimumSpacedByGapSaturatesLaterChildPositions() {
        val positions = IntArray(3)
        val rightToLeftPositions = IntArray(3)

        Arrangement.spacedBy(Int.MIN_VALUE).arrange(100, intArrayOf(10, 10, 10), positions)
        Arrangement.spacedBy(Int.MIN_VALUE).arrange(
            100,
            intArrayOf(10, 10, 10),
            ComponentOrientation.RIGHT_TO_LEFT,
            rightToLeftPositions,
        )

        assertEquals(
            listOf(0, Int.MIN_VALUE + 10, Int.MIN_VALUE),
            positions.toList(),
            "a negative gap too large to represent after two children must stay at the leading overflow " +
                "edge, not wrap back",
        )
        assertEquals(
            listOf(90, Int.MAX_VALUE, Int.MAX_VALUE),
            rightToLeftPositions.toList(),
            "the mirrored packing direction must saturate at its opposite edge instead of wrapping back to zero",
        )
    }

    @Test
    fun anArrangementSaturatesWhenLargeChildSizesExceedAnIntSum() {
        val positions = IntArray(2)

        Arrangement.End.arrange(
            totalSize = 0,
            sizes = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE),
            orientation = ComponentOrientation.LEFT_TO_RIGHT,
            outPositions = positions,
        )

        assertEquals(
            listOf(Int.MIN_VALUE, Int.MIN_VALUE),
            positions.toList(),
            "two maximum child sizes overflow before the leading edge, rather than wrapping to positive surplus",
        )
    }

    @Test
    fun aCacheKeepsTheLatestOfferWhenEqualResultsPlaceChildrenDifferently() =
        onEventDispatchThread {
            val panel = composed(ConstrainedPanel(MeasurePolicyLayout(OfferSensitivePolicy(), null)))
            val child = FixedSizeChild()
            panel.add(child)

            panel.measure(Constraints(maxWidth = 20, maxHeight = 10))
            panel.measure(Constraints(maxWidth = 40, maxHeight = 10))
            panel.setSize(10, 10)
            panel.doLayout()

            assertEquals(
                Rectangle(40, 0, 0, 0),
                child.bounds,
                "the second offer replaces the first cached result even though both policy passes settle on 10",
            )
        }

    @Test
    fun aSecondPassAtTheSameExtentRunsThePolicyOnlyAfterAnInvalidation() =
        onEventDispatchThread {
            val policy = PassCountingPolicy()
            val panel = policyPanel(policy, FixedSizeChild(30, 40))

            peered(panel) {
                panel.setSize(200, 200)
                panel.doLayout()
                panel.doLayout()

                assertEquals(1, policy.passes, "a pass at the extent the last one settled on must reuse its result")

                panel.revalidate()
                panel.doLayout()

                assertEquals(2, policy.passes, "a pass after the container was invalidated must run the policy again")
            }
        }

    @Test
    fun aChildGrantedBothOfItsExtentsIsNeverAskedWhatItPrefers() =
        onEventDispatchThread {
            val child = CountingChild()
            val panel = composed(rowPolicyPanel())
            val row = panel.policyLayout
            panel.add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true)))
            row.declareLayoutChain(child, layoutChainOf { SwingModifier.fillMaxHeight() })
            panel.setSize(200, 200)

            panel.doLayout()

            assertEquals(
                Rectangle(0, 0, 200, 200),
                child.bounds,
                "a child filling the row across its axis and granted the whole of it along that axis occupies " +
                    "the container's whole inner extent",
            )
            assertEquals(
                0,
                child.questions,
                "such a child must be asked nothing, since what it prefers is discarded",
            )
        }

    @Test
    fun aChildThePlacementResizedIsAskedAfreshAtTheExtentItWasPlacedAt() =
        onEventDispatchThread {
            val child = WrappingChild()
            val panel = composed(rowPolicyPanel())
            panel.add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true)))

            peered(panel) {
                panel.setSize(WRAPPING_WIDTH, WRAPPING_WIDTH)

                panel.doLayout()

                assertEquals(
                    WRAPPED_HEIGHT,
                    child.height,
                    "the first pass reads what the child prefers while it is still no wider than nothing",
                )

                panel.doLayout()

                assertEquals(
                    UNWRAPPED_HEIGHT,
                    child.height,
                    "a placement that resized the child gives that reading up, so the pass after it asks " +
                        "the child again at the width it now holds rather than keeping the one taken before",
                )
            }
        }

    @Test
    fun aChildWithNoPeerThePlacementResizedIsAskedAfreshAtTheExtentItWasPlacedAt() =
        onEventDispatchThread {
            val child = WrappingChild()
            val panel = composed(rowPolicyPanel())
            panel.add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true)))
            panel.setSize(WRAPPING_WIDTH, WRAPPING_WIDTH)

            panel.doLayout()
            panel.doLayout()

            assertEquals(
                UNWRAPPED_HEIGHT,
                child.height,
                "a child with no peer holds no reading to compare, so the pass after a placement that resized it " +
                    "asks it again at the width it now holds",
            )
        }

    @Test
    fun aChildAskedOnlyForItsMinimumIsAskedAfreshAtTheExtentItWasPlacedAt() =
        onEventDispatchThread {
            val child = WrappingMinimumChild()
            val panel = composed(rowPolicyPanel())
            val row = panel.policyLayout
            panel.add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true)))
            row.declareLayoutChain(child, layoutChainOf { SwingModifier.height(IntrinsicSize.Min) })

            peered(panel) {
                panel.setSize(WRAPPING_WIDTH, WRAPPING_WIDTH)

                panel.doLayout()
                panel.doLayout()

                assertEquals(
                    UNWRAPPED_HEIGHT,
                    child.height,
                    "a child granted both extents is asked only for its minimum through its layout modifiers, and " +
                        "the pass after a placement that resized it asks again at the width it now holds",
                )
            }
        }

    @Test
    fun aRowWithNoPeerPlacedAgainKeepsTheWeightsItSettledOn() =
        onEventDispatchThread {
            val wide = FixedSizeChild(150, 10)
            val row = composed(rowPolicyPanel())
            row.add(wide, LinearConstraint(weight = WeightPlacement(1f, fill = false)))
            row.add(FixedSizeChild(10, 10), LinearConstraint(weight = WeightPlacement(1f, fill = false)))
            val parent =
                policyPanel(
                    MeasurePolicy { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints(maxWidth = 300))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    row,
                )
            parent.setSize(400, 400)

            parent.doLayout()
            row.doLayout()
            row.doLayout()

            assertEquals(
                150,
                wide.width,
                "a child whose preferred size its placement leaves as it was keeps the share the row settled on " +
                    "under its parent's loose offer, rather than the row sharing its narrower width out again",
            )
        }

    @Test
    fun aNestedContainerPlacesWhatTheMeasureItsParentAskedForGranted() =
        onEventDispatchThread {
            val row =
                composed(rowPolicyPanel())
            row.add(sized(), LinearConstraint(weight = WeightPlacement(1f, fill = false)))
            row.add(sized(), LinearConstraint(weight = WeightPlacement(3f, fill = false)))
            val column =
                composed(columnPolicyPanel())
            column.add(row, LinearConstraint())

            peered(column) {
                column.setSize(400, 200)

                column.validate()

                assertEquals(
                    listOf(
                        Rectangle(0, 0, SHARING_CHILD.width, SHARING_CHILD.height),
                        Rectangle(SHARING_CHILD.width, 0, SHARING_CHILD.width, SHARING_CHILD.height),
                    ),
                    row.declarationBounds(),
                    "the column's own placement of the row invalidates the row, and that must not throw away " +
                        "the pass the column already ran over it: the row places what that pass granted",
                )
            }
        }

    @Test
    fun aContainerInvalidatedByItsOwnPlacementStillAsksItsChildrenAfresh() =
        onEventDispatchThread {
            val child = AskingChild()
            val row =
                composed(rowPolicyPanel())
            row.add(child, LinearConstraint())

            peered(row) {
                row.setSize(400, 200)
                row.validate()
                // A second pass at the same extent leaves the reading warm: the first one placed the child
                // somewhere new, which gives it up.
                row.invalidate()
                row.validate()

                child.wants = Dimension(90, SHARING_CHILD.height)
                row.setSize(200, 200)
                row.validate()

                assertEquals(
                    90,
                    child.width,
                    "a container keeps what a pass settled on across the placement that invalidates it, but " +
                        "what each child prefers is a reading of the child's own and is given up either way",
                )
            }
        }

    @Test
    fun aContainerThatGainsOrLosesAChildMeasuresAfreshRatherThanPlacingThePassBeforeIt() =
        onEventDispatchThread {
            val row =
                composed(rowPolicyPanel())
            val original = sized()
            row.add(original, LinearConstraint())
            row.measure(Constraints(maxWidth = 200, maxHeight = 200))

            // The extents below are exactly what each pass settled on, so a container that kept a pass it
            // should not have would still match and place it.
            val gained = sized()
            row.add(gained, LinearConstraint())
            row.setSize(SHARING_CHILD.width, SHARING_CHILD.height)
            row.doLayout()

            assertEquals(
                Rectangle(SHARING_CHILD.width, 0, 0, SHARING_CHILD.height),
                gained.bounds,
                "a child the last pass never saw must be measured and placed by a pass of its own, which " +
                    "leaves it whatever space the children before it did not take",
            )

            row.measure(Constraints(maxWidth = 200, maxHeight = 200))
            row.setSize(SHARING_CHILD.width * 2, SHARING_CHILD.height)
            row.remove(original)
            row.doLayout()

            assertEquals(
                Rectangle(0, 0, SHARING_CHILD.width, SHARING_CHILD.height),
                gained.bounds,
                "and the child that remains takes the place the one that left gave up, rather than keeping " +
                    "the place a pass made while both were there",
            )
        }

    @Test
    fun aPolicyPlacesAChildAgainstTheLeftEdgeUnderEitherOrientationAndMirrorsOnlyWhenItAsksTo() =
        onEventDispatchThread {
            val absolute = placedAt(ComponentOrientation.RIGHT_TO_LEFT) { placeable -> placeable.place(0, 0) }
            assertEquals(
                Rectangle(0, 0, 30, 40),
                absolute,
                "place is absolute, so a child sits against the left edge under a right-to-left parent too",
            )

            val mirrored = placedAt(ComponentOrientation.RIGHT_TO_LEFT) { placeable -> placeable.placeRelative(0, 0) }
            assertEquals(
                Rectangle(170, 0, 30, 40),
                mirrored,
                "placeRelative puts a child against the right edge of a right-to-left parent",
            )

            val leading = placedAt(ComponentOrientation.LEFT_TO_RIGHT) { placeable -> placeable.placeRelative(0, 0) }
            assertEquals(
                Rectangle(0, 0, 30, 40),
                leading,
                "and against the left edge of a left-to-right one, with no orientation read in the policy",
            )
        }

    @Test
    fun rightToLeftRelativePlacementSaturatesRatherThanWrappingAcrossAnEdge() =
        onEventDispatchThread {
            assertEquals(
                Int.MAX_VALUE,
                relativeChildX(
                    parentWidth = Int.MAX_VALUE,
                    childConstraints = Constraints(0, 0, 0, 0),
                    x = Int.MIN_VALUE,
                ),
                "mirroring an Int.MIN_VALUE placement past the right edge must stop there rather than wrap left",
            )
            assertEquals(
                Int.MIN_VALUE,
                relativeChildX(
                    parentWidth = 0,
                    childConstraints = Constraints(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0),
                    x = Int.MAX_VALUE,
                ),
                "mirroring a maximum-sized child and placement past the left edge must stop there " +
                    "rather than wrap right",
            )
        }

    @Test
    fun aChainedPlaceablesFirstBaselineDerivesItsComponentSizeAndOffsetFromItsMeasurement() =
        onEventDispatchThread {
            val child = RecordingBaselineChild()

            assertEquals(
                60,
                firstBaselineOf(
                    child,
                    layoutChainOf {
                        SwingModifier.absolutePadding(left = 5, top = 7, right = 11, bottom = 13).absoluteOffset(19, 23)
                    },
                ),
                "a baseline must be the component's measured-size baseline plus every modifier's vertical displacement",
            )
            assertEquals(
                Dimension(84, 60),
                child.baselineSize,
                "the padding outside the component must be removed before its baseline is asked for",
            )
        }

    @Test
    fun finalComponentPlacementSaturatesItsPolicyCoordinateAndModifierOffset() =
        onEventDispatchThread {
            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, _ ->
                        val child = measurables.single().measure(Constraints(0, 0, 0, 0))
                        layout(0, 0) { child.place(Int.MAX_VALUE, Int.MAX_VALUE) }
                    },
                    null,
                )
            val panel = composed(ConstrainedPanel(layout))
            val child = FixedSizeChild()
            panel.add(child)
            layout.declareLayoutChain(child, layoutChainOf { SwingModifier.absoluteOffset(1, 1) })
            panel.setSize(0, 0)

            panel.doLayout()

            assertEquals(
                Rectangle(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0),
                child.bounds,
                "a modifier move beyond a maximum policy coordinate must stay on that edge rather than wrap",
            )
        }

    @Test
    fun insetOriginsSaturateWhenAPolicyPlacementAddsToThem() =
        onEventDispatchThread {
            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, _ ->
                        val child = measurables.single().measure(Constraints(0, 0, 0, 0))
                        layout(0, 0) { child.place(1, 1) }
                    },
                    null,
                )
            val panel = composed(MaximumOriginPanel(layout))
            val child = FixedSizeChild()
            panel.add(child)
            panel.setSize(0, 0)

            panel.doLayout()

            assertEquals(
                Rectangle(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0),
                child.bounds,
                "a placement one pixel beyond a maximum inset origin must stay on that origin instead of wrapping",
            )
        }

    @Test
    fun negativeInsetOriginsAndPolicyCoordinatesComposeWithModifiersBeforeSaturating() =
        onEventDispatchThread {
            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, _ ->
                        val child = measurables.single().measure(Constraints(0, 0, 0, 0))
                        layout(0, 0) { child.place(-1, 0) }
                    },
                    null,
                )
            val panel = composed(MinimumOriginPanel(layout))
            val child = FixedSizeChild()
            panel.add(child)
            layout.declareLayoutChain(child, layoutChainOf { SwingModifier.absoluteOffset(Int.MAX_VALUE, 0) })
            panel.setSize(0, 0)

            panel.doLayout()

            assertEquals(
                Rectangle(-2, 0, 0, 0),
                child.bounds,
                "a negative origin, policy coordinate and offset must cancel as one sum before its final clamp",
            )
        }

    @Test
    fun relativePlacementComposesWithInsetOriginsAndModifierOffsetsBeforeSaturating() =
        onEventDispatchThread {
            val layout =
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, _ ->
                        val child = measurables.single().measure(Constraints(0, 0, 0, 0))
                        layout(0, 0) { child.placeRelative(Int.MIN_VALUE, 0) }
                    },
                    null,
                )
            val panel = composed(MinimumOriginPanel(layout))
            val child = FixedSizeChild()
            panel.add(child)
            layout.declareLayoutChain(child, layoutChainOf { SwingModifier.absoluteOffset(1, 0) })
            panel.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
            panel.setSize(0, 0)

            panel.doLayout()

            assertEquals(
                Rectangle(1, 0, 0, 0),
                child.bounds,
                "the right-to-left relative coordinate must retain its Long value until it composes with the origin " +
                    "and offset",
            )
        }

    @Test
    fun chainedBaselineOffsetsKeepCancellationUntilTheFinalBaseline() =
        onEventDispatchThread {
            assertEquals(
                40,
                firstBaselineOf(
                    RecordingBaselineChild(),
                    layoutChainOf {
                        SwingModifier
                            .absoluteOffset(0, Int.MIN_VALUE)
                            .absoluteOffset(0, 1)
                            .absoluteOffset(0, Int.MAX_VALUE)
                    },
                ),
                "offsets that cancel must leave the component baseline unchanged rather than clamp midway through",
            )
        }

    @Test
    fun aChainedBaselineSaturatesAboveTheLargestRepresentableCoordinate() =
        onEventDispatchThread {
            assertEquals(
                Int.MAX_VALUE,
                firstBaselineOf(
                    RecordingBaselineChild(),
                    layoutChainOf { SwingModifier.absoluteOffset(0, Int.MAX_VALUE) },
                ),
                "a baseline beyond the greatest coordinate must stay at that edge rather than wrap negative",
            )
        }

    @Test
    fun aChainedBaselineSaturatesAboveTheUnspecifiedLine() =
        onEventDispatchThread {
            assertEquals(
                AlignmentLine.UNSPECIFIED + 1,
                firstBaselineOf(
                    RecordingBaselineChild(),
                    layoutChainOf { SwingModifier.absoluteOffset(0, Int.MIN_VALUE).absoluteOffset(0, Int.MIN_VALUE) },
                ),
                "a baseline below the least coordinate must stay just above the unspecified line rather than wrap " +
                    "positive or read as no baseline",
            )
        }

    @Test
    fun aChainedPlaceableKeepsAnAbsentBaseline() =
        onEventDispatchThread {
            assertEquals(
                AlignmentLine.UNSPECIFIED,
                firstBaselineOf(
                    NoBaselineChild(),
                    layoutChainOf {
                        SwingModifier.absolutePadding(left = 5, top = 7, right = 11, bottom = 13).absoluteOffset(19, 23)
                    },
                ),
                "a component without a baseline must stay out of baseline alignment despite its modifier displacement",
            )
        }

    @Test
    fun replacingAConstraintKeepsTheChildMeasurableAndModifierChainWhileDroppingItsSettledResult() =
        onEventDispatchThread {
            val firstConstraint = Dimension(17, 0)
            val replacementConstraint = Dimension(23, 0)
            val policy =
                MeasurePolicy { measurables, _ ->
                    layout((measurables.single().parentData as Dimension).width, 0) {}
                }
            val layout = MeasurePolicyLayout(policy, null)
            val panel = composed(ConstrainedPanel(layout))
            val child = FixedSizeChild()
            panel.add(child, firstConstraint)
            layout.declareLayoutChain(child, layoutChainOf { SwingModifier.absoluteOffset(2, 3) })
            val measurable = layout.measurables.of(child)

            assertEquals(
                firstConstraint,
                layout.measurables.measuredSize(Constraints.Unbounded),
                "the first constrained measurement must leave a result that could otherwise be reused",
            )

            layout.addLayoutComponent(child, replacementConstraint)
            panel.invalidate()

            assertSame(measurable, layout.measurables.of(child), "a constraint replacement must not rebuild the child")
            assertEquals(replacementConstraint, measurable.parentData, "the policy must read the new parent data")
            assertEquals(
                replacementConstraint.width,
                layout.measurables.settledOn(firstConstraint.width, 0).width,
                "a result settled with the old constraint must not be used after the replacement",
            )
        }

    @Test
    fun alignmentLinesCompareEqualToAPlainMapOfTheSameContents() =
        onEventDispatchThread {
            val policy = MeasurePolicy { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 12)) {} }
            val panel = composed(ConstrainedPanel(MeasurePolicyLayout(policy, null)))
            panel.setSize(CHILD_WIDTH, CHILD_HEIGHT)
            panel.doLayout()

            val lines = panel.alignmentLines
            val plain = mapOf<AlignmentLine, Int>(FirstBaseline to 12)
            assertTrue(
                lines == plain,
                "a lazily shifted alignment-lines map must equal a plain map of the same contents",
            )
            assertEquals(plain.hashCode(), lines.hashCode(), "equal alignment-lines maps must hash alike")
        }

    @Test
    fun alignmentLinesHoldTheLinesTheChildrenPutWhereThePolicyPlacesThem() =
        onEventDispatchThread {
            val child =
                object : JPanel() {
                    override fun getBaseline(
                        width: Int,
                        height: Int,
                    ): Int = 7
                }
            val policy =
                MeasurePolicy { measurables, constraints ->
                    val placeable = measurables.single().measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 3) }
                }
            val panel = policyPanel(policy, child)
            panel.border = EmptyBorder(2, 0, 0, 0)
            panel.setSize(CHILD_WIDTH, CHILD_HEIGHT)
            panel.doLayout()

            val lines = panel.alignmentLines
            val plain = mapOf<AlignmentLine, Int>(FirstBaseline to 12)
            assertTrue(
                lines == plain,
                "a container must hold the line its child puts, moved by the placement and insets",
            )
            assertEquals(plain.hashCode(), lines.hashCode(), "equal alignment-lines maps must hash alike")
        }
}

/** The bounds of a policy container's children in their declaration order, not Swing's z-order. */
private fun ConstrainedPanel.declarationBounds(): List<Rectangle> = stackingOrder.order.map { it.bounds }

/** Where a policy placing its one child with [placement] leaves that child, under [orientation]. */
private fun placedAt(
    orientation: ComponentOrientation,
    placement: PlacementScope.(Placeable) -> Unit,
): Rectangle {
    val policy = SingleChildPolicy(placement)
    val panel = policyPanel(policy, FixedSizeChild(30, 40))
    panel.componentOrientation = orientation
    panel.setSize(200, 100)
    panel.doLayout()
    return panel.getComponent(0).bounds
}

/** Where a right-to-left policy placing its child at [x] from the leading edge leaves it. */
private fun relativeChildX(
    parentWidth: Int,
    childConstraints: Constraints,
    x: Int,
): Int {
    val policy =
        MeasurePolicy { measurables, _ ->
            val child = measurables.single().measure(childConstraints)
            layout(parentWidth, 0) { child.placeRelative(x, 0) }
        }
    val panel = policyPanel(policy, FixedSizeChild())
    panel.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
    panel.setSize(parentWidth, 0)
    panel.doLayout()
    return panel.getComponent(0).x
}

/** A panel laid out by [policy], holding [children] under no constraint of their own. */
private fun policyPanel(
    policy: MeasurePolicy,
    vararg children: Component,
): ConstrainedPanel {
    val panel = composed(ConstrainedPanel(MeasurePolicyLayout(policy, null)))
    children.forEach(panel::add)
    return panel
}

/** A child counting how often its container asks it what extent it prefers. */
private class CountingChild : JPanel() {
    var questions: Int = 0
        private set

    override fun getPreferredSize(): Dimension {
        questions++
        return super.getPreferredSize()
    }
}

/** The extent each child of the nested row asks for, well below the share of that row its weight names. */
private val SHARING_CHILD = Dimension(50, 40)

/** A child asking for one fixed extent and nothing else, so what it occupies is what it was granted. */
private fun sized(): JComponent = JPanel().also { it.preferredSize = SHARING_CHILD }

// What a child wanting several lines below the width the panel is given, and one line at it, asks for.
private const val WRAPPED_HEIGHT = 80
private const val UNWRAPPED_HEIGHT = 20

/** A child asking for whatever it was last told to, with no reading of its own bounds behind it. */
private class AskingChild : JPanel() {
    var wants: Dimension = SHARING_CHILD

    override fun getPreferredSize(): Dimension = wants
}

/** A policy panel with legal but unusually large horizontal insets. */
private class HugeInsetPanel(
    layout: MeasurePolicyLayout,
) : ConstrainedPanel(layout) {
    init {
        border = EmptyBorder(0, Int.MAX_VALUE, 0, Int.MAX_VALUE)
    }
}

/** What a policy measuring [child] through [chain] at a fixed 100 by 80 reads as the child's first baseline. */
private fun firstBaselineOf(
    child: Component,
    chain: List<LayoutModifierNode>,
): Int {
    var baseline = 0
    val layout =
        MeasurePolicyLayout(
            MeasurePolicy { measurables, _ ->
                baseline = measurables.single().measure(Constraints(100, 100, 80, 80))[FirstBaseline]
                EmptyResult
            },
            null,
        )
    val panel = composed(ConstrainedPanel(layout))
    panel.add(child)
    layout.declareLayoutChain(child, chain)
    panel.setSize(100, 80)
    panel.doLayout()
    return baseline
}

/** A panel whose inner rectangle starts at the greatest coordinate an AWT component can carry. */
private class MaximumOriginPanel(
    layout: MeasurePolicyLayout,
) : ConstrainedPanel(layout) {
    init {
        border = EmptyBorder(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0)
    }
}

/** A panel whose inner rectangle starts at the least coordinate an AWT component can carry. */
private class MinimumOriginPanel(
    layout: MeasurePolicyLayout,
) : ConstrainedPanel(layout) {
    init {
        border = EmptyBorder(0, Int.MIN_VALUE, 0, 0)
    }
}

/** A component that exposes both the dimensions and the baseline a query reaches it with. */
private class RecordingBaselineChild : JPanel() {
    var baselineSize: Dimension? = null
        private set

    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int {
        baselineSize = Dimension(width, height)
        return height / 2
    }
}

/** A component that never carries a baseline, whatever extents a layout asks it about. */
private class NoBaselineChild : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = -1
}

/** A child whose height depends on its own width, the way a component wrapping its content does. */
private class WrappingChild : JPanel() {
    override fun getPreferredSize(): Dimension =
        Dimension(0, if (width >= WRAPPING_WIDTH) UNWRAPPED_HEIGHT else WRAPPED_HEIGHT)
}

/** A child whose minimum height depends on its own width, the way [WrappingChild]'s preferred height does. */
private class WrappingMinimumChild : JPanel() {
    override fun getMinimumSize(): Dimension =
        Dimension(0, if (width >= WRAPPING_WIDTH) UNWRAPPED_HEIGHT else WRAPPED_HEIGHT)
}

/** The width at which [WrappingChild] stops needing more than one line, the extent its panel is given. */
private const val WRAPPING_WIDTH = 200

/** A policy counting how often it is run, placing its children at their preferred extent. */
private class PassCountingPolicy : MeasurePolicy {
    var passes: Int = 0
        private set

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        passes++
        val offer = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(offer) }
        return layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
    }
}

/** A test-only modifier that intentionally reports a size outside the constraints it receives. */
private class ForcedRawExtentNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val child = measurable.measure(constraints)
        return layout(70, 40) { child.place(7, 9) }
    }
}

/** A policy that measures its one child at what it prefers and places it the way a test asks. */
private class SingleChildPolicy(
    private val placement: PlacementScope.(Placeable) -> Unit,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val offer = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val placeable = measurables.single().measure(offer)
        return object : MeasureResult {
            override val width: Int get() = constraints.maxWidth
            override val height: Int get() = constraints.maxHeight

            override fun PlacementScope.placeChildren() {
                placement(placeable)
            }
        }
    }
}

/** A policy whose result size stays fixed while its placement records the width it was offered. */
private class OfferSensitivePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val child = measurables.single().measure(Constraints(0, 0, 0, 0))
        return layout(10, 10) { child.place(constraints.maxWidth, 0) }
    }
}

/** A result occupying nothing and placing nobody. */
private object EmptyResult : MeasureResult {
    override val width: Int get() = 0
    override val height: Int get() = 0

    override fun PlacementScope.placeChildren(): Unit = Unit
}
