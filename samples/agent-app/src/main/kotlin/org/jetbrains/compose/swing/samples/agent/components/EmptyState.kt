package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.border
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.samples.agent.theme.IconLabel
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.kordamp.ikonli.Ikon

/** What a section shows while it has nothing to list. */
@Composable
internal fun ColumnScope.EmptyState(
    icon: Ikon,
    text: @Nls String,
) {
    val theme = LocalAgentTheme.current
    val shape = theme.shapes.medium
    Row(
        SwingModifier.fillMaxWidth().border(1, theme.colors.border, shape).padding(horizontal = 12, vertical = 10),
        horizontalArrangement = Arrangement.spacedBy(8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconLabel(icon)
        Label(text, SwingModifier.font(theme.fonts.small).foreground(theme.colors.mutedText))
    }
}
