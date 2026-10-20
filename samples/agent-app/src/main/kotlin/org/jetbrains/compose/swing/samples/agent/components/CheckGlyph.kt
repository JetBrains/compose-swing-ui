package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather
import java.awt.Font
import java.awt.font.TextAttribute
import javax.swing.Icon

/** A plan step's mark: a checked circle once [isDone], an accent ring while [isCurrent], a muted ring otherwise. */
@Composable
internal fun rememberCheckIcon(
    isDone: Boolean,
    isCurrent: Boolean,
): Icon {
    val (ikon, tint) =
        when {
            isDone -> Feather.CHECK_CIRCLE to IconTint.Success
            isCurrent -> Feather.CIRCLE to IconTint.Accent
            else -> Feather.CIRCLE to IconTint.Muted
        }
    return rememberThemedIcon(ikon, tint)
}

/** A label showing only the [rememberCheckIcon] mark. */
@Composable
internal fun CheckGlyph(
    isDone: Boolean,
    isCurrent: Boolean,
    modifier: SwingModifier = SwingModifier,
) {
    Label("", modifier.icon(rememberCheckIcon(isDone, isCurrent)))
}

/** The font a plan step's text takes: struck through once the step [isDone]. */
@Composable
internal fun planStepFont(isDone: Boolean): Font {
    val body = LocalAgentTheme.current.fonts.body
    return remember(body, isDone) {
        if (isDone) body.deriveFont(mapOf(TextAttribute.STRIKETHROUGH to TextAttribute.STRIKETHROUGH_ON)) else body
    }
}
