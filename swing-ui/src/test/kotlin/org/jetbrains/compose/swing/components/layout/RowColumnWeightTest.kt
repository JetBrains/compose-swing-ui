package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A weight is how a child asks for the space its container has left over once every child that asked
 * for none has taken the extent it prefers. Weighted children share that space in proportion to their
 * weights; a child that declares a maximum of its own takes no more than that maximum allows, and a
 * child that does not fill takes only as much of its share as it prefers.
 *
 * Each test reads back the extent and position the container assigned, which is the whole of what a
 * caller can observe of a weight.
 */
class RowColumnWeightTest {
    @Test
    fun aWeightedChildTakesTheHeightTheOtherChildrenLeave() = runComposeSwingTest {
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT)) {
                SizedChild(0)
                SizedChild(1, SwingModifier.weight(1f))
            }
        }

        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, COLUMN_EXTENT - CHILD_HEIGHT),
            ),
            childBounds(),
            "a single weighted child must take the whole height the column has left over",
        )
    }

    @Test
    fun twoWeightedChildrenSplitTheLeftoverHeightInProportion() = runComposeSwingTest {
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, SPLIT_COLUMN_EXTENT)) {
                SizedChild(0)
                SizedChild(1, SwingModifier.weight(1f))
                SizedChild(2, SwingModifier.weight(2f))
            }
        }

        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(0, 40, CHILD_WIDTH, 100),
                Rectangle(0, 140, CHILD_WIDTH, 200),
            ),
            childBounds(),
            "children weighted 1f and 2f must take a third and two thirds of the 300px left over",
        )
    }

    @Test
    fun aWeightedChildThatDoesNotFillKeepsTheHeightItPrefers() = runComposeSwingTest {
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT)) {
                SizedChild(0)
                SizedChild(1, SwingModifier.weight(1f, fill = false))
            }
        }

        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
            ),
            childBounds(),
            "a child that does not fill must take only what it prefers of the height it was granted",
        )
    }

    @Test
    fun aWeightedChildNeverGrowsPastTheMaximumItDeclares() = runComposeSwingTest {
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, CAPPED_COLUMN_EXTENT)) {
                SizedChild(0, SwingModifier.weight(1f).maximumSize(CHILD_WIDTH, MAXIMUM_EXTENT))
            }
        }

        assertEquals(
            listOf(Rectangle(0, 0, CHILD_WIDTH, MAXIMUM_EXTENT)),
            childBounds(),
            "a weighted child must stop at the maximum it declares, leaving the rest of its share empty",
        )
        assertEquals(
            CAPPED_COLUMN_EXTENT,
            containerSize().height,
            "the column keeps the height it was given, so the height its child refused stays empty",
        )
    }

    @Test
    fun twoWeightedChildrenSplitTheLeftoverWidthInProportion() = runComposeSwingTest {
        setContent {
            Row(modifier = containerModifier(SPLIT_ROW_EXTENT, CROSS_EXTENT)) {
                SizedChild(0)
                SizedChild(1, SwingModifier.weight(1f))
                SizedChild(2, SwingModifier.weight(2f))
            }
        }

        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(50, 0, 100, CHILD_HEIGHT),
                Rectangle(150, 0, 200, CHILD_HEIGHT),
            ),
            childBounds(),
            "children weighted 1f and 2f must take a third and two thirds of the 300px left over",
        )
    }

    @Test
    fun twoChildrenAskingForEverythingSplitTheSurplusBetweenThem() = runComposeSwingTest {
        setContent {
            Row(modifier = containerModifier(SPLIT_ROW_EXTENT, CROSS_EXTENT)) {
                SizedChild(0)
                SizedChild(1, SwingModifier.weight(Float.POSITIVE_INFINITY))
                SizedChild(2, SwingModifier.weight(Float.POSITIVE_INFINITY))
            }
        }

        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(50, 0, 150, CHILD_HEIGHT),
                Rectangle(200, 0, 150, CHILD_HEIGHT),
            ),
            childBounds(),
            "two children each asking for everything must split the 300px left over between them, not " +
                "collapse to nothing",
        )
    }

    @Test
    fun aWeightThatWouldGrantNoSpaceIsRejected() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    Column { SizedChild(0, SwingModifier.weight(0f)) }
                }
                awaitIdle()
            }

        assertTrue(
            "greater than zero" in error.message.orEmpty(),
            "the error must say what a weight has to be, but was: ${error.message}",
        )
    }

    private companion object {
        // Wider than a child asks for, so the cross axis never interferes with what a weight does.
        const val CROSS_EXTENT = 100

        // 40px of the column goes to the child that claims no share; the rest is what a weight takes.
        const val COLUMN_EXTENT = 300
        const val SPLIT_COLUMN_EXTENT = 340
        const val SPLIT_ROW_EXTENT = 350

        // More height than the capped child could ever accept, so the cap is what decides its extent.
        const val CAPPED_COLUMN_EXTENT = 400
        const val MAXIMUM_EXTENT = 70
    }
}
