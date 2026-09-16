package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins that a chain position switching between [SwingModifier.drawBehind] and [SwingModifier.drawWithContent]
 * gets each one's own node, rather than reusing the other's with its content-ordering mode stuck.
 */
class DrawModifierRebindTest {
    @Test
    fun switchingOnePositionBetweenDrawBehindAndDrawWithContentPaintsExactlyOnce() =
        runComposeSwingTest {
            var useDrawWithContent by mutableStateOf(false)
            var contentPaints = 0
            setContent {
                DecoratedBox {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .preferredSize(Dimension(32, 32))
                                .opaque(false)
                                .then(
                                    if (useDrawWithContent) {
                                        SwingModifier.drawWithContent {
                                            drawRect(Color.RED)
                                            drawContent()
                                        }
                                    } else {
                                        SwingModifier.drawBehind { drawRect(Color.RED) }
                                    },
                                ),
                    ) {
                        DecoratedBox(
                            modifier =
                                SwingModifier
                                    .preferredSize(Dimension(16, 16))
                                    .opaque(false)
                                    .drawBehind {
                                        contentPaints++
                                        drawRect(Color.GREEN)
                                    },
                        )
                    }
                }
            }

            fun assertPaintsOnce(message: String) {
                contentPaints = 0
                val image = onNodeWithTag("outer").captureToImage()
                assertEquals(1, contentPaints, "$message: content paints exactly once.")
                assertEquals(Color.RED.rgb, image.getRGB(31, 31), "$message: red at the tip.")
                assertEquals(Color.GREEN.rgb, image.getRGB(8, 8), "$message: green after, where content paints.")
            }

            assertPaintsOnce("drawBehind")

            useDrawWithContent = true
            awaitIdle()
            assertPaintsOnce("drawWithContent")

            useDrawWithContent = false
            awaitIdle()
            assertPaintsOnce("drawBehind again")
        }
}
