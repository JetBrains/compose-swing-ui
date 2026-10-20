package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.MenuSeparator
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.window.LocalWindow
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.WindowScope
import javax.swing.JOptionPane

/**
 * The window's menus, on the screen menu bar on macOS. The pane toggles carry no accelerator: their keys go
 * through [WindowShortcuts], so no key fires twice.
 */
@Composable
internal fun WindowScope.AgentMenuBar(
    state: AppState,
    panes: PaneLayout,
    settingsState: SettingsState,
    onCloseWindow: () -> Unit,
    onNewSession: () -> Unit,
) {
    val window = LocalWindow.current
    val aboutTitle = message("menu.help.about")
    val aboutText = message("about.text")
    val settings = settingsState.settings
    MenuBar {
        Menu(message("menu.file")) {
            MenuItem(message("menu.file.newSession"), onClick = onNewSession, accelerator = menuShortcut("N"))
            MenuSeparator()
            MenuItem(message("menu.file.close"), onClick = onCloseWindow, accelerator = menuShortcut("W"))
        }
        Menu(message("menu.edit")) {
            MenuItem(
                message("menu.edit.stop"),
                onClick = state.runner::stop,
                modifier = SwingModifier.enabled(state.runner.isRunning),
                accelerator = menuShortcut("PERIOD"),
            )
            MenuItem(message("menu.edit.clearComposer"), onClick = { state.composer.requestClearDraft() })
        }
        Menu(message("menu.view")) {
            CheckBoxMenuItem(
                message("menu.view.sessionsPane"),
                panes.sessions.isVisible,
                panes::setSessionsVisible,
            )
            CheckBoxMenuItem(
                message("menu.view.contextPane"),
                panes.context.isVisible,
                panes::setContextVisible,
            )
            MenuSeparator()
            CheckBoxMenuItem(
                message("menu.view.darkTheme"),
                checked = settings.themeMode == ThemeMode.Dark,
                onCheckedChange = { settingsState.toggleTheme() },
                accelerator = menuShortcut("shift T"),
            )
            CheckBoxMenuItem(
                message("menu.view.reduceMotion"),
                settings.isReduceMotion,
                settingsState::setReduceMotion,
            )
        }
        Menu(message("menu.help")) {
            MenuItem(aboutTitle, onClick = {
                JOptionPane.showMessageDialog(window, aboutText, aboutTitle, JOptionPane.INFORMATION_MESSAGE)
            })
        }
    }
}
