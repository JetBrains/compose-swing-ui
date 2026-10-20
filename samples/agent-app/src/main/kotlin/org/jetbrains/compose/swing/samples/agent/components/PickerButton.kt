package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.PopupMenu
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather

/**
 * A [ToolbarButton] showing [text] and a chevron that opens [content] in a menu under it. Each item gets the
 * callback that closes the menu.
 */
@Composable
@SwingComposable
internal fun PickerButton(
    text: @Nls String,
    toolTip: @Nls String,
    modifier: SwingModifier = SwingModifier,
    content:
        @Composable @SwingMenuComposable
        (close: () -> Unit) -> Unit,
) {
    val anchor = rememberPopupAnchor()
    var expanded by remember { mutableStateOf(false) }
    ToolbarButton(
        onClick = { expanded = true },
        modifier = modifier.popupAnchor(anchor),
        text = text,
        icon = rememberThemedIcon(Feather.CHEVRON_DOWN, size = 12),
        textBeforeIcon = true,
        toolTip = toolTip,
    )
    PopupMenu(anchor, expanded, onDismiss = { expanded = false }) {
        content { expanded = false }
    }
}

/** One choice of a picker menu, with a check mark while it is the current one. */
@Composable
@SwingMenuComposable
internal fun ChoiceItem(
    text: @Nls String,
    current: Boolean,
    modifier: SwingModifier = SwingModifier,
    onChoose: () -> Unit,
) {
    // Choosing the current entry again keeps its mark: the declared state wins over the toggle.
    CheckBoxMenuItem(text, checked = current, onCheckedChange = { onChoose() }, modifier = modifier)
}
