package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.window.LocalWindow
import java.awt.Window
import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * Returns the action that starts a session in the folder [chooseFolder] picks, and starts none when it returns
 * `null`. The chooser gets the title of its dialog.
 */
@Composable
internal fun AppState.newSessionAction(chooseFolder: (String) -> Path? = rememberFolderChooser()): () -> Unit {
    val title = message("sessions.folder.choose")
    return { chooseFolder(title)?.let { newSession(folder = it) } }
}

/**
 * Sends a prompt like [AppState.send]. When no session is open it first asks where the new session works, and
 * sends nothing if the user cancels. Returns whether the prompt was sent.
 */
internal fun AppState.sendChoosingFolder(
    text: String,
    chooseFolder: () -> Path?,
): Boolean {
    val isReady = text.isNotBlank() && !runner.isRunning && selection.canSend
    return when {
        !isReady -> false
        activeSession != null -> send(text)
        else -> chooseFolder()?.let { send(text, it) } ?: false
    }
}

/** [sendChoosingFolder] with the folder chooser of the window. */
@Composable
internal fun AppState.sendAction(chooseFolder: (String) -> Path? = rememberFolderChooser()): (String) -> Boolean {
    val title = message("sessions.folder.choose")
    return { text -> sendChoosingFolder(text) { chooseFolder(title) } }
}

private fun pickFolder(
    title: String,
    window: Window?,
): Path? {
    val chooser =
        JFileChooser(System.getProperty("user.home")).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = title
        }
    return if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}

@Composable
@ReadOnlyComposable
internal fun rememberFolderChooser(): (String) -> Path? {
    val window = LocalWindow.current
    return { title -> pickFolder(title, window) }
}
