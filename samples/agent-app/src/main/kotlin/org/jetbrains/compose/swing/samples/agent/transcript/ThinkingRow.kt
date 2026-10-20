package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.expandVertically
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.shrinkVertically
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.samples.agent.components.ProseText
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather
import java.awt.Cursor
import javax.swing.SwingConstants

/**
 * The agent's reasoning as a compact row. While [isDone] is `false` it shows [text] as it streams; once done it
 * collapses to a one-line summary that a click expands again.
 */
@Composable
internal fun ConstrainedScope.ThinkingRow(
    text: String,
    isDone: Boolean,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    var isExpanded by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    val showsText = !isDone || isExpanded
    val headerModifier =
        SwingModifier
            .onHover(onEnter = { hovered = true }, onExit = { hovered = false })
            .onPointerEvent(onClick = { if (isDone) isExpanded = !isExpanded })
            .cursor(Cursor.getPredefinedCursor(if (isDone) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6)) {
        Row(
            headerModifier
                .fillMaxWidth()
                // Padded like a tool card's header, so the icon and the chevron line up with the cards' columns.
                .padding(horizontal = 12),
            horizontalArrangement = Arrangement.spacedBy(8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isLit = hovered && isDone
            val tint = if (isLit) theme.colors.text else theme.colors.mutedText
            Label(
                "",
                headerModifier.icon(rememberThemedIcon(Feather.ZAP, if (isLit) IconTint.Text else IconTint.Muted, 14)),
            )
            if (isDone) {
                Label(message("transcript.thought"), headerModifier.font(theme.fonts.body).foreground(tint))
                // A one-line digest. Preferring no width, it ends with an ellipsis where the row runs out of room
                // instead of widening the transcript to its full length.
                Label(
                    if (showsText) "" else text.lineSequence().first(),
                    headerModifier
                        .weight(1f)
                        .width(0)
                        .font(theme.fonts.small)
                        .foreground(theme.colors.mutedText),
                )
                DisclosureButton(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = it },
                    text = "",
                    label = message("transcript.thought"),
                )
            } else {
                Label(message("transcript.thinking"), SwingModifier.font(theme.fonts.body))
            }
        }
        AnimatedVisibility(
            visible = showsText && text.isNotEmpty(),
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            label = "thinking text",
        ) {
            ThinkingText(text)
        }
    }
}

@Composable
private fun ConstrainedScope.ThinkingText(text: String) {
    val theme = LocalAgentTheme.current
    // A rule on the leading edge, as tall as the text; the start padding lines it up under the icon.
    Box(SwingModifier.fillMaxWidth()) {
        Box(SwingModifier.matchParentSize().padding(start = 18)) {
            Separator(SwingModifier.width(2).fillMaxHeight(), SwingConstants.VERTICAL)
        }
        ProseText(
            text,
            theme.fonts.small,
            theme.colors.mutedText,
            SwingModifier.fillMaxWidth().padding(start = 34, end = 12),
        )
    }
}
