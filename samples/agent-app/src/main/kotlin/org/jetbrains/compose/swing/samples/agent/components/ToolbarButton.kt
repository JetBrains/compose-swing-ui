package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.formdev.flatlaf.FlatClientProperties
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.horizontalTextPosition
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import javax.swing.Icon
import javax.swing.SwingConstants

/**
 * A toolbar-style push button: no fill at rest, the look and feel's fill while hovered or pressed. It shows [icon]
 * and [text], the text first when [textBeforeIcon]. A screen reader names it by its [text], or by its [toolTip] when
 * it shows only an icon.
 */
@Composable
internal fun ToolbarButton(
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    text: @Nls String = "",
    icon: Icon? = null,
    textBeforeIcon: Boolean = false,
    toolTip: @Nls String? = null,
) {
    Button(text, onClick, toolbarModifier(modifier, text.ifEmpty { toolTip }, icon, textBeforeIcon, toolTip))
}

/** A [ToolbarButton] that stays filled with the look and feel's selected color while [selected]. */
@Composable
internal fun ToolbarToggleButton(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
    text: @Nls String = "",
    icon: Icon? = null,
    toolTip: @Nls String? = null,
    accessibleLabel: @Nls String? = null,
) {
    ToggleButton(
        text,
        selected,
        onSelectedChange,
        toolbarModifier(modifier, accessibleLabel ?: text.ifEmpty { toolTip }, icon, textBeforeIcon = false, toolTip),
    )
}

private fun toolbarModifier(
    modifier: SwingModifier,
    accessibleLabel: @Nls String?,
    icon: Icon?,
    textBeforeIcon: Boolean,
    toolTip: @Nls String?,
): SwingModifier =
    modifier
        .clientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        .icon(icon)
        .horizontalTextPosition(if (textBeforeIcon) SwingConstants.LEADING else SwingConstants.TRAILING)
        .toolTip(toolTip)
        .accessibleName(accessibleLabel)
