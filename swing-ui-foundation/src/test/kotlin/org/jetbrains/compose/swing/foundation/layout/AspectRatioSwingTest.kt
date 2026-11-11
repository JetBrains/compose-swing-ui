// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The aspect-ratio cases that have no counterpart in androidx's `AspectRatioTest`, which [AspectRatioTest] ports. */
@ExtendWith(ComposedPanels::class)
class AspectRatioSwingTest {
    @Test
    fun aRatioNoSizeSatisfiesLeavesTheChildMeasuredUnderWhatItWasOffered() =
        runSwingTest {
            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                sizeAt(2f, Constraints.Unbounded),
                "with no extent to take a size from on either axis - the question a container asks when it is " +
                    "asked what it prefers - no size satisfies the ratio, so the child must be measured under " +
                    "the constraints it was offered and answer with the extent it asks for",
            )
            assertEquals(
                Dimension(1, 2),
                sizeAt(0.5f, Constraints(minWidth = 1)),
                "a least width of one still names a size at a ratio that implies a height above nothing from it",
            )
            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                sizeAt(100f, Constraints(minWidth = 1)),
                "and where the ratio implies no height at all from that width, the child must again be measured " +
                    "under the constraints it was offered",
            )
        }

    @Test
    fun aRatioReportsTheExtentItSizedItsChildTo() =
        runSwingTest {
            val (sized, reported) = laidOutAt(2f, Constraints(200, 200, 200, 200))

            assertEquals(
                Dimension(200, 100),
                sized,
                "a ratio no size within an extent fixed on both axes satisfies must size its child at the " +
                    "ratio all the same, outside the extent it was offered",
            )
            assertEquals(
                Dimension(200, 200),
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
                Dimension(CHILD_WIDTH, CHILD_WIDTH / 2),
                containerPreferredSize(),
                "a box whose preferred size is read takes the child's preferred width and the ratio's height at that " +
                    "width, as androidx's width(IntrinsicSize.Max).height(IntrinsicSize.Max) does",
            )
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_WIDTH / 2)),
                stackedChildBounds(),
                "and the pass that lays the box out bounds the width, so the child must take half of it as its " +
                    "height - the ratio reaching the child only where the composition declared it",
            )
        }

    @Test
    fun aColumnAtItsOwnWidthTakesARatioChildsWidthFromTheChildsHeight() =
        runComposeSwingTest {
            setContent {
                Column {
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        SizedChild(0)
                        SizedChild(1, SwingModifier.aspectRatio(2f).alignBy { it.measuredWidth })
                    }
                }
            }

            assertEquals(
                Dimension(2 * CHILD_HEIGHT, 2 * CHILD_HEIGHT),
                containerPreferredSize(),
                "a column asking for its own width must ask the ratio for its width at the height the child " +
                    "takes, as androidx's AspectRatioNode answers it",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, 2 * CHILD_HEIGHT, CHILD_HEIGHT),
                ),
                childBounds(),
                "and the column laid out at that width must size the child from its height too",
            )
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
}
