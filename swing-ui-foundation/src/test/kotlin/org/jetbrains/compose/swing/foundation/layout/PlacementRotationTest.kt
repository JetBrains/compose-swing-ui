package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.channelDifference
import org.jetbrains.compose.swing.foundation.graphics.renderImage
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacementRotationTest {
    @Test
    fun aRepaintWithNothingChangedRunsNoLayerBlock() =
        runComposeSwingTest {
            var runs = 0
            val block: PlacementLayerScope.() -> Unit = {
                runs++
                rotationZ = 30f
            }
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer(block))
                }
            }
            val node = onNodeWithTag("layered")
            node.captureToImage()
            val placedRuns = runs

            node.captureToImage()
            node.captureToImage()

            assertEquals(placedRuns, runs)
        }

    @Test
    fun aParentResizingAScaledChildLaysItOutAtTheNewSize() = assertResizedUnderALayer(clip = false)

    @Test
    fun aParentResizingAScaledAndClippedChildLaysItOutAtTheNewSize() = assertResizedUnderALayer(clip = true)

    /** A parent that resizes a child placed with a layer lays that child out at its new layout size. */
    private fun assertResizedUnderALayer(clip: Boolean) =
        runComposeSwingTest {
            var width by mutableIntStateOf(40)
            setContent {
                Layout(
                    content = {
                        Box(
                            modifier =
                                SwingModifier.testTag("layered").placementLayer {
                                    scaleX = 2f
                                    this.clip = clip
                                },
                            propagateMinConstraints = true,
                        ) { SizedChild(0) }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints.fixed(width, CHILD_HEIGHT))
                        layout(width, CHILD_HEIGHT) { placeable.place(0, 0) }
                    },
                )
            }
            val inner = onNodeWithTag("layered").fetch<JComponent>().getComponent(0)
            assertEquals(40, inner.width)

            width = 60
            awaitIdle()

            assertEquals(60, inner.width, "the child's own layout must run at its new size")
            assertTrue(inner.isValidUpToTheValidateRoot(), "the laid out tree must be valid")
        }

    /** A fade and a turn set in one block paint what a fading layer holding a turning layer paints. */
    @Test
    fun aFadeInARotatingLayerFadesTheTurnedContent() =
        runComposeSwingTest {
            setContent {
                Row {
                    MarkedCanvas(
                        SwingModifier.testTag("one").placementLayer {
                            alpha = 0.5f
                            rotationZ = 30f
                            scaleX = 1.2f
                        },
                    )
                    MarkedCanvas(
                        SwingModifier
                            .testTag("nested")
                            .placementLayer { alpha = 0.5f }
                            .placementLayer {
                                rotationZ = 30f
                                scaleX = 1.2f
                            },
                    )
                }
            }

            // One block records the turned content, the nested layers record a layer that turns it: the mark's edge
            // rasterizes on two pixels differently.
            assertImagesPixelPerfect(
                onNodeWithTag("nested").captureToImage(),
                onNodeWithTag("one").captureToImage(),
                maxDifferentPixels = 2,
            )
        }

    /** A shadow declared inside a half-turned layer turns with the content, its offset included. */
    @Test
    fun aShadowInsideAHalfTurnedLayerTurnsWithIt() = assertShadowTurnsWithTheLayer(quarterTurns = 2, 40, 24)

    /** The same holds for a quarter turn, which carries each axis of the offset onto the other. */
    @Test
    fun aShadowInsideAQuarterTurnedLayerTurnsWithIt() = assertShadowTurnsWithTheLayer(quarterTurns = 1, 32, 32)

    /**
     * Compares a canvas of [width] by [height] turned clockwise by [quarterTurns] under a shadow with the upright
     * one turned alike. The blur runs along the device's axes and rounds toward the side it runs from, so a turned
     * falloff pixel may differ by one level.
     */
    private fun assertShadowTurnsWithTheLayer(
        quarterTurns: Int,
        width: Int,
        height: Int,
    ) = runComposeSwingTest {
        setContent {
            Row {
                MarkedCanvas(
                    SwingModifier.testTag("upright").shadow(3, Color.BLACK, offsetX = 6, offsetY = 2),
                    width,
                    height,
                )
                MarkedCanvas(
                    SwingModifier
                        .testTag("turned")
                        .placementLayer { rotationZ = 90f * quarterTurns }
                        .shadow(3, Color.BLACK, offsetX = 6, offsetY = 2),
                    width,
                    height,
                )
            }
        }
        val upright = onNodeWithTag("upright").captureToImage()
        val sideways = quarterTurns % 2 == 1
        val expectedWidth = if (sideways) upright.height else upright.width
        val expectedHeight = if (sideways) upright.width else upright.height
        val expected =
            renderImage(expectedWidth, expectedHeight) {
                it.translate(expectedWidth / 2.0, expectedHeight / 2.0)
                it.rotate(Math.PI / 2 * quarterTurns)
                it.translate(-upright.width / 2.0, -upright.height / 2.0)
                it.drawImage(upright, 0, 0, null)
            }
        val turned = onNodeWithTag("turned").captureToImage()

        assertEquals(Dimension(expected.width, expected.height), Dimension(turned.width, turned.height))
        assertTrue(turned.channelDifference(expected) <= 1, "a turned pixel differs by more than one level")
    }

    @Test
    fun sharedMatrixMatchesIndependentCornerMathForNonzeroBoxes() {
        val box = Rectangle2D.Double(-17.0, 23.0, 160.0, 88.0)
        for (angle in listOf(-150f, -90f, -58f, -45f, -29f, 0f, 29f, 45f, 58f, 90f, 180f)) {
            for (pivot in listOf(-0.25f, 0f, 0.14f, 0.2f, 0.5f, 1f, 1.25f)) {
                val values = RotationValues(angle, -0.8f, 1.4f, pivot)
                val matrix = TransformOrigin(pivot, 0.5f).createTransform(box, values.scaleX, values.scaleY, angle)
                for (point in listOf(Point2D.Double(-17.0, 23.0), Point2D.Double(143.0, 111.0))) {
                    val expected = values.point(point.x + 17.0, point.y - 23.0)
                    val actual = matrix.transform(point, null)
                    assertEquals(expected.x - 17.0, actual.x, 0.00001)
                    assertEquals(expected.y + 23.0, actual.y, 0.00001)
                }
            }
        }
    }
}

/** A [width] by [height] canvas, red with a blue mark in its top left corner, so a turn or a mirror shows. */
@Composable
private fun MarkedCanvas(
    modifier: SwingModifier,
    width: Int = 40,
    height: Int = 24,
) {
    Canvas(modifier = modifier.preferredSize(width, height), renderingHints = null) {
        drawRect(Color.RED)
        drawRect(Color.BLUE, 4f, 4f, 10f, 6f)
    }
}

private data class RotationValues(
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val pivot: Float = 0.5f,
) {
    fun point(
        x: Double,
        y: Double,
    ): Point2D.Double {
        val radians = Math.toRadians(rotation.toDouble())
        val pivotX = pivot.toDouble() * 160
        val dx = (x - pivotX) * scaleX
        val dy = (y - 44) * scaleY
        val sine = sin(radians).let { if (abs(it) < 0.000001) 0.0 else it }
        val cosine = cos(radians).let { if (abs(it) < 0.000001) 0.0 else it }
        return Point2D.Double(pivotX + dx * cosine - dy * sine, 44 + dx * sine + dy * cosine)
    }
}
