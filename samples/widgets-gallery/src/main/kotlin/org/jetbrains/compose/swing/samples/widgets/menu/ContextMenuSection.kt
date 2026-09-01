package org.jetbrains.compose.swing.samples.widgets.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.MenuSeparator
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview

// ContextMenu: a right-click popup built from the same menu composables a menu bar uses. The anchor
// modifier names the component the popup opens over, and the menu is declared beside it. The popup
// reads live composition state, so each selection updates hoisted state that a status label reflects,
// and CheckBoxMenuItems show the current flags.
@Preview
@Composable
internal fun ContextMenuSection() {
    SectionColumn {
        SectionHeading("Context menu")
        ActionMenuCard()
        StatefulMenuCard()
    }
}

@Composable
private fun ColumnScope.ActionMenuCard() {
    ExampleCard("ContextMenu with MenuItem actions") {
        var lastAction by remember { mutableStateOf("none") }
        val anchor = rememberPopupAnchor()
        Label("Right-click here for actions", modifier = SwingModifier.popupAnchor(anchor))
        ContextMenu(anchor) {
            MenuItem("Cut", onClick = { lastAction = "Cut" })
            MenuItem("Copy", onClick = { lastAction = "Copy" })
            MenuItem("Paste", onClick = { lastAction = "Paste" })
            MenuSeparator()
            Menu("More") {
                MenuItem("Select all", onClick = { lastAction = "Select all" })
                MenuItem("Clear", onClick = { lastAction = "Clear" })
            }
        }
        Label("Last action: $lastAction")
    }
}

@Composable
private fun ColumnScope.StatefulMenuCard() {
    ExampleCard("ContextMenu with CheckBoxMenuItem state") {
        var wrap by remember { mutableStateOf(true) }
        var lineNumbers by remember { mutableStateOf(false) }
        val anchor = rememberPopupAnchor()
        Label("Right-click here to toggle options", modifier = SwingModifier.popupAnchor(anchor))
        ContextMenu(anchor) {
            CheckBoxMenuItem("Word wrap", checked = wrap, onCheckedChange = { wrap = it })
            CheckBoxMenuItem("Line numbers", checked = lineNumbers, onCheckedChange = { lineNumbers = it })
        }
        Label("Word wrap: ${if (wrap) "on" else "off"}, line numbers: ${if (lineNumbers) "on" else "off"}")
    }
}
