package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.BoxLayout
import javax.swing.JTabbedPane
import javax.swing.UIManager

// TabbedPane: a controlled selected index synced with external buttons, an optionally-disabled tab, a
// dynamically added/removed tab keyed for stable identity, and a second strip whose placement and
// overflow policy switch live while its tabs carry an icon, colors, a mnemonic and a custom header.
@Preview
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
                onSelectedIndexChange = { selected = it },
                modifier = SwingModifier.preferredSize(Dimension(420, 160)),
                tabPlacement = JTabbedPane.TOP,
            ) {
                FlowPanel(SwingModifier.tab("General")) {
                    Label("General settings live here.")
                }
                FlowPanel(
                    SwingModifier.tab(
                        "Advanced",
                        tooltip = "Toggle the checkbox to enable",
                        enabled = advancedEnabled,
                    ),
                ) {
                    Label("Advanced settings (enabled = $advancedEnabled).")
                }
                if (extraTab) {
                    // Keyed so the tab keeps its own identity - and whatever its content remembers -
                    // wherever it is redeclared, rather than by the position it happens to appear at.
                    key("extra") {
                        FlowPanel(SwingModifier.tab("Extra")) {
                            Label("This tab appears and disappears with the checkbox.")
                        }
                    }
                }
            }
        }
        TabPlacementCard()
    }
}

@Composable
private fun ColumnScope.TabPlacementCard() {
    ExampleCard("TabbedPane (placement, layout policy, tab styling)") {
        val placements =
            listOf(
                "Top" to JTabbedPane.TOP,
                "Left" to JTabbedPane.LEFT,
                "Bottom" to JTabbedPane.BOTTOM,
                "Right" to JTabbedPane.RIGHT,
            )
        var placementIndex by remember { mutableIntStateOf(0) }
        var scrollLayout by remember { mutableStateOf(false) }
        var selected by remember { mutableIntStateOf(0) }

        RadioGroup(
            selectedIndex = placementIndex,
            onSelectionChange = { placementIndex = it },
            axis = BoxLayout.X_AXIS,
        ) {
            placements.forEach { (name, _) -> option(name) }
        }
        CheckBox(
            text = "Scroll tab layout (this strip carries enough tabs to overflow)",
            checked = scrollLayout,
            onCheckedChange = { scrollLayout = it },
        )

        val infoIcon = UIManager.getIcon("OptionPane.informationIcon")
        TabbedPane(
            selectedIndex = selected,
            onSelectedIndexChange = { selected = it },
            modifier = SwingModifier.preferredSize(Dimension(320, 160)),
            tabPlacement = placements[placementIndex].second,
            tabLayoutPolicy = if (scrollLayout) JTabbedPane.SCROLL_TAB_LAYOUT else JTabbedPane.WRAP_TAB_LAYOUT,
        ) {
            FlowPanel(SwingModifier.tab("Info", icon = infoIcon)) {
                Label("A tab carrying an icon.")
            }
            FlowPanel(
                SwingModifier.tab(
                    "Styled",
                    background = Color(0xFF, 0xF9, 0xC4),
                    foreground = Color(0xE6, 0x51, 0x00),
                ),
            ) {
                Label("A tab with its own background and title color.")
            }
            FlowPanel(SwingModifier.tab("Data", mnemonic = KeyEvent.VK_D, displayedMnemonicIndex = 0)) {
                Label("Alt+D (or the platform's mouseless modifier) selects this tab.")
            }
            FlowPanel(SwingModifier.tab("Custom", header = { Label("★ Custom") })) {
                Label("This tab's strip entry is a header composable, not its title.")
            }
            repeat(EXTRA_TABS) { index ->
                FlowPanel(SwingModifier.tab("More ${index + 1}")) {
                    Label("Extra tab ${index + 1}, here to force overflow.")
                }
            }
        }
    }
}

private const val EXTRA_TABS = 6
