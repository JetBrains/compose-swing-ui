// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.apply
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.visible
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The box cases this tree adds to the ones ported from androidx `BoxTest`: insets, rounding, alignment
 * changes by state, Swing visibility and constraint refusal.
 */
class BoxSwingTest {
    @Test
    fun eachAlignmentPutsAChildInItsOwnCornerOfTheBox() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        SwingModifier.containerTag().styleable(
                            containerStyle(BoxTest.STACKED * CHILD_WIDTH, BoxTest.STACKED * CHILD_HEIGHT),
                        ),
                ) {
                    SizedChild(0, SwingModifier.align(Alignment.BottomEnd))
                    SizedChild(1, SwingModifier.align(Alignment.TopEnd))
                    SizedChild(2, SwingModifier.align(Alignment.BottomStart))
                    SizedChild(3, SwingModifier.align(Alignment.TopStart))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(100, 80, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(100, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 80, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "in a left-to-right box start must be the left edge and end the right edge",
            )
        }

    @Test
    fun aBoxDoesNotPassItsMinimumExtentToContentByDefault() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160))) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the default Box policy must relax its incoming minimum before measuring content",
            )
        }

    @Test
    fun anEmptyBoxUsesTheDedicatedEmptyPolicy() =
        runComposeSwingTest {
            setContent { Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) }

            assertEquals(Dimension(0, 0), containerPreferredSize(), "an empty Box asks for no content extent")
            assertEquals(emptyList(), stackedChildBounds(), "the content-less overload must compose no children")
            assertSame(
                EmptyBoxMeasurePolicy,
                (box().layout as MeasurePolicyLayout).policy,
                "the modifier-only overload must not allocate a content Box measure policy",
            )
        }

    @Test
    fun aBoxReusesCachedPoliciesAcrossRecompositions() =
        runComposeSwingTest {
            var alignment by mutableStateOf(Alignment.TopStart)
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160)),
                    contentAlignment = alignment,
                ) {
                    SizedChild(0)
                }
            }

            val topStartPolicy = (box().layout as MeasurePolicyLayout).policy
            assertSame(
                maybeCachedBoxMeasurePolicy(Alignment.TopStart, propagateMinConstraints = false),
                topStartPolicy,
                "the default Box policy must come from the standard-alignment cache",
            )

            alignment = Alignment.BottomEnd
            awaitIdle()
            assertSame(
                maybeCachedBoxMeasurePolicy(Alignment.BottomEnd, propagateMinConstraints = false),
                (box().layout as MeasurePolicyLayout).policy,
                "a new standard alignment must replace the policy with its cached counterpart",
            )

            alignment = Alignment.TopStart
            awaitIdle()
            assertSame(
                topStartPolicy,
                (box().layout as MeasurePolicyLayout).policy,
                "returning to a prior standard alignment must reuse its original policy",
            )
        }

    @Test
    fun aMatchingChildTakesTheBoxInsideItsInsets() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        SwingModifier.containerTag().styleable {
                            apply(containerStyle(200, 160))
                            border(EmptyBorder(5, 10, 15, 20))
                        },
                ) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.matchParentSize())
                }
            }

            assertEquals(
                listOf(Rectangle(10, 5, 170, 140)),
                stackedChildBounds(),
                "a matching child must take the box inside its insets, not the box itself",
            )
        }

    @Test
    fun aChildIsAlignedInsideTheBoxsInsets() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        SwingModifier.containerTag().styleable {
                            apply(containerStyle(200, 160))
                            border(EmptyBorder(5, 10, 15, 20))
                        },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        130,
                        105,
                        CHILD_WIDTH,
                        CHILD_HEIGHT,
                    ),
                ),
                stackedChildBounds(),
                "an alignment must be resolved against the box inside its insets, not against the box itself",
            )
        }

    @Test
    fun aCenteredChildOnAnOddExtentRoundsHalfAPixelUp() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(201, 161)),
                    contentAlignment = Alignment.Center,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(76, 61, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "an offset of 75.5 by 60.5 must round up to 76 by 61, the odd pixel of leftover going before the child",
            )
        }

    @Test
    fun aMatchingChildHeldBelowTheBoxIsAlignedInWhatIsLeftOver() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160)),
                    contentAlignment = Alignment.Center,
                ) {
                    Child(
                        0,
                        200,
                        160,
                        SwingModifier.matchParentSize(),
                        style = { maximumSize(CHILD_WIDTH, CHILD_HEIGHT) },
                    )
                    Child(
                        index = 1,
                        width = 200,
                        height = 160,
                        modifier = SwingModifier.matchParentSize().align(Alignment.BottomEnd),
                        style = { maximumSize(CHILD_WIDTH, CHILD_HEIGHT) },
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        75,
                        60,
                        CHILD_WIDTH,
                        CHILD_HEIGHT,
                    ),
                    Rectangle(150, 120, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "a matching child a maximum size holds back must sit where its own or the box's alignment puts it",
            )
        }

    @Test
    fun aMatchingChildDoesNotDragTheBoxToTheExtentItsParentOffers() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.styleable { preferredSize(200, 160) }) {
                    Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        Child(0, CHILD_WIDTH, CHILD_HEIGHT)
                        Child(1, 200, 160, SwingModifier.matchParentSize())
                    }
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerSize(),
                "a box offered more space than it needs is sized by the children that do not match it, so a " +
                    "matching child measured against what its parent offered would take the box with it",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "and the matching child takes the extent its siblings settled",
            )
        }

    @Test
    fun aChildsOwnAlignmentPlacesItOnBothAxes() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160)),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SizedChild(0, SwingModifier.align(Alignment.TopCenter))
                }
            }

            assertEquals(
                listOf(Rectangle(75, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "an alignment a child declares must place it on both axes, leaving neither to the box",
            )
        }

    @Test
    fun aBoxFollowsTheAlignmentItIsDeclaredWith() =
        runComposeSwingTest {
            var alignment by mutableStateOf(Alignment.TopStart)
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160)),
                    contentAlignment = alignment,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment the box is declared with",
            )

            alignment = Alignment.BottomEnd
            awaitIdle()

            assertEquals(
                listOf(Rectangle(150, 120, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment declared on the next pass",
            )
        }

    @Test
    fun aChildFollowsTheAlignmentItDeclaresForItself() =
        runComposeSwingTest {
            var alignment by mutableStateOf<Alignment?>(Alignment.TopEnd)
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160)),
                    contentAlignment = Alignment.TopStart,
                ) {
                    SizedChild(0, alignment?.let { SwingModifier.align(it) } ?: SwingModifier)
                }
            }

            assertEquals(
                listOf(Rectangle(150, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment the child names for itself",
            )

            alignment = Alignment.TopCenter
            awaitIdle()
            assertEquals(
                listOf(Rectangle(75, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment the child names on the next pass",
            )

            alignment = null
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "with its own alignment dropped the child takes the box's",
            )
        }

    @Test
    fun aChildThatStopsMatchingTheBoxGoesBackToTheSizeItPrefers() =
        runComposeSwingTest {
            var matching by mutableStateOf(true)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT)
                    Child(1, 200, 160, if (matching) SwingModifier.matchParentSize() else SwingModifier)
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerSize(),
                "the matching child asks the box for nothing, so the box is sized by its sibling alone",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "and takes the whole of that box in place of the size it prefers",
            )

            matching = false
            awaitIdle()

            assertEquals(
                Dimension(200, 160),
                containerSize(),
                "with the match dropped the child is measured again and the box grows to what it prefers",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, 200, 160),
                ),
                stackedChildBounds(),
                "and the child is laid out at that size rather than at the box's",
            )
        }

    @Test
    fun anInvisibleChildIsMeasuredAndPlacedLikeAnyOther() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT)
                    Child(1, 200, 160, style = { visible(false) })
                }
            }

            assertEquals(
                Dimension(200, 160),
                containerPreferredSize(),
                "the box asks for what the child it hides prefers, the largest of the two",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, 200, 160),
                ),
                stackedChildBounds(),
                "and places the hidden one at the extent it asked for, alongside the child it shows",
            )
        }

    @Test
    fun aBoxDoesNotClaimOptimizedDrawing() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) { SizedChild(0) }
            }

            assertFalse(
                box().isOptimizedDrawingEnabled,
                "children of a box overlap, so painting one must repaint what it overlaps",
            )
        }

    @Test
    fun aChildFillingOneAxisKeepsTheExtentItPrefersOnTheOther() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 200, 160)
                    Child(1, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 200, 160),
                    Rectangle(0, 0, 200, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "the filling child should take the box's width and the height it prefers",
            )
        }

    /**
     * The difference between a fill and `matchParentSize`: a filling child is still measured, so the box
     * asks for the extent that child prefers along the axis it does not fill.
     */

    @Test
    fun aFillingChildStillSetsTheBoxsExtentOnTheAxisItDoesNotFill() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                box().preferredSize,
                "a box holding one filling child should ask for the extent that child prefers",
            )
        }

    @Test
    fun aChainDeclaringToTheScopesOfTwoContainersIsRefused() {
        val weightFirst =
            with(BoxScopeInstance) { with(RowScopeInstance) { SwingModifier.weight(1f) }.matchParentSize() }
        val alignFirst =
            with(RowScopeInstance) { with(BoxScopeInstance) { SwingModifier.align(Alignment.CenterEnd) }.weight(1f) }

        for (declared in listOf(weightFirst, alignFirst)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    runComposeSwingTest {
                        setContent { Box { Child(0, CHILD_WIDTH, CHILD_HEIGHT, declared) } }
                    }
                }

            assertTrue(
                "incompatible layout families" in failure.message.orEmpty(),
                "the refusal should identify the incompatible parent-data families: ${failure.message}",
            )
        }
    }

    @Test
    fun aBoxsLayoutManagerRefusesAConstraintOfAnotherKind() {
        val box =
            ConstrainedPanel(
                MeasurePolicyLayout(
                    BoxMeasurePolicy(Alignment.TopStart, propagateMinConstraints = false),
                    BoxParentDataProtocol,
                ),
            )

        val failure = assertFailsWith<IllegalArgumentException> { box.add("North", JLabel("dropped")) }

        assertTrue(
            "can carry no layout constraint" in failure.message.orEmpty(),
            "the manager should refuse a constraint it reads nothing of: ${failure.message}",
        )
    }

    @Test
    fun testZIndexInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.zIndex(2f) }

        assertEquals("zIndex", declared.lastElement().name, "zIndex must report itself under its own name")
        assertEquals(
            mapOf("zIndex" to 2f),
            declared.lastElement().declaredValues,
            "and must report the value it was declared with",
        )
    }
}
