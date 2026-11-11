// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.minimumSize
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Behavioral tests for what a [Canvas] surface paints within its bounds. */
class CanvasSurfaceTest {
    @Test
    fun theDrawingStaysInsideWhatTheBorderReserves() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .styleable {
                                preferredSize(Dimension(64, 48))
                                border(EmptyBorder(8, 8, 8, 8))
                            },
                ) {
                    drawRect(Color.RED)
                }
            }

            val painted = onNodeWithTag("canvas").captureToImage()

            onAllNodesOfType<JComponent>().filterToOne(SwingMatcher.hasTestTag("canvas")).assertProperty(
                Insets(8, 8, 8, 8),
                "The border is reserved on every side.",
            ) { insets }
            painted.assertPixelColor(
                Color(0, true),
                1,
                1,
                "A surface paints inside its insets, as a widget paints inside its border. Filling the " +
                    "width and height it is handed must not reach what the border reserved.",
            )
            painted.assertPixelColor(Color.RED, 32, 24, "What is left inside the reservation is the drawing's.")
        }

    @Test
    fun drawingPastWhatTheBorderLeavesIsCutAtTheBorder() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .styleable {
                                preferredSize(Dimension(64, 48))
                                border(EmptyBorder(8, 8, 8, 8))
                            },
                ) {
                    drawRect(Color.RED, x = -8f, y = -8f, width = 64f, height = 48f)
                }
            }

            val painted = onNodeWithTag("canvas").captureToImage()

            painted.assertPixelColor(Color(0, true), 1, 1, "a drawing reaching into the border must not paint there")
            painted.assertPixelColor(Color.RED, 8, 8, "inside the border, the drawing shows")
        }

    @Test
    fun whatOnDrawLeavesOnTheGraphicsDoesNotReachTheBorder() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .styleable {
                                preferredSize(Dimension(64, 48))
                                border(LineBorder(Color.BLUE, 8))
                            },
                ) {
                    drawRect(Color.RED)
                    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0f)
                    graphics.translate(64, 48)
                }
            }

            val painted = onNodeWithTag("canvas").captureToImage()

            painted.assertPixelColor(Color.RED, 32, 24, "the drawing should show")
            painted.assertPixelColor(
                Color.BLUE,
                1,
                1,
                "the border paints after onDraw, with none of the composite or transform onDraw left set",
            )
        }

    @Test
    fun anXorModeOnDrawLeavesSetDoesNotReachTheBorder() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .styleable {
                                preferredSize(Dimension(64, 48))
                                border(LineBorder(Color.BLUE, 8))
                            },
                ) {
                    graphics.setXORMode(Color.WHITE)
                }
            }

            val painted = onNodeWithTag("canvas").captureToImage()

            painted.assertPixelColor(Color.BLUE, 1, 1, "the border paints in the composite onDraw found")
        }

    @Test
    fun aBorderReservesItsInsetsFromTheDrawing() =
        runComposeSwingTest {
            var drawingSize: Dimension? = null
            setContent {
                SwingNode(factory = { JPanel() }) {
                    Canvas(
                        modifier =
                            SwingModifier
                                .testTag("bordered")
                                .styleable {
                                    preferredSize(64, 48)
                                    border(EmptyBorder(3, 4, 5, 6))
                                },
                        renderingHints = null,
                    ) { drawRect(Color.RED) }
                    Canvas(
                        modifier = SwingModifier.testTag("expected").styleable { preferredSize(64, 48) },
                        renderingHints = null,
                    ) {
                        drawRect(Color.RED, x = 4f, y = 3f, width = 54f, height = 40f)
                    }
                    Canvas(
                        modifier =
                            SwingModifier
                                .testTag("decorated")
                                .shadow(4, Color.BLACK)
                                .styleable {
                                    preferredSize(64, 48)
                                    border(LineBorder(Color.BLUE, 2))
                                },
                    ) { drawingSize = size }
                }
            }

            assertImagesPixelPerfect(
                onNodeWithTag("expected").captureToImage(),
                onNodeWithTag("bordered").captureToImage(),
            )
            onNodeWithTag("decorated").captureToImage()
            assertEquals(
                Dimension(60, 44),
                drawingSize,
                "the drawing gets what the border leaves, and none of the shadow's outsets",
            )
        }

    @Test
    fun aFadedDrawKeepsTheCompositeRuleOnDrawSet() =
        runComposeSwingTest {
            setContent {
                SwingNode(factory = { JPanel() }) {
                    Canvas(modifier = SwingModifier.testTag("faded").styleable { preferredSize(Dimension(16, 16)) }) {
                        drawRect(Color.RED)
                        graphics.composite = AlphaComposite.DstOut
                        drawRect(Color.BLACK, alpha = 0.5f)
                    }
                    Canvas(
                        modifier = SwingModifier.testTag("expected").styleable { preferredSize(Dimension(16, 16)) },
                    ) {
                        drawRect(Color.RED)
                        graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OUT, 0.5f)
                        drawRect(Color.BLACK)
                    }
                }
            }

            assertImagesPixelPerfect(
                onNodeWithTag("expected").captureToImage(),
                onNodeWithTag("faded").captureToImage(),
            )
        }

    @Test
    fun anUnsizedCanvasReportsItsInsetsAsPreferredSize() =
        runComposeSwingTest {
            setContent {
                Canvas(modifier = SwingModifier.testTag("bare")) { }
                Canvas(
                    modifier =
                        SwingModifier.testTag("bordered").styleable {
                            border(EmptyBorder(1, 2, 3, 4))
                        },
                ) { }
            }

            onNodeWithTag("bare").assertProperty(Dimension(), "a canvas with no size set asks for none") {
                preferredSize
            }
            onNodeWithTag("bordered").assertProperty(
                Dimension(6, 4),
                "a canvas with no size set asks only for its border's insets",
            ) { preferredSize }
        }

    @Test
    fun anUnsizedCanvasAsksForNoOutsets() =
        runComposeSwingTest {
            setContent {
                SwingNode(factory = { JPanel() }) {
                    Canvas(modifier = SwingModifier.testTag("bare")) { }
                    // Only a Foundation container gives paint outsets.
                    Box {
                        Canvas(modifier = SwingModifier.testTag("shadowed").shadow(4, Color.BLACK)) { }
                    }
                }
            }

            onNodeWithTag("bare").assertProperty(Dimension(), "a canvas with no size set asks for none") {
                preferredSize
            }
            val outsets = (onNodeWithTag("shadowed").fetch<JComponent>() as Decoratable).decoration.paintOutsets()
            check(outsets.left > 0 && outsets.top > 0) { "the shadow must take outsets: $outsets" }
            onNodeWithTag("shadowed").assertProperty(
                Dimension(outsets.left + outsets.right, outsets.top + outsets.bottom),
                "a canvas with no size set asks only for the outsets its decoration paints in",
            ) { preferredSize }
        }

    @Test
    fun insetsAskedForIntoAnInsetsAreWrittenIntoIt() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .styleable {
                                preferredSize(Dimension(64, 48))
                                border(LineBorder(Color.BLUE, 2))
                            },
                ) { }
            }
            val given = Insets(0, 0, 0, 0)

            assertSame(
                given,
                onNodeWithTag("canvas").fetch<JComponent>().getInsets(given),
                "getInsets fills the passed Insets rather than allocating a new one.",
            )
            assertEquals(Insets(2, 2, 2, 2), given, "The border's stroke width becomes the surface's insets.")
        }

    @Test
    fun theMinimumSizeIsASetMinimumAsSetOrTheDecorationsOutsets() =
        runComposeSwingTest {
            var minimum by mutableStateOf<Dimension?>(null)
            setContent {
                Box {
                    Canvas(
                        modifier =
                            SwingModifier
                                .testTag("canvas")
                                .shadow(4, Color.BLACK)
                                .styleable {
                                    preferredSize(Dimension(64, 48))
                                    minimumSize(minimum)
                                },
                    ) { }
                }
            }
            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            val outsets = canvas.paintOutsets
            check(outsets.left > 0 && outsets.top > 0) { "the shadow must take outsets: $outsets" }
            assertEquals(
                Dimension(outsets.left + outsets.right, outsets.top + outsets.bottom),
                canvas.minimumSize,
                "a surface asks no minimum for the drawing",
            )

            minimum = Dimension(20, 10)
            awaitIdle()
            assertEquals(Dimension(20, 10), canvas.minimumSize, "a minimum set on the surface answers as set")
        }
}
