// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import kotlin.test.Test

/**
 * A measured result belongs to the pass that produced it. A parent asking a container for its intrinsic extent
 * between measuring and placing it must not rewrite what that measure placed.
 */
class MeasureResultAliasingTest {
    @Test
    fun anIntrinsicQueryDoesNotRewriteARowsMeasuredResult() =
        runComposeSwingTest {
            setAskedBetweenMeasureAndPlace {
                Row(horizontalArrangement = Arrangement.End) {
                    Box(modifier = SwingModifier.testTag(FILLED).weight(1f).fillMaxHeight())
                }
            }

            onNodeWithTag(FILLED).assertProperty(Rectangle(0, 0, 100, 50)) { bounds }
        }

    @Test
    fun anIntrinsicQueryDoesNotRewriteABoxsMeasuredResult() =
        runComposeSwingTest {
            setAskedBetweenMeasureAndPlace {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(modifier = SwingModifier.testTag(FILLED).fillMaxSize())
                }
            }

            onNodeWithTag(FILLED).assertProperty(Rectangle(0, 0, 100, 50)) { bounds }
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
