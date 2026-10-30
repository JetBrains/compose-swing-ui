package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DrawBehindRenderingHintsTest {
    @Test
    fun drawBehindDrawsWithTheCanvasDefaultRenderingHints() =
        runComposeSwingTest {
            var antialiasing: Any? = null
            var strokeControl: Any? = null
            var contentAntialiasing: Any? = null
            setContent {
                DecoratedBox(
                    modifier =
                        SwingModifier
                            .testTag("box")
                            .preferredSize(Dimension(32, 32))
                            .drawBehind {
                                antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                                strokeControl = graphics.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)
                            }.drawWithContent {
                                contentAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                            },
                )
            }

            onNodeWithTag("box").captureToImage()

            assertEquals(RenderingHints.VALUE_ANTIALIAS_ON, antialiasing)
            assertEquals(RenderingHints.VALUE_STROKE_PURE, strokeControl)
            assertEquals(
                RenderingHints.VALUE_ANTIALIAS_OFF,
                contentAntialiasing,
                "The hints stay on the copy the block draws on.",
            )
        }

    @Test
    fun aDrawBehindShapeHasSmoothEdgesUnlessItsBlockTurnsAntialiasingOff() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("default")
                                .preferredSize(Dimension(32, 32))
                                .drawBehind { drawCircle(Color.BLACK) },
                    )
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("off")
                                .preferredSize(Dimension(32, 32))
                                .drawBehind {
                                    graphics.setRenderingHint(
                                        RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_OFF,
                                    )
                                    drawCircle(Color.BLACK)
                                },
                    )
                }
            }

            assertNotNull(
                differingPixelBounds(
                    onNodeWithTag("off").captureToImage(),
                    onNodeWithTag("default").captureToImage(),
                ),
                "A hint the block sets wins over the defaults, so the edge pixels differ.",
            )
        }
}
