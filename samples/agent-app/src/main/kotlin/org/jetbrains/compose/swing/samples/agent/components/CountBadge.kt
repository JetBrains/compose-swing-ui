package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.styleClass

/** A small pill showing a count as [text], styled by [style]: a neutral, success or danger badge class. */
@Composable
internal fun CountBadge(
    text: @Nls String,
    modifier: SwingModifier = SwingModifier,
    style: StyleClass = StyleClass.Badge,
) {
    Label(text, modifier.font(LocalAgentTheme.current.fonts.mono).styleClass(style))
}
