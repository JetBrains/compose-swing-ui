package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Surface blur reserves its falloff without changing the content's coordinates or drawing size. */
class SurfaceBlurOutsetsTest {
    @Test
    fun wideAndFractionallyScaledBlursKeepTheirHaloInsideTransparentBounds() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(0)
            var drawn = Dimension()
            setContent {
                Box {
                    DecoratedCanvas(
                        modifier =
                            SwingModifier
                                .testTag("blurred-surface")
                                .preferredSize(CONTENT_SIZE)
                                .blur(radius),
                    ) {
                        drawn = Dimension(size)
                        drawRect(Color.RED)
                    }
                }
            }

            for (value in listOf(1, 4, 8, 10, 17, 24, 32, 65)) {
                radius = value
                awaitIdle()
                val image = onNodeWithTag("blurred-surface").captureToImage()
                val margin = blurOutsets(value)
                val component = onNodeWithTag("blurred-surface").fetch<JComponent>()

                assertEquals(CONTENT_SIZE, drawn, "Radius $value must preserve the Canvas drawing size.")
                assertEquals(
                    CONTENT_SIZE,
                    component.preferredSize,
                    "A preferred size set on the surface answers as set.",
                )
                assertEquals(
                    expandedSize(margin),
                    component.size,
                    "The component grows by the blur's outsets at radius $value.",
                )
                assertEquals(
                    expandedSize(margin),
                    Dimension(image.width, image.height),
                    "The capture covers the blur's outsets at radius $value.",
                )
                assertEquals(Insets(margin, margin, margin, margin), component.insets, "The insets carry the outsets.")
                image.assertHalo(margin)
                image.assertTransparentPerimeter()
            }
        }

    @Test
    fun removingAndRestoringBlurRelayoutsTheContainerAndItsChild() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(16)
            setContent {
                Box {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("blurred-surface")
                                .preferredSize(CONTENT_SIZE)
                                .opaque(false)
                                .blur(radius),
                    ) {
                        DecoratedCanvas(
                            modifier = SwingModifier.testTag("blurred-child").preferredSize(CONTENT_SIZE),
                        ) {
                            drawRect(Color.RED)
                        }
                    }
                }
            }

            fun assertLaidOutAround(margin: Int) {
                val component = onNodeWithTag("blurred-surface").fetch<JComponent>()
                assertEquals(
                    expandedSize(margin),
                    component.size,
                    "The container's bounds grow by the blur's outsets around its layout bounds.",
                )
                assertEquals(
                    Rectangle(margin, margin, CONTENT_SIZE.width, CONTENT_SIZE.height),
                    onNodeWithTag("blurred-child").fetch<JComponent>().bounds,
                    "The child keeps its size, inside the blur's outsets.",
                )
            }

            for (blurred in listOf(16, 32)) {
                radius = blurred
                awaitIdle()
                val image = onNodeWithTag("blurred-surface").captureToImage()

                assertLaidOutAround(blurOutsets(blurred))
                image.assertHalo(blurOutsets(blurred))
                image.assertTransparentPerimeter()
            }
            for (unblurred in listOf(0, -1)) {
                radius = unblurred
                awaitIdle()

                assertLaidOutAround(0)
                assertEquals(
                    Color.RED.rgb,
                    onNodeWithTag("blurred-surface").captureToImage().getRGB(0, 0),
                    "Without a blur the child paints to the container's edge.",
                )
            }
            radius = 16
            awaitIdle()

            assertLaidOutAround(blurOutsets(16))
        }

    @Test
    fun anUnsizedContainerAddsBlurToItsChildsIntrinsicSizesAtPreferredAndMinimumSize() =
        runComposeSwingTest {
            setContent {
                Box {
                    Box(modifier = SwingModifier.testTag("blurred-surface").blur(17)) {
                        DecoratedCanvas(
                            modifier = SwingModifier.preferredSize(CONTENT_SIZE).minimumSize(Dimension(21, 15)),
                        )
                    }
                }
            }

            val component = onNodeWithTag("blurred-surface").fetch<JComponent>()
            val margin = blurOutsets(17)
            assertEquals(
                expandedSize(margin),
                component.preferredSize,
                "An unsized container asks for its child's preferred size grown by the blur's outsets.",
            )
            assertEquals(
                Dimension(21 + 2 * margin, 15 + 2 * margin),
                component.minimumSize,
                "The minimum size is its child's minimum grown by the blur's outsets.",
            )
        }

    @Test
    fun nestedBlurAndShadowAddTheirOwnOutsets() =
        runComposeSwingTest {
            var drawn = Dimension()
            setContent {
                Box {
                    DecoratedCanvas(
                        modifier =
                            SwingModifier
                                .testTag("blurred-surface")
                                .preferredSize(CONTENT_SIZE)
                                .blur(17)
                                .blur(10)
                                .shadow(4, Color.BLACK, offsetX = 2, offsetY = 1),
                    ) {
                        drawn = Dimension(size)
                        drawRect(Color.RED)
                    }
                }
            }

            val image = onNodeWithTag("blurred-surface").captureToImage()
            val margin = blurOutsets(17) + blurOutsets(10)
            val shadowReach = blurOutsets(4)
            val component = onNodeWithTag("blurred-surface").fetch<JComponent>()
            assertEquals(CONTENT_SIZE, drawn, "The stacked effects leave the Canvas drawing size unchanged.")
            assertEquals(
                expandedSize(margin + shadowReach),
                component.size,
                "The component grows by the blurs' and the shadow's reach.",
            )
            assertEquals(
                Insets(
                    margin + shadowReach - 1,
                    margin + shadowReach - 2,
                    margin + shadowReach + 1,
                    margin + shadowReach + 2,
                ),
                (component as Decoratable).decoration.paintOutsets(),
                "The shadow's offset moves its reach toward the bottom and the end.",
            )
            image.assertTransparentPerimeter()
            assertTrue(image.opacityAt(margin - 1, image.height / 2) > 0, "The blurred halo paints inside the outsets.")
        }

    @Test
    fun aShadowFadesOutInsideTheOutsetsItReserves() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(0)
            setContent {
                Box {
                    DecoratedCanvas(
                        modifier =
                            SwingModifier
                                .testTag("blurred-surface")
                                .preferredSize(CONTENT_SIZE)
                                .shadow(radius, Color.BLACK, offsetX = 3, offsetY = 2),
                    ) {
                        drawRect(Color.RED)
                    }
                }
            }

            for (value in listOf(4, 17, 40)) {
                radius = value
                awaitIdle()
                val image = onNodeWithTag("blurred-surface").captureToImage()

                assertEquals(
                    expandedSize(blurOutsets(value)),
                    Dimension(image.width, image.height),
                    "The capture covers the shadow's outsets at radius $value.",
                )
                image.assertTransparentPerimeter()
            }
        }

    @Test
    fun anOuterClipCanCutTheHaloWhileBlurOutsideAShapeSpreadsIt() =
        runComposeSwingTest {
            val margin = blurOutsets(17)
            val contentBounds = Shape.of(Rectangle(0, 0, CONTENT_SIZE.width, CONTENT_SIZE.height))
            setContent {
                Box {
                    DecoratedCanvas(
                        modifier =
                            SwingModifier
                                .testTag("blurred-surface")
                                .preferredSize(CONTENT_SIZE)
                                .blur(17)
                                .clip(RectangleShape)
                                .background(Brush.of(Color.RED)),
                    )
                    DecoratedCanvas(
                        modifier =
                            SwingModifier
                                .testTag("blurred-child")
                                .preferredSize(CONTENT_SIZE)
                                .clip(contentBounds)
                                .blur(17)
                                .background(Brush.of(Color.RED)),
                    )
                }
            }

            val spread = onNodeWithTag("blurred-surface").captureToImage()
            val clipped = onNodeWithTag("blurred-child").captureToImage()
            spread.assertHalo(margin)
            spread.assertTransparentPerimeter()
            assertEquals(
                Insets(0, 0, 0, 0),
                onNodeWithTag("blurred-child").fetch<JComponent>().insets,
                "The outer clip covers only the layout bounds, so the blur inside it takes no paint outsets.",
            )
            assertEquals(
                CONTENT_SIZE,
                Dimension(clipped.width, clipped.height),
                "The outer clip keeps the blurred child at its layout size.",
            )
            assertTrue(clipped.opacityAt(0, clipped.height / 2) > 0, "The blurred content paints up to the clip.")
            assertTrue(
                clipped.opacityAt(0, clipped.height / 2) < clipped.opacityAt(clipped.width / 2, clipped.height / 2),
                "The blur still softens the edge the clip cuts.",
            )
        }

    private fun expandedSize(margin: Int): Dimension =
        Dimension(CONTENT_SIZE.width + margin * 2, CONTENT_SIZE.height + margin * 2)

    private fun BufferedImage.assertHalo(margin: Int) {
        assertTrue(opacityAt(margin - 1, height / 2) > 0, "Left halo must be visible.")
        assertTrue(opacityAt(width - margin, height / 2) > 0, "Right halo must be visible.")
        assertTrue(opacityAt(width / 2, margin - 1) > 0, "Top halo must be visible.")
        assertTrue(opacityAt(width / 2, height - margin) > 0, "Bottom halo must be visible.")
    }

    /** Asserts nothing is painted on the image's outermost pixels, so the halo fades out inside the bounds. */
    private fun BufferedImage.assertTransparentPerimeter() {
        val painted = differingPixelBounds(renderImage(width, height) {}, this)
        assertTrue(
            painted == null || Rectangle(1, 1, width - 2, height - 2).contains(painted),
            "Painted pixels $painted reach the edge of a ${width}x$height image.",
        )
    }

    private fun BufferedImage.opacityAt(
        x: Int,
        y: Int,
    ): Int = getRGB(x, y) ushr 24

    private companion object {
        val CONTENT_SIZE = Dimension(81, 57)
    }
}
