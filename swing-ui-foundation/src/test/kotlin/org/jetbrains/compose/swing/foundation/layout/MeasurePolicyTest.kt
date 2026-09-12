package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
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
 */
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
    fun aPolicyNamingANegativeExtentIsRefused() {
        val panel = policyPanel({ _, _ -> layout(-1, 10) {} }, FixedSizeChild())

        val failure = assertFailsWith<IllegalArgumentException> { panel.doLayout() }

        assertTrue(
            "-1 by 10" in failure.message.orEmpty(),
            "the refusal must name the extent the policy asked for, but was: ${failure.message}",
        )
    }

    @Test
    fun measuringAChildAgainOverwritesThePlaceableTheFirstMeasureHandedOut() {
        val policy = CapturingPolicy()
        val panel = policyPanel(policy, FixedSizeChild(WIDE.width, WIDE.height))
        panel.preferredSize

        val child = policy.measurables.single()
        val first = child.measure(Constraints(maxWidth = 30, maxHeight = 30))
        assertEquals(30, first.width, "the first measure is held to the width it was offered")

        val second = child.measure(Constraints(maxWidth = 70, maxHeight = 70))

        assertSame(first, second, "one object is both the measurable and the placeable it hands back")
        assertEquals(WIDE.width, first.width, "a placeable read after a second measure reports the second extent")
    }

    @Test
    fun aRowMeasuredUnderUnboundedConstraintsCollapsesItsWeightedChildren() {
        val row = LinearLayout(LayoutAxis.Horizontal, HorizontalAxisArrangement(Arrangement.Start), TOP)
        var settled: MeasureResult? = null
        // A caller outside the library reaches the row's policy with measurables of its own container's,
        // where nothing routes the question to intrinsicSize first.
        val policy =
            MeasurePolicy { measurables, _ ->
                with(row) {
                    PolicyMeasureScope.measure(measurables, Constraints.Unbounded)
                }.also { settled = it }
            }
        val panel = JPanel(TestPolicyLayout(policy))
        panel.add(
            FixedSizeChild(NARROW.width, NARROW.height),
            LinearConstraint(weight = WeightPlacement(1f, fill = true)),
        )
        panel.add(FixedSizeChild(NARROW.width, NARROW.height))
        panel.setSize(PANEL_EXTENT, PANEL_EXTENT)

        panel.doLayout()

        assertEquals(
            NARROW.width,
            settled?.width,
            "with no extent to divide, a weighted child collapses rather than claiming the whole axis",
        )
    }

    @Test
    fun hugeSpacingCannotWrapIntoSpaceForWeightedChildren() {
        val row =
            LinearLayout(
                LayoutAxis.Horizontal,
                HorizontalAxisArrangement(Arrangement.spacedBy(Int.MAX_VALUE)),
                TOP,
            )
        val panel = JPanel(row)
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
            panel.components.map { it.bounds },
            "two maximum gaps exhaust the row's width of 10, rather than overflowing into a negative total and " +
                "granting the weighted children a width of 12",
        )
    }

    @Test
    fun largeInsetsSaturateBeforeTheyReachMeasurementOrLayout() {
        var offered: Constraints? = null
        val panel =
            HugeInsetPanel(
                TestPolicyLayout { _, constraints ->
                    offered = constraints
                    layout(0, 0) {}
                },
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
            "insets wider than the panel leave the policy no inner extent rather than wrapping around to two pixels",
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
    fun aCacheKeepsTheLatestOfferWhenEqualResultsPlaceChildrenDifferently() {
        val panel = ConstrainedPanel(TestPolicyLayout(OfferSensitivePolicy()))
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
    fun aChildGrantedBothOfItsExtentsIsNeverAskedWhatItPrefers() {
        val child = CountingChild()
        val row = LinearLayout(LayoutAxis.Horizontal, HorizontalAxisArrangement(Arrangement.Start), TOP)
        val panel = JPanel(row)
        panel.add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true), fillsCrossAxis = true))
        panel.setSize(PANEL_EXTENT, PANEL_EXTENT)

        panel.doLayout()

        assertEquals(
            Rectangle(0, 0, PANEL_EXTENT, PANEL_EXTENT),
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
    fun aChildThePlacementResizedIsAskedAfreshAtTheExtentItWasPlacedAt() {
        val child = WrappingChild()
        val panel = JPanel(LinearLayout(LayoutAxis.Horizontal, HorizontalAxisArrangement(Arrangement.Start), TOP))
        panel.add(child, LinearConstraint(weight = WeightPlacement(1f, fill = true)))

        peered(panel) {
            panel.setSize(PANEL_EXTENT, PANEL_EXTENT)

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
    fun aNestedContainerPlacesWhatTheMeasureItsParentAskedForGranted() {
        val row =
            ConstrainedPanel(LinearLayout(LayoutAxis.Horizontal, HorizontalAxisArrangement(Arrangement.Start), TOP))
        row.add(sized(), LinearConstraint(weight = WeightPlacement(1f, fill = false)))
        row.add(sized(), LinearConstraint(weight = WeightPlacement(3f, fill = false)))
        val column =
            ConstrainedPanel(LinearLayout(LayoutAxis.Vertical, VerticalAxisArrangement(Arrangement.Top), START))
        column.add(row, LinearConstraint())

        peered(column) {
            column.setSize(PANEL_EXTENT * 2, PANEL_EXTENT)

            column.validate()

            assertEquals(
                listOf(
                    Rectangle(0, 0, SHARING_CHILD.width, SHARING_CHILD.height),
                    Rectangle(SHARING_CHILD.width, 0, SHARING_CHILD.width, SHARING_CHILD.height),
                ),
                row.components.map { it.bounds },
                "the column's own placement of the row invalidates the row, and that must not throw away " +
                    "the pass the column already ran over it: the row places what that pass granted",
            )
        }
    }

    @Test
    fun aContainerInvalidatedByItsOwnPlacementStillAsksItsChildrenAfresh() {
        val child = AskingChild()
        val row =
            ConstrainedPanel(LinearLayout(LayoutAxis.Horizontal, HorizontalAxisArrangement(Arrangement.Start), TOP))
        row.add(child, LinearConstraint())

        peered(row) {
            row.setSize(PANEL_EXTENT * 2, PANEL_EXTENT)
            row.validate()
            // A second pass at the same extent leaves the reading warm: the first one placed the child
            // somewhere new, which gives it up.
            row.invalidate()
            row.validate()

            child.wants = Dimension(WIDENED_CHILD, SHARING_CHILD.height)
            row.setSize(PANEL_EXTENT, PANEL_EXTENT)
            row.validate()

            assertEquals(
                WIDENED_CHILD,
                child.width,
                "a container keeps what a pass settled on across the placement that invalidates it, but " +
                    "what each child prefers is a reading of the child's own and is given up either way",
            )
        }
    }

    @Test
    fun aContainerThatGainsOrLosesAChildMeasuresAfreshRatherThanPlacingThePassBeforeIt() {
        val row =
            ConstrainedPanel(LinearLayout(LayoutAxis.Horizontal, HorizontalAxisArrangement(Arrangement.Start), TOP))
        row.add(sized(), LinearConstraint())
        row.measure(Constraints(maxWidth = PANEL_EXTENT, maxHeight = PANEL_EXTENT))

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
                "leaves it whatever room the children before it did not take",
        )

        row.measure(Constraints(maxWidth = PANEL_EXTENT, maxHeight = PANEL_EXTENT))
        row.setSize(SHARING_CHILD.width * 2, SHARING_CHILD.height)
        row.remove(row.getComponent(0))
        row.doLayout()

        assertEquals(
            Rectangle(0, 0, SHARING_CHILD.width, SHARING_CHILD.height),
            gained.bounds,
            "and the child that remains takes the place the one that left gave up, rather than keeping " +
                "the place a pass made while both were there",
        )
    }

    @Test
    fun aPolicyPlacesAChildAgainstTheLeftEdgeUnderEitherOrientationAndMirrorsOnlyWhenItAsksTo() {
        val absolute = placedAt(ComponentOrientation.RIGHT_TO_LEFT) { placeable -> placeable.place(0, 0) }
        assertEquals(
            Rectangle(0, 0, NARROW.width, NARROW.height),
            absolute,
            "place is absolute, so a child sits against the left edge under a right-to-left parent too",
        )

        val mirrored = placedAt(ComponentOrientation.RIGHT_TO_LEFT) { placeable -> placeable.placeRelative(0, 0) }
        assertEquals(
            Rectangle(PANEL_EXTENT - NARROW.width, 0, NARROW.width, NARROW.height),
            mirrored,
            "placeRelative puts a child against the right edge of a right-to-left parent",
        )

        val leading = placedAt(ComponentOrientation.LEFT_TO_RIGHT) { placeable -> placeable.placeRelative(0, 0) }
        assertEquals(
            Rectangle(0, 0, NARROW.width, NARROW.height),
            leading,
            "and against the left edge of a left-to-right one, with no orientation read in the policy",
        )
    }

    private companion object {
        val TOP = VerticalAxisAlignment(Alignment.Top)
        val NARROW = Dimension(30, 40)
        val WIDE = Dimension(70, 40)
        const val PANEL_EXTENT = 200

        val START = HorizontalAxisAlignment(Alignment.Start)
    }
}

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

/** A panel laid out by [policy], holding [children] under no constraint of their own. */
private fun policyPanel(
    policy: MeasurePolicy,
    vararg children: Component,
): JPanel {
    val panel = JPanel(TestPolicyLayout(policy))
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

/**
 * Runs [body] with [root] under a frame that grants it a peer - what makes a container hold between two
 * Swing calls what a pass measured - on the dispatch thread that frame lays its own content out on. The
 * frame is never shown, so nothing takes focus.
 */
private fun peered(
    root: JComponent,
    body: () -> Unit,
) {
    val frame = JFrame()
    try {
        SwingUtilities.invokeAndWait {
            frame.contentPane.layout = null
            frame.contentPane.add(root)
            frame.addNotify()
            body()
        }
    } finally {
        SwingUtilities.invokeAndWait { frame.dispose() }
    }
}

/** The extent each child of the nested row asks for, well below the share of that row its weight names. */
private val SHARING_CHILD = Dimension(50, 40)

/** A child asking for one fixed extent and nothing else, so what it occupies is what it was granted. */
private fun sized(): JComponent = JPanel().also { it.preferredSize = SHARING_CHILD }

// What a child wanting several lines below the width the panel is given, and one line at it, asks for.
private const val WRAPPED_HEIGHT = 80
private const val UNWRAPPED_HEIGHT = 20

/** How wide [AskingChild] asks to be once it is told to want more than it did. */
private const val WIDENED_CHILD = 90

/** A child asking for whatever it was last told to, with no reading of its own bounds behind it. */
private class AskingChild : JPanel() {
    var wants: Dimension = SHARING_CHILD

    override fun getPreferredSize(): Dimension = wants
}

/** A policy panel with legal but unusually large horizontal insets. */
private class HugeInsetPanel(
    layout: MeasurePolicyLayout,
) : ConstrainedPanel(layout) {
    override fun getInsets(): Insets = Insets(0, Int.MAX_VALUE, 0, Int.MAX_VALUE)
}

/** A child whose height depends on its own width, the way a component wrapping its content does. */
private class WrappingChild : JPanel() {
    override fun getPreferredSize(): Dimension =
        Dimension(0, if (width >= WRAPPING_WIDTH) UNWRAPPED_HEIGHT else WRAPPED_HEIGHT)
}

/** The width at which [WrappingChild] stops needing more than one line, the extent its panel is given. */
private const val WRAPPING_WIDTH = 200

/** A policy that keeps what it was handed, and asks for nothing. */
private class CapturingPolicy : MeasurePolicy {
    var measurables: List<Measurable> = emptyList()
        private set

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        this@CapturingPolicy.measurables = measurables.toList()
        return EmptyResult
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
