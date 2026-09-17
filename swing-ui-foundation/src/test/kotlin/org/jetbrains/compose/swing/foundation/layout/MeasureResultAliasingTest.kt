package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A measured result belongs to the pass that produced it. A parent asking a container for its intrinsic extent
 * between measuring and placing it must not rewrite what that measure placed.
 */
class MeasureResultAliasingTest {
    @Test
    fun anIntrinsicQuestionDoesNotRewriteARowsMeasuredResult() =
        runComposeSwingTest {
            setAskedBetweenMeasureAndPlace {
                Row(horizontalArrangement = Arrangement.End) {
                    Box(modifier = SwingModifier.testTag(FILLED).weight(1f).fillMaxHeight())
                }
            }

            assertEquals(Rectangle(0, 0, 100, 50), onNodeWithTag(FILLED).fetch<JComponent>().bounds)
        }

    @Test
    fun anIntrinsicQuestionDoesNotRewriteABoxsMeasuredResult() =
        runComposeSwingTest {
            setAskedBetweenMeasureAndPlace {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(modifier = SwingModifier.testTag(FILLED).fillMaxSize())
                }
            }

            assertEquals(Rectangle(0, 0, 100, 50), onNodeWithTag(FILLED).fetch<JComponent>().bounds)
        }

    /** Composes [content] under a parent that measures it at 100 x 50, asks its intrinsic width, then places it. */
    private fun ComposeSwingTest.setAskedBetweenMeasureAndPlace(content: @Composable () -> Unit) {
        setContent {
            Layout(
                content = { content() },
                measurePolicy = { measurables, _ ->
                    val child = measurables.single()
                    val placeable = child.measure(Constraints.fixed(100, 50))
                    child.maxIntrinsicWidth(50)
                    layout(100, 50) { placeable.place(0, 0) }
                },
            )
        }
    }

    private companion object {
        const val FILLED = "filled"
    }
}
