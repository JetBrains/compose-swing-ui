package org.jetbrains.compose.swing.samples.widgets.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption

// State-driven dynamic hierarchy: conditional composition (if{}) inserts or removes a whole subtree
// from the component tree, taking its state with it - contrasted with visible(), which keeps the slot
// in place and merely hides it.
@Composable
internal fun DynamicHierarchySection() {
    SectionColumn {
        SectionHeading("Dynamic hierarchy")
        StructuralToggleCard()
        VisibleContrastCard()
    }
}

@Composable
private fun ColumnScope.StructuralToggleCard() {
    ExampleCard("Structural add/remove (conditional composition)") {
        var showDetails by remember { mutableStateOf(false) }
        CheckBox(
            text = "Show details panel",
            checked = showDetails,
            onCheckedChange = { showDetails = it },
        )
        WrappedCaption("The panel below is part of the tree only while the box is checked.")

        if (showDetails) {
            var name by remember { mutableStateOf("Ada") }
            var level by remember { mutableIntStateOf(3) }
            BorderPanel(hgap = 8, vgap = 8) {
                north {
                    Label("Details (this entire subtree was just inserted)")
                }
                center {
                    FlowPanel {
                        Label("Name:")
                        TextField(value = name, onValueChange = { name = it }, columns = 16)
                    }
                }
                south {
                    FlowPanel {
                        Label("Level: $level")
                        Slider(value = level, onValueChange = { level = it }, min = 0, max = 10)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.VisibleContrastCard() {
    ExampleCard("Contrast: visible() keeps the slot") {
        var shown by remember { mutableStateOf(true) }
        CheckBox(
            text = "Show via visible() modifier",
            checked = shown,
            onCheckedChange = { shown = it },
        )
        WrappedCaption(
            "Structural if{}: the subtree is added/removed from the tree, taking its state with it. " +
                "visible(false): the component stays in the tree and only becomes hidden.",
        )

        var clicks by remember { mutableIntStateOf(0) }
        BorderPanel(modifier = SwingModifier.visible(shown), hgap = 8, vgap = 8) {
            north {
                Label("This panel is always composed; only its visibility changes.")
            }
            center {
                FlowPanel {
                    Button("Clicked $clicks time(s)", onClick = { clicks++ })
                }
            }
        }
    }
}
