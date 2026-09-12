package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a [Box] does at a declaration no ordinary one reaches: an extent that cannot hold anything, or a
 * box its own parent measures rather than one laid out against a rectangle. [BoxTest] holds every case
 * an ordinary declaration reaches, this one each case named for what it pins.
 */
class BoxEdgeCaseTest {
    @Test
    fun aChildWhoseMaximumSizeIsNegativeIsHeldToNothingRatherThanTakingTheBoxsPassDown() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.maximumSize(NEGATIVE_MAXIMUM, NEGATIVE_MAXIMUM))
                    Child(
                        index = 1,
                        width = CHILD_WIDTH,
                        height = CHILD_HEIGHT,
                        modifier = SwingModifier.matchParentSize().maximumSize(NEGATIVE_MAXIMUM, NEGATIVE_MAXIMUM),
                    )
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 0, 0), Rectangle(0, 0, 0, 0)),
                stackedChildBounds(),
                "a maximum size below zero holds a child to nothing, whether or not it matches the box, and " +
                    "the box must complete its pass rather than measure either under an inverted range",
            )
        }

    @Test
    fun aNestedBoxCentersItsChildInTheExtentItSettledOnRatherThanTheOneItWasOffered() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.preferredSize(SPACIOUS_EXTENT, SPACIOUS_EXTENT)) {
                    Box(modifier = SwingModifier.testTag(CONTAINER_TAG), contentAlignment = Alignment.Center) {
                        Child(0, CHILD_WIDTH, CHILD_HEIGHT)
                    }
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "a box its column measured settles at the extent of the child it holds, so centering that " +
                    "child in it leaves the child at the origin rather than adrift in the column's extent",
            )
        }

    private companion object {
        /** Far wider and taller than the box's one child, so aligning in either extent is unmistakable. */
        const val SPACIOUS_EXTENT = 400

        /** A maximum a component may carry as readily as any other, and which no extent can fit inside. */
        const val NEGATIVE_MAXIMUM = -1
    }
}
