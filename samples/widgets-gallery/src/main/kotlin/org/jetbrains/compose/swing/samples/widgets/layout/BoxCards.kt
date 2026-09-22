package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.BiasAlignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Cursor
import java.awt.Dimension

// Box stacks its children on top of one another: contentAlignment places every one of them, a child
// overrides that with its own align, matchParentSize takes the box's whole extent without contributing
// to it, and zIndex reorders the stack without touching declaration order. Every card is driven from a
// control and prints the placement or extent the box gave, so what the control changed can be read off
// the card instead of measured against the border.
@Preview
@Composable
internal fun BoxSection() {
    SectionColumn {
        SectionHeading("Box")
        BoxContentAlignmentCard()
        BoxChildAlignCard()
        BoxMatchParentSizeCard()
        BoxZIndexCard()
        BiasAlignmentCard()
    }
}

// A selector drives the box's own contentAlignment through all nine positions; two children of
// different sizes make each one visible, since a bigger and a smaller child land differently within it.
@Composable
private fun ColumnScope.BoxContentAlignmentCard() {
    ExampleCard("Box (contentAlignment)") {
        val alignments =
            listOf(
                "TopStart" to Alignment.TopStart,
                "TopCenter" to Alignment.TopCenter,
                "TopEnd" to Alignment.TopEnd,
                "CenterStart" to Alignment.CenterStart,
                "Center" to Alignment.Center,
                "CenterEnd" to Alignment.CenterEnd,
                "BottomStart" to Alignment.BottomStart,
                "BottomCenter" to Alignment.BottomCenter,
                "BottomEnd" to Alignment.BottomEnd,
            )
        var selected by remember { mutableIntStateOf(4) }
        var frontX by remember { mutableIntStateOf(0) }
        var frontY by remember { mutableIntStateOf(0) }
        LayoutParameterSelector("contentAlignment", alignments, selected) { selected = it }
        Label("contentAlignment = ${alignments[selected].first}, front child at ($frontX, $frontY) px")
        Box(
            modifier = SwingModifier.layoutTrack().preferredSize(260, 140),
            contentAlignment = alignments[selected].second,
        ) {
            LayoutSwatch(
                "behind",
                LayoutSampleColors.Blue,
                modifier =
                    SwingModifier
                        .preferredSize(140, 80),
            )
            LayoutSwatch(
                "front",
                LayoutSampleColors.Orange,
                modifier =
                    SwingModifier
                        .preferredSize(70, 32)
                        .componentListener(
                            onComponentMoved = {
                                frontX = it.component.x
                                frontY = it.component.y
                            },
                        ),
            )
        }
    }
}

// The checkbox moves the box's own contentAlignment between Center and BottomCenter. The four labels that
// name a corner with align stay where they are; the one label left plain is the only child the box is
// still free to place, and its printed position is what moves.
@Composable
private fun ColumnScope.BoxChildAlignCard() {
    ExampleCard("Box (child align overrides contentAlignment)") {
        var bottomCenter by remember { mutableStateOf(false) }
        var plainX by remember { mutableIntStateOf(0) }
        var plainY by remember { mutableIntStateOf(0) }
        CheckBox(
            text = "Box places unaligned children at BottomCenter",
            checked = bottomCenter,
            onCheckedChange = { bottomCenter = it },
        )
        Label("Unaligned child sits at ${if (bottomCenter) "BottomCenter" else "Center"}: ($plainX, $plainY) px")
        Box(
            modifier = SwingModifier.layoutTrack().preferredSize(260, 140),
            contentAlignment = if (bottomCenter) Alignment.BottomCenter else Alignment.Center,
        ) {
            LayoutSwatch(
                "align(TopStart)",
                LayoutSampleColors.Blue,
                SwingModifier.align(Alignment.TopStart).preferredSize(BOX_ALIGNMENT_SWATCH_SIZE),
            )
            LayoutSwatch(
                "align(TopEnd)",
                LayoutSampleColors.Orange,
                SwingModifier.align(Alignment.TopEnd).preferredSize(BOX_ALIGNMENT_SWATCH_SIZE),
            )
            LayoutSwatch(
                "align(BottomStart)",
                LayoutSampleColors.Green,
                SwingModifier.align(Alignment.BottomStart).preferredSize(BOX_ALIGNMENT_SWATCH_SIZE),
            )
            LayoutSwatch(
                "align(BottomEnd)",
                LayoutSampleColors.Purple,
                SwingModifier.align(Alignment.BottomEnd).preferredSize(BOX_ALIGNMENT_SWATCH_SIZE),
            )
            LayoutSwatch(
                "Plain child",
                LayoutSampleColors.Yellow,
                modifier =
                    SwingModifier
                        .preferredSize(BOX_ALIGNMENT_SWATCH_SIZE)
                        .componentListener(
                            onComponentMoved = {
                                plainX = it.component.x
                                plainY = it.component.y
                            },
                        ),
            )
        }
    }
}

// The outlined box keeps a stable extent while the checkbox adds or removes matchParentSize from the
// blue backdrop. The smaller orange child leaves enough of the backdrop exposed to make the change
// visible instead of covering it completely.
@Composable
private fun ColumnScope.BoxMatchParentSizeCard() {
    ExampleCard("Box (matchParentSize)") {
        var match by remember { mutableStateOf(true) }
        var backdropWidth by remember { mutableIntStateOf(0) }
        var backdropHeight by remember { mutableIntStateOf(0) }
        CheckBox(
            text = "Backdrop declares matchParentSize",
            checked = match,
            onCheckedChange = { match = it },
        )
        Label("Blue backdrop: $backdropWidth x $backdropHeight px")
        Box(
            modifier =
                SwingModifier
                    .layoutTrack()
                    .preferredSize(260, 120)
                    .accessibleName("matchParentSize box"),
            contentAlignment = Alignment.Center,
        ) {
            Panel(
                modifier =
                    SwingModifier
                        .layoutSampleSurface(LayoutSampleColors.Blue)
                        .componentListener(
                            onComponentResized = {
                                backdropWidth = it.component.width
                                backdropHeight = it.component.height
                            },
                        ).then(if (match) SwingModifier.matchParentSize() else SwingModifier),
            ) { }
            LayoutSwatch(
                "Independent child",
                LayoutSampleColors.Orange,
                modifier = SwingModifier.preferredSize(140, 50),
            )
        }
    }
}

// Both children remain partly exposed. Their cursors identify which layer owns a pointer location, and
// clicking an exposed part raises that child so the shared center immediately changes hit target.
@Composable
private fun ColumnScope.BoxZIndexCard() {
    ExampleCard("Box (zIndex)") {
        var front by remember { mutableStateOf('A') }
        Label("Front child: $front")
        Label("Hover identifies a layer; click its exposed area to raise it.")
        Box(
            modifier = SwingModifier.layoutTrack().preferredSize(200, 140),
            contentAlignment = Alignment.Center,
        ) {
            InteractiveLayoutSwatch(
                "B - crosshair",
                LayoutSampleColors.Green,
                onClick = { front = 'B' },
                modifier =
                    SwingModifier
                        .preferredSize(90, 140)
                        .toolTip("Layer B: click to raise")
                        .zIndex(if (front == 'B') 1f else 0f),
                cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
            )
            InteractiveLayoutSwatch(
                "A - hand",
                LayoutSampleColors.Orange,
                onClick = { front = 'A' },
                modifier =
                    SwingModifier
                        .preferredSize(140, 90)
                        .toolTip("Layer A: click to raise")
                        .zIndex(if (front == 'A') 1f else 0f),
            )
        }
    }
}

// A BiasAlignment built from an arbitrary float, not one of the nine named constants: the slider moves
// the child continuously through the box rather than snapping between fixed positions, and the child's
// own x follows it one step at a time.
@Composable
internal fun ColumnScope.BiasAlignmentCard() {
    ExampleCard("BiasAlignment (arbitrary bias)") {
        var bias by remember { mutableIntStateOf(0) }
        var childX by remember { mutableIntStateOf(0) }
        LayoutSlider(
            label = "Horizontal bias",
            valueText = "${bias / 100f}, child x = $childX px",
            value = bias,
            onValueChange = { bias = it },
            min = -100,
            max = 100,
        )
        Box(
            modifier = SwingModifier.layoutTrack().preferredSize(300, 40),
            contentAlignment = BiasAlignment(bias / 100f, 0f),
        ) {
            LayoutSwatch(
                "Slides with the bias",
                LayoutSampleColors.Teal,
                modifier =
                    SwingModifier
                        .preferredSize(120, 28)
                        .componentListener(onComponentMoved = { childX = it.component.x }),
            )
        }
    }
}

private val BOX_ALIGNMENT_SWATCH_SIZE = Dimension(120, 32)
