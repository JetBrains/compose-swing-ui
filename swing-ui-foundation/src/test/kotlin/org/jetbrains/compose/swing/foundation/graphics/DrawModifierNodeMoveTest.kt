package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test

/** Behavioral tests for a [DrawModifierNode] whose component moves to another composition. */
class DrawModifierNodeMoveTest {
    @Test
    fun aDrawNodeMovedToAnotherCompositionAndRemovedThereFailsNothingWhenAReadItPaintedChanges() =
        runComposeSwingTest {
            var inOuter by mutableStateOf(false)
            var shownInOuter by mutableStateOf(true)
            var color by mutableStateOf(Color.RED)
            val host = JPanel()
            lateinit var context: CompositionContext
            lateinit var content: @Composable () -> Unit
            setContent {
                context = rememberCompositionContext()
                content =
                    remember {
                        movableContentOf {
                            DecoratedBox(
                                modifier =
                                    SwingModifier
                                        .testTag("box")
                                        .preferredSize(Dimension(16, 16))
                                        .drawBehind { drawRect(color) },
                            )
                        }
                    }
                SwingNode(factory = { host })
                if (inOuter && shownInOuter) content()
            }
            awaitIdle()
            val handle = host.setContent(parent = context) { if (!inOuter) content() }
            awaitIdle()
            onNodeWithTag("box").captureToImage()

            inOuter = true
            awaitIdle()
            shownInOuter = false
            awaitIdle()
            color = Color.BLUE
            awaitIdle()

            handle.dispose()
        }
}
