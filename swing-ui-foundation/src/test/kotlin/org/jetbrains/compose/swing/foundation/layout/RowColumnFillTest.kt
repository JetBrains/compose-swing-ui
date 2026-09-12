package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every container here has more room than its children ask for, so the extent a filling child ends up
 * at is the whole of what the fill did, and a sibling that declares no fill shows what the same
 * container does without one.
 */
class RowColumnFillTest {
    @Test
    fun aChildCanFillTheRowsMainAxis() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT)) {
                    SizedChild(0, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CROSS_EXTENT, CHILD_HEIGHT)),
                childBounds(),
                "fillMaxWidth must consume the bounded width a row offers its child",
            )
        }

    @Test
    fun aChildCanFillTheColumnsMainAxis() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT)) {
                    SizedChild(0, SwingModifier.fillMaxHeight())
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, MAIN_EXTENT)),
                childBounds(),
                "fillMaxHeight must consume the bounded height a column offers its child",
            )
        }

    @Test
    fun aFillingChildTakesTheColumnsWholeWidth() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT)) {
                    SizedChild(0, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CROSS_EXTENT, CHILD_HEIGHT)),
                childBounds(),
                "fillMaxWidth must give the child the column's whole width in place of the width it prefers",
            )
        }

    @Test
    fun aFillingChildTakesTheRowsWholeHeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(MAIN_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0, SwingModifier.fillMaxHeight())
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CROSS_EXTENT)),
                childBounds(),
                "fillMaxHeight must give the child the row's whole height in place of the height it prefers",
            )
        }

    @Test
    fun aFillingChildTakesOnlyTheWidthTheColumnsBorderLeaves() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT).emptyBorder(BORDER)) {
                    SizedChild(0, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                listOf(Rectangle(BORDER, BORDER, CROSS_EXTENT - 2 * BORDER, CHILD_HEIGHT)),
                childBounds(),
                "a filling child must span the column's width inside its border, not through it",
            )
        }

    @Test
    fun aFillingChildIsPlacedByNeitherAlignment() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                    horizontalAlignment = Alignment.End,
                ) {
                    SizedChild(0, SwingModifier.align(Alignment.CenterHorizontally).fillMaxWidth())
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CROSS_EXTENT, CHILD_HEIGHT)),
                childBounds(),
                "a child spanning the whole width has nowhere to be placed, so both alignments leave it there",
            )
        }

    @Test
    fun aSiblingThatDoesNotFillKeepsTheWidthAndThePlacementItAsksFor() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                    horizontalAlignment = Alignment.Start,
                ) {
                    SizedChild(0, SwingModifier.fillMaxWidth())
                    SizedChild(1)
                    SizedChild(2, SwingModifier.align(Alignment.End))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CROSS_EXTENT, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CROSS_EXTENT - CHILD_WIDTH, 2 * CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "a sibling that declares no fill must keep the width it prefers and the alignment placing it",
            )
        }

    @Test
    fun aFillingChildStopsAtTheMaximumWidthItDeclares() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT)) {
                    SizedChild(0, SwingModifier.fillMaxWidth().maximumSize(MAXIMUM_CROSS, CHILD_HEIGHT))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, MAXIMUM_CROSS, CHILD_HEIGHT)),
                childBounds(),
                "a filling child must stop at the maximum it declares, as a weighted child stops at its own",
            )
            assertEquals(
                CROSS_EXTENT,
                containerSize().width,
                "the column keeps the width it was given, so the width its child refused stays empty",
            )
        }

    @Test
    fun aFillingChildStopsAtTheMaximumHeightItDeclares() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(MAIN_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0, SwingModifier.fillMaxHeight().maximumSize(CHILD_WIDTH, MAXIMUM_CROSS))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, MAXIMUM_CROSS)),
                childBounds(),
                "a filling child must stop at the maximum it declares across the row as well",
            )
        }

    @Test
    fun aChildThatDoesNotFillStopsAtTheMaximumWidthItDeclares() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT)) {
                    SizedChild(0, SwingModifier.maximumSize(NARROW_CROSS, CHILD_HEIGHT))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, NARROW_CROSS, CHILD_HEIGHT)),
                childBounds(),
                "a maximum holds back a child that declares no fill too, short of the width it prefers",
            )
        }

    @Test
    fun aChildTakesTheWholeWidthAndItsShareOfTheHeightAtOnce() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, WEIGHTED_MAIN_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f).fillMaxWidth())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CROSS_EXTENT, WEIGHTED_MAIN_EXTENT - CHILD_HEIGHT),
                ),
                childBounds(),
                "a child that declares both must take the whole width and the height the column has left over",
            )
        }

    /** The same declarations in the other order: fill and weight travel through independent channels. */
    @Test
    fun aChildDeclaringItsFillBeforeItsWeightIsPlacedTheSameWay() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, WEIGHTED_MAIN_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.fillMaxWidth().weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CROSS_EXTENT, WEIGHTED_MAIN_EXTENT - CHILD_HEIGHT),
                ),
                childBounds(),
                "the order the two are declared in must not change where the child is placed",
            )
        }

    @Test
    fun aChildFollowsWhetherItFillsTheColumnsWidth() =
        runComposeSwingTest {
            var filling by mutableStateOf(false)
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT)) {
                    SizedChild(0, if (filling) SwingModifier.fillMaxWidth() else SwingModifier)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a child declaring no fill keeps the width it prefers",
            )

            filling = true
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, CROSS_EXTENT, CHILD_HEIGHT)),
                childBounds(),
                "a fill declared on a later pass must reach the layout and widen the child",
            )

            filling = false
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "with the fill dropped the child goes back to the width it prefers",
            )
        }

    @Test
    fun aChildFollowsWhetherItFillsTheRowsHeight() =
        runComposeSwingTest {
            var filling by mutableStateOf(false)
            setContent {
                Row(modifier = containerModifier(MAIN_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0, if (filling) SwingModifier.fillMaxHeight() else SwingModifier)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a child declaring no fill keeps the height it prefers",
            )

            filling = true
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CROSS_EXTENT)),
                childBounds(),
                "a fill declared on a later pass must reach the layout and heighten the child",
            )

            filling = false
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "with the fill dropped the child goes back to the height it prefers",
            )
        }

    @Test
    fun aColumnWithAFillingChildStillAsksForTheWidthItsChildrenPrefer() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.fillMaxWidth())
                    SizedChild(1)
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, 2 * CHILD_HEIGHT),
                containerPreferredSize(),
                "a fill claims the width the column is given, so the column asks its parent for no more",
            )
        }

    private companion object {
        // Far wider, or taller, than a child asks for, so the extent a fill takes is unmistakable.
        const val CROSS_EXTENT = 200

        // Room along the axis for the three children a fixture declares, and no surplus to place.
        const val MAIN_EXTENT = 120

        // 40px of the column goes to the child that claims no share; the rest is the weighted child's.
        const val WEIGHTED_MAIN_EXTENT = 300

        // Less than the container's cross extent, so the maximum is what decides the child's extent.
        const val MAXIMUM_CROSS = 70

        // Narrower than a fixture child asks for, so a maximum decides the extent of a child that has
        // no fill to be held back from.
        const val NARROW_CROSS = 30

        // Wide enough on every side that a child placed through the border would be visibly off.
        const val BORDER = 10
    }

    @Test
    fun everyRowAndColumnParentDataBuilderAppendsToTheChainWithoutRepeatingIt() {
        with(RowScopeInstance) {
            assertDeclaredChainCarriedOnce { weight(1f) }
            assertDeclaredChainCarriedOnce { align(Alignment.CenterVertically) }
        }
        with(ColumnScopeInstance) {
            assertDeclaredChainCarriedOnce { weight(1f) }
            assertDeclaredChainCarriedOnce { align(Alignment.CenterHorizontally) }
        }
    }
}
