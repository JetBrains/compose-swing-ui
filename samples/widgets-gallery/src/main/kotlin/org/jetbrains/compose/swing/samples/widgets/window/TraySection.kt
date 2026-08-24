package org.jetbrains.compose.swing.samples.widgets.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.components.Tray
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage

// Tray: a system-tray icon mounted only while a toggle is on, so the section has no tray side effect on
// entry. The icon's activation and menu callbacks write back into composition state that status labels echo.
@Composable
internal fun TraySection() {
    SectionColumn {
        SectionHeading("System tray")
        TrayToggleCard()
    }
}

@Composable
private fun ColumnScope.TrayToggleCard() {
    ExampleCard("Tray (button-gated)") {
        var showTray by remember { mutableStateOf(false) }
        var lastAction by remember { mutableStateOf("none") }
        var notificationsOn by remember { mutableStateOf(true) }
        var imageAutoSize by remember { mutableStateOf(false) }

        ToggleButton(
            text = "Show tray icon",
            selected = showTray,
            onSelectedChange = { showTray = it },
        )
        WrappedCaption(
            "The tray icon is present only while this section is open: the Tray lives in the section's " +
                "composition and is removed when you switch away.",
        )
        FlowPanel {
            Label("Tray icon: ${if (showTray) "shown" else "hidden"}")
        }
        Label("Last action: $lastAction")
        Label("Notifications: ${if (notificationsOn) "on" else "off"}")
        CheckBox(
            text = "Auto-size icon to the platform's tray",
            checked = imageAutoSize,
            onCheckedChange = { imageAutoSize = it },
        )

        if (showTray) {
            // Composition-scoped: the icon is added on enter and removed when this leaves the composition.
            val icon = remember { trayImage() }
            Tray(
                image = icon,
                onAction = { lastAction = "icon activated" },
                tooltip = "Compose Swing showcase",
                imageAutoSize = imageAutoSize,
                menu = {
                    CheckBoxMenuItem(
                        text = "Notifications",
                        checked = notificationsOn,
                        onCheckedChange = { notificationsOn = it },
                    )
                    MenuSeparator()
                    MenuItem("Quit", onClick = { lastAction = "quit selected" })
                },
            )
        }
    }
}

private const val TRAY_ICON_SIZE = 16
private const val TRAY_ICON_CORNER = 6
private val TrayIconColor = Color(0x2D, 0x4B, 0x73)

private fun trayImage(): Image {
    val image = BufferedImage(TRAY_ICON_SIZE, TRAY_ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.color = TrayIconColor
        graphics.fillRoundRect(0, 0, TRAY_ICON_SIZE, TRAY_ICON_SIZE, TRAY_ICON_CORNER, TRAY_ICON_CORNER)
    } finally {
        graphics.dispose()
    }
    return image
}
