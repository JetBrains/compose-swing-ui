package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A layout modifier written as a lambda measures and places the child it is declared on. */
class LayoutModifierLambdaTest {
    @Test
    fun theLambdaReportsTheExtentAndPlacesTheChildInIt() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(
                        index = 0,
                        modifier =
                            SwingModifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width + 10, placeable.height) { placeable.place(10, 0) }
                            },
                    )
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH + 10, CHILD_HEIGHT),
                containerPreferredSize(),
                "the row must ask for the extent the lambda reports, not the extent the child asks for",
            )
            assertEquals(
                listOf(Rectangle(10, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "the child must be placed where the lambda placed it, inside the extent the lambda reported",
            )
        }

    /** A child the lambda measures but does not place is hidden, as one its container leaves unplaced is. */
    @Test
    fun aChildTheLambdaDoesNotPlaceIsHidden() =
        runComposeSwingTest {
            setContent {
                Row {
                    SizedChild(
                        index = 0,
                        modifier =
                            SwingModifier.testTag("child").layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {}
                            },
                    )
                }
            }

            assertEquals(
                Dimension(0, 0),
                onNodeWithTag("child").fetch<Component>().size,
                "the unplaced child must be left at a zero size",
            )
            assertFalse(onNodeWithTag("child").fetch<Component>().isVisible, "the unplaced child must be hidden")
        }

    /** A lambda declared on a child its container measures but does not place measures, and places nothing. */
    @Test
    fun theLambdaOfAChildItsContainerDoesNotPlaceRunsNoPlacementBlock() =
        runComposeSwingTest {
            var measures = 0
            var placements = 0
            setContent {
                Layout(
                    content = {
                        SizedChild(
                            index = 0,
                            modifier =
                                SwingModifier.layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    measures++
                                    layout(placeable.width, placeable.height) {
                                        placements++
                                        placeable.place(0, 0)
                                    }
                                },
                        )
                    },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) {}
                    },
                )
            }

            assertTrue(measures > 0, "the container must measure the child through the lambda")
            assertEquals(0, placements, "the lambda must not run its placement block for a child left unplaced")
        }
}
