package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import com.formdev.flatlaf.FlatClientProperties
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.widthIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.samples.agent.components.ToolbarButton
import org.jetbrains.compose.swing.samples.agent.components.ToolbarToggleButton
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.AgentSelection
import org.jetbrains.compose.swing.samples.agent.state.ModelDiscovery
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather
import javax.swing.BorderFactory

/**
 * The bar across the top: pane toggles at the edges, and the theme and settings
 * buttons. On macOS it sits under the transparent title bar, so it leaves room for the window
 * buttons.
 */
@Composable
internal fun ConstrainedScope.TopBar(
    panes: PaneLayout,
    settingsState: SettingsState,
    selection: AgentSelection,
    onThemeToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    val colors = LocalAgentTheme.current.colors
    val settings = settingsState.settings
    // One sidebar glyph for both panes, its bar on the side the pane opens, which swaps in a right-to-left window.
    val leftToRight = !settings.isRightToLeft
    // Large native button spacing is 52 pixels high; the bottom border adds one pixel.
    Box(modifier.fillMaxWidth().height(if (isMacOs) 53 else 46)) {
        Panel(
            PanelLayout.Border(),
            SwingModifier
                .matchParentSize()
                .background(colors.background)
                .border(BorderFactory.createMatteBorder(0, 0, 1, 0, colors.border)),
        ) {
            if (isMacOs) {
                Panel(
                    PanelLayout.Border(),
                    SwingModifier
                        .west()
                        .opaque(false)
                        .clientProperty(
                            FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER,
                            "mac zeroInFullScreen",
                        ).testTag("window-buttons-placeholder"),
                ) {}
            }
            Row(
                SwingModifier.center().border(BorderFactory.createEmptyBorder(0, 10, 0, 10)),
                horizontalArrangement = Arrangement.spacedBy(6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarToggleButton(
                    selected = panes.sessions.isVisible,
                    onSelectedChange = panes::setSessionsVisible,
                    modifier = SwingModifier.testTag("sessions-toggle"),
                    icon = rememberThemedIcon(Feather.SIDEBAR, mirrored = !leftToRight),
                    toolTip = message("topbar.sessionsPane") + " (" + (if (isMacOs) "⌘\\" else "Ctrl+\\") + ")",
                    accessibleLabel = message("topbar.sessionsPane"),
                )
                if (selection.discovery is ModelDiscovery.Ready) {
                    AgentPicker(selection)
                    Box(SwingModifier.weight(1f)) {
                        ModelPicker(selection, SwingModifier.widthIn(max = 240))
                    }
                }
                Box(SwingModifier.weight(1f))
                ThemeButton(settings.themeMode, onThemeToggle)
                SettingsButton(onOpenSettings)
                ToolbarToggleButton(
                    selected = panes.context.isVisible,
                    onSelectedChange = panes::setContextVisible,
                    modifier = SwingModifier.testTag("context-toggle"),
                    icon = rememberThemedIcon(Feather.SIDEBAR, mirrored = leftToRight),
                    toolTip = message("topbar.contextPane") + " (" + (if (isMacOs) "⌘⇧\\" else "Ctrl+Shift+\\") + ")",
                    accessibleLabel = message("topbar.contextPane"),
                )
            }
        }
    }
}

/** A sun in the dark theme and a moon in the light one. */
@Composable
private fun ThemeButton(
    themeMode: ThemeMode,
    onToggle: () -> Unit,
) {
    val tooltip = message(if (themeMode == ThemeMode.Dark) "topbar.theme.light" else "topbar.theme.dark")
    ToolbarButton(
        onClick = onToggle,
        modifier = SwingModifier.testTag("theme-toggle"),
        icon = rememberThemedIcon(if (themeMode == ThemeMode.Dark) Feather.SUN else Feather.MOON),
        toolTip = tooltip,
    )
}

/** A gear opening the [SettingsDialog]. */
@Composable
private fun SettingsButton(onOpenSettings: () -> Unit) {
    ToolbarButton(
        onClick = onOpenSettings,
        modifier = SwingModifier.testTag("settings"),
        icon = rememberThemedIcon(Feather.SETTINGS),
        toolTip = message("topbar.settings"),
    )
}
