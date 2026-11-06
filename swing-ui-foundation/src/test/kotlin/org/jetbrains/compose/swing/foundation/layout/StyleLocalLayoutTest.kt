package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import kotlin.test.Test
import kotlin.test.assertEquals

/** How foundation layouts arrange by the style locals a container above them sets. */
class StyleLocalLayoutTest {
    /** The row's own style names no orientation: it takes the one its container's style sets. */
    @Test
    fun aRowArrangesByTheOrientationAContainerStyleSets() =
        runComposeSwingTest {
            var orientation by mutableStateOf(ComponentOrientation.LEFT_TO_RIGHT)
            setContent {
                Box(modifier = SwingModifier.styleable { componentOrientation(orientation) }) {
                    Row(
                        modifier = SwingModifier.testTag(CONTAINER_TAG).styleable { preferredSize(300, CHILD_HEIGHT) },
                    ) {
                        repeat(CHILD_COUNT) { SizedChild(it) }
                    }
                }
            }
            assertEquals(rowCells(0, 50, 100), childBounds())

            orientation = ComponentOrientation.RIGHT_TO_LEFT
            awaitIdle()

            assertEquals(
                rowCells(250, 200, 150),
                childBounds(),
                "Arrangement.Start must pack the children against the right edge the inherited orientation leads from",
            )
        }
}
