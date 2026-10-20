package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.formdev.flatlaf.FlatClientProperties
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.slideInVertically
import org.jetbrains.compose.swing.animation.togetherWith
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.PermissionDecision
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import org.kordamp.ikonli.feather.Feather

/**
 * The agent asking before it runs a tool: what it wants to run, with Allow once, Always allow and Deny. Once
 * answered, it folds into a one-line note of the answer.
 */
@Composable
internal fun ConstrainedScope.PermissionCard(
    permission: TranscriptItem.Permission,
    onDecision: (PermissionDecision) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    // A card arriving mid-turn slides in; one answered before, in a session opened later, is simply there.
    val appearance = remember { MutableTransitionState(permission.decision != null).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appearance,
        modifier = modifier,
        enter = slideInVertically { -it / 2 } + fadeIn(),
        label = "permission card",
    ) {
        // Each state emits its own card, so the warning outline goes with the prompt instead of switching classes.
        AnimatedContent(
            targetState = permission.decision,
            modifier = SwingModifier.fillMaxWidth(),
            transitionSpec = { fadeIn(tween(220, delayMillis = 80)) togetherWith fadeOut(tween(100)) },
            label = "permission decision",
        ) { decision ->
            if (decision == null) {
                Card(SwingModifier.fillMaxWidth(), style = StyleClass.WarningCard) {
                    PermissionPrompt(permission, onDecision)
                }
            } else {
                Card(SwingModifier.fillMaxWidth(), style = StyleClass.RaisedCard) {
                    PermissionSummary(permission, decision)
                }
            }
        }
    }
}

@Composable
private fun ConstrainedScope.PermissionPrompt(
    permission: TranscriptItem.Permission,
    onDecision: (PermissionDecision) -> Unit,
) {
    val theme = LocalAgentTheme.current
    Column(SwingModifier.fillMaxWidth().padding(5), verticalArrangement = Arrangement.spacedBy(10)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8), verticalAlignment = Alignment.CenterVertically) {
            Label("", SwingModifier.icon(rememberThemedIcon(Feather.SHIELD, IconTint.Warning)))
            Label(message("permission.question", toolLabel(permission.tool)), SwingModifier.font(theme.fonts.title))
        }
        OutputText(permission.detail, isError = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8)) {
            Button(
                message("permission.allowOnce"),
                onClick = { onDecision(PermissionDecision.Allow) },
                modifier = SwingModifier.styleClass(StyleClass.Primary),
            )
            Button(message("permission.always"), onClick = { onDecision(PermissionDecision.AllowAlways) })
            Button(
                message("permission.deny"),
                onClick = { onDecision(PermissionDecision.Deny) },
                modifier =
                    SwingModifier
                        .clientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS)
                        .styleClass(StyleClass.Destructive),
            )
        }
    }
}

/** The answer in one line: what was decided, then what it was about. */
@Composable
private fun ConstrainedScope.PermissionSummary(
    permission: TranscriptItem.Permission,
    decision: PermissionDecision,
) {
    val theme = LocalAgentTheme.current
    val color = if (decision == PermissionDecision.Deny) theme.colors.danger else theme.colors.success
    Row(
        SwingModifier.fillMaxWidth().padding(horizontal = 5, vertical = 1),
        horizontalArrangement = Arrangement.spacedBy(8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon =
            when (decision) {
                PermissionDecision.Allow -> Feather.CHECK
                PermissionDecision.AllowAlways -> Feather.CHECK_CIRCLE
                PermissionDecision.Deny -> Feather.SLASH
            }
        val tint = if (decision == PermissionDecision.Deny) IconTint.Danger else IconTint.Success
        Label("", SwingModifier.icon(rememberThemedIcon(icon, tint)))
        Label(
            message(
                when (decision) {
                    PermissionDecision.Allow -> "permission.allowed"
                    PermissionDecision.AllowAlways -> "permission.alwaysAllowed"
                    PermissionDecision.Deny -> "permission.denied"
                },
            ),
            SwingModifier.font(theme.fonts.body).foreground(color),
        )
        Label(
            permission.detail.lineSequence().first(),
            SwingModifier.weight(1f).font(theme.fonts.mono).foreground(theme.colors.mutedText),
        )
    }
}
