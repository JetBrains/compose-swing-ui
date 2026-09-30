package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.defaults.DefaultFont
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.DefaultPaintOutsets
import org.jetbrains.compose.swing.foundation.layout.PaintOutsets
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.clipToBounds
import org.jetbrains.compose.swing.foundation.layout.offset
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.paintOutsets
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Color
import java.awt.Container
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent

// One value drives every card. Its selector stays above the cards as they scroll, and each card provides the value
// to the widgets on its stage only: the card's own containers, captions and controls keep their layout.
@Preview
@Composable
internal fun PaintOutsetsSection() {
    var selected by remember { mutableIntStateOf(0) }
    var fixed by remember { mutableIntStateOf(4) }
    val value = PAINT_OUTSETS_VALUES[selected].second(fixed)
    Panel(PanelLayout.Border()) {
        Column(SwingModifier.north()) {
            SectionHeading("Paint outsets")
            Column(SwingModifier.padding(start = 5, bottom = 6), verticalArrangement = Arrangement.spacedBy(4)) {
                LayoutParameterSelector("paintOutsets", PAINT_OUTSETS_VALUES, selected) { selected = it }
                if (PAINT_OUTSETS_VALUES[selected].first == FIXED_VALUE) {
                    LayoutSlider(
                        label = "n",
                        valueText = "$fixed px",
                        value = fixed,
                        onValueChange = { fixed = it },
                        accessibleName = "Fixed paint outsets",
                        min = 0,
                        max = 8,
                    )
                }
            }
        }
        SectionColumn {
            FormEdgesCard(value)
            BaselineRowCard(value)
            DecoratedBesideStockCard(value)
            ExplicitOverInheritedCard(value)
            DeclarationOrderCard(value)
            SwingParentCard(value)
            ChildPastContainerCard(value)
        }
    }
}

/** What the selector offers: a name, and the value it declares for the slider's amount, null for no declaration. */
private val PAINT_OUTSETS_VALUES: List<Pair<String, (Int) -> PaintOutsets?>> =
    listOf(
        "Nothing declared" to { null },
        "PaintOutsets.None" to { PaintOutsets.None },
        "PaintOutsets.Decoration" to { PaintOutsets.Decoration },
        "PaintOutsets.FullInsets" to { PaintOutsets.FullInsets },
        FIXED_VALUE to { PaintOutsets(it) },
        "FocusRing (custom)" to { FocusRing },
    )

private const val FIXED_VALUE = "PaintOutsets(n)"

/** Names 2 px past the decoration's paint outsets: a focus ring a look and feel keeps in the insets. */
private object FocusRing : PaintOutsets {
    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets {
        val d = decorationOutsets
        return Insets(d.top + 2, d.left + 2, d.bottom + 2, d.right + 2)
    }
}

@Composable
private fun ColumnScope.FormEdgesCard(value: PaintOutsets?) {
    ExampleCard("Form edges") {
        WrappedCaption(
            "Under FullInsets each widget's text starts on the dashed line, and its band is its insets. Under None, " +
                "and with nothing declared, the bounds start on the line and there is no band.",
            CAPTION_WIDTH,
        )
        var name by remember { mutableStateOf("Text field") }
        val guides = remember { GuidedChildren() }
        val lines = setOf(GuideLine.Left)
        Column(
            modifier =
                SwingModifier
                    .layoutStage()
                    .testTag(PAINT_OUTSETS_FORM_TAG)
                    .alignmentGuides(guides, LocalAlignmentGuidesShown.current, BOX_MARKS, lines),
            verticalArrangement = Arrangement.spacedBy(16),
        ) {
            ProvideComponentDefaults(DefaultPaintOutsets provides value, DefaultFont provides STAGE_FONT) {
                Label(
                    "Label",
                    SwingModifier
                        .foreground(LayoutSampleColors.Text)
                        .testTag(PAINT_OUTSETS_FORM_LABEL_TAG)
                        .alignmentGuide(guides, value),
                )
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = SwingModifier.testTag(PAINT_OUTSETS_FORM_FIELD_TAG).alignmentGuide(guides, value),
                    columns = 8,
                )
                Button(
                    text = "Button",
                    onClick = {},
                    modifier = SwingModifier.testTag(PAINT_OUTSETS_FORM_BUTTON_TAG).alignmentGuide(guides, value),
                )
            }
        }
        AlignmentGuideLegend(BOX_MARKS, lines)
    }
}

@Composable
private fun ColumnScope.BaselineRowCard(value: PaintOutsets?) {
    ExampleCard("Baseline row") {
        WrappedCaption(
            "The dashed baselines stay on one row under every value: a layout box moved in from the bounds carries " +
                "its baseline along.",
            CAPTION_WIDTH,
        )
        var text by remember { mutableStateOf("Field") }
        val guides = remember { GuidedChildren() }
        val lines = setOf(GuideLine.FirstBaseline)
        // The spacing is wider than the insets two neighbors face each other with, so no two bands overlap.
        Row(
            modifier =
                SwingModifier
                    .layoutStage()
                    .alignmentGuides(guides, LocalAlignmentGuidesShown.current, BOX_MARKS, lines),
            horizontalArrangement = Arrangement.spacedBy(32),
        ) {
            ProvideComponentDefaults(DefaultPaintOutsets provides value, DefaultFont provides STAGE_FONT) {
                Label(
                    "Label:",
                    SwingModifier
                        .foreground(LayoutSampleColors.Text)
                        .testTag(PAINT_OUTSETS_BASELINE_LABEL_TAG)
                        .alignmentGuide(guides, value)
                        .alignByBaseline(),
                )
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier =
                        SwingModifier
                            .testTag(PAINT_OUTSETS_BASELINE_FIELD_TAG)
                            .alignmentGuide(guides, value)
                            .alignByBaseline(),
                    columns = 4,
                )
                Button(
                    text = "OK",
                    onClick = {},
                    modifier =
                        SwingModifier
                            .testTag(PAINT_OUTSETS_BASELINE_BUTTON_TAG)
                            .alignmentGuide(guides, value)
                            .alignByBaseline(),
                )
            }
        }
        AlignmentGuideLegend(BOX_MARKS, lines)
    }
}

@Composable
private fun ColumnScope.DecoratedBesideStockCard(value: PaintOutsets?) {
    ExampleCard("Decorated beside stock") {
        WrappedCaption(
            "Under Decoration, and with nothing declared, the shadow is the band past the layout box, and the " +
                "boxes are 16 px apart. Under None the shadow is inside the box; under FullInsets the boxes are " +
                "the content areas, and the bands overlap.",
            CAPTION_WIDTH,
        )
        val guides = remember { GuidedChildren() }
        val lines = setOf(GuideLine.Top)
        Row(
            modifier =
                SwingModifier
                    .layoutStage()
                    .testTag(PAINT_OUTSETS_DECORATED_TAG)
                    .alignmentGuides(guides, LocalAlignmentGuidesShown.current, BOX_MARKS, lines),
            horizontalArrangement = Arrangement.spacedBy(16),
        ) {
            ProvideComponentDefaults(DefaultPaintOutsets provides value, DefaultFont provides STAGE_FONT) {
                Button(
                    text = "Stock",
                    onClick = {},
                    modifier = SwingModifier.testTag(PAINT_OUTSETS_STOCK_TAG).alignmentGuide(guides, value),
                )
                ShadowedBox(SwingModifier.testTag(PAINT_OUTSETS_SHADOWED_TAG).alignmentGuide(guides, value))
            }
        }
        AlignmentGuideLegend(BOX_MARKS, lines)
    }
}

@Composable
private fun ColumnScope.ExplicitOverInheritedCard(value: PaintOutsets?) {
    ExampleCard("Explicit over inherited") {
        WrappedCaption(
            "The first field inherits the section's value; the second also declares paintOutsets(FullInsets), " +
                "which replaces it. The second field's band is its insets under every value.",
            CAPTION_WIDTH,
        )
        var inherited by remember { mutableStateOf("inherited") }
        var explicit by remember { mutableStateOf("FullInsets") }
        val guides = remember { GuidedChildren() }
        val lines = setOf(GuideLine.Top)
        Row(
            modifier =
                SwingModifier
                    .layoutStage()
                    .alignmentGuides(guides, LocalAlignmentGuidesShown.current, BOX_MARKS, lines),
            horizontalArrangement = Arrangement.spacedBy(16),
        ) {
            ProvideComponentDefaults(DefaultPaintOutsets provides value, DefaultFont provides STAGE_FONT) {
                TextField(
                    value = inherited,
                    onValueChange = { inherited = it },
                    modifier = SwingModifier.testTag(PAINT_OUTSETS_INHERITED_TAG).alignmentGuide(guides, value),
                    columns = 6,
                )
                TextField(
                    value = explicit,
                    onValueChange = { explicit = it },
                    modifier =
                        SwingModifier
                            .testTag(PAINT_OUTSETS_EXPLICIT_TAG)
                            .paintOutsets(PaintOutsets.FullInsets)
                            .alignmentGuide(guides, PaintOutsets.FullInsets),
                    columns = 6,
                )
            }
        }
        AlignmentGuideLegend(BOX_MARKS, lines)
    }
}

@Composable
private fun ColumnScope.DeclarationOrderCard(value: PaintOutsets?) {
    ExampleCard("Declaration order") {
        WrappedCaption(
            "Where the value names part of the insets: after width(120) it adds the band to the field, which is " +
                "wider than 120 px. Before width(120) the band comes off the layout box, and the field stays 120 px.",
            CAPTION_WIDTH,
        )
        var widthFirst by remember { mutableIntStateOf(0) }
        var widthLast by remember { mutableIntStateOf(0) }
        Label("width(120).paintOutsets(value): field $widthFirst px")
        Label("paintOutsets(value).width(120): field $widthLast px")
        val guides = remember { GuidedChildren() }
        val lines = setOf(GuideLine.Top)
        Row(
            modifier =
                SwingModifier
                    .layoutStage()
                    .alignmentGuides(guides, LocalAlignmentGuidesShown.current, BOX_MARKS, lines),
            horizontalArrangement = Arrangement.spacedBy(16),
        ) {
            ProvideComponentDefaults(DefaultFont provides STAGE_FONT) {
                MeasuredField(
                    "first",
                    SwingModifier
                        .testTag(PAINT_OUTSETS_WIDTH_FIRST_TAG)
                        .alignmentGuide(guides, value)
                        .width(120)
                        .then(if (value != null) SwingModifier.paintOutsets(value) else SwingModifier),
                    onWidthChange = { widthFirst = it },
                )
                MeasuredField(
                    "last",
                    SwingModifier
                        .testTag(PAINT_OUTSETS_WIDTH_LAST_TAG)
                        .alignmentGuide(guides, value)
                        .then(if (value != null) SwingModifier.paintOutsets(value) else SwingModifier)
                        .width(120),
                    onWidthChange = { widthLast = it },
                )
            }
        }
        AlignmentGuideLegend(BOX_MARKS, lines)
    }
}

/** An editable field that reports its width as Swing resizes it. */
@Composable
private fun MeasuredField(
    initialText: String,
    modifier: SwingModifier,
    onWidthChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.componentListener(onComponentResized = { onWidthChange(it.component.width) }),
    )
}

// A Panel is not Decoratable, so it draws no guides: the box's own insets and size tell the values apart.
@Composable
private fun ColumnScope.SwingParentCard(value: PaintOutsets?) {
    ExampleCard("Under a Swing parent") {
        WrappedCaption(
            "Under None the box's insets and size include the whole shadow; under PaintOutsets(n), the part of it " +
                "past n px. Under the other values the insets are the border alone, and the shadow is cut at the " +
                "bounds.",
            CAPTION_WIDTH,
        )
        var shadowed by remember { mutableStateOf(SwingGeometry()) }
        Label(
            "Shadowed box: getInsets() = ${shadowed.insets.top}, ${shadowed.insets.left}, " +
                "${shadowed.insets.bottom}, ${shadowed.insets.right}",
        )
        Label("Shadowed box: size = ${shadowed.width} × ${shadowed.height} px")
        Panel(
            PanelLayout.Flow(alignment = FlowLayout.LEADING, hgap = 16, vgap = 16),
            modifier = SwingModifier.layoutPanelTrack(),
        ) {
            ProvideComponentDefaults(DefaultPaintOutsets provides value, DefaultFont provides STAGE_FONT) {
                Button("Stock", onClick = {})
                ShadowedBox(
                    SwingModifier
                        .testTag(PAINT_OUTSETS_SWING_PARENT_SHADOWED_TAG)
                        .componentListener(
                            onComponentResized = {
                                // The event hands a Component, and only a Container has insets.
                                val box = it.component as Container
                                shadowed = SwingGeometry(box.insets, box.width, box.height)
                            },
                        ),
                )
            }
        }
    }
}

/** What Swing holds for a component: its insets and its size. */
private data class SwingGeometry(
    val insets: Insets = Insets(0, 0, 0, 0),
    val width: Int = 0,
    val height: Int = 0,
)

// The track's padding leaves the stage room on both sides for the button to be seen past the track.
@Composable
private fun ColumnScope.ChildPastContainerCard(value: PaintOutsets?) {
    ExampleCard("Children placed past a container") {
        WrappedCaption(
            "Move the button past an edge of the track: it paints there and takes the click, until clipToBounds() " +
                "cuts it at the edge. Under FullInsets the track places the button's layout box, so its band is " +
                "past the track at offset 0 too.",
            CAPTION_WIDTH,
        )
        var offset by remember { mutableIntStateOf(0) }
        var clip by remember { mutableStateOf(false) }
        var clicks by remember { mutableIntStateOf(0) }
        LayoutSlider(
            label = "offset",
            valueText = "$offset px",
            value = offset,
            onValueChange = { offset = it },
            accessibleName = "Child offset",
            min = -90,
            max = 120,
        )
        CheckBox(text = "clipToBounds() on the track", checked = clip, onCheckedChange = { clip = it })
        val guides = remember { GuidedChildren() }
        Box(SwingModifier.background(LayoutSampleColors.Blue, RectangleShape).testTag(PAINT_OUTSETS_STAGE_TAG)) {
            Row(
                modifier =
                    SwingModifier
                        .padding(horizontal = 100, vertical = 16)
                        .width(140)
                        .layoutTrack()
                        .testTag(PAINT_OUTSETS_TRACK_TAG)
                        .alignmentGuides(guides, LocalAlignmentGuidesShown.current, BOX_MARKS)
                        .then(if (clip) SwingModifier.clipToBounds() else SwingModifier),
            ) {
                ProvideComponentDefaults(DefaultPaintOutsets provides value, DefaultFont provides STAGE_FONT) {
                    Button(
                        text = "Clicked $clicks",
                        onClick = { clicks++ },
                        modifier =
                            SwingModifier
                                .testTag(PAINT_OUTSETS_PAST_BUTTON_TAG)
                                .alignmentGuide(guides, value)
                                .offset(x = offset),
                    )
                }
            }
        }
        AlignmentGuideLegend(BOX_MARKS)
    }
}

/** A Foundation box with a Swing border, which its insets carry, and a shadow, which its decoration paints. */
@Composable
private fun ShadowedBox(modifier: SwingModifier) {
    // A border is compared by identity.
    val border =
        remember {
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LayoutSampleColors.Border),
                BorderFactory.createEmptyBorder(5, 12, 5, 12),
            )
        }
    Box(modifier.shadow(4, Color(0, 0, 0, 160)).background(Color.WHITE, RectangleShape).border(border)) {
        Label("Shadowed", SwingModifier.foreground(LayoutSampleColors.Text))
    }
}

/** The marks of a card that tells the bounds, the layout box and the paint outsets between them apart. */
private val BOX_MARKS = setOf(GuideMark.Bounds, GuideMark.LayoutBox, GuideMark.PaintOutsets)

/** Large enough to tell a widget's insets apart at the window's normal scale. */
private val STAGE_FONT = Font(Font.SANS_SERIF, Font.PLAIN, 18)

/** A caption this wide fits beside the sidebar in a window about 580 px wide. */
private const val CAPTION_WIDTH = 250

internal const val PAINT_OUTSETS_FORM_TAG = "paint-outsets-form-column"
internal const val PAINT_OUTSETS_FORM_LABEL_TAG = "paint-outsets-form-label"
internal const val PAINT_OUTSETS_FORM_FIELD_TAG = "paint-outsets-form-field"
internal const val PAINT_OUTSETS_FORM_BUTTON_TAG = "paint-outsets-form-button"
internal const val PAINT_OUTSETS_BASELINE_LABEL_TAG = "paint-outsets-baseline-label"
internal const val PAINT_OUTSETS_BASELINE_FIELD_TAG = "paint-outsets-baseline-field"
internal const val PAINT_OUTSETS_BASELINE_BUTTON_TAG = "paint-outsets-baseline-button"
internal const val PAINT_OUTSETS_DECORATED_TAG = "paint-outsets-decorated-row"
internal const val PAINT_OUTSETS_STOCK_TAG = "paint-outsets-decorated-stock"
internal const val PAINT_OUTSETS_SHADOWED_TAG = "paint-outsets-decorated-shadowed"
internal const val PAINT_OUTSETS_INHERITED_TAG = "paint-outsets-explicit-inherited"
internal const val PAINT_OUTSETS_EXPLICIT_TAG = "paint-outsets-explicit-explicit"
internal const val PAINT_OUTSETS_WIDTH_FIRST_TAG = "paint-outsets-order-width-first"
internal const val PAINT_OUTSETS_WIDTH_LAST_TAG = "paint-outsets-order-width-last"
internal const val PAINT_OUTSETS_SWING_PARENT_SHADOWED_TAG = "paint-outsets-swing-parent-shadowed"
internal const val PAINT_OUTSETS_STAGE_TAG = "paint-outsets-past-stage"
internal const val PAINT_OUTSETS_TRACK_TAG = "paint-outsets-past-track"
internal const val PAINT_OUTSETS_PAST_BUTTON_TAG = "paint-outsets-past-button"
