package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
