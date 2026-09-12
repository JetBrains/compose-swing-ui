package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An offset moves a child from where its container would otherwise place it, and changes nothing else:
 * the child is measured into the same room, and the container asks its own parent for the same extent
 * as it would without one. `offset` moves along the reading order, so its horizontal move mirrors under
 * a right-to-left orientation, while `absoluteOffset` moves toward the right under either.
 *
 * Ported from androidx `foundation-layout`'s own `OffsetTest`, whose case names are kept so the two
 * files read side by side. Two cases are named for what they pin instead:
 * [anOffsetLeavesTheChildTheWholeRoomItWouldOtherwiseBeMeasuredIn], which is what separates an offset
 * from a padding, and [anOffsetFollowsTheValueItIsDeclaredWith], which stands in for the observable
 * half of androidx's `updateOffsetDp_doesNotRemeasure` - the counts that test makes of measure, place
 * and draw passes are internals of the Compose node it asserts on, and this tree has no counterpart.
 */
class OffsetTest {
    @Test
    fun offset_positionIsModified() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.offset(OFFSET_X, OFFSET_Y))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "an offset child must leave its container asking for the child's own extent, since an offset " +
                    "reserves no room the way a padding does",
            )
            assertEquals(
                listOf(Rectangle(OFFSET_X, OFFSET_Y, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "an offset child must sit that far from where the box would otherwise place it",
            )
        }

    @Test
    fun offset_positionIsModified_rtl() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT, ComponentOrientation.RIGHT_TO_LEFT),
                ) {
                    SizedChild(0, SwingModifier.offset(OFFSET_X, OFFSET_Y))
                }
            }

            assertEquals(
                listOf(Rectangle(CONTAINER_WIDTH - CHILD_WIDTH - OFFSET_X, OFFSET_Y, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "under a right-to-left orientation an offset must move the child toward the trailing edge, away " +
                    "from the right the box started it at",
            )
        }

    @Test
    fun offsetWithAnIntMinimumHorizontalMoveSaturatesWhenMirroredRightToLeft() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT, ComponentOrientation.RIGHT_TO_LEFT),
                ) {
                    SizedChild(0, SwingModifier.offset(Int.MIN_VALUE, 0))
                }
            }

            assertEquals(
                listOf(Rectangle(Int.MAX_VALUE, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "mirroring Int.MIN_VALUE must reach the greatest rightward coordinate instead of overflowing negative",
            )
        }

    @Test
    fun chainedOffsetsSaturateTheirAccumulatedDisplacement() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(
                        0,
                        SwingModifier.absoluteOffset(Int.MAX_VALUE, Int.MAX_VALUE).absoluteOffset(1, 1),
                    )
                }
            }

            assertEquals(
                listOf(Rectangle(Int.MAX_VALUE, Int.MAX_VALUE, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "two offsets beyond the greatest coordinate must stay there instead of wrapping to the opposite edge",
            )
        }

    @Test
    fun chainedNegativeOffsetsSaturateTheirAccumulatedDisplacement() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(
                        0,
                        SwingModifier.absoluteOffset(Int.MIN_VALUE, Int.MIN_VALUE).absoluteOffset(-1, -1),
                    )
                }
            }

            assertEquals(
                listOf(Rectangle(Int.MIN_VALUE, Int.MIN_VALUE, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "two offsets beyond the least coordinate must stay there instead of wrapping to the opposite edge",
            )
        }

    @Test
    fun chainedOffsetsPreserveCancellationBeforeTheirFinalSaturation() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(
                        0,
                        SwingModifier
                            .absoluteOffset(Int.MIN_VALUE, Int.MIN_VALUE)
                            .absoluteOffset(1, 1)
                            .absoluteOffset(Int.MAX_VALUE, Int.MAX_VALUE),
                    )
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "offsets whose full-precision sum is zero must not inherit an intermediate saturation",
            )
        }

    @Test
    fun absoluteOffset_positionModified() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.absoluteOffset(OFFSET_X, OFFSET_Y))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "an absolutely offset child must leave its container asking for the child's own extent too",
            )
            assertEquals(
                listOf(Rectangle(OFFSET_X, OFFSET_Y, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "an absolute offset must move the child the same way an offset does under a left-to-right " +
                    "orientation",
            )
        }

    @Test
    fun absoluteOffset_positionModified_rtl() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT, ComponentOrientation.RIGHT_TO_LEFT),
                ) {
                    SizedChild(0, SwingModifier.absoluteOffset(OFFSET_X, OFFSET_Y))
                }
            }

            assertEquals(
                listOf(Rectangle(CONTAINER_WIDTH - CHILD_WIDTH + OFFSET_X, OFFSET_Y, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "an absolute offset must keep moving the child toward the right under a right-to-left " +
                    "orientation, unlike an offset which would mirror",
            )
        }

    @Test
    fun anOffsetLeavesTheChildTheWholeRoomItWouldOtherwiseBeMeasuredIn() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT)) {
                    SizedChild(0, SwingModifier.offset(OFFSET_X, OFFSET_Y).fillMaxWidth().fillMaxHeight())
                }
            }

            assertEquals(
                listOf(Rectangle(OFFSET_X, OFFSET_Y, CONTAINER_WIDTH, CONTAINER_HEIGHT)),
                childBounds(),
                "a child filling the box must still be measured into the whole of it with an offset declared, " +
                    "and only be moved - a padding would have taken the room out of what the child is measured in",
            )
        }

    @Test
    fun anOffsetFollowsTheValueItIsDeclaredWith() =
        runComposeSwingTest {
            var offsetX by mutableStateOf(OFFSET_X)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.offset(offsetX, OFFSET_Y))
                }
            }

            assertEquals(
                listOf(Rectangle(OFFSET_X, OFFSET_Y, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "an offset declared from state must first move the child by the value that state holds",
            )

            offsetX = LATER_OFFSET_X
            awaitIdle()

            assertEquals(
                listOf(Rectangle(LATER_OFFSET_X, OFFSET_Y, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "an offset declared from state must move the child to every later value it takes",
            )
            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "and must leave the container asking for the same extent as it did before the change",
            )
        }

    @Test
    fun testOffsetInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.offset(INSPECTED_X, INSPECTED_Y) }

        assertEquals("offset", declared.lastElement().name, "offset must report itself under its own name")
        assertEquals(
            mapOf("x" to INSPECTED_X, "y" to INSPECTED_Y),
            declared.lastElement().declaredValues,
            "and must report the move it was declared with",
        )
    }

    @Test
    fun testAbsoluteOffsetInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.absoluteOffset(INSPECTED_X, INSPECTED_Y) }

        assertEquals(
            "absoluteOffset",
            declared.lastElement().name,
            "absoluteOffset must report itself under its own name",
        )
        assertEquals(
            mapOf("x" to INSPECTED_X, "y" to INSPECTED_Y),
            declared.lastElement().declaredValues,
            "and must report the move it was declared with",
        )
    }

    private companion object {
        /** How far an offset under test moves its child, different on each axis so the two cannot be mistaken. */
        const val OFFSET_X = 10
        const val OFFSET_Y = 20

        /** The horizontal move an offset declared from state takes next, so the child has to move again. */
        const val LATER_OFFSET_X = 35

        /**
         * The extent a box is given where the case needs room for the box to start the child at its
         * trailing edge before the offset moves it.
         */
        const val CONTAINER_WIDTH = 200
        const val CONTAINER_HEIGHT = 150

        /** The move an offset is declared with where only what it reports about itself is read. */
        const val INSPECTED_X = 3
        const val INSPECTED_Y = 4
    }
}
