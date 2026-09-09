package org.jetbrains.compose.swing.foundation.graphics.drawscope

import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Area
import java.awt.geom.Path2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral tests for [DrawTransform] and the scoped transforms, insets and clips built on it, drawn through the
 * public `Canvas` with no rendering hints and compared with drawings that need no transform.
 */
class DrawTransformTest {
    @Test
    fun nestedTransformsComposeOutsideInAndClipInTheTransformedSpace() =
        runComposeSwingTest {
            val (nested, reference) =
                captureEach(
                    40,
                    40,
                    {
                        translate(10f, 0f) {
                            rotate(90f, pivotX = 10f, pivotY = 10f) {
                                scale(2f, 2f, pivotX = 0f, pivotY = 0f) {
                                    clipRect(0f, 0f, 10f, 5f) { drawRect(Color.BLUE) }
                                }
                            }
                        }
                        drawRect(Color.RED, x = 0f, y = 0f, width = 5f, height = 5f)
                    },
                    {
                        drawRect(Color.BLUE, x = 20f, y = 0f, width = 10f, height = 20f)
                        drawRect(Color.RED, x = 0f, y = 0f, width = 5f, height = 5f)
                    },
                )

            assertImagesPixelPerfect(reference, nested)
        }

    @Test
    fun insetMovesTheOriginAndShrinksTheSizeWithoutClippingAndRestoresAfterward() =
        runComposeSwingTest {
            val nested = mutableListOf<Dimension>()
            val overflow: DrawScope.() -> Unit = {
                nested += size
                drawRect(Color.RED, x = -5f, y = -5f, width = 100f, height = 100f)
            }

            val images =
                captureEach(
                    60,
                    60,
                    {
                        inset(10f, 12f, 11f, 13f, overflow)
                        drawRect(Color.BLUE, x = 0f, y = 0f, width = 5f, height = 5f)
                    },
                    {
                        drawRect(Color.RED, x = 5f, y = 7f, width = 100f, height = 100f)
                        drawRect(Color.BLUE, x = 0f, y = 0f, width = 5f, height = 5f)
                    },
                    { inset(10f, overflow) },
                    { drawRect(Color.RED, x = 5f, y = 5f, width = 100f, height = 100f) },
                    { inset(5f, 8f, overflow) },
                    { drawRect(Color.RED, x = 0f, y = 3f, width = 100f, height = 100f) },
                )

            images.chunked(2).forEach { (inset, reference) -> assertImagesPixelPerfect(reference, inset) }
            assertEquals(
                listOf(Dimension(39, 35), Dimension(40, 40), Dimension(50, 44)),
                nested,
                "the nested scope is as large as what each inset leaves",
            )
        }

    @Test
    fun insetLeavingANegativeWidthIsRejected() =
        runComposeSwingTest {
            assertFailsWith<IllegalArgumentException> { captureEach(60, 60, { inset(31f, 0f, 30f, 0f) {} }) }
        }

    @Test
    fun insetLeavingANegativeHeightIsRejected() =
        runComposeSwingTest {
            assertFailsWith<IllegalArgumentException> { captureEach(60, 60, { inset(0f, 31f, 0f, 30f) {} }) }
        }

    @Test
    fun insetLeavingAZeroSizeIsAllowed() =
        runComposeSwingTest {
            val nested = mutableListOf<Dimension>()

            captureEach(
                60,
                60,
                { inset(30f, 0f, 30f, 0f) { nested += size } },
                { inset(0f, 30f, 0f, 30f) { nested += size } },
            )

            assertEquals(listOf(Dimension(0, 60), Dimension(60, 0)), nested, "each inset leaves an empty scope")
        }

    @Test
    fun defaultPivotIsTheExactMiddleOfAnOddSizedSurface() =
        runComposeSwingTest {
            val images =
                captureEach(
                    41,
                    41,
                    { scale(-1f, 1f) { drawRect(Color.RED, x = 0f, y = 0f, width = 10f, height = 41f) } },
                    { drawRect(Color.RED, x = 31f, y = 0f, width = 10f, height = 41f) },
                    { rotate(180f) { drawRect(Color.RED, x = 0f, y = 0f, width = 10f, height = 10f) } },
                    { drawRect(Color.RED, x = 31f, y = 31f, width = 10f, height = 10f) },
                )

            images.chunked(2).forEach { (transformed, reference) -> assertImagesPixelPerfect(reference, transformed) }
        }

    @Test
    fun rotateAndScaleTurnClockwiseAndStretchAboutThePivot() =
        runComposeSwingTest {
            val images =
                captureEach(
                    40,
                    40,
                    {
                        rotate(
                            90f,
                            pivotX = 10f,
                            pivotY = 10f,
                        ) { drawRect(Color.RED, x = 10f, y = 0f, width = 10f, height = 5f) }
                    },
                    { drawRect(Color.RED, x = 15f, y = 10f, width = 5f, height = 10f) },
                    { rotate(90f) { drawRect(Color.RED, x = 20f, y = 0f, width = 10f, height = 5f) } },
                    { drawRect(Color.RED, x = 35f, y = 20f, width = 5f, height = 10f) },
                    { scale(-1f, 1f) { drawRect(Color.RED, x = 0f, y = 0f, width = 10f, height = 5f) } },
                    { drawRect(Color.RED, x = 30f, y = 0f, width = 10f, height = 5f) },
                    {
                        scale(
                            2f,
                            2f,
                            pivotX = 10f,
                            pivotY = 10f,
                        ) { drawRect(Color.RED, x = 10f, y = 10f, width = 5f, height = 5f) }
                    },
                    { drawRect(Color.RED, x = 10f, y = 10f, width = 10f, height = 10f) },
                    { scale(0.5f, pivotX = 40f, pivotY = 40f) { drawRect(Color.RED) } },
                    { drawRect(Color.RED, x = 20f, y = 20f, width = 20f, height = 20f) },
                )

            images.chunked(2).forEach { (transformed, reference) -> assertImagesPixelPerfect(reference, transformed) }
        }

    @Test
    fun clipsLimitDrawingToTheirArea() =
        runComposeSwingTest {
            val corner =
                Path2D.Float().apply {
                    moveTo(2f, 2f)
                    lineTo(30f, 2f)
                    lineTo(30f, 8f)
                    lineTo(8f, 8f)
                    lineTo(8f, 30f)
                    lineTo(2f, 30f)
                    closePath()
                }

            val images =
                captureEach(
                    40,
                    40,
                    { clipPath(corner) { drawRect(Color.RED) } },
                    { drawPath(corner, Color.RED) },
                    { clipRect(4f, 6f, 10f, 12f) { drawRect(Color.RED) } },
                    { drawRect(Color.RED, x = 4f, y = 6f, width = 10f, height = 12f) },
                    { clipRect(Point(4, 24), Dimension(8, 6)) { drawRect(Color.RED) } },
                    { drawRect(Color.RED, x = 4f, y = 24f, width = 8f, height = 6f) },
                )

            images.chunked(2).forEach { (clipped, reference) -> assertImagesPixelPerfect(reference, clipped) }
        }

    @Test
    fun aNestedBlockLeavesTheEnclosingClipAsItFoundIt() =
        runComposeSwingTest {
            val nestedBlocks: List<DrawScope.() -> Unit> =
                listOf(
                    { withTransform({ rotate(17f, 3f, 5f) }) { clipRect(1f, 2f, 20f, 9f) { drawRect(Color.RED) } } },
                    { inset(3f, 4f, 5f, 6f) { drawRect(Color.RED) } },
                )
            val clips = mutableListOf<Pair<Area, Area>>()

            captureEach(40, 40, {
                rotate(30f) {
                    scale(1f / 3f) {
                        clipRect(2f, 3f, 50f, 40f) {
                            for (block in nestedBlocks) {
                                val before = Area(graphics.clip)
                                block()
                                clips += before to Area(graphics.clip)
                            }
                        }
                    }
                }
            })

            assertEquals(nestedBlocks.size, clips.size, "every nested block ran")
            clips.forEachIndexed { index, (before, after) ->
                assertTrue(before.isSameAs(after), "nested block $index leaves the clip it found")
            }
        }
}

/** Whether this area covers exactly what [other] covers. */
private fun Area.isSameAs(other: Area): Boolean = Area(this).apply { exclusiveOr(other) }.isEmpty
