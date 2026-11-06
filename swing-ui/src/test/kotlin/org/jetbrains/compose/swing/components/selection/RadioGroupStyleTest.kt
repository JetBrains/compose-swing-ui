package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Panels
import org.jetbrains.compose.swing.components.style.RadioGroups
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import kotlin.test.Test

/** What reaches the panel a [RadioGroup] lays its options out in. */
class RadioGroupStyleTest {
    @Test
    fun aStyleProvidedForRadioGroupsReachesTheGroupsPanel() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(RadioGroups) { PanelName.provide("provided") } }) {
                RadioGroup(selectedIndex = 0, onSelectionChange = {}) { option("Small") }
            }
        }

        onNodeWithText("Small").onParent().assertProperty("provided") { name }
    }

    @Test
    fun aStyleProvidedForPanelsDoesNotReachTheGroupsPanel() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(Panels) { PanelName.provide("provided") } }) {
                RadioGroup(selectedIndex = 0, onSelectionChange = {}) { option("Small") }
            }
        }

        onNodeWithText("Small").onParent().assertProperty(null) { name }
    }

    @Test
    fun theCallersStyleOverridesAProvidedOneOnTheGroupsPanel() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(RadioGroups) { PanelName.provide("provided") } }) {
                RadioGroup(
                    selectedIndex = 0,
                    onSelectionChange = {},
                    style = { PanelName.provide("caller") },
                ) { option("Small") }
            }
        }

        onNodeWithText("Small").onParent().assertProperty("caller") { name }
    }
}

private val PanelName = stylePropertyOf(panelNameDescriptor())

private fun panelNameDescriptor() =
    ComponentPropertyDescriptor<JPanel, String?>("name", { it.name }, { panel, value -> panel.name = value })
