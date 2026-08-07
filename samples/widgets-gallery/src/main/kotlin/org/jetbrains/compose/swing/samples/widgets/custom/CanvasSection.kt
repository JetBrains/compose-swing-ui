package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Canvas
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D

// Canvas custom drawing through the raw Graphics2D pipeline - none of it a built-in widget, all of it
// hand-rendered. The slider values are read directly inside onDraw: Canvas observes the snapshot state
// touched while drawing, so moving a slider repaints the surface automatically, with no read hoisted
// into the composition or captured into the lambda.
@Composable
internal fun CanvasSection() {
    SectionColumn {
        SectionHeading("Canvas")
        ExampleCard("Canvas (raw Graphics2D)") {
            WrappedCaption(
                "Everything here is hand-drawn through the raw Graphics2D: a diagonal gradient fill, a ring " +
                    "of rotated translucent petals built from cubic Bézier curves, and a stroked arc gauge. " +
                    "Both sliders are read at paint time, so the surface re-renders itself as you drag them.",
            )

            var petals by remember { mutableIntStateOf(8) }
            var sweep by remember { mutableIntStateOf(70) }
            FlowPanel {
                Label("Petals: $petals")
                Slider(value = petals, onValueChange = { petals = it }, min = 3, max = 16)
                Label("Sweep: $sweep%")
                Slider(value = sweep, onValueChange = { sweep = it }, min = 0, max = 100)
            }

            Canvas(
                modifier =
                    SwingModifier
                        .testTag(CANVAS_TAG)
                        .preferredSize(Dimension(260, 260))
                        .lineBorder(Color.GRAY),
            ) { g, width, height ->
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

                paintGradientBackdrop(g, width, height)
                paintPetalRing(g, width, height, petals)
                paintArcGauge(g, width, height, sweep)
                paintCenterReadout(g, width, height, sweep)
            }
        }
    }
}

private fun paintGradientBackdrop(
    g: Graphics2D,
    width: Int,
    height: Int,
) {
    g.paint = GradientPaint(0f, 0f, DEEP_BLUE, width.toFloat(), height.toFloat(), BRIGHT_BLUE)
    g.fillRect(0, 0, width, height)
}

private fun paintPetalRing(
    g: Graphics2D,
    width: Int,
    height: Int,
    count: Int,
) {
    val centerX = width / 2.0
    val centerY = height / 2.0
    val reach = minOf(width, height) / 2.0 * PETAL_REACH_FRACTION

    val waist = reach * PETAL_WAIST_FRACTION
    val shoulder = reach * PETAL_SHOULDER_FRACTION
    val petal =
        Path2D.Double().apply {
            moveTo(0.0, 0.0)
            curveTo(waist, -reach * PETAL_NECK_FRACTION, waist, -shoulder, 0.0, -reach)
            curveTo(-waist, -shoulder, -waist, -reach * PETAL_NECK_FRACTION, 0.0, 0.0)
            closePath()
        }

    val fill = whiteAlpha(PETAL_ALPHA)
    val stroke = whiteAlpha(PETAL_STROKE_ALPHA)
    g.stroke = BasicStroke(PETAL_STROKE_WIDTH)
    repeat(count) { index ->
        val saved = g.transform
        g.translate(centerX, centerY)
        g.rotate(FULL_TURN_RADIANS * index / count)
        g.color = fill
        g.fill(petal)
        g.color = stroke
        g.draw(petal)
        g.transform = saved
    }
}

private fun whiteAlpha(alpha: Int): Color = Color(WHITE_RGB, WHITE_RGB, WHITE_RGB, alpha)

private fun paintArcGauge(
    g: Graphics2D,
    width: Int,
    height: Int,
    percent: Int,
) {
    val inset = minOf(width, height) * GAUGE_INSET_FRACTION
    val size = minOf(width, height) - inset - inset
    val x = (width - size) / 2.0
    val y = (height - size) / 2.0

    g.stroke = BasicStroke(GAUGE_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.color = whiteAlpha(GAUGE_TRACK_ALPHA)
    g.draw(Arc2D.Double(x, y, size, size, 0.0, FULL_CIRCLE, Arc2D.OPEN))

    val extent = -FULL_CIRCLE * percent / PERCENT_MAX
    g.color = AMBER
    g.draw(Arc2D.Double(x, y, size, size, GAUGE_START_ANGLE, extent, Arc2D.OPEN))
}

private fun paintCenterReadout(
    g: Graphics2D,
    width: Int,
    height: Int,
    percent: Int,
) {
    val discSize = minOf(width, height) * DISC_FRACTION
    val discX = (width - discSize) / 2.0
    val discY = (height - discSize) / 2.0
    g.color = DEEP_BLUE
    g.fill(Ellipse2D.Double(discX, discY, discSize, discSize))

    val text = "$percent%"
    g.font = Font(Font.SANS_SERIF, Font.BOLD, READOUT_FONT_SIZE)
    g.color = Color.WHITE
    val metrics = g.fontMetrics
    val textX = (width - metrics.stringWidth(text)) / 2
    val textY = height / 2 + metrics.ascent / 2 - metrics.descent / 2
    g.drawString(text, textX, textY)
}

internal const val CANVAS_TAG = "canvas-section-surface"

private val DEEP_BLUE = Color(0x0D, 0x47, 0xA1)
private val BRIGHT_BLUE = Color(0x42, 0x85, 0xF4)
private val AMBER = Color(0xFF, 0xC1, 0x07)
private const val WHITE_RGB = 255

private const val PETAL_REACH_FRACTION = 0.92
private const val PETAL_WAIST_FRACTION = 0.45
private const val PETAL_NECK_FRACTION = 0.30
private const val PETAL_SHOULDER_FRACTION = 0.85
private const val PETAL_ALPHA = 60
private const val PETAL_STROKE_ALPHA = 120
private const val PETAL_STROKE_WIDTH = 1.5f
private val FULL_TURN_RADIANS = 2 * Math.PI
private const val GAUGE_INSET_FRACTION = 0.12
private const val GAUGE_STROKE = 8f
private const val GAUGE_TRACK_ALPHA = 70
private const val GAUGE_START_ANGLE = 90.0
private const val FULL_CIRCLE = 360.0
private const val PERCENT_MAX = 100
private const val DISC_FRACTION = 0.34
private const val READOUT_FONT_SIZE = 22
