package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.GridBagPanelScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.labelFor
import org.jetbrains.compose.swing.modifier.accessibility.labelTarget
import org.jetbrains.compose.swing.modifier.accessibility.rememberLabelTarget
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.modifier.interaction.onAccept
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.LocalWindow
import java.awt.Dialog.ModalityType
import java.awt.GridBagConstraints
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFileChooser

@Composable
internal fun SettingsDialog(
    settingsState: SettingsState,
    onClose: () -> Unit,
) {
    Dialog(
        onCloseRequest = onClose,
        title = message("settings.title"),
        modality = ModalityType.DOCUMENT_MODAL,
        resizable = true,
    ) {
        SettingsContent(settingsState, onClose = onClose)
    }
}

/** Preferences apply immediately; the executable applies on Enter, focus loss or closing the form. */
@Composable
internal fun SettingsContent(
    settingsState: SettingsState,
    modifier: SwingModifier = SwingModifier,
    onClose: () -> Unit = {},
) {
    var draft by remember { mutableStateOf(settingsState.settings.piExecutable) }
    val executable by rememberUpdatedState(draft)
    DisposableEffect(settingsState) {
        onDispose {
            if (executable.trim() != settingsState.settings.piExecutable) settingsState.setPiExecutable(executable)
        }
    }
    Panel(
        PanelLayout.GridBag,
        modifier
            .border(BorderFactory.createEmptyBorder(16, 16, 16, 16))
            .testTag("settings-form")
            .onKeyStroke("ESCAPE", JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, onClose),
    ) {
        Preferences(settingsState, draft, { draft = it }, { settingsState.setPiExecutable(draft) })
        Button(
            message("settings.close"),
            onClose,
            SwingModifier
                .item(
                    gridx = 1,
                    gridy = 5,
                    weighty = 1.0,
                    anchor = GridBagConstraints.LAST_LINE_END,
                    insets = Insets(12, 0, 0, 0),
                ).defaultButton(true)
                .testTag("settings-close"),
        )
    }
}

@Composable
private fun GridBagPanelScope.Preferences(
    settingsState: SettingsState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onApplyExecutable: () -> Unit,
) {
    val settings = settingsState.settings
    val labels =
        listOf(
            "settings.theme",
            "settings.language",
            "settings.rtl",
            "settings.reduceMotion",
            "settings.piExecutable",
        )
    val targets = labels.map { rememberLabelTarget() }
    labels.forEachIndexed { row, key ->
        Label(
            message(key),
            SwingModifier
                .item(
                    gridx = 0,
                    gridy = row,
                    anchor = GridBagConstraints.LINE_START,
                    insets = Insets(4, 0, 4, 12),
                ).labelFor(targets[row]),
        )
    }

    fun control(row: Int) =
        SwingModifier
            .item(
                gridx = 1,
                gridy = row,
                weightx = 1.0,
                fill = GridBagConstraints.HORIZONTAL,
                insets = Insets(4, 0, 4, 0),
            )
    ThemePreference(settingsState, control(0).labelTarget(targets[0]))
    ComboBox(
        items = AppLanguage.entries.map { it.displayName },
        selectedItem = settings.language.displayName,
        onSelectionChange = { name ->
            AppLanguage.entries.firstOrNull { it.displayName == name }?.let(settingsState::selectLanguage)
        },
        modifier = control(1).labelTarget(targets[1]).testTag("settings-language"),
    )
    CheckBox(
        "",
        settings.isRightToLeft,
        settingsState::setRightToLeft,
        control(2).labelTarget(targets[2]).testTag("settings-rtl"),
    )
    CheckBox(
        "",
        settings.isReduceMotion,
        settingsState::setReduceMotion,
        control(3).labelTarget(targets[3]).testTag("settings-reduce-motion"),
    )
    ExecutableField(draft, onDraftChange, onApplyExecutable, control(4), SwingModifier.labelTarget(targets[4]))
}

@Composable
private fun ThemePreference(
    settingsState: SettingsState,
    modifier: SwingModifier,
) {
    val themes = listOf(ThemeMode.Light, ThemeMode.Dark)
    val themeNames = listOf(message("settings.theme.light"), message("settings.theme.dark"))
    ComboBox(
        items = themeNames,
        selectedItem = themeNames[themes.indexOf(settingsState.settings.themeMode)],
        onSelectionChange = { name ->
            themeNames.indexOf(name).takeIf { it >= 0 }?.let { settingsState.selectTheme(themes[it]) }
        },
        modifier = modifier.testTag("settings-theme"),
    )
}

@Composable
private fun ExecutableField(
    draft: String,
    onDraftChange: (String) -> Unit,
    onApply: () -> Unit,
    modifier: SwingModifier,
    fieldModifier: SwingModifier,
) {
    val window = LocalWindow.current
    Panel(
        PanelLayout.Border(hgap = 6),
        modifier,
    ) {
        TextField(
            draft,
            onDraftChange,
            fieldModifier
                .center()
                .onAccept(onApply)
                .onFocus(onLost = onApply)
                .toolTip(
                    message("settings.piExecutable.prompt"),
                ).testTag("settings-pi-executable"),
            columns = 28,
        )
        Button(message("settings.browse"), {
            val chooser = executableChooser(draft)
            if (chooser.showOpenDialog(window) ==
                JFileChooser.APPROVE_OPTION
            ) {
                onDraftChange(chooser.selectedFile.path)
            }
        }, SwingModifier.lineEnd())
    }
}

internal fun executableChooser(executable: String): JFileChooser {
    val file = File(executable).absoluteFile
    return JFileChooser(file.parentFile).apply { selectedFile = file }
}
