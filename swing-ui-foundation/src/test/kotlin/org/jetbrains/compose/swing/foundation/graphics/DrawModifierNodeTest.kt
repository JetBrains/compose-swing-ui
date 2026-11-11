// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.clipRect
import org.jetbrains.compose.swing.foundation.graphics.drawscope.record
import org.jetbrains.compose.swing.foundation.graphics.drawscope.translate
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for [DrawModifierNode]: where a draw node runs, what it observes, and what it is told.
 *
 * The public API scenarios in this class are adopted from Compose Multiplatform's
 * `compose/ui/ui/src/androidDeviceTest/kotlin/androidx/compose/ui/DrawModifierTest.kt`; Android-specific
 * RenderNode cases are intentionally not ported.
 */
class DrawModifierNodeTest {
    @Test
    fun simpleDrawTest() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .drawBehind { drawRect(Color.YELLOW) },
                        style = {
                            opaque(false)
                            preferredSize(Dimension(32, 32))
                        },
                    ) {
                        DecoratedBox(
                            modifier =
                                SwingModifier
                                    .testTag("inner")
                                    .drawBehind { drawRect(Color.RED) },
                            style = {
                                opaque(false)
                                preferredSize(Dimension(16, 16))
                            },
                        )
                    }
                }
            }

            val image = onNodeWithTag("outer").captureToImage()
            assertEquals(Color.YELLOW.rgb, image.getRGB(31, 31), "The outer drawBehind paints under the inner content.")
            assertEquals(Color.RED.rgb, image.getRGB(8, 8), "The inner drawBehind paints its own area.")
        }

    @Test
    fun recomposeDrawTest() =
        runComposeSwingTest {
            var outerColor by mutableStateOf(Color.BLUE)
            var innerColor by mutableStateOf(Color.WHITE)
            setContent {
                DecoratedBox {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .drawBehind { drawRect(outerColor) },
                        style = {
                            opaque(false)
                            preferredSize(Dimension(32, 32))
                        },
                    ) {
                        DecoratedBox(
                            modifier =
                                SwingModifier
                                    .testTag("inner")
                                    .drawBehind { drawRect(innerColor) },
                            style = {
                                opaque(false)
                                preferredSize(Dimension(16, 16))
                            },
                        )
                    }
                }
            }
            val component = onNodeWithTag("outer").fetch<JComponent>()
            var image = onNodeWithTag("outer").captureToImage()
            assertEquals(Color.BLUE.rgb, image.getRGB(31, 31), "The outer drawBehind paints its declared color first.")
            assertEquals(Color.WHITE.rgb, image.getRGB(8, 8), "The inner drawBehind paints its declared color first.")

            withRecordedRepaints { repaints ->
                outerColor = Color.RED
                innerColor = Color.YELLOW
                awaitIdle()

                assertTrue(repaints.repaintsOf(component) > 0, "A changed drawBehind read must repaint the component.")
            }
            image = onNodeWithTag("outer").captureToImage()
            assertEquals(Color.RED.rgb, image.getRGB(31, 31), "The outer drawBehind repaints with the changed color.")
            assertEquals(Color.YELLOW.rgb, image.getRGB(8, 8), "The inner drawBehind repaints with the changed color.")
        }

    @Test
    fun aRecompositionWithTheSameDrawBlocksDoesNotRepaint() =
        runComposeSwingTest {
            var recomposeTrigger by mutableStateOf(0)
            var compositions = 0
            setContent {
                DecoratedBox {
                    recomposeTrigger
                    SideEffect { compositions++ }
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("box")
                                .drawBehind(fillRed)
                                .drawWithContent(drawContentOverRed),
                        style = { preferredSize(Dimension(16, 16)) },
                    )
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()

            val composed = compositions

            withRecordedRepaints { repaints ->
                recomposeTrigger++
                awaitIdle()

                assertTrue(compositions > composed, "the chain was declared again")
                assertEquals(0, repaints.repaintsOf(box), "draw blocks declared again unchanged draw nothing new")
            }
        }

    @Test
    fun drawOrderWithChildren() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .then(
                                    SwingModifier.drawWithContent {
                                        drawRect(Color.GREEN)
                                        val offset = size.width / 3f
                                        clipRect(offset, offset, offset * 2, offset * 2) {
                                            this@drawWithContent.drawContent()
                                            drawRect(
                                                Color.WHITE,
                                                x = 0f,
                                                y = size.height / 2f,
                                                width = size.width.toFloat(),
                                                height = size.height / 2f,
                                            )
                                        }
                                    },
                                ).drawBehind {
                                    drawRect(Color.WHITE, height = size.height / 2f)
                                },
                        style = {
                            opaque(false)
                            preferredSize(Dimension(32, 32))
                        },
                    )
                }
            }

            val image = onNodeWithTag("outer").captureToImage()
            assertEquals(Color.GREEN.rgb, image.getRGB(2, 2), "outside the clip, drawWithContent's own paint shows")
            assertEquals(Color.GREEN.rgb, image.getRGB(8, 16), "inside the clip's top half, content shows through")
            assertEquals(Color.WHITE.rgb, image.getRGB(16, 12), "inside the clip's bottom half, its own paint covers")
            assertEquals(Color.WHITE.rgb, image.getRGB(16, 20), "Below the clip, drawBehind's paint shows through.")
        }

    @Test
    fun drawModifierWithLayout() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .then(
                                    SwingModifier.drawWithContent {
                                        drawRect(Color.BLUE)
                                        translate(8f, 8f) { this@drawWithContent.drawContent() }
                                    },
                                ),
                        style = {
                            opaque(false)
                            preferredSize(Dimension(32, 32))
                        },
                    ) {
                        DecoratedBox(
                            modifier =
                                SwingModifier
                                    .testTag("inner")
                                    .background(Brush.of(Color.WHITE)),
                            style = {
                                opaque(false)
                                preferredSize(Dimension(16, 16))
                            },
                        )
                    }
                }
            }

            val image = onNodeWithTag("outer").captureToImage()
            assertEquals(Color.BLUE.rgb, image.getRGB(4, 4), "the draw block paints the parent before content")
            assertEquals(Color.WHITE.rgb, image.getRGB(16, 16), "drawContent is translated, its size unchanged")
        }

    @Test
    fun drawingBackTheContentRecordedIntoALayerPaintsWhatDrawContentPaints() =
        runComposeSwingTest {
            val layer = ImageLayer()
            var throughLayer by mutableStateOf(false)
            setContent {
                DecoratedBox {
                    DecoratedCanvas(
                        modifier =
                            SwingModifier.testTag("canvas").drawWithContent {
                                if (throughLayer) {
                                    record(layer) { this@drawWithContent.drawContent() }
                                    layer.draw(graphics)
                                } else {
                                    drawContent()
                                }
                            },
                        style = { preferredSize(Dimension(32, 32)) },
                    ) {
                        drawCircle(Color.RED, radius = 10f)
                        drawRect(Color.BLUE, 20f, 4f, 8f, 20f)
                    }
                }
            }
            val direct = onNodeWithTag("canvas").captureToImage()

            throughLayer = true
            awaitIdle()

            assertImagesPixelPerfect(direct, onNodeWithTag("canvas").captureToImage())
        }

    @Test
    fun recordDrawsIntoTheLayerRatherThanTheComponent() =
        runComposeSwingTest {
            val layer = ImageLayer()
            setContent {
                DecoratedBox {
                    DecoratedCanvas(
                        modifier =
                            SwingModifier.testTag("canvas").drawWithContent {
                                record(layer) { this@drawWithContent.drawContent() }
                            },
                        style = { preferredSize(Dimension(32, 32)) },
                    ) {
                        drawRect(Color.RED)
                    }
                }
            }

            val image = onNodeWithTag("canvas").captureToImage()

            assertNotEquals(Color.RED.rgb, image.getRGB(16, 16), "Recorded content does not reach the component.")
            assertEquals(Dimension(32, 32), layer.size, "The recording covers the scope's size.")
            assertEquals(Color.RED.rgb, layer.toBufferedImage().getRGB(16, 16), "The layer holds the content.")
        }

    @Test
    fun aChainPositionSwitchingFromDrawBehindToDrawWithContentAndBackPaintsExactlyOnce() =
        assertSwitchingDrawBehindAndDrawWithContentPaintsExactlyOnce(startsWithDrawWithContent = false)

    @Test
    fun aChainPositionSwitchingFromDrawWithContentToDrawBehindAndBackPaintsExactlyOnce() =
        assertSwitchingDrawBehindAndDrawWithContentPaintsExactlyOnce(startsWithDrawWithContent = true)

    /**
     * A chain position that switches between [SwingModifier.drawBehind] and [SwingModifier.drawWithContent] must
     * get each mode's own node, rather than reusing the other mode's node with its content-ordering stuck.
     */
    private fun assertSwitchingDrawBehindAndDrawWithContentPaintsExactlyOnce(startsWithDrawWithContent: Boolean) =
        runComposeSwingTest {
            var useDrawWithContent by mutableStateOf(startsWithDrawWithContent)
            var contentPaints = 0
            setContent {
                DecoratedFlowPanel(
                    modifier =
                        decorated {
                            val drawStep =
                                if (useDrawWithContent) {
                                    SwingModifier.drawWithContent {
                                        drawRect(Color.RED)
                                        drawContent()
                                    }
                                } else {
                                    SwingModifier.drawBehind { drawRect(Color.RED) }
                                }
                            SwingModifier
                                .testTag("outer")
                                .then(drawStep)
                        },
                    style = {
                        opaque(false)
                        preferredSize(Dimension(32, 32))
                    },
                ) {
                    DecoratedFlowPanel(
                        modifier =
                            SwingModifier.testTag("inner").drawBehind {
                                contentPaints++
                                drawRect(Color.GREEN)
                            },
                        style = {
                            opaque(false)
                            preferredSize(Dimension(16, 16))
                        },
                    )
                }
            }

            fun assertPaintsOnce(message: String) {
                contentPaints = 0
                val image = onNodeWithTag("outer").captureToImage()
                assertEquals(1, contentPaints, "$message: content paints exactly once.")
                assertEquals(Color.RED.rgb, image.getRGB(31, 31), "$message: red at the tip.")
                assertEquals(Color.GREEN.rgb, image.getRGB(8, 8), "$message: green after, where content paints.")
            }

            fun modeName() = if (useDrawWithContent) "drawWithContent" else "drawBehind"

            assertPaintsOnce(modeName())
            useDrawWithContent = !useDrawWithContent
            awaitIdle()
            assertPaintsOnce(modeName())
            useDrawWithContent = !useDrawWithContent
            awaitIdle()
            assertPaintsOnce("${modeName()} again")
        }
}

private val fillRed: DrawScope.() -> Unit = { drawRect(Color.RED) }

private val drawContentOverRed: ContentDrawScope.() -> Unit = {
    drawRect(Color.RED)
    drawContent()
}
