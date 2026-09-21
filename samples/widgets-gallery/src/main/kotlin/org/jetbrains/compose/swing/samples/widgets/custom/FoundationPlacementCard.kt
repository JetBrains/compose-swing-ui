package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.drawWithContent
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawStyle
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNode
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNodeElement
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.PlacementLayerScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.requiredSize
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.geom.Rectangle2D

@Composable
internal fun ColumnScope.FoundationPlacementCard() {
    var values by remember { mutableStateOf(PlacementValues()) }
    ExampleCard("placeWithLayer / TransformOrigin") {
        WrappedCaption(
            "Transform a complete decorated scene. The dashed box marks the layer; the white cross marks its pivot. " +
                "The canvas overflows the box so clipping is visible even at 100% scale. Clicks, the pointer and " +
                "text selection follow the scale and rotation.",
            width = 280,
        )
        PlacementPreview(values)
        SliderRow {
            GraphicsSlider("Scale", values.scale, unit = "%", range = 60..140, tag = PLACEMENT_SCALE_TAG) {
                values = values.copy(scale = it)
            }
            GraphicsSlider("Rotation", values.rotation, unit = "°", range = -180..180, tag = PLACEMENT_ROTATION_TAG) {
                values = values.copy(rotation = it)
            }
        }
        SliderRow {
            GraphicsSlider("Opacity", values.opacity, unit = "%", range = 20..100, tag = PLACEMENT_OPACITY_TAG) {
                values = values.copy(opacity = it)
            }
            GraphicsSlider("Pivot X", values.pivot, unit = "%", range = 0..100, tag = PLACEMENT_PIVOT_TAG) {
                values = values.copy(pivot = it)
            }
        }
        SliderRow {
            CheckBox("Clip to dashed boundary", values.clip, { values = values.copy(clip = it) })
            Button("Reset placement", onClick = { values = PlacementValues() })
        }
    }
}

@Composable
private fun ColumnScope.PlacementPreview(values: PlacementValues) {
    val currentValues = rememberUpdatedState(values)
    // The sliders report percents; the layer takes fractions of 1.
    val layerBlock =
        remember<PlacementLayerScope.() -> Unit> {
            {
                val current = currentValues.value
                alpha = current.opacity / 100f
                scaleX = current.scale / 100f
                scaleY = current.scale / 100f
                rotationZ = current.rotation.toFloat()
                transformOrigin = current.transformOrigin
                clip = current.clip
            }
        }
    Box(
        modifier =
            SwingModifier
                .fillMaxWidth()
                .height(300)
                .background(Color(0xED, 0xF1, 0xF7), RectangleShape)
                .testTag(PLACEMENT_STAGE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                SwingModifier
                    .testTag(PLACEMENT_LAYER_TAG)
                    .size(340, 88)
                    .drawWithContent {
                        drawContent()
                        paintPlacementGuide(values)
                    }.then(PlacementLayerElement(layerBlock)),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12), verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag(PLACEMENT_CHILD_TAG)
                            .requiredSize(180, 104)
                            .shadow(12, Color(0, 0, 0, 110), 4, 6)
                            .background(
                                Brush.verticalGradient(0f to Color(0x42, 0x85, 0xF4), 1f to Color(0x0D, 0x47, 0xA1)),
                            ),
                ) {
                    drawCircle(Color(0xFF, 0xC1, 0x07), radius = 24f)
                    drawLine(Color.WHITE, 16f, height / 2f, width - 16f, height / 2f)
                }
                PlacementControls()
            }
        }
    }
}

@Composable
private fun PlacementControls() {
    var text by remember { mutableStateOf("Select this text") }
    var presses by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(6)) {
        TextField(text, { text = it }, SwingModifier.testTag(PLACEMENT_FIELD_TAG), columns = 10)
        val label = if (presses == 0) "Press me" else "Pressed $presses"
        Button(label, { presses++ }, SwingModifier.testTag(PLACEMENT_BUTTON_TAG))
    }
}

private data class PlacementValues(
    val opacity: Int = 100,
    val scale: Int = 100,
    val rotation: Int = 0,
    val pivot: Int = 50,
    val clip: Boolean = false,
) {
    val transformOrigin: TransformOrigin get() = TransformOrigin(pivot / 100f, 0.5f)
}

internal data class PlacementLayerElement(
    private val layerBlock: PlacementLayerScope.() -> Unit,
) : LayoutModifierNodeElement<PlacementLayerNode>() {
    override fun create(): PlacementLayerNode = PlacementLayerNode(layerBlock)

    override fun update(node: PlacementLayerNode) {
        node.layerBlock = layerBlock
    }
}

internal class PlacementLayerNode(
    var layerBlock: PlacementLayerScope.() -> Unit,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }
}

private val GuideDash = floatArrayOf(6f, 4f)

// The guides track the transformed clipping window but remain legible when the layer fades.
private fun DrawScope.paintPlacementGuide(values: PlacementValues) {
    val scale = values.scale / 100f
    val pivotX = width * values.pivot / 100f
    val pivotY = height / 2f
    val ink = Color(0x26, 0x36, 0x50)
    val box = Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat())
    val matrix = values.transformOrigin.createTransform(box, scale, scale, values.rotation.toFloat())
    // A stroke on the box's far edges would fall just outside the component.
    drawPath(
        matrix.createTransformedShape(Rectangle2D.Float(0f, 0f, width - 1f, height - 1f)),
        ink,
        style = DrawStyle.Stroke(1f, miterLimit = 10f, dash = GuideDash),
    )
    drawCircle(ink, radius = 8f, centerX = pivotX, centerY = pivotY)
    drawCircle(Color.WHITE, radius = 8f, centerX = pivotX, centerY = pivotY, style = DrawStyle.Stroke(1f))
    drawLine(Color.WHITE, x1 = pivotX - 5f, y1 = pivotY, x2 = pivotX + 5f, y2 = pivotY)
    drawLine(Color.WHITE, x1 = pivotX, y1 = pivotY - 5f, x2 = pivotX, y2 = pivotY + 5f)
}

internal const val PLACEMENT_OPACITY_TAG = "foundation-placement-opacity"
internal const val PLACEMENT_SCALE_TAG = "foundation-placement-scale"
internal const val PLACEMENT_ROTATION_TAG = "foundation-placement-rotation"
internal const val PLACEMENT_PIVOT_TAG = "foundation-placement-pivot"
internal const val PLACEMENT_LAYER_TAG = "foundation-placement-layer"
internal const val PLACEMENT_CHILD_TAG = "foundation-placement-child"
internal const val PLACEMENT_STAGE_TAG = "foundation-placement-stage"
internal const val PLACEMENT_FIELD_TAG = "foundation-placement-field"
internal const val PLACEMENT_BUTTON_TAG = "foundation-placement-button"
