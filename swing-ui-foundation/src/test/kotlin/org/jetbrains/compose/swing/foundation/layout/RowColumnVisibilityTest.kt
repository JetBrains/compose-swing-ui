package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.visible
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A child hidden with `SwingModifier.visible(false)` stays attached to its row or column and the layout
 * pass treats it as any other child: it is measured, it keeps its space along the axis and its gap, and
 * it is placed in the slot it reserved. Hiding a child therefore moves nothing around it, and the
 * container asks its own parent for the same extent either way.
 *
 * `isVisible` is what stops the child painting, taking focus and reaching accessibility. It is not a
 * layout filter, so it is the one thing about the child a pass does not consult.
 */
class RowColumnVisibilityTest {
    @Test
    fun anInvisibleChildKeepsItsSpaceAndItsGap() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = ContainerModifier.styleable(containerStyle(100, 300)),
                    verticalArrangement = Arrangement.spacedBy(10),
                ) {
                    SizedChild(0)
                    SizedChild(1, style = { visible(false) })
                    SizedChild(2)
                }
            }

            onNodeWithTag(CONTAINER_TAG).onChildren().assertCountEquals(CHILD_COUNT)
            assertEquals(
                columnRows(0, 50, 100),
                childBounds(),
                "a hidden child is placed in the slot it reserved, and the children after it sit past it",
            )
        }

    @Test
    fun aColumnAsksForTheSpaceAndGapOfAnInvisibleChild() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    verticalArrangement = Arrangement.spacedBy(10),
                ) {
                    SizedChild(0)
                    SizedChild(1, style = { visible(false) })
                    SizedChild(2)
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(
                Dimension(CHILD_WIDTH, 140),
                "a hidden child costs its own extent and its gap, so the column asks for both",
            ) { preferredSize }
        }

    @Test
    fun anInvisibleChildKeepsItsSpaceAndItsGapInARowToo() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = ContainerModifier.styleable(containerStyle(300, 100)),
                    horizontalArrangement = Arrangement.spacedBy(10),
                ) {
                    SizedChild(0)
                    SizedChild(1, style = { visible(false) })
                    SizedChild(2)
                }
            }

            assertEquals(
                rowCells(0, 60, 120),
                childBounds(),
                "a hidden child holds its place along either axis",
            )
        }

    @Test
    fun hidingAndRevealingAChildMovesNothingAroundIt() =
        runComposeSwingTest {
            var shown by mutableStateOf(false)
            setContent {
                Column(
                    modifier = ContainerModifier.styleable(containerStyle(100, 300)),
                    verticalArrangement = Arrangement.spacedBy(10),
                ) {
                    SizedChild(0)
                    SizedChild(1, style = { visible(shown) })
                    SizedChild(2)
                }
            }
            val rows = columnRows(0, 50, 100)

            onNodeWithText("child 1").assertProperty(false, "the child under test starts hidden") { isVisible }
            assertEquals(rows, childBounds(), "and the column holds the slot it hides")

            shown = true
            awaitIdle()

            onNodeWithText("child 1").assertProperty(
                true,
                "the declaration reaches the child when it is shown",
            ) { isVisible }
            assertEquals(rows, childBounds(), "which is already standing where it was placed")

            shown = false
            awaitIdle()

            onNodeWithText("child 1").assertProperty(false, "and when it is hidden again") { isVisible }
            assertEquals(rows, childBounds(), "moving nothing either way")
        }
}
