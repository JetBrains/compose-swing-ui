package org.jetbrains.compose.swing.foundation

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
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
                            .preferredSize(Dimension(64, 48))
                            .border(EmptyBorder(8, 8, 8, 8)),
                ) {
                    drawRect(Color.RED)
                }
            }

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            val painted = onNodeWithTag("canvas").captureToImage()

            assertEquals(
                Insets(8, 8, 8, 8),
                canvas.insets,
                "The border is reserved on every side.",
            )
            assertEquals(
                0,
                painted.getRGB(1, 1),
                "A surface paints inside its insets, as a widget paints inside its border. Filling the " +
                    "width and height it is handed must not reach what the border reserved.",
            )
            assertEquals(
                Color.RED.rgb,
                painted.getRGB(32, 24),
                "What is left inside the reservation is the drawing's.",
            )
        }

    @Test
    fun drawingPastWhatTheBorderLeavesIsCutAtTheBorder() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .preferredSize(Dimension(64, 48))
                            .border(EmptyBorder(8, 8, 8, 8)),
                ) {
                    drawRect(Color.RED, x = -8f, y = -8f, width = 64f, height = 48f)
                }
            }

            val painted = onNodeWithTag("canvas").captureToImage()

            assertEquals(0, painted.getRGB(1, 1), "a drawing reaching into the border must not paint there")
            assertEquals(Color.RED.rgb, painted.getRGB(8, 8), "inside the border, the drawing shows")
        }

    @Test
    fun whatOnDrawLeavesOnTheGraphicsDoesNotReachTheBorder() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .preferredSize(Dimension(64, 48))
                            .border(LineBorder(Color.BLUE, 8)),
                ) {
                    drawRect(Color.RED)
                    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0f)
                    graphics.translate(64, 48)
                }
            }

            val painted = onNodeWithTag("canvas").captureToImage()

            assertEquals(Color.RED.rgb, painted.getRGB(32, 24), "the drawing should show")
            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(1, 1),
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
                            .preferredSize(Dimension(64, 48))
                            .border(LineBorder(Color.BLUE, 8)),
                ) {
                    graphics.setXORMode(Color.WHITE)
                }
            }

            val painted = onNodeWithTag("canvas").captureToImage()

            assertEquals(Color.BLUE.rgb, painted.getRGB(1, 1), "the border paints in the composite onDraw found")
        }

    @Test
    fun aFadedDrawKeepsTheCompositeRuleOnDrawSet() =
        runComposeSwingTest {
            setContent {
                SwingNode(factory = { JPanel() }) {
                    Canvas(modifier = SwingModifier.testTag("faded").preferredSize(Dimension(16, 16))) {
                        drawRect(Color.RED)
                        graphics.composite = AlphaComposite.DstOut
                        drawRect(Color.BLACK, alpha = 0.5f)
                    }
                    Canvas(modifier = SwingModifier.testTag("expected").preferredSize(Dimension(16, 16))) {
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
    fun anUnsizedCanvasAnswersItsInsetsAsPreferredSize() =
        runComposeSwingTest {
            setContent {
                Canvas(modifier = SwingModifier.testTag("bare")) { }
                Canvas(modifier = SwingModifier.testTag("bordered").border(EmptyBorder(1, 2, 3, 4))) { }
            }

            assertEquals(
                Dimension(),
                onNodeWithTag("bare").fetch<JComponent>().preferredSize,
                "a canvas with no size set asks for none",
            )
            assertEquals(
                Dimension(6, 4),
                onNodeWithTag("bordered").fetch<JComponent>().preferredSize,
                "a canvas with no size set asks only for its border's insets",
            )
        }

    @Test
    fun insetsAskedForIntoAnInsetsAreWrittenIntoIt() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag("canvas")
                            .preferredSize(Dimension(64, 48))
                            .border(LineBorder(Color.BLUE, 2)),
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
}
