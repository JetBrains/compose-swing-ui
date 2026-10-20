package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.IconLabel
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.kordamp.ikonli.feather.Feather

/** What an empty session shows: a calm invitation to write the first prompt. */
@Composable
internal fun ConstrainedScope.EmptyTranscript(modifier: SwingModifier = SwingModifier) {
    val theme = LocalAgentTheme.current
    Column(
        modifier.padding(top = 120),
        verticalArrangement = Arrangement.spacedBy(8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconLabel(Feather.MESSAGE_CIRCLE, SwingModifier.padding(bottom = 4), size = 28)
        Label(message("transcript.empty.title"), SwingModifier.font(theme.fonts.title).foreground(theme.colors.text))
        Label(message("transcript.empty"), SwingModifier.font(theme.fonts.body).foreground(theme.colors.mutedText))
    }
}
