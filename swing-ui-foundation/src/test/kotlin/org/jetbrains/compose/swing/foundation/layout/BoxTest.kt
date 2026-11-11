/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.foundation.layout.BoxTest in AndroidX's foundation-layout; see
 * this module's META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.minimumSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A box stacks every child in the same place: it asks for the largest size among the children that do
 * not match its own, and lays each of them out at the size it prefers, where its own alignment or the
 * box's puts it. A child declaring `matchParentSize` is given the whole of the box instead, and is
 * passed over while the box's size is worked out.
 *
 * Each case here is a case of androidx `foundation-layout`'s own `BoxTest`, under that test's name and in
 * its order, so the two can be read against each other and a case dropped in translation shows up as a
 * gap. The one case this tree settles the other way round is `testBox_outermostGravityWins`: where
 * androidx gives a chain of alignments to the first declared, here the last wins. The cases of this
 * tree's own are [BoxSwingTest], the order the box stacks its children in is [BoxStackOrderTest], and a
 * case reachable only at a degenerate declaration is [BoxEdgeCaseTest].
 */
@Suppress("LargeClass")
class BoxTest {
    @Test
    fun testBox() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.align(Alignment.BottomEnd))
                    Child(
                        index = 1,
                        width = 200,
                        height = 160,
                        modifier =
                            SwingModifier
                                .matchParentSize(),
                        style = { maximumSize(30, 20) },
                    )
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "the box must ask for the child that does not match it, however large the matching one is",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, 30, 20),
                ),
                stackedChildBounds(),
                "the matching child must take the box, held to the maximum size it declares",
            )
        }

    @Test
    fun testBox_withMultipleAlignedChildren() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.align(Alignment.BottomEnd))
                    Child(1, 200, 160, SwingModifier.align(Alignment.BottomEnd))
                }
            }

            assertEquals(
                Dimension(200, 160),
                containerPreferredSize(),
                "the box must ask for the largest of its children along either axis",
            )
            assertEquals(
                listOf(
                    Rectangle(150, 120, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, 200, 160),
                ),
                stackedChildBounds(),
                "each child must sit at the alignment it declares, in the box the largest of them sized",
            )
        }

    @Test
    fun testBox_withStretchChildren() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 200, 160)
                    Child(1, 200, 160, SwingModifier.matchParentSize(), style = { maximumSize(190, 160) })
                    Child(2, 200, 160, SwingModifier.matchParentSize(), style = { maximumSize(200, 150) })
                    Child(3, 200, 160, SwingModifier.matchParentSize(), style = { maximumSize(190, 150) })
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 200, 160),
                    Rectangle(0, 0, 190, 160),
                    Rectangle(0, 0, 200, 150),
                    Rectangle(0, 0, 190, 150),
                ),
                stackedChildBounds(),
                "a matching child must take the box on each axis its own maximum size leaves free",
            )
        }

    @Test
    fun testBox_Rtl() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        SwingModifier.containerTag().styleable(
                            containerStyle(
                                STACKED * CHILD_WIDTH,
                                STACKED * CHILD_HEIGHT,
                                ComponentOrientation.RIGHT_TO_LEFT,
                            ),
                        ),
                ) {
                    ALIGNMENT_GRID.forEachIndexed { index, alignment ->
                        SizedChild(index, SwingModifier.align(alignment))
                    }
                }
            }

            assertEquals(
                listOf(
                    Rectangle(100, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(100, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(100, 80, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 80, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 80, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "a right-to-left box must mirror the horizontal alignments and leave the vertical ones alone",
            )
        }

    @Test
    fun testBox_expanded() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160))) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.matchParentSize())
                    Child(
                        index = 1,
                        width = 100,
                        height = 80,
                        modifier = SwingModifier.align(Alignment.BottomEnd),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 200, 160),
                    Rectangle(100, 80, 100, 80),
                ),
                stackedChildBounds(),
                "a matching child must take a box its own parent sized, and its siblings must be placed in it",
            )
        }

    @Test
    fun testBox_canPropagateMinConstraints() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160)),
                    propagateMinConstraints = true,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 200, 160)),
                stackedChildBounds(),
                "propagating Box constraints must measure content at the box's incoming minimum",
            )
        }

    @Test
    fun testBox_alignmentParameter() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160)),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(150, 120, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "a child declaring no alignment of its own must sit where the box's alignment puts it",
            )
        }

    @Test
    fun testBox_childAffectsBoxSize() =
        runComposeSwingTest {
            var width by mutableStateOf(CHILD_WIDTH)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) { Child(0, width, CHILD_HEIGHT) }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "the box must ask for the size its child prefers",
            )

            width = 200
            awaitIdle()

            assertEquals(
                Dimension(200, CHILD_HEIGHT),
                containerPreferredSize(),
                "a child that comes to prefer another size must carry the box's own size with it",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 200, CHILD_HEIGHT)),
                stackedChildBounds(),
                "and must be laid out at the size it now prefers",
            )
        }

    @Test
    fun testBox_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    // Each minimum is below the fixture child's own on one axis, so the largest minimum along
                    // an axis is one child's and neither axis sums to it.
                    Child(0, CHILD_WIDTH, 160, style = { minimumSize(20, CHILD_HEIGHT) })
                    Child(1, 200, CHILD_HEIGHT, style = { minimumSize(CHILD_WIDTH, 15) })
                    Child(
                        index = 2,
                        width = 400,
                        height = 320,
                        modifier = SwingModifier.matchParentSize(),
                        style = { minimumSize(400, 320) },
                    )
                }
            }

            assertEquals(
                Dimension(200, 160),
                containerPreferredSize(),
                "the box must prefer the largest child on each axis, and nothing of a matching child",
            )
            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                box().minimumSize,
                "and its minimum must be the largest minimum on each axis, the matching child passed over again",
            )
            assertEquals(
                Dimension(Int.MAX_VALUE, Int.MAX_VALUE),
                box().maximumSize,
                "and must take any extent it is offered, however large",
            )
        }

    @Test
    fun testBox_hasCorrectIntrinsicMeasurements_withNoAlignedChildren() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        SwingModifier.testTag(CONTAINER_TAG).styleable {
                            border(EmptyBorder(5, 10, 15, 20))
                        },
                ) {
                    Child(0, 200, 160, SwingModifier.matchParentSize())
                }
            }

            val insetsAlone = Dimension(30, 20)
            assertEquals(
                insetsAlone,
                containerPreferredSize(),
                "a box whose children all match it must ask for its insets alone",
            )
            assertEquals(
                insetsAlone,
                box().minimumSize,
                "and must ask for no more than that as its minimum",
            )
        }

    @Test
    fun testBox_outermostGravityWins() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.containerTag().styleable(containerStyle(200, 160))) {
                    SizedChild(0, SwingModifier.align(Alignment.BottomEnd).align(Alignment.TopStart))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "a modifier is folded in declaration order, so the last alignment declared wins on both axes",
            )
        }

    @Test
    fun testAlignInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.align(Alignment.Center) }

        assertEquals("align", declared.lastElement().name, "align must report itself under its own name")
        assertEquals(
            mapOf("alignment" to Alignment.Center),
            declared.lastElement().declaredValues,
            "and must report the alignment it was declared with",
        )
    }

    @Test
    fun testMatchParentSizeInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.matchParentSize() }

        assertEquals(
            "matchParentSize",
            declared.lastElement().name,
            "matchParentSize must report itself under its own name",
        )
        assertEquals(
            emptyMap<String, Any?>(),
            declared.lastElement().declaredValues,
            "and declares no value of its own",
        )
    }

    internal companion object {
        /** How many fixture children fit along either axis of the box the alignment grid is placed in. */
        const val STACKED = 3
    }
}

/**
 * The nine alignments a box can be declared with, read row by row: the three tops, then the three
 * halfway down, then the three bottoms. A box three fixture children wide and tall places one child per
 * cell of that grid, in this order.
 */
internal val ALIGNMENT_GRID =
    listOf(
        Alignment.TopStart,
        Alignment.TopCenter,
        Alignment.TopEnd,
        Alignment.CenterStart,
        Alignment.Center,
        Alignment.CenterEnd,
        Alignment.BottomStart,
        Alignment.BottomCenter,
        Alignment.BottomEnd,
    )
