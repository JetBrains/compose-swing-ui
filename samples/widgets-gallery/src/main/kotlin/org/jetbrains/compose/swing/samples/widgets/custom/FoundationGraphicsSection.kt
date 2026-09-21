package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.BlurEffect
import org.jetbrains.compose.swing.foundation.graphics.ImageLayer
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.drawWithContent
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawStyle
import org.jetbrains.compose.swing.foundation.graphics.drawscope.measureText
import org.jetbrains.compose.swing.foundation.graphics.drawscope.record
import org.jetbrains.compose.swing.foundation.graphics.drawscope.rotate
import org.jetbrains.compose.swing.foundation.graphics.drawscope.translate
import org.jetbrains.compose.swing.foundation.graphics.rememberImageLayer
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Point
import java.awt.RadialGradientPaint
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

@Preview
@Composable
internal fun FoundationGraphicsSection() {
    SectionColumn {
        SectionHeading("Foundation graphics")
        CanvasCard()
        DrawModifiersCard()
        FoundationDecorationCard()
        FoundationShadowStylesCard()
        FoundationPlacementCard()
    }
}

@Composable
private fun ColumnScope.CanvasCard() {
    var values by remember { mutableStateOf(CanvasValues()) }

    ExampleCard("Canvas / DrawScope") {
        WrappedCaption(
            "Paint with Java2D through DrawScope. Each slider changes one part of the illustration.",
            width = 280,
        )
        Canvas(
            modifier =
                SwingModifier
                    .testTag(CANVAS_TAG)
                    .fillMaxWidth()
                    .preferredSize(Dimension(320, 240))
                    .lineBorder(Color(0x42, 0x85, 0xF4)),
        ) {
            drawRect(
                GradientPaint(
                    0f,
                    0f,
                    Color(0x0D, 0x47, 0xA1),
                    width.toFloat(),
                    height.toFloat(),
                    Color(0x42, 0x85, 0xF4),
                ),
            )
            paintPetalRing(values.petals, values.rotation)
            paintArcGauge(values.sweep)
            paintCenterReadout(values.sweep)
        }
        SliderRow {
            GraphicsSlider(
                "Petals",
                values.petals,
                unit = "",
                range = 3..16,
                tag = CANVAS_PETALS_TAG,
            ) {
                values = values.copy(petals = it)
            }
            GraphicsSlider(
                "Sweep",
                values.sweep,
                unit = "%",
                range = 0..100,
                tag = CANVAS_SWEEP_TAG,
            ) {
                values = values.copy(sweep = it)
            }
        }
        SliderRow {
            GraphicsSlider("Rotation", values.rotation, unit = "°", range = 0..360, tag = CANVAS_ROTATION_TAG) {
                values = values.copy(rotation = it)
            }
            Button("Reset canvas", onClick = { values = CanvasValues() })
        }
    }
}

private data class CanvasValues(
    val petals: Int = 8,
    val sweep: Int = 70,
    val rotation: Int = 0,
)

@Composable
private fun ColumnScope.DrawModifiersCard() {
    ExampleCard("drawBehind / drawWithContent") {
        var sweep by remember { mutableIntStateOf(50) }
        var overlayOnTop by remember { mutableStateOf(true) }
        var spotlight by remember { mutableStateOf<Point?>(null) }
        val blurred = rememberImageLayer()
        val sharp = rememberImageLayer()
        WrappedCaption(
            "The blue background is drawBehind. The amber CONTENT tile and pink OVERLAY overlap: " +
                "swap their order with drawContent(). Move the pointer over the canvas: an outer " +
                "drawWithContent records everything inside it into layers, then blurs and dims it, except " +
                "around the pointer.",
            width = 280,
        )
        Canvas(
            modifier =
                SwingModifier
                    .testTag(DRAW_MODIFIERS_TAG)
                    .fillMaxWidth()
                    .preferredSize(Dimension(320, 160))
                    .cursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
                    // A drag keeps delivering events past the canvas edge, unlike a plain hover exit, so a
                    // dragged-out point must be checked against the canvas bounds rather than just cleared on exit.
                    .mouseMotionListener { spotlight = it.point.takeIf(it.component::contains) }
                    .onHover(onExit = { spotlight = null })
                    .drawWithContent {
                        drawContent()
                        spotlight?.let { paintSpotlight(it, blurred, sharp) }
                    }.drawBehind {
                        drawRect(Color(0xE3, 0xF2, 0xFD))
                        drawRect(
                            Color(0x90, 0xCA, 0xF9),
                            width = width.toFloat() * sweep / 100,
                            height = height.toFloat(),
                        )
                    }.drawWithContent {
                        if (overlayOnTop) drawContent()
                        paintOrderTile(overlay = true)
                        if (!overlayOnTop) drawContent()
                    },
        ) {
            paintOrderTile(overlay = false)
        }
        SliderRow {
            GraphicsSlider("Sweep", sweep, unit = "%", range = 0..100, tag = DRAW_SWEEP_TAG) { sweep = it }
            CheckBox("Overlay on top", overlayOnTop, { overlayOnTop = it })
        }
    }
}

/**
 * Covers the content with a blurred and darkened copy of it, except a soft-edged circle around [point] that stays
 * sharp: [blurred] records the content and draws it through a blur, and [sharp] records it once more and masks it
 * with a radial gradient before it is drawn on top.
 *
 * [blurred] records the whole content even when only part of it is repainted, so the blur reads the same
 * neighboring pixels a full repaint does.
 */
private fun ContentDrawScope.paintSpotlight(
    point: Point,
    blurred: ImageLayer,
    sharp: ImageLayer,
) {
    val clip = graphics.clip
    graphics.setClip(0, 0, width, height)
    record(blurred) { this@paintSpotlight.drawContent() }
    graphics.clip = clip
    blurred.renderEffect = BlurEffect(radiusX = 6f)
    blurred.draw(graphics)
    val backdrop = Color(0x0D, 0x1B, 0x2A, 150)
    drawRect(backdrop)
    record(sharp) {
        this@paintSpotlight.drawContent()
        graphics.composite = AlphaComposite.DstIn
        val mask = RadialGradientPaint(point, 70f, floatArrayOf(0.6f, 1f), arrayOf(Color.BLACK, Color(0, true)))
        drawRect(mask)
    }
    sharp.draw(graphics)
}

internal const val DRAW_MODIFIERS_TAG = "foundation-draw-modifiers"
internal const val DRAW_SWEEP_TAG = "foundation-draw-sweep"
internal const val CANVAS_TAG = "foundation-canvas"
internal const val CANVAS_PETALS_TAG = "foundation-canvas-petals"
internal const val CANVAS_SWEEP_TAG = "foundation-canvas-sweep"
internal const val CANVAS_ROTATION_TAG = "foundation-canvas-rotation"

/**
 * Where [paintOrderTile] paints the OVERLAY tile, or the CONTENT tile, on a canvas [width] wide. Fixed geometry makes
 * the overlapping region easy to compare when the order changes.
 */
internal fun orderTileBounds(
    width: Int,
    overlay: Boolean,
): Rectangle2D.Float {
    val x = width / 2f - if (overlay) 52f else 112f
    val y = if (overlay) 52f else 28f
    val tileWidth = 164f
    val tileHeight = 84f
    return Rectangle2D.Float(x, y, tileWidth, tileHeight)
}

private fun DrawScope.paintOrderTile(overlay: Boolean) {
    val tile = orderTileBounds(width, overlay)
    val tileColor = if (overlay) Color(0xC2, 0x18, 0x5B) else Color(0xFF, 0xC1, 0x07)
    drawRect(tileColor, tile.x, tile.y, width = tile.width, height = tile.height)
    val text = measureText(if (overlay) "OVERLAY" else "CONTENT", Font(Font.SANS_SERIF, Font.BOLD, 17))
    val textColor = if (overlay) Color.WHITE else Color(0x0D, 0x47, 0xA1)
    drawText(text, x = tile.x + 18f, y = tile.y + 22f, paint = textColor)
}

private fun DrawScope.paintPetalRing(
    count: Int,
    rotation: Int,
) {
    val reach = minOf(width, height) / 2.0f * 0.92f
    val waist = reach * 0.45f
    val shoulder = reach * 0.85f
    val petal =
        Path2D.Float().apply {
            moveTo(0.0f, 0.0f)
            curveTo(waist, -reach * 0.30f, waist, -shoulder, 0.0f, -reach)
            curveTo(-waist, -shoulder, -waist, -reach * 0.30f, 0.0f, 0.0f)
            closePath()
        }
    val fill = Color(255, 255, 255, 60)
    val stroke = Color(255, 255, 255, 120)
    val angleDegrees = 360f / count
    repeat(count) { index ->
        translate(center.x.toFloat(), center.y.toFloat()) {
            rotate(degrees = rotation.toFloat() + angleDegrees * index, pivotX = 0f, pivotY = 0f) {
                drawPath(petal, fill)
                drawPath(petal, stroke, style = DrawStyle.Stroke(width = 1.5f))
            }
        }
    }
}

private fun DrawScope.paintArcGauge(percent: Int) {
    val inset = minOf(width, height) * 0.12f
    val size = minOf(width, height) - inset - inset
    val x = (width - size) / 2.0f
    val y = (height - size) / 2.0f
    val stroke = DrawStyle.Stroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val trackColor = Color(255, 255, 255, 70)
    drawArc(
        paint = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        x = x,
        y = y,
        width = size,
        height = size,
        style = stroke,
    )
    val fillColor = Color(0xFF, 0xC1, 0x07)
    drawArc(
        paint = fillColor,
        startAngle = -90f,
        sweepAngle = 360f * percent / 100,
        useCenter = false,
        x = x,
        y = y,
        width = size,
        height = size,
        style = stroke,
    )
}

private fun DrawScope.paintCenterReadout(percent: Int) {
    val discRadius = minOf(width, height) * 0.34f / 2.0f
    val discColor = Color(0x0D, 0x47, 0xA1)
    drawCircle(discColor, radius = discRadius)
    val text = "$percent%"
    val layout = measureText(text, Font(Font.SANS_SERIF, Font.BOLD, 22))
    drawText(
        layout,
        x = center.x.toFloat() - layout.advance / 2f,
        y = center.y.toFloat() - (layout.ascent + layout.descent) / 2f,
        paint = Color.WHITE,
    )
}
