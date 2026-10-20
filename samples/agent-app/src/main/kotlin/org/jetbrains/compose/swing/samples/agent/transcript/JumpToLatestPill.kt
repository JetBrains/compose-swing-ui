package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import org.kordamp.ikonli.feather.Feather

/** A small floating pill button that takes the reader back to the newest content. */
@Composable
internal fun JumpToLatestPill(
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    Button(
        message("transcript.jumpToLatest"),
        onClick = onClick,
        modifier =
            modifier
                .styleClass(StyleClass.Pill)
                .icon(rememberThemedIcon(Feather.ARROW_DOWN, IconTint.Text, size = 14))
                .testTag("jump-to-latest"),
    )
}
