package org.jetbrains.compose.swing.samples.agent.composer

import androidx.compose.runtime.Composable
import com.formdev.flatlaf.FlatClientProperties
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.disabledIcon
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather

/** The round button at the composer's end: an arrow that sends, replaced by a square that stops the running turn. */
@Composable
internal fun SendButton(
    isRunning: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    sendToolTip: @Nls String = message("composer.send"),
) {
    if (isRunning) {
        RoundButton(Feather.SQUARE, message("composer.stop"), true, onStop, modifier.testTag("composer-stop"))
    } else {
        RoundButton(
            Feather.ARROW_UP,
            message("composer.send"),
            canSend,
            onSend,
            modifier.testTag("composer-send"),
            sendToolTip,
        )
    }
}

/** A circular button in the default button's colors that shows only [icon]; [label] names it. */
@Composable
private fun RoundButton(
    icon: Ikon,
    label: @Nls String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    toolTip: @Nls String = label,
) {
    Button(
        "",
        onClick,
        modifier
            .styleClass(StyleClass.Primary)
            .clientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT)
            .clientProperty(FlatClientProperties.SQUARE_SIZE, true)
            .icon(rememberThemedIcon(icon, IconTint.OnAccent))
            .disabledIcon(rememberThemedIcon(icon, IconTint.Muted))
            .enabled(enabled)
            .toolTip(toolTip)
            .accessibleName(label),
    )
}
