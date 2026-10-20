package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.IconLabel
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.kordamp.ikonli.feather.Feather

/** What a finished turn cost, as a quiet line under it. */
@Composable
internal fun ConstrainedScope.UsageFooter(
    inputTokens: Int,
    outputTokens: Int,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6), verticalAlignment = Alignment.CenterVertically) {
        IconLabel(Feather.ACTIVITY, size = 12)
        Label(
            message("transcript.usage", inputTokens, outputTokens),
            SwingModifier.font(theme.fonts.small).foreground(theme.colors.mutedText),
        )
    }
}
