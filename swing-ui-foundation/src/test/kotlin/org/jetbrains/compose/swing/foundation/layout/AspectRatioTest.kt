package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A child declaring an aspect ratio is sized to the widest or tallest size its constraints allow at that
 * ratio - from the width they allow first, or from the height first where it asks for that - and is
 * measured under the constraints it was offered where none of those extents names a size at that ratio.
 * A ratio that is not finite and greater than zero names no size and is refused where it is declared.
 *
 * Ported from androidx `foundation-layout`'s own `AspectRatioTest`, whose names the cases here keep so the
 * two files read side by side. Most of the sizes that test names are taken under constraints no container
 * this library ships hands a child - a least width with no greatest one, a width bounded while the height
 * is not - so a case that measures builds its container by hand and hands the child the constraints
 * outright, the way that test's own `getSize` builds a layout of its own.
 *
 * The ratio a composition declares reaches its child only through what the applier tells the box holding
 * it, which a hand-built container says nothing about; that is
 * [aRatioIsMeasuredThroughWhereTheChildIsComposed].
 *
 * That test's `testAspectRatioModifier_intrinsicDimensions` is not here. It asks for one extent while
 * fixing the other, and `java.awt.LayoutManager2` asks a container what it prefers, the least it can
 * occupy and the most it may, never a width for a given height; [IntrinsicTest] says the same of the
 * questions this tree does ask. What that case asks with neither extent fixed - the ratio reporting the
 * content's own size - is [aRatioNoSizeSatisfiesLeavesTheChildMeasuredUnderWhatItWasOffered].
 */
class AspectRatioTest {
    @Test
    fun testAspectRatio_sizesCorrectly() {
        assertEquals(
            Dimension(30, 30),
            sizeAt(1f, Constraints(maxWidth = 30)),
            "a child as wide as it is tall must take the whole width it may occupy, and the height that implies",
        )
        assertEquals(
            Dimension(30, 15),
            sizeAt(2f, Constraints(maxWidth = 30)),
            "a child twice as wide as it is tall must halve that width into its height",
        )
        assertEquals(
            Dimension(10, 10),
            sizeAt(1f, Constraints(maxWidth = 30, maxHeight = 10)),
            "where the width it may occupy implies a height past the one it may, the height must decide both",
        )
        assertEquals(
            Dimension(20, 10),
            sizeAt(2f, Constraints(maxWidth = 30, maxHeight = 10)),
            "and the width must follow from that height at the ratio, rather than staying the width offered",
        )
        assertEquals(
            Dimension(10, 5),
            sizeAt(2f, Constraints(minWidth = 10, minHeight = 5)),
            "with no greatest extent to take a size from, the least width the child must occupy must decide both",
        )
        assertEquals(
            Dimension(20, 10),
            sizeAt(2f, Constraints(minWidth = 5, minHeight = 10)),
            "and where that least width implies a height below the least the child must occupy, the height " +
                "must decide instead",
        )
        assertEquals(
            Dimension(20, 10),
            sizeAt(2f, Constraints(20, 20, 20, 20)),
            "under an extent fixed on both axes no size satisfies the ratio, so the child must take the size " +
                "the width implies however much height it was granted",
        )
        assertEquals(
            Dimension(50, 25),
            sizeAt(2f, Constraints(minWidth = 50, minHeight = 20)),
            "a least width implying a height above the least the child must occupy must be taken as it stands",
        )
    }

    @Test
    fun testAspectRatio_sizesCorrectly_forHeightFirst() {
        assertEquals(
            Dimension(30, 30),
            sizeAt(1f, Constraints(maxHeight = 30), matchHeightConstraintsFirst = true),
            "a child taking its size from the height must take the whole height it may occupy",
        )
        assertEquals(
            Dimension(15, 30),
            sizeAt(0.5f, Constraints(maxHeight = 30), matchHeightConstraintsFirst = true),
            "a child half as wide as it is tall must halve that height into its width",
        )
        assertEquals(
            Dimension(10, 10),
            sizeAt(1f, Constraints(maxWidth = 10, maxHeight = 30), matchHeightConstraintsFirst = true),
            "where the height it may occupy implies a width past the one it may, the width must decide both",
        )
        assertEquals(
            Dimension(10, 20),
            sizeAt(0.5f, Constraints(maxWidth = 10, maxHeight = 30), matchHeightConstraintsFirst = true),
            "and the height must follow from that width at the ratio, rather than staying the height offered",
        )
        assertEquals(
            Dimension(5, 10),
            sizeAt(0.5f, Constraints(minWidth = 5, minHeight = 10), matchHeightConstraintsFirst = true),
            "with no greatest extent to take a size from, the least height the child must occupy must decide both",
        )
        assertEquals(
            Dimension(10, 20),
            sizeAt(0.5f, Constraints(minWidth = 10, minHeight = 5), matchHeightConstraintsFirst = true),
            "and where that least height implies a width below the least the child must occupy, the width " +
                "must decide instead",
        )
        assertEquals(
            Dimension(10, 20),
            sizeAt(0.5f, Constraints(20, 20, 20, 20), matchHeightConstraintsFirst = true),
            "under an extent fixed on both axes no size satisfies the ratio, so the child must take the size " +
                "the height implies however much width it was granted",
        )
        assertEquals(
            Dimension(25, 50),
            sizeAt(0.5f, Constraints(minWidth = 20, minHeight = 50), matchHeightConstraintsFirst = true),
            "a least height implying a width above the least the child must occupy must be taken as it stands",
        )
    }

    @Test
    fun aRatioNoSizeSatisfiesLeavesTheChildMeasuredUnderWhatItWasOffered() {
        assertEquals(
            Dimension(CHILD_WIDTH, CHILD_HEIGHT),
            sizeAt(2f, Constraints.Unbounded),
            "with no extent to take a size from on either axis - the question a container asks when it is " +
                "asked what it prefers - no size satisfies the ratio, so the child must be measured under " +
                "the constraints it was offered and answer with the extent it asks for",
        )
        assertEquals(
            Dimension(1, 2),
            sizeAt(0.5f, Constraints(minWidth = SMALLEST_WIDTH)),
            "a least width of one still names a size at a ratio that implies a height above nothing from it",
        )
        assertEquals(
            Dimension(CHILD_WIDTH, CHILD_HEIGHT),
            sizeAt(100f, Constraints(minWidth = SMALLEST_WIDTH)),
            "and where the ratio implies no height at all from that width, the child must again be measured " +
                "under the constraints it was offered",
        )
    }

    @Test
    fun aRatioReportsTheExtentItSizedItsChildTo() {
        val (sized, reported) = laidOutAt(2f, Constraints(FIXED_OFFER, FIXED_OFFER, FIXED_OFFER, FIXED_OFFER))

        assertEquals(
            Dimension(FIXED_OFFER, FIXED_OFFER / 2),
            sized,
            "a ratio no size within an extent fixed on both axes satisfies must size its child at the " +
                "ratio all the same, outside the extent it was offered",
        )
        assertEquals(
            Dimension(FIXED_OFFER, FIXED_OFFER),
            reported,
            "and the parent must see an apparent extent coerced into the fixed offer it made",
        )
    }

    @Test
    fun aRatioIsMeasuredThroughWhereTheChildIsComposed() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.aspectRatio(2f))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "a box asked what it prefers bounds neither extent, so the ratio names no size and the child " +
                    "answers with the extent it asks for",
            )
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_WIDTH / 2)),
                stackedChildBounds(),
                "and the pass that lays the box out bounds the width, so the child must take half of it as its " +
                    "height - the ratio reaching the child only where the composition declared it",
            )
        }

    @Test
    fun testAspectRatioModifier_zeroRatio() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>("no width per unit height is no ratio at all") {
                SwingModifier.aspectRatio(0f)
            }
        }
    }

    @Test
    fun testAspectRatioModifier_negativeRatio() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>("a width below zero per unit height names no size") {
                SwingModifier.aspectRatio(-2f)
            }
        }
    }

    @Test
    fun aRatioThatIsNotFiniteIsRefused() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>("a ratio no arithmetic can name is no ratio") {
                SwingModifier.aspectRatio(Float.NaN)
            }
            assertFailsWith<IllegalArgumentException>("a width without end per unit height implies no height") {
                SwingModifier.aspectRatio(Float.POSITIVE_INFINITY)
            }
            assertFailsWith<IllegalArgumentException>("a ratio below zero is refused however far below it is") {
                SwingModifier.aspectRatio(Float.NEGATIVE_INFINITY)
            }
        }
    }

    @Test
    fun testInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.aspectRatio(2f) }

        assertEquals(
            "aspectRatio",
            declared.lastElement().name,
            "a ratio must report itself under its own name",
        )
        assertEquals(
            mapOf("ratio" to 2f, "matchHeightConstraintsFirst" to false),
            declared.lastElement().declaredValues,
            "and must report the ratio it was declared with, and whether it takes a size from the height first",
        )
    }

    /**
     * The extent a child declaring [ratio] settles on when it is offered [constraints] outright, read off
     * the component the way androidx's own suite reads it off a position latch.
     *
     * No container this library ships offers a child the constraints named here - a box and a row each work
     * theirs out from their own extent - so the child is held by a container built for the case, whose
     * policy hands on what the case names. The ratio is declared through the scope a caller spells it in.
     */
    private fun sizeAt(
        ratio: Float,
        constraints: Constraints,
        matchHeightConstraintsFirst: Boolean = false,
    ): Dimension = laidOutAt(ratio, constraints, matchHeightConstraintsFirst).first

    /**
     * The extent the component was sized to and the extent its measurable reported, which a parent
     * aligns and sums against; see [sizeAt] for how the child is held.
     */
    private fun laidOutAt(
        ratio: Float,
        constraints: Constraints,
        matchHeightConstraintsFirst: Boolean = false,
    ): Pair<Dimension, Dimension> {
        val child = FixedSizeChild(CHILD_WIDTH, CHILD_HEIGHT)
        val policy = OfferedConstraints(constraints)
        val layout = TestPolicyLayout(policy)
        val panel = JPanel(layout)
        panel.add(child)
        // After the add, which is what builds the measurable the chain lives on.
        layout.declareLayoutChain(child, ratioChain(ratio, matchHeightConstraintsFirst))
        // Large enough that the container built for a case never itself decides what its child occupies.
        panel.setSize(1000, 1000)

        panel.doLayout()

        return child.size to Dimension(policy.width, policy.height)
    }

    /** The layout modifiers a child declaring [ratio] is measured through, as its container reads them. */
    private fun ratioChain(
        ratio: Float,
        matchHeightConstraintsFirst: Boolean,
    ): List<LayoutModifier> {
        val declared = with(BoxScopeInstance) { SwingModifier.aspectRatio(ratio, matchHeightConstraintsFirst) }
        return declared.foldIn(mutableListOf<LayoutModifier>()) { chain, element ->
            chain.also { if (element is LayoutModifier) it.add(element) }
        }
    }

    private companion object {
        /** The least width a child can be asked for while still being asked for one at all. */
        const val SMALLEST_WIDTH = 1

        /** An extent fixed on both axes, which no size at a ratio of two to one satisfies. */
        const val FIXED_OFFER = 200
    }
}

/**
 * A policy handing its one child the constraints the case named, whatever extent the container it belongs
 * to was given - which is how a case reaches an offer a container of this library never makes.
 */
private class OfferedConstraints(
    private val offered: Constraints,
) : MeasurePolicy,
    MeasureResult {
    private lateinit var child: Placeable

    override val width: Int get() = child.width

    override val height: Int get() = child.height

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        child = measurables.single().measure(offered)
        return this@OfferedConstraints
    }

    override fun PlacementScope.placeChildren() {
        child.place(0, 0)
    }
}
