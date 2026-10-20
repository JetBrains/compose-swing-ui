package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.window.LocalWindow
import javax.swing.JOptionPane

/** Asks whether to remove a session's whole history, which cannot be undone; `true` when the user agrees. */
@Composable
internal fun rememberConfirmClear(): () -> Boolean =
    rememberConfirmation(message("sessions.clear.action"), message("sessions.clear.confirm"))

@Composable
internal fun rememberConfirmDelete(): () -> Boolean =
    rememberConfirmation(message("sessions.delete.action"), message("sessions.delete.confirm"))

@Composable
private fun rememberConfirmation(
    title: @Nls String,
    prompt: @Nls String,
): () -> Boolean {
    val window = LocalWindow.current
    val cancel = message("dialog.cancel")
    return {
        val choice =
            JOptionPane.showOptionDialog(
                window,
                prompt,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                arrayOf(title, cancel),
                cancel,
            )
        choice == 0
    }
}

internal fun AppState.clearWithConfirmation(
    id: String,
    confirm: () -> Boolean,
) {
    if (confirm()) clearSession(id)
}
