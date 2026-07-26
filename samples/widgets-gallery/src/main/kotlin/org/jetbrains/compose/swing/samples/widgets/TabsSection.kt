package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension
import javax.swing.JTabbedPane

// TabbedPane: a controlled selected index synced with external buttons, an optionally-disabled tab,
// and a dynamically added/removed tab - each tab(...) appearing or disappearing drives the matching
// insert/remove through the slot mechanism.
@Composable
internal fun TabsSection() {
    SectionColumn {
        SectionHeading("Tabs")
        ExampleCard("TabbedPane (controlled selection, dynamic tabs)") {
            var selected by remember { mutableIntStateOf(0) }
            var advancedEnabled by remember { mutableStateOf(false) }
            var extraTab by remember { mutableStateOf(false) }

            FlowPanel {
                Button("Select first", onClick = { selected = 0 })
                Button("Select last", onClick = { selected = if (extraTab) 2 else 1 })
                CheckBox(
                    text = "Enable Advanced",
                    checked = advancedEnabled,
                    onCheckedChange = { advancedEnabled = it },
                )
                CheckBox(
                    text = "Show extra tab",
                    checked = extraTab,
                    onCheckedChange = { extraTab = it },
                )
            }
            Label("Selected tab index: $selected")

            TabbedPane(
                selectedIndex = selected,
                modifier = SwingModifier.preferredSize(Dimension(420, 160)),
                onSelectedIndexChange = { selected = it },
                tabPlacement = JTabbedPane.TOP,
            ) {
                tab("General") {
                    FlowPanel { Label("General settings live here.") }
                }
                tab("Advanced", tooltip = "Toggle the checkbox to enable", enabled = advancedEnabled) {
                    FlowPanel { Label("Advanced settings (enabled = $advancedEnabled).") }
                }
                if (extraTab) {
                    tab("Extra") {
                        FlowPanel { Label("This tab appears and disappears with the checkbox.") }
                    }
                }
            }
        }
    }
}
