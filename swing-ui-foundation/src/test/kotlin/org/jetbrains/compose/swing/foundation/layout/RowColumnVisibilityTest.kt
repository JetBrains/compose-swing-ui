package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.visible(false))
                    SizedChild(2)
                }
            }

            assertEquals(CHILD_COUNT, childCount(), "a hidden child stays attached to the column that declares it")
            assertEquals(
                columnRows(0, CHILD_HEIGHT + GAP, 2 * (CHILD_HEIGHT + GAP)),
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
                    verticalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.visible(false))
                    SizedChild(2)
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_COUNT * CHILD_HEIGHT + 2 * GAP),
                containerPreferredSize(),
                "a hidden child costs its own extent and its gap, so the column asks for both",
            )
        }

    @Test
    fun anInvisibleChildKeepsItsSpaceAndItsGapInARowToo() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = containerModifier(MAIN_EXTENT, CROSS_EXTENT),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.visible(false))
                    SizedChild(2)
                }
            }

            assertEquals(
                rowCells(0, CHILD_WIDTH + GAP, 2 * (CHILD_WIDTH + GAP)),
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
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.visible(shown))
                    SizedChild(2)
                }
            }
            val rows = columnRows(0, CHILD_HEIGHT + GAP, 2 * (CHILD_HEIGHT + GAP))

            assertFalse(toggledChild().isVisible, "the child under test starts hidden")
            assertEquals(rows, childBounds(), "and the column holds the slot it hides")

            shown = true
            awaitIdle()

            assertTrue(toggledChild().isVisible, "the declaration reaches the child when it is shown")
            assertEquals(rows, childBounds(), "which is already standing where it was placed")

            shown = false
            awaitIdle()

            assertFalse(toggledChild().isVisible, "and when it is hidden again")
            assertEquals(rows, childBounds(), "moving nothing either way")
        }

    private companion object {
        /** The gap the fixture arrangement holds between two adjacent children. */
        const val GAP = 10

        /** The extent the fixture container is given along the axis it arranges its children on. */
        const val MAIN_EXTENT = 300

        /** The extent the fixture container is given across that axis, wider than any child asks for. */
        const val CROSS_EXTENT = 100

        /** How many children the container holds, hidden ones included. */
        fun ComposeSwingTest.childCount(): Int = container().componentCount

        /** The child whose visibility a test drives, the second of the three the fixture declares. */
        fun ComposeSwingTest.toggledChild(): Component = container().childrenInDeclarationOrder()[1]

        fun ComposeSwingTest.container(): JPanel = onNodeWithTag(CONTAINER_TAG).fetch<JPanel>()
    }
}
