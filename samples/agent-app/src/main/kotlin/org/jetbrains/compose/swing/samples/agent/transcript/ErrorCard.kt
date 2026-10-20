package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.components.ProseText
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather

/** A turn that failed: what went wrong, then whatever the agent said about it. */
@Composable
internal fun ConstrainedScope.ErrorCard(
    error: TranscriptItem.Failure,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    val exitCode = error.exitCode
    val title = if (exitCode == null) message("transcript.error") else message("transcript.error.exitCode", exitCode)
    Card(modifier.testTag("error-card"), style = StyleClass.DangerCard) {
        Column(SwingModifier.fillMaxWidth().padding(5), verticalArrangement = Arrangement.spacedBy(8)) {
            Row(
                SwingModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("", SwingModifier.icon(rememberThemedIcon(Feather.ALERT_TRIANGLE, IconTint.Danger)))
                Label(title, SwingModifier.font(theme.fonts.body))
                Label(
                    error.detail.lineSequence().first(),
                    SwingModifier.weight(1f).font(theme.fonts.body),
                )
            }
            val body = error.detail.substringAfter('\n', "")
            if (body.isNotBlank()) {
                ProseText(body, theme.fonts.mono, theme.colors.text, SwingModifier.fillMaxWidth())
            }
        }
    }
}
