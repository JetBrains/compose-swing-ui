package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A default minimum size raises the least a child must occupy along an axis whose incoming minimum is
 * zero, and leaves an axis that already claims one as it is. The minimum it raises is held between
 * nothing and the incoming maximum, so a child in a container smaller than the minimum takes the
 * container rather than growing past it.
 *
 * Ported from the `defaultMinSize` cases of androidx `foundation-layout`'s own `SizeTest`, whose case
 * names are kept so the two files read side by side. androidx reads the constraints a child is measured
 * under from inside a measure policy; here the raised minimum is read back off the extent the child was
 * laid out at, which is the same statement made about what the child ends up occupying.
 *
 * [testDefaultMinSizeInspectableValue] stands in for the `defaultMinSize` line of androidx's
 * `testInspectableParameter`, under the name the neighboring suites give that case.
 */
class DefaultMinSizeTest {
    @Test
    fun testDefaultMinSize() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CONTAINER_EXTENT, CONTAINER_EXTENT)) {
                    Child(0, ASKED_EXTENT, ASKED_EXTENT, SwingModifier.defaultMinSize(MIN_WIDTH, MIN_HEIGHT))
                    Child(
                        index = 1,
                        width = ASKED_EXTENT,
                        height = ASKED_EXTENT,
                        modifier = SwingModifier.fillMaxWidth().defaultMinSize(MIN_WIDTH, MIN_HEIGHT),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, MIN_WIDTH, MIN_HEIGHT),
                    Rectangle(0, 0, CONTAINER_EXTENT, MIN_HEIGHT),
                ),
                stackedChildBounds(),
                "a default minimum size must raise the child to it along each axis whose minimum the box " +
                    "leaves at zero, and leave the width a fill already claims at the box's own",
            )
        }

    @Test
    fun testDefaultMinSize_withCoercingMaxConstraints() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(TIGHT_WIDTH, TIGHT_HEIGHT)) {
                    Child(0, ASKED_EXTENT, ASKED_EXTENT, SwingModifier.defaultMinSize(MIN_WIDTH, MIN_HEIGHT))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, TIGHT_WIDTH, TIGHT_HEIGHT)),
                stackedChildBounds(),
                "a default minimum size larger than the box must raise the child to the box's own extent " +
                    "rather than past it",
            )
        }

    @Test
    fun testDefaultMinSizeModifier_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(
                        index = 0,
                        width = ASKED_EXTENT,
                        height = ASKED_EXTENT,
                        modifier =
                            SwingModifier
                                .minimumSize(ASKED_EXTENT, ASKED_EXTENT)
                                .defaultMinSize(MIN_WIDTH, MIN_HEIGHT),
                    )
                }
            }

            assertEquals(
                Dimension(MIN_WIDTH, MIN_HEIGHT),
                containerPreferredSize(),
                "a box must ask for the minimum its child was raised to, not the smaller extent that child " +
                    "prefers",
            )
            assertEquals(
                Dimension(MIN_WIDTH, MIN_HEIGHT),
                containerMinimumSize(),
                "and must report the same as the least it can shrink to, since the raised minimum is what the " +
                    "child occupies there too",
            )
        }

    @Test
    fun testDefaultMinSizeInspectableValue() {
        val declared =
            with(BoxScopeInstance) {
                SwingModifier.defaultMinSize(minWidth = MIN_WIDTH, minHeight = MIN_HEIGHT)
            }

        assertEquals(
            "defaultMinSize",
            declared.lastElement().name,
            "defaultMinSize must report itself under its own name",
        )
        assertEquals(
            mapOf("minWidth" to MIN_WIDTH, "minHeight" to MIN_HEIGHT),
            declared.lastElement().declaredValues,
            "and must report the minimum it was declared with",
        )
    }

    private companion object {
        /** The extent every child here asks for, below every minimum these cases raise it to. */
        const val ASKED_EXTENT = 10

        /** The minimum a child is raised to, different on each axis so the two cannot be mistaken. */
        const val MIN_WIDTH = 60
        const val MIN_HEIGHT = 80

        /** A box with room to spare for every minimum raised inside it. */
        const val CONTAINER_EXTENT = 200

        /** A box smaller than the minimum declared in it, which is what that minimum is held to. */
        const val TIGHT_WIDTH = 30
        const val TIGHT_HEIGHT = 40
    }
}
