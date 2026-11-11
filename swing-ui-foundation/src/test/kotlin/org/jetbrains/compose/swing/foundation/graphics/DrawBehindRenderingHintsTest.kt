// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
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
                            .drawBehind {
                                antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                                strokeControl = graphics.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)
                            }.drawWithContent {
                                contentAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                            },
                    style = { preferredSize(Dimension(32, 32)) },
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
                                .drawBehind { drawCircle(Color.BLACK) },
                        style = { preferredSize(Dimension(32, 32)) },
                    )
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("off")
                                .drawBehind {
                                    graphics.setRenderingHint(
                                        RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_OFF,
                                    )
                                    drawCircle(Color.BLACK)
                                },
                        style = { preferredSize(Dimension(32, 32)) },
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
