package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.current
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleDescription
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.samples.agent.components.ToolbarButton
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather

/** A keyboard-operable disclosure whose icon and accessible description reflect its expanded state. */
@Composable
internal fun DisclosureButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    text: @Nls String,
    modifier: SwingModifier = SwingModifier,
    label: @Nls String = text,
) {
    val leftToRight = DefaultComponentOrientation.current?.isLeftToRight != false
    val icon =
        when {
            expanded -> Feather.CHEVRON_DOWN
            leftToRight -> Feather.CHEVRON_RIGHT
            else -> Feather.CHEVRON_LEFT
        }
    ToolbarButton(
        onClick = { onExpandedChange(!expanded) },
        modifier =
            modifier
                .accessibleDescription(message(if (expanded) "disclosure.expanded" else "disclosure.collapsed"))
                .onKeyStroke("ENTER") { onExpandedChange(!expanded) },
        text = text,
        icon = rememberThemedIcon(icon),
        toolTip = label,
    )
}
