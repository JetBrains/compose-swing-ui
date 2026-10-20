package org.jetbrains.compose.swing.samples.agent.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.iconTextGap
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.components.CountBadge
import org.jetbrains.compose.swing.samples.agent.components.EmptyState
import org.jetbrains.compose.swing.samples.agent.components.Section
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.TouchedFile
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather

/** The files the agent read or edited, each with the lines its edits added and removed. */
@Composable
internal fun ColumnScope.FilesSection(files: List<TouchedFile>) {
    Section(message("context.files")) {
        if (files.isEmpty()) {
            EmptyState(Feather.FILE, message("context.files.empty"))
        } else {
            Card(SwingModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2)) {
                files.forEach { file -> key(file.path) { FileRow(file) } }
            }
        }
    }
}

@Composable
private fun ColumnScope.FileRow(file: TouchedFile) {
    val theme = LocalAgentTheme.current
    val folder = file.path.substringBeforeLast('/', missingDelimiterValue = "")
    Row(
        SwingModifier.fillMaxWidth().padding(horizontal = 8, vertical = 6),
        horizontalArrangement = Arrangement.spacedBy(8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(
            file.path.substringAfterLast('/'),
            SwingModifier
                .weight(1f)
                .width(0)
                .icon(rememberThemedIcon(Feather.FILE_TEXT))
                .iconTextGap(8)
                .font(theme.fonts.body)
                .foreground(theme.colors.text),
        )

        if (folder.isNotEmpty()) {
            Label(
                folder,
                SwingModifier
                    .weight(1f)
                    .width(0)
                    .font(theme.fonts.small)
                    .foreground(theme.colors.mutedText),
            )
        }
        if (file.added > 0) CountBadge(message("diff.added", file.added), style = StyleClass.SuccessBadge)
        if (file.removed > 0) CountBadge(message("diff.removed", file.removed), style = StyleClass.DangerBadge)
    }
}
