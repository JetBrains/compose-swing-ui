package org.jetbrains.compose.swing.foundation.layout

import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.opentest4j.MultipleFailuresError
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import java.lang.reflect.Proxy
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
    fun cleanupAttemptsEveryCompositionAndClearsTheRegistryBeforeReportingFailures() {
        assertTrue(ComposedPanels.handles.isEmpty(), "fixture registry must start empty")
        val firstFailure = IllegalStateException("first disposal failed")
        val secondFailure = IllegalArgumentException("second disposal failed")
        var thirdAttempted = false

        fun failingDisposer(
            failure: Throwable,
            beforeFailure: () -> Unit = {},
        ): DisposableHandle =
            DisposableHandle {
                beforeFailure()
                throw failure
            }

        ComposedPanels.handles +=
            failingDisposer(firstFailure) {
                assertTrue(ComposedPanels.handles.isEmpty(), "clear the registry before disposing any composition")
            }
        ComposedPanels.handles += failingDisposer(secondFailure)
        ComposedPanels.handles += DisposableHandle { thirdAttempted = true }
        val context =
            Proxy.newProxyInstance(
                ExtensionContext::class.java.classLoader,
                arrayOf(ExtensionContext::class.java),
            ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as ExtensionContext

        val failure = assertFailsWith<MultipleFailuresError> { ComposedPanels().afterEach(context) }

        assertAll(
            { assertEquals(2, failure.failures.size) },
            { assertSame(firstFailure, failure.failures[0]) },
            { assertSame(secondFailure, failure.failures[1]) },
            { assertTrue(thirdAttempted, "cleanup must attempt handles after earlier failures") },
            { assertTrue(ComposedPanels.handles.isEmpty(), "the shared registry must be empty after cleanup") },
        )
    }

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
        val rows =
            listOf(
                "minWidth is 20 and maxWidth is 10" to { Constraints(minWidth = 20, maxWidth = 10) },
                "minHeight is 20 and maxHeight is 10" to { Constraints(minHeight = 20, maxHeight = 10) },
                "zero or more" to { Constraints(minWidth = -1) },
                "zero or more" to { Constraints(minHeight = -1) },
            )
        for ((message, constraints) in rows) {
            val failure = assertFailsWith<IllegalArgumentException> { constraints() }
            assertTrue(
                message in failure.message.orEmpty(),
                "the refusal must explain the invalid offer: ${failure.message}",
            )
        }
    }

    @Test
    fun aPolicyNamingANegativeExtentIsRefused() =
        runSwingTest {
            for (extent in listOf(Dimension(-1, 10), Dimension(10, -1))) {
                val panel = policyPanel({ _, _ -> layout(extent.width, extent.height) {} }, FixedSizeChild())
                val failure = assertFailsWith<IllegalArgumentException> { panel.doLayout() }
                assertTrue(
                    "${extent.width} by ${extent.height}" in failure.message.orEmpty(),
                    "the refusal must name the extent the policy requested, but was: ${failure.message}",
                )
            }
        }

    @Test
    fun aStockChildUsesItsConstrainedExtentForMeasurementAndPlacement() =
        assertChildExtent(
            { FixedSizeChild(70, 40) },
            Constraints(30, 50, 20, 30),
            Dimension(50, 30),
            Dimension(50, 30),
            Rectangle(10, 15, 50, 30),
        )

    @Test
    fun anOversizedPolicyContainerIsCenteredAtItsRawExtent() =
        assertChildExtent(
            { policyPanel(MeasurePolicy { _, _ -> layout(70, 40) {} }) },
            Constraints(30, 50, 20, 30),
            Dimension(50, 30),
            Dimension(70, 40),
            Rectangle(0, 10, 70, 40),
        )

    @Test
    fun anUndersizedPolicyContainerIsCenteredInsideItsMinimumExtent() =
        assertChildExtent(
            { policyPanel(MeasurePolicy { _, _ -> layout(10, 5) {} }) },
            Constraints(30, 50, 20, 40),
            Dimension(30, 20),
            Dimension(10, 5),
            Rectangle(20, 22, 10, 5),
        )

    private fun assertChildExtent(
        childFactory: () -> JComponent,
        offer: Constraints,
        apparent: Dimension,
        measured: Dimension,
        bounds: Rectangle,
    ) = runSwingTest {
        var result: Placeable? = null
        val child = childFactory()
        val panel =
            policyPanel(
                MeasurePolicy { measurables, _ ->
                    val placeable = measurables.single().measure(offer)
                    result = placeable
                    layout(100, 100) { placeable.place(10, 15) }
                },
                child,
            )
        panel.setSize(100, 100)
        panel.doLayout()
        val placeable = checkNotNull(result)
        assertEquals(apparent, Dimension(placeable.width, placeable.height))
        assertEquals(measured, Dimension(placeable.measuredWidth, placeable.measuredHeight))
        assertEquals(bounds, child.bounds)
    }

    @Test
    fun aModifierChainPreservesItsChildsCenteredOverflowPlacement() =
        runSwingTest {
            val child = FixedSizeChild(10, 10)
            val panel =
                policyPanel(
                    MeasurePolicy { measurables, _ ->
                        val child = measurables.single().measure(Constraints(maxWidth = 50, maxHeight = 30))
                        layout(100, 100) { child.place(10, 15) }
                    },
                    child,
                    listOf(
                        object : LayoutModifierNode() {
                            override fun MeasureScope.measure(
                                measurable: Measurable,
                                constraints: Constraints,
                            ): MeasureResult {
                                val child = measurable.measure(constraints)
                                return layout(70, 40) { child.place(7, 9) }
                            }
                        },
                    ),
                )
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
        runSwingTest {
            val policy =
                MeasurePolicy { measurables, constraints ->
                    val child = measurables.single().measure(constraints)
                    layout(child.width, child.height) {}
                }
            val child = FixedSizeChild(70, 40).also { it.minimumSize = Dimension(5, 7) }
            val panel = policyPanel(policy, child)
            val children = listOf(panel.policyLayout.measurables.of(child))
            with(policy) {
                val intrinsicSizes =
                    mapOf(
                        "minimum width" to PolicyMeasureScope.minIntrinsicWidth(children, Int.MAX_VALUE),
                        "minimum height" to PolicyMeasureScope.minIntrinsicHeight(children, Int.MAX_VALUE),
                        "maximum width" to PolicyMeasureScope.maxIntrinsicWidth(children, Int.MAX_VALUE),
                        "maximum height" to PolicyMeasureScope.maxIntrinsicHeight(children, Int.MAX_VALUE),
                    )
                val expected =
                    mapOf(
                        "minimum width" to child.minimumSize.width,
                        "minimum height" to child.minimumSize.height,
                        "maximum width" to child.preferredSize.width,
                        "maximum height" to child.preferredSize.height,
                    )
                assertEquals(expected, intrinsicSizes, "default unbounded hooks adapt stock Swing's native sizes")
            }
        }

    @Test
    fun aMeasureResultExposesItsAlignmentLinesAndCanPlaceWithTheStandaloneSurface() =
        runSwingTest {
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
        runSwingTest {
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
            val panel = policyPanel(policy)
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
        runSwingTest {
            val row =
                rowPolicy(arrangement = Arrangement.spacedBy(Int.MAX_VALUE))
            val panel = policyPanel(row)
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
        runSwingTest {
            var offered: Constraints? = null
            val panel =
                composed(
                    ConstrainedPanel(
                        MeasurePolicyLayout(
                            MeasurePolicy { _, constraints ->
                                offered = constraints
                                layout(0, 0) {}
                            },
                            null,
                        ),
                    ).apply { border = EmptyBorder(0, Int.MAX_VALUE, 0, Int.MAX_VALUE) },
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
        val rows =
            mapOf(
                ComponentOrientation.LEFT_TO_RIGHT to listOf(0, Int.MIN_VALUE + 10, Int.MIN_VALUE),
                ComponentOrientation.RIGHT_TO_LEFT to listOf(90, Int.MAX_VALUE, Int.MAX_VALUE),
            )
        for ((orientation, expected) in rows) {
            val positions = IntArray(3)
            Arrangement.spacedBy(Int.MIN_VALUE).arrange(100, intArrayOf(10, 10, 10), orientation, positions)
            assertEquals(expected, positions.toList(), "packing with a minimum gap saturates in either direction")
        }
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
        runSwingTest {
            val child = FixedSizeChild()
            val panel =
                policyPanel(
                    MeasurePolicy { measurables, constraints ->
                        val child = measurables.single().measure(Constraints(0, 0, 0, 0))
                        layout(10, 10) { child.place(constraints.maxWidth, 0) }
                    },
                    child,
                )

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
        runSwingTest {
            val policy =
                object : MeasurePolicy {
                    var passes = 0

                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult {
                        passes++
                        val offer = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
                        val placeables = measurables.map { it.measure(offer) }
                        return layout(
                            constraints.maxWidth,
                            constraints.maxHeight,
                        ) { placeables.forEach { it.place(0, 0) } }
                    }
                }
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
        runSwingTest {
            val child = PreferredHeightChild()
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
    fun aChildGrantedAWidthUsesItsPreferredHeightAtThatWidth() =
        assertRepeatedChildExtent(
            ::PreferredHeightChild,
            { child ->
                composed(rowPolicyPanel()).apply {
                    add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true)))
                }
            },
            listOf(UNWRAPPED_HEIGHT, UNWRAPPED_HEIGHT),
            { it.height },
        )

    @Test
    fun aChildGrantedAWidthUsesItsMinimumHeightAtThatWidth() =
        assertRepeatedChildExtent(
            ::MinimumHeightChild,
            { child ->
                composed(rowPolicyPanel()).apply {
                    add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true)))
                    policyLayout.declareLayoutChain(child, layoutChainOf { SwingModifier.height(IntrinsicSize.Min) })
                }
            },
            listOf(UNWRAPPED_HEIGHT),
            { it.height },
            initialLayouts = 1,
        )

    @Test
    fun aChildWithUnboundedWidthUsesItsHeightAtItsPreferredWidth() =
        assertRepeatedChildExtent(
            ::UnboundedWidthChild,
            { child ->
                policyPanel(
                    MeasurePolicy { measurables, constraints ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    },
                    child,
                )
            },
            listOf(UNWRAPPED_HEIGHT, UNWRAPPED_HEIGHT),
            { it.height },
        )

    @Test
    fun aChildGrantedAHeightUsesItsWidthAtThatHeightOnTheNextLayout() =
        assertRepeatedChildExtent(
            ::GrantedHeightChild,
            { child ->
                policyPanel(
                    MeasurePolicy { measurables, constraints ->
                        val placeable =
                            measurables.single().measure(
                                Constraints(
                                    maxWidth = constraints.maxWidth,
                                    minHeight = constraints.maxHeight,
                                    maxHeight = constraints.maxHeight,
                                ),
                            )
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    },
                    child,
                )
            },
            listOf(WRAPPED_WIDTH, 20),
            { it.width },
        )

    private fun assertRepeatedChildExtent(
        childFactory: () -> JComponent,
        panelFactory: (JComponent) -> ConstrainedPanel,
        expected: List<Int>,
        extent: (JComponent) -> Int,
        initialLayouts: Int = 0,
    ) {
        for (hasPeer in listOf(false, true)) {
            runSwingTest {
                val child = childFactory()
                val panel = panelFactory(child)
                val reread = {
                    panel.setSize(WRAPPING_WIDTH, WRAPPING_WIDTH)
                    repeat(initialLayouts) { panel.doLayout() }
                    for ((pass, size) in expected.withIndex()) {
                        panel.doLayout()
                        assertEquals(size, extent(child), "peer=$hasPeer, pass=$pass")
                    }
                }
                if (hasPeer) peered(panel, reread) else reread()
            }
        }
    }

    @Test
    fun aRowWithNoPeerPlacedAgainKeepsTheWeightsItSettledOn() =
        runSwingTest {
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
        runSwingTest {
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
        runSwingTest {
            val child =
                object : JPanel() {
                    var wants: Dimension = SHARING_CHILD

                    override fun getPreferredSize(): Dimension = wants
                }
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
        runSwingTest {
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
        runSwingTest {
            val rows =
                listOf(
                    "absolute right-to-left" to (Triple(ComponentOrientation.RIGHT_TO_LEFT, false, 0)),
                    "relative right-to-left" to (Triple(ComponentOrientation.RIGHT_TO_LEFT, true, 170)),
                    "relative left-to-right" to (Triple(ComponentOrientation.LEFT_TO_RIGHT, true, 0)),
                )
            for ((name, row) in rows) {
                val (orientation, relative, expectedX) = row
                val panel =
                    policyPanel(
                        MeasurePolicy { measurables, constraints ->
                            val offer = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
                            val child = measurables.single().measure(offer)
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                if (relative) child.placeRelative(0, 0) else child.place(0, 0)
                            }
                        },
                        FixedSizeChild(30, 40),
                    )
                panel.componentOrientation = orientation
                panel.setSize(200, 100)
                panel.doLayout()
                assertEquals(Rectangle(expectedX, 0, 30, 40), panel.getComponent(0).bounds, name)
            }
        }

    @Test
    fun rightToLeftPlacementPastTheRightEdgeSaturatesAtTheMaximum() =
        assertRelativePlacement(Int.MAX_VALUE, Constraints(0, 0, 0, 0), Int.MIN_VALUE, Int.MAX_VALUE)

    @Test
    fun rightToLeftPlacementPastTheLeftEdgeSaturatesAtTheMinimum() =
        assertRelativePlacement(0, Constraints(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0), Int.MAX_VALUE, Int.MIN_VALUE)

    private fun assertRelativePlacement(
        parentWidth: Int,
        childConstraints: Constraints,
        x: Int,
        expected: Int,
    ) = runSwingTest {
        val panel =
            policyPanel(
                MeasurePolicy { measurables, _ ->
                    val child = measurables.single().measure(childConstraints)
                    layout(parentWidth, 0) { child.placeRelative(x, 0) }
                },
                FixedSizeChild(),
            )
        panel.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        panel.setSize(parentWidth, 0)
        panel.doLayout()
        assertEquals(expected, panel.getComponent(0).x)
    }

    @Test
    fun aChainedPlaceablesFirstBaselineUsesItsMeasuredSizeAndOffsetAndKeepsAnAbsentBaseline() =
        runSwingTest {
            val rows =
                listOf(
                    RecordingBaselineChild() to 60,
                    object : JPanel() {
                        override fun getBaseline(
                            width: Int,
                            height: Int,
                        ): Int = -1
                    } to AlignmentLine.UNSPECIFIED,
                )
            for ((child, expected) in rows) {
                assertEquals(
                    expected,
                    firstBaselineOf(
                        child,
                        layoutChainOf {
                            SwingModifier
                                .absolutePadding(left = 5, top = 7, right = 11, bottom = 13)
                                .absoluteOffset(19, 23)
                        },
                    ),
                    "a measured baseline includes modifier displacement, while an absent baseline stays absent",
                )
                if (child is RecordingBaselineChild) {
                    assertEquals(Dimension(84, 60), child.baselineSize, "padding is removed before the baseline query")
                }
            }
        }

    @Test
    fun aMaximumPolicyCoordinateAndModifierOffsetSaturateTogether() =
        assertFinalPlacement(
            PlacementScenario(
                EmptyBorder(0, 0, 0, 0),
                Dimension(Int.MAX_VALUE, Int.MAX_VALUE),
                Dimension(1, 1),
                Rectangle(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0),
            ),
        )

    @Test
    fun aMaximumInsetOriginAndPolicyCoordinateSaturateTogether() =
        assertFinalPlacement(
            PlacementScenario(
                EmptyBorder(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0),
                Dimension(1, 1),
                Dimension(0, 0),
                Rectangle(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0),
                applyOffset = false,
            ),
        )

    @Test
    fun aNegativeOriginCancelsAPositiveOffsetBeforeSaturation() =
        assertFinalPlacement(
            PlacementScenario(
                EmptyBorder(0, Int.MIN_VALUE, 0, 0),
                Dimension(-1, 0),
                Dimension(Int.MAX_VALUE, 0),
                Rectangle(-2, 0, 0, 0),
            ),
        )

    @Test
    fun aRelativeCoordinateCancelsANegativeOriginBeforeSaturation() =
        assertFinalPlacement(
            PlacementScenario(
                EmptyBorder(0, Int.MIN_VALUE, 0, 0),
                Dimension(Int.MIN_VALUE, 0),
                Dimension(1, 0),
                Rectangle(1, 0, 0, 0),
                relative = true,
            ),
        )

    private fun assertFinalPlacement(scenario: PlacementScenario) =
        runSwingTest {
            val child = FixedSizeChild()
            val panel =
                policyPanel(
                    MeasurePolicy { measurables, _ ->
                        val child = measurables.single().measure(Constraints(0, 0, 0, 0))
                        layout(0, 0) {
                            if (scenario.relative) {
                                child.placeRelative(scenario.position.width, scenario.position.height)
                            } else {
                                child.place(scenario.position.width, scenario.position.height)
                            }
                        }
                    },
                    child,
                    if (scenario.applyOffset) {
                        layoutChainOf { SwingModifier.absoluteOffset(scenario.offset.width, scenario.offset.height) }
                    } else {
                        emptyList()
                    },
                ).apply { border = scenario.border }
            if (scenario.relative) panel.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
            panel.setSize(0, 0)
            panel.doLayout()
            assertEquals(scenario.expected, child.bounds)
        }

    @Test
    fun chainedBaselineOffsetsKeepCancellationUntilTheFinalBaseline() =
        runSwingTest {
            val rows =
                listOf(
                    "cancelling offsets" to (listOf(Int.MIN_VALUE, 1, Int.MAX_VALUE) to 40),
                    "above the largest coordinate" to (listOf(Int.MAX_VALUE) to Int.MAX_VALUE),
                    "below the unspecified line" to
                        (listOf(Int.MIN_VALUE, Int.MIN_VALUE) to AlignmentLine.UNSPECIFIED + 1),
                )
            for ((name, row) in rows) {
                val (offsets, expected) = row
                assertEquals(
                    expected,
                    firstBaselineOf(
                        RecordingBaselineChild(),
                        layoutChainOf {
                            offsets.fold<Int, SwingModifier>(
                                SwingModifier,
                            ) { modifier, y -> modifier.absoluteOffset(0, y) }
                        },
                    ),
                    "$name: compose offsets before clamping the final baseline",
                )
            }
        }

    @Test
    fun replacingAConstraintKeepsTheChildMeasurableAndModifierChainWhileDroppingItsSettledResult() =
        runSwingTest {
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
        runSwingTest {
            for (source in listOf("policy", "placed child")) {
                val child =
                    object : JPanel() {
                        override fun getBaseline(
                            width: Int,
                            height: Int,
                        ): Int = 7
                    }
                val policy =
                    MeasurePolicy { measurables, constraints ->
                        if (source == "policy") {
                            layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 12)) {}
                        } else {
                            val placeable = measurables.single().measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 3) }
                        }
                    }
                val panel =
                    if (source == "policy") {
                        policyPanel(policy)
                    } else {
                        policyPanel(policy, child).apply {
                            border = EmptyBorder(2, 0, 0, 0)
                        }
                    }
                panel.setSize(CHILD_WIDTH, CHILD_HEIGHT)
                panel.doLayout()
                val lines = panel.alignmentLines
                val plain = mapOf<AlignmentLine, Int>(FirstBaseline to 12)
                assertTrue(lines == plain, "$source: alignment lines equal a plain map after placement and insets")
                assertEquals(plain.hashCode(), lines.hashCode(), "$source: equal alignment lines hash alike")
            }
        }
}

/** The bounds of a policy container's children in their declaration order, not Swing's z-order. */
private fun ConstrainedPanel.declarationBounds(): List<Rectangle> = stackingOrder.order.map { it.bounds }

/** A panel laid out by [policy], holding [children] under no constraint of their own. */
private fun policyPanel(
    policy: MeasurePolicy,
    vararg children: Component,
): ConstrainedPanel {
    val panel = composed(ConstrainedPanel(MeasurePolicyLayout(policy, null)))
    children.forEach(panel::add)
    return panel
}

private fun policyPanel(
    policy: MeasurePolicy,
    child: Component,
    chain: List<LayoutModifierNode>,
): ConstrainedPanel {
    val panel = policyPanel(policy, child)
    if (chain.isNotEmpty()) panel.policyLayout.declareLayoutChain(child, chain)
    return panel
}

/** The extent each child of the nested row asks for, well below the share of that row its weight names. */
private val SHARING_CHILD = Dimension(50, 40)

/** A child asking for one fixed extent and nothing else, so what it occupies is what it was granted. */
private fun sized(): JComponent = JPanel().also { it.preferredSize = SHARING_CHILD }

// What a child wanting several lines below the width the panel is given, and one line at it, asks for.
private const val UNWRAPPED_HEIGHT = 20

// The widths a height-dependent child prefers below and at its granted height.
private const val WRAPPED_WIDTH = 80

/** What a policy measuring [child] through [chain] at a fixed 100 by 80 reads as the child's first baseline. */
private fun firstBaselineOf(
    child: Component,
    chain: List<LayoutModifierNode>,
): Int {
    var baseline = 0
    val panel =
        policyPanel(
            MeasurePolicy { measurables, _ ->
                baseline = measurables.single().measure(Constraints(100, 100, 80, 80))[FirstBaseline]
                layout(0, 0) {}
            },
            child,
            chain,
        )
    panel.setSize(100, 80)
    panel.doLayout()
    return baseline
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

/**
 * The extent at which a wrapping child needs one line or column.
 */
private const val WRAPPING_WIDTH = 200

private class PreferredHeightChild : JPanel() {
    var questions = 0
        private set

    override fun getPreferredSize(): Dimension {
        questions++
        return Dimension(0, if (width >= WRAPPING_WIDTH) UNWRAPPED_HEIGHT else UNWRAPPED_HEIGHT * 4)
    }
}

private class MinimumHeightChild : JPanel() {
    override fun getMinimumSize(): Dimension =
        Dimension(0, if (width >= WRAPPING_WIDTH) UNWRAPPED_HEIGHT else UNWRAPPED_HEIGHT * 4)
}

private class UnboundedWidthChild : JPanel() {
    override fun getPreferredSize(): Dimension =
        Dimension(WRAPPING_WIDTH, if (width >= WRAPPING_WIDTH) UNWRAPPED_HEIGHT else UNWRAPPED_HEIGHT * 4)
}

private class GrantedHeightChild : JPanel() {
    override fun getPreferredSize(): Dimension = Dimension(if (height >= WRAPPING_WIDTH) 20 else WRAPPED_WIDTH, 0)
}

private data class PlacementScenario(
    val border: EmptyBorder,
    val position: Dimension,
    val offset: Dimension,
    val expected: Rectangle,
    val relative: Boolean = false,
    val applyOffset: Boolean = true,
)
