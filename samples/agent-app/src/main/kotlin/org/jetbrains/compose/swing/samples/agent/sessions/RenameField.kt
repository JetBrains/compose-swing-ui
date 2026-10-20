package org.jetbrains.compose.swing.samples.agent.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.formdev.flatlaf.FlatClientProperties
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.modifier.interaction.onAccept
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.modifier.interaction.rememberFocusRequester
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.samples.agent.i18n.message

/**
 * The title as an editable field, selected and focused as it appears. Enter or leaving the field commits; Escape
 * cancels.
 */
@Composable
internal fun RowScope.RenameField(
    title: @Nls String,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val document = rememberDocumentState(title)
    val focus = rememberFocusRequester()
    var isEditing by remember { mutableStateOf(true) }
    val commit = {
        if (isEditing) {
            isEditing = false
            onCommit(document.text.toString())
        }
    }
    LaunchedEffect(Unit) {
        document.edit { selectAll() }
        focus.requestFocus()
    }
    TextField(
        document,
        SwingModifier
            .weight(1f)
            .width(0)
            .clientProperty(FlatClientProperties.PLACEHOLDER_TEXT, message("sessions.untitled"))
            .focusRequester(focus)
            .testTag("rename-field")
            .onAccept(commit)
            .onFocus(onLost = commit)
            .onKeyStroke("ESCAPE") {
                isEditing = false
                onCancel()
            },
    )
}
