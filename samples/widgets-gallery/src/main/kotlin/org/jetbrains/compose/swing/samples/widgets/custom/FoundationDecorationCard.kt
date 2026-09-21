package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.CircleShape
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.Shape
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.blur
import org.jetbrains.compose.swing.foundation.graphics.border
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.BoxScope
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.modifier.listener.keyListener
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.window.Dialog
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint.CycleMethod
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import javax.swing.Icon
import javax.swing.JColorChooser
import javax.swing.JPanel
import javax.swing.colorchooser.ColorSelectionModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun ColumnScope.FoundationDecorationCard() {
    var values by remember { mutableStateOf(DecorationValues()) }
    var editedStop by remember { mutableStateOf<GradientStop?>(null) }
    val shape = values.selectedShape.shape(values.starPoints)

    ExampleCard("Brush / Shape / decoration modifiers") {
        WrappedCaption(
            "One gradient fills the surface, strokes its rings and paints every brush tile below. " +
                "Click a tile to fill the surface with its brush, or a swatch to pick a stop's color. " +
                "Each extra ring is a border, then a padding.",
            width = 280,
        )
        Box(
            modifier =
                SwingModifier
                    .fillMaxWidth()
                    .height(236)
                    .background(Brush.verticalGradient(0f to Color(0xF7, 0xF9, 0xFC), 1f to Color(0xDC, 0xE3, 0xEE)))
                    .drawBehind {
                        for (x in 8 until width step 16) {
                            for (y in 8 until height step 16) {
                                drawCircle(Color(0x26, 0x36, 0x50, 40), 1f, x.toFloat(), y.toFloat())
                            }
                        }
                    }.testTag(DECORATION_STAGE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            DecorationSurface(values, shape)
        }
        BrushTiles(values) { style -> values = values.copy(brushStyle = style) }
        DecorationControls(values, onEditStop = { editedStop = it }) { update -> values = values.update() }
        Button("Reset decoration", onClick = { values = DecorationValues() })
        editedStop?.let { stop ->
            Dialog(onCloseRequest = { editedStop = null }, title = "${stop.name} color") {
                GradientColorChooser(values.stops.color(stop)) { color ->
                    values = values.copy(stops = values.stops.withColor(stop, color))
                }
            }
        }
    }
}

// The chooser reports every change of its selection, so dragging in it recolors the gradient live.
@Composable
internal fun GradientColorChooser(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    SwingNode(
        factory = { JColorChooser(color).apply { previewPanel = JPanel() } },
        modifier =
            SwingModifier
                .testTag(GRADIENT_CHOOSER_TAG)
                .changeListener { onColorChange((it.source as ColorSelectionModel).selectedColor) },
        update = { set(color) { this.color = it } },
    )
}

@Composable
private fun BoxScope.DecorationSurface(
    values: DecorationValues,
    shape: Shape,
) {
    val ringBrush = Brush.verticalGradient(0f to values.stops.end, 1f to values.stops.start)
    Canvas(
        modifier =
            SwingModifier
                .testTag(DECORATION_TAG)
                .size(180)
                .alpha(values.opacity / 100f)
                .let { if (values.blurEnabled) it.blur(values.blurRadius) else it }
                .then(
                    if (values.shadowEnabled) {
                        SwingModifier.shadow(
                            values.shadowRadius,
                            Color(0, 0, 0, 100),
                            values.shadowOffsetX,
                            values.shadowOffsetY,
                        )
                    } else {
                        SwingModifier
                    },
                ).let { if (values.paddingEnabled) it.padding(8) else it }
                // Each ring outside the surface is a border followed by a padding, so the gap between rings
                // stays clear.
                .let { chain ->
                    (1 until values.rings).fold(chain) { ring, _ ->
                        ring.border(values.borderWidth, ringBrush, shape).padding(values.borderWidth + 5)
                    }
                }.clip(shape, antialias = true)
                .background(StyledBrush(values.brushStyle, values.stops, values.middlePosition))
                .border(values.borderWidth, ringBrush, shape),
    ) {
        // A soft white highlight in the upper left, as if lit from there.
        drawRect(
            RadialGradientPaint(
                Point2D.Float(width * 0.3f, height * 0.25f),
                maxOf(width, height) * 0.7f,
                floatArrayOf(0f, 1f),
                arrayOf(Color(255, 255, 255, 150), Color(255, 255, 255, 0)),
            ),
        )
    }
}

private data class GradientStops(
    val start: Color,
    val middle: Color,
    val end: Color,
) {
    fun color(stop: GradientStop): Color =
        when (stop) {
            GradientStop.Start -> start
            GradientStop.Middle -> middle
            GradientStop.End -> end
        }

    fun withColor(
        stop: GradientStop,
        color: Color,
    ): GradientStops =
        when (stop) {
            GradientStop.Start -> copy(start = color)
            GradientStop.Middle -> copy(middle = color)
            GradientStop.End -> copy(end = color)
        }
}

private enum class GradientStop { Start, Middle, End }

private val GRADIENT_PRESETS =
    listOf(
        "Sunset" to GradientStops(Color(0x6A, 0x11, 0xCB), Color(0xFF, 0x4E, 0x8A), Color(0xFF, 0xC1, 0x07)),
        "Ocean" to GradientStops(Color(0x0D, 0x47, 0xA1), Color(0x21, 0x96, 0xF3), Color(0x6D, 0xF5, 0xD5)),
        "Forest" to GradientStops(Color(0x13, 0x4E, 0x5E), Color(0x2E, 0x9D, 0x6A), Color(0xC6, 0xF1, 0x6B)),
        "Mono" to GradientStops(Color(0x26, 0x32, 0x38), Color(0x78, 0x90, 0x9C), Color(0xEC, 0xEF, 0xF1)),
    )

private data class DecorationValues(
    val stops: GradientStops = GRADIENT_PRESETS.first().second,
    val middlePosition: Int = 50,
    val brushStyle: BrushStyle = BrushStyle.Horizontal,
    val selectedShape: DecorationShape = DecorationShape.Rounded,
    val starPoints: Int = 5,
    val rings: Int = 2,
    val opacity: Int = 100,
    val borderWidth: Int = 4,
    val shadowRadius: Int = 10,
    val shadowOffsetX: Int = 4,
    val shadowOffsetY: Int = 6,
    val blurRadius: Int = 4,
    val shadowEnabled: Boolean = true,
    val blurEnabled: Boolean = false,
    val paddingEnabled: Boolean = false,
)

private enum class DecorationShape {
    Rectangle,
    Rounded,
    Circle,
    Star,
    Hexagon,
    Heart,
    ;

    fun shape(starPoints: Int): Shape =
        when (this) {
            Rectangle -> RectangleShape
            Rounded -> RoundedCornerShape(size = 28f)
            Circle -> CircleShape
            Star -> StarShape(starPoints)
            Hexagon -> StarShape(points = 3, innerRatio = 1f)
            Heart -> HeartShape
        }
}

// Alternates outer and inner vertices around the center; an inner ratio of 1 gives a regular polygon.
private data class StarShape(
    val points: Int,
    val innerRatio: Float = 0.5f,
) : Shape {
    override fun outline(
        width: Int,
        height: Int,
    ): Path2D {
        val outer = minOf(width, height) / 2.0
        val path = Path2D.Double()
        for (vertex in 0 until points * 2) {
            val radius = if (vertex % 2 == 0) outer else outer * innerRatio
            val angle = -PI / 2 + PI * vertex / points
            val x = width / 2.0 + radius * cos(angle)
            val y = height / 2.0 + radius * sin(angle)
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.closePath()
        return path
    }
}

// Two cubic lobes meeting at the bottom point, scaled to the bounds.
private object HeartShape : Shape {
    override fun outline(
        width: Int,
        height: Int,
    ): Path2D {
        val w = width.toDouble()
        val h = height.toDouble()
        return Path2D.Double().apply {
            moveTo(w * HEART_TOP.x, h * HEART_TOP.y)
            for ((first, second, end) in HEART_CURVES) {
                curveTo(w * first.x, h * first.y, w * second.x, h * second.y, w * end.x, h * end.y)
            }
            closePath()
        }
    }
}

// The notch at the top, then each cubic segment as two control points and an end point, as fractions of the bounds.
private val HEART_TOP = Point2D.Double(0.5, 0.28)
private val HEART_CURVES =
    arrayOf(
        arrayOf(Point2D.Double(0.35, -0.02), Point2D.Double(0.0, 0.12), Point2D.Double(0.02, 0.38)),
        arrayOf(Point2D.Double(0.05, 0.62), Point2D.Double(0.35, 0.78), Point2D.Double(0.5, 0.98)),
        arrayOf(Point2D.Double(0.65, 0.78), Point2D.Double(0.95, 0.62), Point2D.Double(0.98, 0.38)),
        arrayOf(Point2D.Double(1.0, 0.12), Point2D.Double(0.65, -0.02), HEART_TOP),
    )

private enum class BrushStyle { Horizontal, Vertical, Linear, Radial, Repeat, Reflect }

// Resolves at the size it paints at, so a tile and the surface show one style at their own scale. As a data
// class it compares by value: rebuilt from unchanged stops, it repaints nothing.
private data class StyledBrush(
    val style: BrushStyle,
    val stops: GradientStops,
    val middlePosition: Int,
) : Brush {
    override fun paint(
        width: Int,
        height: Int,
    ): Paint {
        val start = 0f to stops.start
        // The slider keeps the middle stop strictly between the ends, as the Java2D gradients require.
        val middle = middlePosition / 100f to stops.middle
        val end = 1f to stops.end
        val gradient =
            when (style) {
                BrushStyle.Horizontal -> {
                    Brush.horizontalGradient(start, middle, end)
                }

                BrushStyle.Vertical -> {
                    Brush.verticalGradient(start, middle, end)
                }

                BrushStyle.Linear -> {
                    Brush.linearGradient(
                        start,
                        middle,
                        end,
                        start = Point2D.Float(0f, height.toFloat()),
                        end = Point2D.Float(width.toFloat(), 0f),
                    )
                }

                BrushStyle.Radial -> {
                    Brush.radialGradient(start, middle, end)
                }

                BrushStyle.Repeat, BrushStyle.Reflect -> {
                    null
                }
            }
        // The repeating styles cycle 14 x 10 pixels on a 48 x 36 tile, and in the same proportion on the surface.
        return gradient?.paint(width, height) ?: LinearGradientPaint(
            0f,
            0f,
            cycleSpan(of = width, cycleLength = 14, tileLength = 48).coerceAtLeast(1f),
            cycleSpan(of = height, cycleLength = 10, tileLength = 36),
            floatArrayOf(0f, middle.first, 1f),
            arrayOf(stops.start, stops.middle, stops.end),
            if (style == BrushStyle.Repeat) CycleMethod.REPEAT else CycleMethod.REFLECT,
        )
    }
}

// How far a repeat/reflect cycle of [cycleLength] pixels on a [tileLength]-pixel tile reaches [of] a span
// scaled up or down from that tile.
private fun cycleSpan(
    of: Int,
    cycleLength: Int,
    tileLength: Int,
): Float = of * (cycleLength.toFloat() / tileLength)

// Tiles showing each brush style, all built from the same stops; the surface's style wears a ring.
@Composable
private fun ColumnScope.BrushTiles(
    values: DecorationValues,
    onSelect: (BrushStyle) -> Unit,
) {
    Row(
        modifier = SwingModifier.fillMaxWidth().padding(top = 8).testTag(BRUSH_TILES_TAG),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (style in BrushStyle.entries) {
            val name = style.name.lowercase()
            val selected = style == values.brushStyle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier =
                        SwingModifier
                            .size(54, 42)
                            .let {
                                if (selected) it.border(2, Color(0x26, 0x32, 0x38), RoundedCornerShape(11f)) else it
                            }.padding(3)
                            .background(StyledBrush(style, values.stops, values.middlePosition), RoundedCornerShape(8f))
                            .cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                            .mouseListener(onMouseClicked = { onSelect(style) })
                            .focusable(true)
                            .keyListener(
                                onKeyPressed = {
                                    if (it.keyCode == KeyEvent.VK_SPACE || it.keyCode == KeyEvent.VK_ENTER) {
                                        onSelect(style)
                                    }
                                },
                            ).toolTip("Fill the surface with the $name brush")
                            .accessibleName("$name brush")
                            .testTag(BRUSH_TILE_TAG_PREFIX + name),
                ) {}
                Label(name, modifier = SwingModifier.font(Font(Font.SANS_SERIF, Font.PLAIN, 10)))
            }
        }
    }
}

@Composable
private fun ColumnScope.DecorationControls(
    values: DecorationValues,
    onEditStop: (GradientStop) -> Unit,
    onValuesChange: (DecorationValues.() -> DecorationValues) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6), verticalAlignment = Alignment.CenterVertically) {
        Label("Shape:")
        ComboBox(
            items = DecorationShape.entries.map { it.name },
            selectedItem = values.selectedShape.name,
            onSelectionChange = { selected ->
                val shape = DecorationShape.entries.firstOrNull { it.name == selected }
                if (shape != null) onValuesChange { copy(selectedShape = shape) }
            },
            modifier = SwingModifier.testTag(DECORATION_SHAPE_TAG).accessibleName("Shape"),
        )
        Label("Colors:")
        ComboBox(
            items = GRADIENT_PRESETS.map { it.first },
            selectedItem = GRADIENT_PRESETS.firstOrNull { it.second == values.stops }?.first,
            onSelectionChange = { selected ->
                val preset = GRADIENT_PRESETS.firstOrNull { it.first == selected }?.second
                if (preset != null) onValuesChange { copy(stops = preset) }
            },
            modifier = SwingModifier.testTag(GRADIENT_PRESET_TAG).accessibleName("Gradient colors"),
        )
    }
    GradientControls(values.stops, values.middlePosition, onEditStop) { onValuesChange { copy(middlePosition = it) } }
    SliderRow {
        GraphicsSlider("Opacity", values.opacity, unit = "%", range = 20..100, tag = DECORATION_OPACITY_TAG) {
            onValuesChange { copy(opacity = it) }
        }
        GraphicsSlider("Border", values.borderWidth, unit = " px", range = 0..12, tag = DECORATION_BORDER_TAG) {
            onValuesChange { copy(borderWidth = it) }
        }
    }
    SliderRow {
        GraphicsSlider("Rings", values.rings, unit = "", range = 1..3, tag = DECORATION_RINGS_TAG) {
            onValuesChange { copy(rings = it) }
        }
        // An empty half keeps the rings slider as wide for every shape.
        if (values.selectedShape == DecorationShape.Star) {
            GraphicsSlider(
                "Star points",
                values.starPoints,
                unit = "",
                range = 3..12,
                tag = DECORATION_STAR_POINTS_TAG,
            ) {
                onValuesChange { copy(starPoints = it) }
            }
        } else {
            Box(modifier = SwingModifier.weight(1f)) {}
        }
    }
    DecorationEffectControls(values, onValuesChange)
}

@Composable
private fun ColumnScope.GradientControls(
    stops: GradientStops,
    middlePosition: Int,
    onEditStop: (GradientStop) -> Unit,
    onMiddlePositionChange: (Int) -> Unit,
) {
    SliderRow {
        for (stop in GradientStop.entries) {
            val color = stops.color(stop)
            Button(
                "",
                onClick = { onEditStop(stop) },
                modifier =
                    SwingModifier
                        .icon(SwatchIcon(color))
                        .toolTip("Pick the ${stop.name.lowercase()} color")
                        .accessibleName("${stop.name} color")
                        .testTag(GRADIENT_SWATCH_TAG_PREFIX + stop.ordinal),
            )
        }
        GraphicsSlider(
            "Middle",
            middlePosition,
            unit = "%",
            range = 10..90,
            tag = GRADIENT_MIDDLE_TAG,
            onValueChange = onMiddlePositionChange,
        )
    }
}

@Composable
private fun ColumnScope.DecorationEffectControls(
    values: DecorationValues,
    onValuesChange: (DecorationValues.() -> DecorationValues) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12)) {
        CheckBox("Shadow", values.shadowEnabled, { onValuesChange { copy(shadowEnabled = it) } })
        CheckBox("Blur", values.blurEnabled, { onValuesChange { copy(blurEnabled = it) } })
        CheckBox("Padding: 8 px", values.paddingEnabled, { onValuesChange { copy(paddingEnabled = it) } })
    }
    SliderRow {
        GraphicsSlider(
            "Shadow",
            values.shadowRadius,
            unit = " px",
            range = 0..24,
            tag = DECORATION_SHADOW_RADIUS_TAG,
            enabled = values.shadowEnabled,
        ) {
            onValuesChange { copy(shadowRadius = it) }
        }
        GraphicsSlider(
            "Blur",
            values.blurRadius,
            unit = " px",
            range = 0..16,
            tag = DECORATION_BLUR_RADIUS_TAG,
            enabled = values.blurEnabled,
        ) {
            onValuesChange { copy(blurRadius = it) }
        }
    }
    SliderRow {
        GraphicsSlider(
            "Offset X",
            values.shadowOffsetX,
            unit = " px",
            range = -16..16,
            tag = DECORATION_SHADOW_X_TAG,
            enabled = values.shadowEnabled,
        ) {
            onValuesChange { copy(shadowOffsetX = it) }
        }
        GraphicsSlider(
            "Offset Y",
            values.shadowOffsetY,
            unit = " px",
            range = -16..16,
            tag = DECORATION_SHADOW_Y_TAG,
            enabled = values.shadowEnabled,
        ) {
            onValuesChange { copy(shadowOffsetY = it) }
        }
    }
}

private data class SwatchIcon(
    val color: Color,
) : Icon {
    override fun getIconWidth(): Int = SWATCH_SIZE

    override fun getIconHeight(): Int = SWATCH_SIZE

    override fun paintIcon(
        component: Component?,
        graphics: Graphics,
        x: Int,
        y: Int,
    ) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = color
            g.fillOval(x, y, SWATCH_SIZE, SWATCH_SIZE)
            val outline = Color(0, 0, 0, 90)
            g.color = outline
            g.drawOval(x, y, SWATCH_SIZE - 1, SWATCH_SIZE - 1)
        } finally {
            g.dispose()
        }
    }
}

private const val SWATCH_SIZE = 16

internal const val DECORATION_TAG = "foundation-decoration"
internal const val DECORATION_STAGE_TAG = "foundation-decoration-stage"
internal const val DECORATION_SHAPE_TAG = "foundation-decoration-shape"
internal const val DECORATION_OPACITY_TAG = "foundation-decoration-opacity"
internal const val DECORATION_BORDER_TAG = "foundation-decoration-border"
internal const val DECORATION_RINGS_TAG = "foundation-decoration-rings"
internal const val DECORATION_STAR_POINTS_TAG = "foundation-decoration-star-points"
internal const val DECORATION_SHADOW_RADIUS_TAG = "foundation-decoration-shadow-radius"
internal const val DECORATION_SHADOW_X_TAG = "foundation-decoration-shadow-x"
internal const val DECORATION_SHADOW_Y_TAG = "foundation-decoration-shadow-y"
internal const val DECORATION_BLUR_RADIUS_TAG = "foundation-decoration-blur-radius"
internal const val GRADIENT_PRESET_TAG = "foundation-gradient-preset"
internal const val GRADIENT_MIDDLE_TAG = "foundation-gradient-middle"
internal const val GRADIENT_SWATCH_TAG_PREFIX = "foundation-gradient-swatch-"
internal const val GRADIENT_CHOOSER_TAG = "foundation-gradient-chooser"
internal const val BRUSH_TILES_TAG = "foundation-brush-tiles"
internal const val BRUSH_TILE_TAG_PREFIX = "foundation-brush-tile-"
