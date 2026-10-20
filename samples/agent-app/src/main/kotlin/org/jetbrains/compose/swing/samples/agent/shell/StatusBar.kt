package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.RotatingIcon
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather

/**
 * The bottom line: whether a turn runs, which agent and model reply, and the session's token count. It also says
 * when [hasWriteFailure] is set, because some change could not be stored.
 */
@Composable
internal fun ConstrainedScope.StatusBar(
    agentName: String,
    model: String,
    tokens: Int,
    isRunning: Boolean,
    hasWriteFailure: Boolean,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    val small = SwingModifier.font(theme.fonts.small).foreground(theme.colors.mutedText)
    Row(
        modifier
            .fillMaxWidth()
            .height(26)
            .background(theme.colors.background, RectangleShape)
            .drawBehind { drawRect(theme.colors.border, height = 1f) }
            .padding(horizontal = 12),
        horizontalArrangement = Arrangement.spacedBy(12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6), verticalAlignment = Alignment.CenterVertically) {
            if (isRunning) {
                StatusSpinner()
            } else {
                Label("", SwingModifier.icon(rememberThemedIcon(Feather.CHECK_CIRCLE, IconTint.Success, size = 12)))
            }
            Label(message(if (isRunning) "status.running" else "status.ready"), small)
        }
        when {
            agentName.isNotEmpty() && model.isNotEmpty() -> Label(message("status.agentModel", agentName, model), small)
            agentName.isNotEmpty() -> Label(agentName, small)
            model.isNotEmpty() -> Label(model, small)
        }
        Box(SwingModifier.weight(1f))
        if (hasWriteFailure) Label(message("status.saveFailed"), small.foreground(theme.colors.danger))
        Label(message("status.tokens", tokens), small)
    }
}

/** The loader glyph turning while a turn runs; composed only then, since it keeps the frame clock busy. */
@Composable
private fun RowScope.StatusSpinner() {
    val degrees by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spinner angle",
    )
    RotatingIcon(Feather.LOADER, degrees = { degrees }, tint = IconTint.Accent, size = 12)
}
