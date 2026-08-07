package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row or column offered more space along its axis than its children ask for keeps every child at
 * the extent that child prefers and leaves the rest of the space empty, for its arrangement to place.
 *
 * The children are panels, each laid out by its own `LayoutManager2`, and none imposes a maximum size
 * on that axis. A layout that pushed its surplus into its children would find no ceiling on any of
 * them and stretch them all.
 *
 * The size is imposed from outside, as a `BorderLayout` center or a split-pane side imposes one, so
 * the container is genuinely given more room than it asked for.
 */
class RowColumnSurplusTest {
    @Test
    fun aTallColumnLeavesTheHeightItsChildrenDidNotAskForEmpty() = runComposeSwingTest {
        setContent {
            BorderPanel(modifier = SwingModifier.preferredSize(IMPOSED_CROSS, IMPOSED_MAIN)) {
                Column(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                    FlowPanel(modifier = SwingModifier.preferredSize(CROSS_EXTENT, FIRST_EXTENT))
                    GridBagPanel(modifier = SwingModifier.preferredSize(CROSS_EXTENT, SECOND_EXTENT)) {}
                    BorderPanel(modifier = SwingModifier.preferredSize(CROSS_EXTENT, THIRD_EXTENT)) {}
                }
            }
        }

        assertEquals(
            Dimension(IMPOSED_CROSS, IMPOSED_MAIN),
            containerSize(),
            "the center region must impose its own height on the column, giving it height to spare",
        )
        assertEquals(
            listOf(
                Rectangle(0, 0, CROSS_EXTENT, FIRST_EXTENT),
                Rectangle(0, FIRST_EXTENT, CROSS_EXTENT, SECOND_EXTENT),
                Rectangle(0, FIRST_EXTENT + SECOND_EXTENT, CROSS_EXTENT, THIRD_EXTENT),
            ),
            childBounds(),
            "every child must keep the height it prefers, leaving the height to spare empty below them",
        )
    }

    @Test
    fun aWideRowLeavesTheWidthItsChildrenDidNotAskForEmpty() = runComposeSwingTest {
        setContent {
            BorderPanel(modifier = SwingModifier.preferredSize(IMPOSED_MAIN, IMPOSED_CROSS)) {
                Row(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                    FlowPanel(modifier = SwingModifier.preferredSize(FIRST_EXTENT, CROSS_EXTENT))
                    GridBagPanel(modifier = SwingModifier.preferredSize(SECOND_EXTENT, CROSS_EXTENT)) {}
                    BorderPanel(modifier = SwingModifier.preferredSize(THIRD_EXTENT, CROSS_EXTENT)) {}
                }
            }
        }

        assertEquals(
            Dimension(IMPOSED_MAIN, IMPOSED_CROSS),
            containerSize(),
            "the center region must impose its own width on the row, giving it width to spare",
        )
        assertEquals(
            listOf(
                Rectangle(0, 0, FIRST_EXTENT, CROSS_EXTENT),
                Rectangle(FIRST_EXTENT, 0, SECOND_EXTENT, CROSS_EXTENT),
                Rectangle(FIRST_EXTENT + SECOND_EXTENT, 0, THIRD_EXTENT, CROSS_EXTENT),
            ),
            childBounds(),
            "every child must keep the width it prefers, leaving the width to spare empty after them",
        )
    }

    private companion object {
        // The extent imposed along the axis is far more than the children between them ask for (120),
        // so what the container does with the surplus is unmistakable.
        const val IMPOSED_MAIN = 400
        const val IMPOSED_CROSS = 300

        const val CROSS_EXTENT = 120
        const val FIRST_EXTENT = 30
        const val SECOND_EXTENT = 40
        const val THIRD_EXTENT = 50
    }
}
