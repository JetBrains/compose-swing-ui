package org.jetbrains.compose.swing.samples.widgets.custom

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Rectangle
import java.awt.geom.Point2D
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertNotEquals

/** The placement card's unclipped layer paints the canvas where it overflows the layer's box, however it got there. */
class PlacementOverflowTest {
    // The default, unclipped state is pinned by placementClipCutsOverflowAgainstItsVisibleBoundary.
    @Test
    fun unclippedPlacementPaintsTheScaledOverflowOnEveryScaleChange() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            assertOverflowAcross(
                PLACEMENT_SCALE_TAG to 120,
                PLACEMENT_SCALE_TAG to 140,
                PLACEMENT_SCALE_TAG to 110,
                PLACEMENT_SCALE_TAG to 90,
                PLACEMENT_SCALE_TAG to 60,
                PLACEMENT_SCALE_TAG to 100,
                PLACEMENT_SCALE_TAG to 130,
            )
        }

    @Test
    fun unclippedPlacementPaintsTheScaledOverflowAfterARotation() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            assertOverflowAcross(
                PLACEMENT_ROTATION_TAG to 20,
                PLACEMENT_SCALE_TAG to 120,
                PLACEMENT_ROTATION_TAG to 0,
                PLACEMENT_SCALE_TAG to 130,
                PLACEMENT_SCALE_TAG to 90,
                PLACEMENT_SCALE_TAG to 120,
            )
        }

    @Test
    fun unclippedPlacementPaintsTheScaledOverflowAfterClipIsTurnedOff() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            onNodeWithText("Clip to dashed boundary").performClick()
            onNodeWithText("Clip to dashed boundary").performClick()
            assertPlacementOverflowPainted("clip on and off")
            assertOverflowAcross(
                PLACEMENT_SCALE_TAG to 120,
                PLACEMENT_SCALE_TAG to 140,
                PLACEMENT_SCALE_TAG to 100,
                PLACEMENT_SCALE_TAG to 130,
            )
        }

    @Test
    fun unclippedPlacementPaintsTheScaledOverflowAfterClippingAScale() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            setPlacementSlider(PLACEMENT_SCALE_TAG, 120)
            onNodeWithText("Clip to dashed boundary").performClick()
            onNodeWithText("Clip to dashed boundary").performClick()
            assertPlacementOverflowPainted("scale 120, clip on and off")
            assertOverflowAcross(
                PLACEMENT_SCALE_TAG to 130,
                PLACEMENT_SCALE_TAG to 100,
                PLACEMENT_SCALE_TAG to 120,
            )
        }

    @Test
    fun unclippedPlacementPaintsTheScaledOverflowAroundAMovedPivot() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            assertOverflowAcross(
                PLACEMENT_PIVOT_TAG to 0,
                PLACEMENT_SCALE_TAG to 120,
                PLACEMENT_PIVOT_TAG to 100,
                PLACEMENT_SCALE_TAG to 130,
            )
        }

    @Test
    fun unclippedPlacementPaintsTheScaledOverflowWhileTheScaleIsDragged() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val slider = onNodeWithTag(PLACEMENT_SCALE_TAG).fetch<JSlider>()
            slider.valueIsAdjusting = true
            for (scale in (100..140 step 5) + (135 downTo 60 step 5) + (65..125 step 5)) {
                slider.value = scale
                awaitIdle()
                assertPlacementOverflowPainted("dragging to $scale")
            }
            slider.valueIsAdjusting = false
            awaitIdle()
            assertPlacementOverflowPainted("released at 125")
        }
}

private suspend fun ComposeSwingTest.setPlacementSlider(
    tag: String,
    value: Int,
) {
    onNodeWithTag(tag).fetch<JSlider>().value = value
    awaitIdle()
}

/** Sets each slider in turn and asserts the overflow paints after it, so a regression names the failing step. */
private suspend fun ComposeSwingTest.assertOverflowAcross(vararg steps: Pair<String, Int>) {
    for ((tag, value) in steps) {
        setPlacementSlider(tag, value)
        assertPlacementOverflowPainted("$tag $value")
    }
}

/**
 * Asserts that the stage shows the placement canvas, rather than the background its corner shows, 4 pixels inside
 * the canvas's top and bottom edges, where the transform the sliders set takes them. The canvas overflows the layer's
 * box, so with the clip off these points lie outside the box at every scale above 85%.
 */
private fun ComposeSwingTest.assertPlacementOverflowPainted(step: String) {
    val layer = onNodeWithTag(PLACEMENT_LAYER_TAG).fetch<JComponent>()
    val local = (layer as Decoratable).decoration.localLayoutBounds(layer)
    val box = placementLayerBox()
    val scale = onNodeWithTag(PLACEMENT_SCALE_TAG).fetch<JSlider>().value / 100f
    val transform =
        TransformOrigin(onNodeWithTag(PLACEMENT_PIVOT_TAG).fetch<JSlider>().value / 100f, 0.5f).createTransform(
            box,
            scale,
            scale,
            onNodeWithTag(PLACEMENT_ROTATION_TAG).fetch<JSlider>().value.toFloat(),
        )
    val center = placementCanvasCenter()
    val image = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
    val paintBounds = Rectangle(-local.x, -local.y, layer.width, layer.height)
    for (offsetY in listOf(-48, 48)) {
        val probe = transform.transform(Point2D.Double(center.x - 40.0, center.y + offsetY.toDouble()), null)
        assertNotEquals(
            image.getRGB(0, 0),
            image.getRGB(probe.x.toInt(), probe.y.toInt()),
            "$step: the canvas is cut at ${probe.x.toInt()}, ${probe.y.toInt()}; " +
                "layer paint bounds $paintBounds around its ${local.width}x${local.height} box",
        )
    }
}
